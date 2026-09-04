package io.github.langqi99.aeallpattern.internal.routing.core.planner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.ToLongFunction;

import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.CraftingRoutePolicy;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.RoutingPatternMetadata;

/**
 * v2 autocrafting planner: an iterative linear backbone plus conflict-directed anytime search over a
 * shared mutable pool, with byproduct reuse and bounded backtracking for contended choices.
 *
 * <p>This is the evolution of the v1 planner (closed-form two-pass, removed). It keeps v1's strengths
 * — quantity-independent batching ({@code ceil} arithmetic), {@code returned} (container/catalyst)
 * inputs in closed form, saturating arithmetic — and adds:
 *
 * <ul>
 *   <li><b>In-engine cycle breaking ("去头尾")</b>: instead of declining when the recipe graph has a
 *       cycle, a DFS from the target drops back-edges, keeping the recipe direction toward the target
 *       and cutting the reverse. A compress/decompress pair (1 block ⇄ 9 ingots) is planned directly;
 *       the reverse side resolves from stock/missing. Cuts only remove options, so feasibility is never
 *       overstated (no false positives).</li>
 *   <li><b>Shared pool + byproducts</b>: stock and crafting byproducts live in one mutable pool;
 *       demand draws byproducts first, then stock, then crafts. Multi-output patterns are supported
 *       (a pattern's extra outputs feed sibling demands).</li>
 *   <li><b>Dynamic-capacity greedy</b>: among an item's recipes, the one with the highest current
 *       capacity ({@code stock + craftable}) is preferred. Concrete fuzzy expansions of one real
 *       recipe are kept in a source group and consume already-stocked variants before recursively
 *       crafting another accepted variant.</li>
 *   <li><b>Budgeted backtracking</b>: contended items (more than one recipe) are searched in
 *       capacity order with a {@code trail} for commit/rollback. No node drops candidates at a fixed
 *       route count: a hot node re-ranks every materially distinct route against the current pools and
 *       search continues while the plan-wide deterministic work budget remains. Failed speculative
 *       subtrees are memoized only for the exact node, amount, depth and rollback-restored availability
 *       state, preventing repeated proof work without reusing a stale inventory result.</li>
 *   <li><b>Conflict-directed anytime replay</b>: if a locally successful child route consumes stock
 *       later needed by a sibling, the planner replays the whole request with the implicated choice
 *       changed. The first complete plan is returned immediately; otherwise a graph-scaled
 *       {@code O(E log E)} budget keeps the best concrete partial plan and reports its actionable
 *       missing materials instead of delegating the pathological graph back to AE2.</li>
 *   <li><b>Consumable-bound no-goods</b>: an unavoidable direct raw-consumable shortage is proven
 *       against the exact current pool and quantity, rather than tied to a whole recipe identity.
 *       Thus materially different routes that both require eight unavailable C are pruned alike,
 *       while a route requiring one available C remains eligible.</li>
 * </ul>
 *
 * <p><b>Soundness (no false positives):</b> the pool is never overdrawn (a draw is capped by what is
 * actually present), so a plan reports {@link CraftPlan#feasible() feasible} only when every demand was
 * met from stock or from a craft whose own inputs were met. Shortfalls always surface in
 * {@link CraftPlan#missing()}.
 */
@SuppressWarnings("DataFlowIssue")
public final class CraftPlannerV2<K> {

    /**
     * Default hot-node threshold. It does not truncate search: after this many visits the node merely
     * switches from its immutable capacity order to current-pool re-ranking. Normal graphs are resolved
     * by the linear backbone or recover via a handful of backtracks, never approaching this threshold.
     */
    public static final int DEFAULT_VISIT_CAP = 256;

    /** Upper guard for the graph-scaled {@code O(E log E)} default search budget. */
    public static final int DEFAULT_SEARCH_WORK_BUDGET =
            Math.max(4_096, Integer.getInteger("aeallpattern.maxCraftSearchWork", 262_144));

    private static final int MIN_SEARCH_WORK_BUDGET = 4_096;
    private static final int FALLBACK_WORK_PER_REACHABLE_UNIT = 64;
    private static final int MAX_FALLBACK_WORK_BUDGET = 262_144;

    /**
     * Maximum number of alternate roots tried for a proven conservative conversion SCC. This is a
     * fixed bound, so cycle orientation remains linear in graph size rather than enumerating cuts.
     */
    static final int MAX_CONVERSION_ORIENTATION_RETRIES = 4;

    /**
     * Stack-overflow safety net for the bounded fallback search. {@link #obtain} recurses once per
     * crafting edge along a single root-to-leaf path ({@code obtain → fire → obtain}), so its stack depth
     * equals the depth of the (acyclic) recipe DAG. The clean linear backbone ({@link #linearPass}) is
     * fully iterative and resolves every feasible, non-contended request without recursing — the
     * recursion is entered only when that backbone reports infeasible or hits contention. To keep a
     * pathologically deep recipe chain from overflowing the calculating thread's stack, descent past this
     * many levels degrades only that branch to "missing" (Policy A), allowing its parent to try another
     * route. 256 levels (~512 stack frames with the paired {@code fire}) is far
     * deeper than any real Minecraft recipe chain yet safe on a default thread stack; overridable via
     * {@code -Daeallpattern.maxCraftDepth} for unusual {@code -Xss} setups.
     */
    public static final int MAX_OBTAIN_DEPTH =
            Math.max(16, Integer.getInteger("aeallpattern.maxCraftDepth", 256));

    private final CraftGraph<K> graph;
    private final int visitCap;
    private final SearchBudget searchBudget;
    private final FallbackBudget fallbackBudget;
    private final Map<K, CraftPattern<K>> routePreferences;
    private final CraftingRoutePolicy routePolicy;
    private final DiagnosticsCollector diagnostics;
    /** Immutable graph analysis shared by every replay with the same cycle orientation. */
    private PreparedGraph<K> preparedGraph;
    private final Set<K> cutOutputs = new LinkedHashSet<>();
    private final Map<CraftPattern<K>, Set<K>> suppressedPositiveFeedbackOutputs =
            new IdentityHashMap<>();
    /** One physical remainder batch withheld from linear byproduct credit to start a feedback path. */
    private final Map<CraftPattern<K>, Map<K, Long>> linearContainerBootstrapReserves =
            new IdentityHashMap<>();
    private final Map<K, Long> reservedSelfSeeds = new HashMap<>();
    /**
     * A narrowly proven two-node startup path for a contracted loop:
     * {@code A -> returned seed B -> net A}. The normal {@code A -> B} converter is also the
     * requested final step, so one physical A must be held aside long enough to manufacture the
     * first B seed instead of being consumed as ordinary final-output input.
     */
    private final Map<CraftPattern<K>, List<FeedbackSeedBootstrap<K>>> feedbackSeedBootstraps =
            new IdentityHashMap<>();
    private final Map<CraftPattern<K>, List<FeedbackSeedBootstrap<K>>> feedbackSeedConverters =
            new IdentityHashMap<>();
    private final Map<FeedbackSeedBootstrap<K>, Long> reservedFeedbackSeedOutputs =
            new HashMap<>();
    /** Portion of each held feedback-output state borrowed from its private reusable-seed host. */
    private final Map<FeedbackSeedBootstrap<K>, Long> reservedFeedbackSeedHostOutputs =
            new HashMap<>();
    /** Proven ordinary feedback state machines, classified before linear/integer planning. */
    private List<ConservativeFeedbackAnalysis.Component<K>> conservativeFeedbackComponents = List.of();
    /** Component members that can obtain their first state from an acyclic producer. */
    private final Set<CraftPattern<K>> craftableConservativeFeedbackPatterns =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean requiresSeedOrderedPlanning;
    /** Ordinary unchanged catalysts may share one seed in the linear pass when no byproduct can feed it. */
    private final Set<K> ordinaryReturnedSeedKeys = new HashSet<>();
    private final Set<K> reachableByproductKeys = new HashSet<>();
    /**
     * Outputs whose resolution can reach a seed-order-sensitive pattern. Aggregating one of these
     * outputs could reorder sibling seed acquisitions, while ordinary dependency cones below the
     * sensitive pattern remain safe to fold into one topological sweep.
     */
    private final Set<K> seedOrderedDependencyCone = new HashSet<>();

    // Current recursion depth of the bounded fallback search (obtain/fire). Guards against stack overflow
    // on degenerate deep chains; see MAX_OBTAIN_DEPTH. Not part of the rolled-back planning state.
    private int depth;

    /** Memo for {@link #isAggregable}: which nodes resolve deterministically without route search. */
    private final Map<K, Boolean> aggregableMemo = new HashMap<>();
    /** Lazily built key set for {@link #byproductFeedableKeys()}. */
    private Set<K> byproductFeedableKeys;

    private final Map<K, List<CraftPattern<K>>> patternsByOutput = new HashMap<>();
    /**
     * Capacity is immutable after the DAG pass. Keep both the score and the stable order so fallback
     * retries do not repeatedly sort the same patterns and re-walk every input from inside TimSort's
     * comparator.
     */
    private final Map<CraftPattern<K>, Long> capacityScoreByPattern = new IdentityHashMap<>();
    private final Map<K, List<CraftPattern<K>>> capacityOrderByOutput = new HashMap<>();
    private final Map<CraftPattern<K>, Integer> routeDepth = new IdentityHashMap<>();
    /** Per-firing unavoidable ordinary raw inputs, aggregated by consumable rather than pattern slot. */
    private final Map<CraftPattern<K>, Map<K, Long>> directRawConsumablesByPattern =
            new IdentityHashMap<>();
    /**
     * Learned lower bounds keyed by the consumable and exact rollback-restored availability state.
     * The value is the smallest quantity already proven unavailable in that state, so the proof is
     * reusable across unrelated patterns with an equal or larger requirement.
     */
    private final Map<ConsumableProofState<K>, Long> provenDirectConsumableShortfalls =
            new HashMap<>();
    // Canonical, stock-independent material transformation for patterns whose whole downstream tree
    // can be proven simple and deterministic. Equal ids let one obtain() call search an equivalent
    // branch once instead of reopening the same dependency tree under a different intermediate key.
    private final Map<CraftPattern<K>, Integer> materialFootprintByPattern = new IdentityHashMap<>();
    private Map<K, Long> capacity;

    // Mutable planning state (all writes go through the trail so a branch can be rolled back).
    private final Map<K, Long> bpPool = new HashMap<>();      // byproduct / surplus supply
    private final Map<K, Long> stockLeft = new HashMap<>();   // remaining inventory snapshot
    private final Map<K, Long> usedStock = new HashMap<>();   // drawn from inventory
    /** Route-private host borrows that may still be reassigned by the global variant matcher. */
    private final Map<ReusableStockRouteKey<K>, Long> reusableBorrowedDemand = new HashMap<>();
    /** Returned non-exact variants, reusable only by the route/consumer that owns them. */
    private final Map<ReusableStockRouteKey<K>, Long> reusablePrivatePool = new HashMap<>();
    /** Returned exact variants, safely reusable by every route in the logical shared pool. */
    private final Map<ReusableStockKey<K>, Long> reusablePool = new HashMap<>();
    /** Exact host allocations already exposed as shared credit; these can no longer be rematched. */
    private final Map<ReusableStockUsageKey<K>, Long> pinnedExactReusableStock = new HashMap<>();
    private final Map<ReusableStockUsageKey<K>, Long> usedReusableStock = new HashMap<>();
    private final Map<K, Long> missing = new HashMap<>();     // unmet at raw leaves
    private final Map<K, Long> grossDemand = new HashMap<>(); // pre-extraction request totals (bytes)
    private final Map<CraftPattern<K>, Long> firings = new IdentityHashMap<>();
    /** Successful/committed contended decisions on the current trail, in execution order. */
    private final List<RouteDecision<K>> routeDecisions = new ArrayList<>();
    /** Decisions implicated in a later sibling's stock shortfall; these alone merit whole-plan replay. */
    private final List<RouteDecision<K>> replayRouteDecisions = new ArrayList<>();

    // Node-local search state is monotonic: rollback restores inventory, not knowledge already learned.
    private final Map<K, Integer> visit = new HashMap<>();
    // Exact failure memo for speculative calls. availabilityState is restored with trail rollback,
    // so a proof is reused only when node, amount and every availability-affecting map are identical.
    private final Set<SearchFailure<K>> failedSpeculativeSearches = new HashSet<>();
    private final Deque<Runnable> trail = new ArrayDeque<>();
    private long availabilityState;
    private long nextAvailabilityState;
    private int processed;

    // Running sum of all unmet (missing) amounts; trail-restored. A search branch is accepted iff it
    // introduces no new missing, so the decision survives nested single-recipe commits.
    private long missingTotal;

    private CraftPlannerV2(
            CraftGraph<K> graph,
            int visitCap,
            SearchBudget searchBudget,
            Map<K, CraftPattern<K>> routePreferences,
            CraftingRoutePolicy routePolicy,
            DiagnosticsCollector diagnostics) {
        this.graph = graph;
        this.visitCap = Math.max(1, visitCap);
        this.searchBudget = searchBudget;
        this.fallbackBudget = new FallbackBudget(diagnostics.fallbackBudgetLimit(), diagnostics);
        this.routePreferences = routePreferences;
        this.routePolicy = routePolicy;
        this.diagnostics = diagnostics;
    }

    private CraftPlannerV2(
            PreparedGraph<K> preparedGraph,
            int visitCap,
            SearchBudget searchBudget,
            Map<K, CraftPattern<K>> routePreferences,
            CraftingRoutePolicy routePolicy,
            DiagnosticsCollector diagnostics) {
        this(preparedGraph.graph, visitCap, searchBudget, routePreferences, routePolicy, diagnostics);
        this.preparedGraph = preparedGraph;
        loadPreparedGraph(preparedGraph);
    }

    public static <K> CraftPlan<K> plan(CraftGraph<K> graph, K target, long amount) {
        int reachableWork = reachableWorkEstimate(graph, target);
        return planDetailed(
                graph, target, amount, DEFAULT_VISIT_CAP,
                scaledSearchWorkBudget(reachableWork), reachableWork,
                null).plan();
    }

    public static <K> CraftPlan<K> plan(CraftGraph<K> graph, K target, long amount, int visitCap) {
        int reachableWork = reachableWorkEstimate(graph, target);
        return planDetailed(
                graph, target, amount, visitCap,
                scaledSearchWorkBudget(reachableWork), reachableWork,
                null).plan();
    }

    public static <K> CraftPlan<K> plan(
            CraftGraph<K> graph, K target, long amount, CraftingRoutePolicy routePolicy) {
        return planDetailed(graph, target, amount, routePolicy).plan();
    }

    static <K> CraftPlan<K> plan(
            CraftGraph<K> graph,
            K target,
            long amount,
            int visitCap,
            int searchWorkBudget) {
        return planDetailed(graph, target, amount, visitCap, searchWorkBudget).plan();
    }

    /**
     * Plans the request and returns measurements collected by that exact run. Diagnostics never cause
     * a second traversal or a second planning pass.
     */
    public static <K> PlanningResult<K> planDetailed(
            CraftGraph<K> graph, K target, long amount) {
        return planDetailed(graph, target, amount, (CraftingRoutePolicy) null);
    }

    public static <K> PlanningResult<K> planDetailed(
            CraftGraph<K> graph, K target, long amount, CraftingRoutePolicy routePolicy) {
        int reachableWork = reachableWorkEstimate(graph, target);
        return planDetailed(
                graph, target, amount, DEFAULT_VISIT_CAP,
                scaledSearchWorkBudget(reachableWork), reachableWork, routePolicy);
    }

    static <K> PlanningResult<K> planDetailed(
            CraftGraph<K> graph,
            K target,
            long amount,
            int visitCap,
            int searchWorkBudget) {
        return planDetailed(
                graph, target, amount, visitCap, searchWorkBudget,
                reachableWorkEstimate(graph, target), null);
    }

    private static <K> PlanningResult<K> planDetailed(
            CraftGraph<K> graph,
            K target,
            long amount,
            int visitCap,
            int searchWorkBudget,
            int replayCharge,
            CraftingRoutePolicy routePolicy) {
        long started = System.nanoTime();
        DiagnosticsCollector diagnostics =
                new DiagnosticsCollector(replayCharge, searchWorkBudget);
        if (amount <= 0) {
            CraftPlan<K> empty = new CraftPlan<>(
                    true, true, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 0, false);
            return new PlanningResult<>(empty, diagnostics.finish(started, null));
        }
        SearchBudget budget = new SearchBudget(searchWorkBudget, diagnostics);
        Map<List<K>, PreparedGraph<K>> preparedByOrientation = new HashMap<>();
        PlanVariant<K> firstVariant =
                new PlanVariant<>(List.of(), Map.of(), 0, 0L);
        CraftPlannerV2<K> firstPlanner =
                new CraftPlannerV2<>(
                        graph, visitCap, budget, firstVariant.routePreferences(), routePolicy, diagnostics);
        CraftPlan<K> first = firstPlanner.run(target, amount, firstVariant.priorityRoots());
        preparedByOrientation.put(firstVariant.priorityRoots(), firstPlanner.preparedGraph);
        if (first.feasible()) {
            return finish(first, diagnostics, budget, started);
        }
        if (first.budgetExhausted()) {
            return finish(first, diagnostics, budget, started);
        }
        CraftPlan<K> bestIncomplete = first;

        PriorityQueue<PlanVariant<K>> frontier = new PriorityQueue<>((left, right) -> {
            int byDiscrepancy = Integer.compare(left.discrepancies(), right.discrepancies());
            return byDiscrepancy != 0
                    ? byDiscrepancy
                    : Long.compare(left.sequence(), right.sequence());
        });
        Set<VariantKey<K>> queued = new HashSet<>();
        queued.add(firstVariant.key());
        long sequence = 1L;

        // Conversion-SCC orientations remain the first discrepancies, preserving the former retry
        // policy while sharing one global anytime-search budget with ordinary route deviations.
        if (!firstPlanner.cutOutputs.isEmpty()) {
            CycleAnalysis<K> cycleAnalysis = CycleAnalysis.analyze(graph, target);
            // The DFS cut position inside a cycle depends on sibling arrival order, so a bad cut can
            // surface its shortfall on ANOTHER member of the same cycle instead of on the recorded
            // cut output. Attribute missing at member granularity and try the most-starved
            // orientation first, so the bounded retries go to the cuts that actually hurt.
            List<Map.Entry<K, Long>> reorientCandidates = new ArrayList<>();
            for (K cutOutput : firstPlanner.cutOutputs) {
                if (!cycleAnalysis.mayReorient(cutOutput)) {
                    continue;
                }
                long attributedMissing = first.missing().getOrDefault(cutOutput, 0L);
                for (K member : cycleAnalysis.membersOf(cutOutput)) {
                    if (!member.equals(cutOutput)) {
                        attributedMissing = Sat.add(
                                attributedMissing, first.missing().getOrDefault(member, 0L));
                    }
                }
                if (attributedMissing > 0) {
                    reorientCandidates.add(Map.entry(cutOutput, attributedMissing));
                }
            }
            reorientCandidates.sort((left, right) -> Long.compare(right.getValue(), left.getValue()));
            int retries = 0;
            for (Map.Entry<K, Long> candidate : reorientCandidates) {
                if (retries >= MAX_CONVERSION_ORIENTATION_RETRIES) break;
                retries++;
                PlanVariant<K> variant =
                        new PlanVariant<>(List.of(candidate.getKey()), Map.of(), 1, sequence++);
                if (queued.add(variant.key())) {
                    frontier.add(variant);
                }
            }
        }

        EnqueueResult enqueue = enqueueRouteVariants(
                firstVariant, firstPlanner, frontier, queued, sequence, budget, replayCharge);
        sequence = enqueue.sequence();
        boolean frontierTruncated = enqueue.truncated();
        diagnostics.recordFrontierSize(frontier.size());

        while (!frontier.isEmpty()) {
            PlanVariant<K> variant = frontier.poll();
            if (!budget.tryConsume(replayCharge)) {
                return finish(markBudgetExhausted(bestIncomplete), diagnostics, budget, started);
            }
            PreparedGraph<K> prepared = preparedByOrientation.get(variant.priorityRoots());
            CraftPlannerV2<K> planner = prepared == null
                    ? new CraftPlannerV2<>(
                            graph, visitCap, budget, variant.routePreferences(), routePolicy, diagnostics)
                    : new CraftPlannerV2<>(
                            prepared, visitCap, budget, variant.routePreferences(), routePolicy, diagnostics);
            CraftPlan<K> candidate = planner.run(target, amount, variant.priorityRoots());
            preparedByOrientation.putIfAbsent(variant.priorityRoots(), planner.preparedGraph);
            if (candidate.feasible()) {
                return finish(candidate, diagnostics, budget, started);
            }
            if (candidate.budgetExhausted()) {
                return finish(
                        markBudgetExhausted(betterIncompletePlan(bestIncomplete, candidate)),
                        diagnostics, budget, started);
            }
            bestIncomplete = betterIncompletePlan(bestIncomplete, candidate);
            enqueue = enqueueRouteVariants(
                    variant, planner, frontier, queued, sequence, budget, replayCharge);
            sequence = enqueue.sequence();
            frontierTruncated |= enqueue.truncated();
            diagnostics.recordFrontierSize(frontier.size());
        }
        CraftPlan<K> result = frontierTruncated
                ? markBudgetExhausted(bestIncomplete)
                : bestIncomplete;
        return finish(result, diagnostics, budget, started);
    }

    private static <K> PlanningResult<K> finish(
            CraftPlan<K> plan,
            DiagnosticsCollector diagnostics,
            SearchBudget budget,
            long started) {
        return new PlanningResult<>(plan, diagnostics.finish(started, budget));
    }

    /**
     * The first planner run remains the ordinary linear/greedy path. Replays are charged by the whole
     * reachable graph size, so the scaled budget permits only {@code O(log E)} complete deviations and
     * keeps preprocessing plus fallback work near {@code O(E log E)}.
     */
    static <K> int scaledSearchWorkBudget(CraftGraph<K> graph, K target) {
        return scaledSearchWorkBudget(reachableWorkEstimate(graph, target));
    }

    private static int scaledSearchWorkBudget(int work) {
        int log = 32 - Integer.numberOfLeadingZeros(Math.max(1, work));
        long scaled = (long) work * (log + 4L);
        return (int) io.github.langqi99.aeallpattern.util.CompatMath.clamp(
                scaled, MIN_SEARCH_WORK_BUDGET, DEFAULT_SEARCH_WORK_BUDGET);
    }

    private static int fallbackWorkBudget(int reachableWork) {
        long scaled = (long) Math.max(1, reachableWork) * FALLBACK_WORK_PER_REACHABLE_UNIT;
        return (int) io.github.langqi99.aeallpattern.util.CompatMath.clamp(scaled, 64L, MAX_FALLBACK_WORK_BUDGET);
    }

    static <K> int reachableWorkEstimate(CraftGraph<K> graph, K target) {
        Set<K> seen = new HashSet<>();
        Deque<K> queue = new ArrayDeque<>();
        seen.add(target);
        queue.add(target);
        long work = 0L;
        while (!queue.isEmpty()) {
            K key = queue.removeFirst();
            work++;
            for (CraftPattern<K> pattern : graph.patternsFor(key)) {
                work++;
                for (CraftInput<K> input : pattern.inputs()) {
                    work++;
                    if (seen.add(input.key())) {
                        queue.addLast(input.key());
                    }
                }
            }
            if (work >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return Math.max(1, (int) work);
    }

    private static <K> EnqueueResult enqueueRouteVariants(
            PlanVariant<K> parent,
            CraftPlannerV2<K> planner,
            PriorityQueue<PlanVariant<K>> frontier,
            Set<VariantKey<K>> queued,
            long sequence,
            SearchBudget budget,
            int replayCharge) {
        int affordableReplays = budget.remaining() / Math.max(1, replayCharge);
        int availableSlots = Math.max(0, affordableReplays - frontier.size());
        RouteAlternatives<K> routeAlternatives = planner.routeAlternatives(availableSlots);
        for (RouteAlternative<K> alternative : routeAlternatives.alternatives()) {
            Map<K, CraftPattern<K>> preferences =
                    new HashMap<>(parent.routePreferences());
            if (alternative.pattern() == alternative.defaultPattern()) {
                preferences.remove(alternative.key());
            } else {
                preferences.put(alternative.key(), alternative.pattern());
            }
            PlanVariant<K> variant = new PlanVariant<>(
                    parent.priorityRoots(),
                    Map.copyOf(preferences),
                    parent.priorityRoots().isEmpty() ? preferences.size() : preferences.size() + 1,
                    sequence++);
            if (queued.add(variant.key())) {
                frontier.add(variant);
            }
        }
        return new EnqueueResult(sequence, routeAlternatives.truncated());
    }

    /**
     * Prefer a diagnosis that asks the player to replenish fewer kinds, then fewer total units.
     * Quantities across item types are only a heuristic tie-breaker; every retained plan remains a
     * concrete, mass-balanced simulation candidate.
     */
    private static <K> CraftPlan<K> betterIncompletePlan(
            CraftPlan<K> current, CraftPlan<K> candidate) {
        int byKinds = Integer.compare(candidate.missing().size(), current.missing().size());
        if (byKinds < 0) {
            return candidate;
        }
        if (byKinds > 0) {
            return current;
        }
        long currentTotal = missingTotal(current);
        long candidateTotal = missingTotal(candidate);
        return candidateTotal < currentTotal ? candidate : current;
    }

    private static <K> long missingTotal(CraftPlan<K> plan) {
        long total = 0L;
        for (long amount : plan.missing().values()) {
            total = Sat.add(total, amount);
        }
        return total;
    }

    private static <K> CraftPlan<K> markBudgetExhausted(CraftPlan<K> plan) {
        return new CraftPlan<>(
                plan.supported(),
                plan.feasible(),
                plan.firings(),
                plan.usedStock(),
                plan.usedReusableStock(),
                plan.missing(),
                plan.grossDemand(),
                plan.itemsProcessed(),
                true);
    }

    private CraftPlan<K> run(K target, long amount, List<K> priorityRoots) {
        diagnostics.recordPlanRun();
        if (amount <= 0) {
            return new CraftPlan<>(true, true, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 0, false);
        }

        List<K> order;
        Set<K> items;
        if (preparedGraph == null) {
            long compileStarted = System.nanoTime();
            identifyPositiveFeedbackByproducts(target);

            // Build an acyclic view of the reachable recipe graph: a DFS from the target drops any
            // recipe whose input is an ancestor still being expanded (a back-edge), i.e. AE2's
            // "去头尾". The resulting graph facts are frozen and reused by every route replay with
            // this cycle orientation.
            items = new LinkedHashSet<>();
            List<K> postOrder = new ArrayList<>();
            buildDag(target, priorityRoots, postOrder, items);
            if (!requiresSeedOrderedPlanning
                    && ordinaryReturnedSeedKeys.stream().anyMatch(reachableByproductKeys::contains)) {
                // A byproduct might otherwise appear in the aggregate pool before the pattern that
                // needs the catalyst is executable.
                requiresSeedOrderedPlanning = true;
            }
            order = new ArrayList<>(postOrder.size());
            for (int i = postOrder.size() - 1; i >= 0; i--) {
                order.add(postOrder.get(i));
            }
            // Include primary and byproduct material flow in SCC classification before any
            // algebraic pass can treat a returned state as supply. Only proven non-growing marked
            // graphs survive as ordinary feedback components; gain/ambiguous loops are excluded.
            conservativeFeedbackComponents =
                    ConservativeFeedbackAnalysis.analyze(order, patternsByOutput);
            for (ConservativeFeedbackAnalysis.Component<K> component
                    : conservativeFeedbackComponents) {
                if (component.hasExternalProducer()) {
                    craftableConservativeFeedbackPatterns.addAll(component.patterns());
                }
            }
            if (!craftableConservativeFeedbackPatterns.isEmpty()) {
                // A valid seed may itself be craftable by an acyclic producer. Ordered replay must
                // get a chance to build it before bootstrap validation; the aggregate flow alone can
                // cancel the transition's input against its own returned output.
                requiresSeedOrderedPlanning = true;
            }
            indexLinearContainerBootstrapReserves();
            indexSeedOrderedDependencyCone(order);
            this.capacity = capacityFromOrder(order, items.size());
            computeRouteDepth(order);
            indexCapacityOrder();
            indexDirectRawConsumables();
            indexEquivalentMaterialFootprints(order);
            preparedGraph = snapshotPreparedGraph(order, items);
            diagnostics.recordCompilation(preparedGraph, System.nanoTime() - compileStarted);
        } else {
            order = preparedGraph.order;
            items = preparedGraph.items;
            diagnostics.recordCompilationReuse();
        }

        // Returned catalysts must be acquired before the firing's outputs enter the shared pool.
        // The recursive path already has that execution order; the aggregate linear pass does not,
        // so using it here could let a positive macro output bootstrap its own seed algebraically.
        CraftPlan<K> linearDiagnosis = null;
        if (!requiresSeedOrderedPlanning) {
            // 1) Linear backbone (v2-memo-deps / v2-lazy-deduct): one topological aggregation pass,
            //    each item resolved exactly once = O(n + E). Reservation-based capacity gives O(1)
            //    deduction (no recompute loop). When this fully succeeds, contention never mattered.
            long linearStarted = System.nanoTime();
            CraftPlan<K> linear = linearPass(order, target, amount);
            diagnostics.addLinearPassNanos(System.nanoTime() - linearStarted);
            if (linear.feasible()) {
                return enforceCycleBootstrap(linear);
            }
            linearDiagnosis = linear;
            // 1b) Allocation repair: search over the contended outputs' route allocations only,
            //     each candidate evaluated by one O(E) sweep. The sweep is the same aggregation
            //     the linear backbone uses, so this applies exactly where the backbone's feasible
            //     answers are already trusted: catalysts resolve through the shared seed reserve,
            //     fixed conversion rings arrive here already cut to a DAG, and contracted
            //     gain/feedback loops keep their compile-time bookkeeping. Unlike the recursive
            //     search this can SPLIT one product's demand across routes; the cycle-bootstrap
            //     check afterwards may still veto a plan, in which case the recursion runs.
            CraftPlan<K> repaired = allocationRepair(order, target, amount);
            if (repaired != null && repaired.feasible()) {
                CraftPlan<K> bootstrapped = enforceCycleBootstrap(repaired);
                if (bootstrapped.feasible()) {
                    return bootstrapped;
                }
            }
        }

        // 2) Contended cone only: fall back to the budgeted recursive search (trail + rollback).
        for (K x : items) {
            stockLeft.put(x, graph.stock(x));
        }
        long searchStarted = System.nanoTime();
        obtain(target, amount, true);
        diagnostics.addSearchNanos(System.nanoTime() - searchStarted);
        boolean feasible = missing.isEmpty();
        boolean budgetTruncated =
                (searchBudget.exhausted() || diagnostics.resolutionExhausted()) && !feasible;
        CraftPlan<K> fallback = new CraftPlan<>(true, feasible,
                new IdentityHashMap<>(firings),
                new HashMap<>(usedStock),
                new HashMap<>(usedReusableStock),
                new HashMap<>(missing),
                new HashMap<>(grossDemand),
                processed,
                budgetTruncated);
        // A budget-truncated descent stops mid-graph and reports still-craftable intermediates as
        // missing (e.g. "2 x complex component" instead of the millions of raw units their subtree
        // needs). The aggregate pass has already expanded the same request all the way to raw leaves,
        // so its diagnosis is the actionable one; the plan stays flagged budget-exhausted because
        // alternate routes were still never fully explored.
        if (budgetTruncated && linearDiagnosis != null && hasCraftableMissing(fallback)) {
            CraftPlan<K> diagnosis = new CraftPlan<>(true, false,
                    linearDiagnosis.firings(),
                    linearDiagnosis.usedStock(),
                    linearDiagnosis.usedReusableStock(),
                    linearDiagnosis.missing(),
                    linearDiagnosis.grossDemand(),
                    linearDiagnosis.itemsProcessed(),
                    true);
            return enforceCycleBootstrap(diagnosis);
        }
        return enforceCycleBootstrap(fallback);
    }

    /** True when a missing entry still has a producing pattern, i.e. the diagnosis was cut short. */
    private boolean hasCraftableMissing(CraftPlan<K> plan) {
        for (K key : plan.missing().keySet()) {
            if (!patternsByOutput.getOrDefault(key, List.of()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private PreparedGraph<K> snapshotPreparedGraph(List<K> order, Set<K> items) {
        Map<K, List<CraftPattern<K>>> frozenPatterns = new HashMap<>();
        patternsByOutput.forEach((key, value) -> frozenPatterns.put(key, List.copyOf(value)));

        IdentityHashMap<CraftPattern<K>, Set<K>> frozenSuppressed = new IdentityHashMap<>();
        suppressedPositiveFeedbackOutputs.forEach(
                (pattern, outputs) -> frozenSuppressed.put(pattern, Set.copyOf(outputs)));
        IdentityHashMap<CraftPattern<K>, Map<K, Long>> frozenContainerReserves =
                new IdentityHashMap<>();
        linearContainerBootstrapReserves.forEach(
                (pattern, reserves) -> frozenContainerReserves.put(pattern, Map.copyOf(reserves)));
        IdentityHashMap<CraftPattern<K>, List<FeedbackSeedBootstrap<K>>> frozenBootstraps =
                new IdentityHashMap<>();
        feedbackSeedBootstraps.forEach(
                (pattern, values) -> frozenBootstraps.put(pattern, List.copyOf(values)));
        IdentityHashMap<CraftPattern<K>, List<FeedbackSeedBootstrap<K>>> frozenConverters =
                new IdentityHashMap<>();
        feedbackSeedConverters.forEach(
                (pattern, values) -> frozenConverters.put(pattern, List.copyOf(values)));

        IdentityHashMap<CraftPattern<K>, Long> frozenScores = new IdentityHashMap<>(capacityScoreByPattern);
        IdentityHashMap<CraftPattern<K>, Map<K, Long>> frozenRaw = new IdentityHashMap<>(directRawConsumablesByPattern);
        IdentityHashMap<CraftPattern<K>, Integer> frozenFootprints = new IdentityHashMap<>(materialFootprintByPattern);

        int patternCount = 0;
        int inputCount = 0;
        int contended = 0;
        for (List<CraftPattern<K>> patterns : frozenPatterns.values()) {
            patternCount += patterns.size();
            if (patterns.size() > 1) contended++;
            for (CraftPattern<K> pattern : patterns) {
                inputCount += pattern.inputs().size();
            }
        }

        return new PreparedGraph<>(
                graph,
                List.copyOf(order),
                Set.copyOf(items),
                Set.copyOf(cutOutputs),
                frozenPatterns,
                frozenSuppressed,
                frozenContainerReserves,
                frozenBootstraps,
                frozenConverters,
                conservativeFeedbackComponents,
                requiresSeedOrderedPlanning,
                Set.copyOf(seedOrderedDependencyCone),
                new HashMap<>(capacity),
                frozenScores,
                new HashMap<>(capacityOrderByOutput),
                frozenRaw,
                frozenFootprints,
                patternCount,
                inputCount,
                contended);
    }

    private void loadPreparedGraph(PreparedGraph<K> prepared) {
        cutOutputs.addAll(prepared.cutOutputs);
        patternsByOutput.putAll(prepared.patternsByOutput);
        computeRouteDepth(prepared.order);
        suppressedPositiveFeedbackOutputs.putAll(prepared.suppressedPositiveFeedbackOutputs);
        linearContainerBootstrapReserves.putAll(prepared.linearContainerBootstrapReserves);
        feedbackSeedBootstraps.putAll(prepared.feedbackSeedBootstraps);
        feedbackSeedConverters.putAll(prepared.feedbackSeedConverters);
        conservativeFeedbackComponents = prepared.conservativeFeedbackComponents;
        for (ConservativeFeedbackAnalysis.Component<K> component
                : conservativeFeedbackComponents) {
            if (component.hasExternalProducer()) {
                craftableConservativeFeedbackPatterns.addAll(component.patterns());
            }
        }
        requiresSeedOrderedPlanning = prepared.seedOrdered;
        seedOrderedDependencyCone.addAll(prepared.seedOrderedDependencyCone);
        capacity = prepared.capacity;
        capacityScoreByPattern.putAll(prepared.capacityScoreByPattern);
        capacityOrderByOutput.putAll(prepared.capacityOrderByOutput);
        directRawConsumablesByPattern.putAll(prepared.directRawConsumablesByPattern);
        materialFootprintByPattern.putAll(prepared.materialFootprintByPattern);
    }

    /**
     * A balanced container cycle still needs one physical state token to start. A purely algebraic
     * flow can otherwise schedule {@code full -> empty -> full} with zero initial containers. When a
     * fired consumed-returning input is refilled by another fired pattern, require one batch from
     * inventory unless some fired acyclic producer supplies either state from outside the pair.
     */
    private CraftPlan<K> enforceCycleBootstrap(CraftPlan<K> plan) {
        Map<K, List<CraftPattern<K>>> firedByOutput = new HashMap<>();
        for (Map.Entry<CraftPattern<K>, Long> entry : plan.firings().entrySet()) {
            if (entry.getValue() > 0) {
                firedByOutput.computeIfAbsent(entry.getKey().output(), ignored -> new ArrayList<>())
                        .add(entry.getKey());
            }
        }

        Map<K, Long> used = new HashMap<>(plan.usedStock());
        Map<K, Long> missing = new HashMap<>(plan.missing());
        Set<Set<K>> handled = new java.util.HashSet<>();

        for (CraftPattern<K> consumer : plan.firings().keySet()) {
            if (plan.firings().getOrDefault(consumer, 0L) <= 0) continue;
            for (CraftInput<K> transition : consumer.inputs()) {
                K remainder = transition.remainder();
                if (remainder == null) continue;
                if (transition.key().equals(remainder)) continue;

                long refillRequirement = Long.MAX_VALUE;
                for (CraftPattern<K> refill : firedByOutput.getOrDefault(transition.key(), List.of())) {
                    for (CraftInput<K> refillInput : refill.inputs()) {
                        if (remainder.equals(refillInput.key())) {
                            refillRequirement = Math.min(refillRequirement, refillInput.amount());
                        }
                    }
                }
                if (refillRequirement == Long.MAX_VALUE) continue;

                Set<K> states = Set.of(transition.key(), remainder);
                if (!handled.add(states)) continue;
                if (used.getOrDefault(transition.key(), 0L) > 0
                        || used.getOrDefault(remainder, 0L) > 0
                        || hasExternalBootstrapProducer(states, firedByOutput)) {
                    continue;
                }

                long required = Math.max(1L, Math.min(transition.amount(), refillRequirement));
                K chosen = graph.stock(transition.key()) >= graph.stock(remainder)
                        ? transition.key() : remainder;
                long extracted = Math.min(required, graph.stock(chosen));
                if (extracted > 0) {
                    used.merge(chosen, extracted, Sat::add);
                }
                if (extracted < required) {
                    missing.merge(chosen, required - extracted, Sat::add);
                }
            }
        }

        Set<CraftPattern<K>> conservativeFeedbackPatterns =
                enforceConservativeFeedbackBootstrap(plan, used, missing);
        enforceDirectFeedbackBootstrap(
                plan, firedByOutput, used, missing, conservativeFeedbackPatterns);

        return new CraftPlan<>(plan.supported(), missing.isEmpty(), plan.firings(), used,
                plan.usedReusableStock(), missing, plan.grossDemand(), plan.itemsProcessed(),
                plan.budgetExhausted());
    }

    /**
     * Reserves one executable initial marking for every proven non-growing ordinary feedback SCC
     * used by this plan. The aggregate solver may erase the state retained after a balanced or lossy
     * round; this postcondition restores the physical token before the first transition.
     *
     * <p>Seed alternatives are the bounded cyclic rotations computed during graph compilation. A
     * state already drawn (or already reported missing) for net demand can serve as that same token,
     * as can a fired acyclic producer entering the SCC from outside. Components not fully used by the
     * chosen firing vector are left to ordinary accounting and the legacy partial-return check.
     */
    private Set<CraftPattern<K>> enforceConservativeFeedbackBootstrap(
            CraftPlan<K> plan,
            Map<K, Long> used,
            Map<K, Long> missing) {
        Set<CraftPattern<K>> handled =
                java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (ConservativeFeedbackAnalysis.Component<K> component
                : conservativeFeedbackComponents) {
            boolean active = true;
            for (CraftPattern<K> pattern : component.patterns()) {
                if (plan.firings().getOrDefault(pattern, 0L) <= 0) {
                    active = false;
                    break;
                }
            }
            if (!active) continue;

            long rounds = 0L;
            if (component.lossy()) {
                for (CraftPattern<K> pattern : component.patterns()) {
                    long fired = plan.firings().getOrDefault(pattern, 0L);
                    if (rounds == 0L) rounds = fired;
                    else if (rounds != fired) {
                        rounds = -1L;
                        break;
                    }
                }
                // A non-uniform lossy firing vector needs a general marked-graph scheduler. Keep it
                // out of this proof and let the older narrow fallback diagnose it conservatively.
                if (rounds < 0L) continue;
            }

            Map<K, Long> external = externalFeedbackSupply(component, plan.firings());
            ConservativeFeedbackAnalysis.SeedOption<K> chosen = null;
            int chosenMissingKinds = Integer.MAX_VALUE;
            long chosenMissingTotal = Long.MAX_VALUE;
            long chosenStock = Long.MAX_VALUE;
            for (ConservativeFeedbackAnalysis.SeedOption<K> option : component.seedOptions()) {
                int missingKinds = 0;
                long missingTotal = 0L;
                long stockNeeded = 0L;
                for (Map.Entry<K, Long> seed : option.amounts().entrySet()) {
                    K key = seed.getKey();
                    long reserved = feedbackSeedCredit(
                            component, rounds, key, used, missing, external);
                    long additional = Math.max(0L, seed.getValue() - reserved);
                    long stockAvailable = Math.max(
                            0L, graph.stock(key) - used.getOrDefault(key, 0L));
                    long shortage = Math.max(0L, additional - stockAvailable);
                    if (shortage > 0) missingKinds++;
                    missingTotal = Sat.add(missingTotal, shortage);
                    stockNeeded = Sat.add(stockNeeded, Math.min(additional, stockAvailable));
                }
                if (chosen == null
                        || missingKinds < chosenMissingKinds
                        || (missingKinds == chosenMissingKinds
                                && missingTotal < chosenMissingTotal)
                        || (missingKinds == chosenMissingKinds
                                && missingTotal == chosenMissingTotal
                                && stockNeeded < chosenStock)) {
                    chosen = option;
                    chosenMissingKinds = missingKinds;
                    chosenMissingTotal = missingTotal;
                    chosenStock = stockNeeded;
                }
            }
            if (chosen == null) continue;

            handled.addAll(component.patterns());
            for (Map.Entry<K, Long> seed : chosen.amounts().entrySet()) {
                K key = seed.getKey();
                long reserved = feedbackSeedCredit(
                        component, rounds, key, used, missing, external);
                long additional = Math.max(0L, seed.getValue() - reserved);
                if (additional <= 0) continue;
                long stockAvailable = Math.max(
                        0L, graph.stock(key) - used.getOrDefault(key, 0L));
                long extracted = Math.min(additional, stockAvailable);
                if (extracted > 0) used.merge(key, extracted, Sat::add);
                if (extracted < additional) {
                    missing.merge(key, additional - extracted, Sat::add);
                }
            }
        }
        return handled;
    }

    private long feedbackSeedCredit(
            ConservativeFeedbackAnalysis.Component<K> component,
            long rounds,
            K key,
            Map<K, Long> used,
            Map<K, Long> missing,
            Map<K, Long> external) {
        long accounted = Sat.add(
                used.getOrDefault(key, 0L), missing.getOrDefault(key, 0L));
        if (component.lossy()) {
            long ordinaryLoss = Sat.mul(
                    component.lossPerRound().getOrDefault(key, 0L), rounds);
            accounted = Math.max(0L, accounted - ordinaryLoss);
        }
        return Sat.add(accounted, external.getOrDefault(key, 0L));
    }

    /** Supply entering a feedback SCC from a fired transition that does not consume any SCC state. */
    private Map<K, Long> externalFeedbackSupply(
            ConservativeFeedbackAnalysis.Component<K> component,
            Map<CraftPattern<K>, Long> fired) {
        Map<K, Long> result = new HashMap<>();
        for (Map.Entry<CraftPattern<K>, Long> entry : fired.entrySet()) {
            CraftPattern<K> pattern = entry.getKey();
            long times = entry.getValue();
            if (times <= 0 || component.patterns().contains(pattern)) continue;
            boolean consumesState = pattern.inputs().stream()
                    .anyMatch(input -> component.states().contains(input.key()));
            if (consumesState) continue;

            if (component.states().contains(pattern.output())) {
                result.merge(pattern.output(), Sat.mul(pattern.outputAmount(), times), Sat::add);
            }
            for (CraftOutput<K> output : pattern.byproducts()) {
                if (component.states().contains(output.key())) {
                    result.merge(output.key(), Sat.mul(output.amount(), times), Sat::add);
                }
            }
            for (CraftInput<K> input : pattern.inputs()) {
                if (input.remainder() != null && component.states().contains(input.remainder())) {
                    result.merge(input.remainder(), Sat.mul(input.amount(), times), Sat::add);
                }
            }
        }
        return result;
    }

    /**
     * Handles a narrow, common partial-return loop such as
     * {@code 2 A -> 2 B + C; C -> A}. The normal flow equations correctly charge the net A
     * consumption, but an executable schedule also has to keep one returned batch in circulation:
     * two firings consume two A net yet need three A initially. This pass adds only that reusable
     * bootstrap reserve.
     *
     * <p>Classification is deliberately narrow: the consumer has one ordinary input and one byproduct,
     * while the refill has that byproduct as its sole ordinary input. We do not search paths, subsets,
     * or firing orders. Multi-input/output and ambiguous relations stay on ordinary accounting instead
     * of turning this into a general Petri-net solver.
     */
    private void enforceDirectFeedbackBootstrap(
            CraftPlan<K> plan,
            Map<K, List<CraftPattern<K>>> firedByOutput,
            Map<K, Long> used,
            Map<K, Long> missing,
            Set<CraftPattern<K>> conservativeFeedbackPatterns) {
        Map<K, SeedRequirement<K>> seedRequirements = new HashMap<>();

        for (Map.Entry<CraftPattern<K>, Long> consumerEntry : plan.firings().entrySet()) {
            CraftPattern<K> consumer = consumerEntry.getKey();
            long consumerFirings = consumerEntry.getValue();
            if (consumerFirings <= 0
                    || conservativeFeedbackPatterns.contains(consumer)
                    || consumer.byproducts().size() != 1
                    || ordinaryInputCount(consumer) != 1) {
                continue;
            }

            for (CraftInput<K> consumed : consumer.inputs()) {
                // Different-state container remainders are handled by the explicit bootstrap pass
                // above; returned/finite-use inputs already have their own closed-form seed semantics.
                if (consumed.returned() || consumed.remainder() != null) continue;

                for (CraftOutput<K> returnedState : consumer.byproducts()) {
                    DirectRefill<K> refill = uniqueDirectRefill(
                            consumed.key(), returnedState.key(), firedByOutput, plan.firings());
                    if (refill == null) continue;
                    // Keep this a byproduct-only classification. If the returned state has its own
                    // primary producer, the graph is no longer the narrow half-loop handled here.
                    if (!graph.patternsFor(returnedState.key()).isEmpty()) {
                        continue;
                    }

                    long gcd = gcd(returnedState.amount(), refill.input().amount());
                    long consumerBatch = refill.input().amount() / gcd;
                    long refillBatch = returnedState.amount() / gcd;
                    long consumedPerCycle = Sat.mul(consumed.amount(), consumerBatch);
                    long recoveredPerCycle = Sat.mul(refill.pattern().outputAmount(), refillBatch);
                    // Strict gain belongs to the contracted closed-loop planner. Its feedback output
                    // is suppressed from the ordinary shared pool before planning, so do not add the
                    // reusable-bootstrap accounting used by lossy and balanced ordinary paths.
                    if (recoveredPerCycle > consumedPerCycle) continue;
                    long totalConsumed = Sat.mul(consumed.amount(), consumerFirings);
                    long reusableSeed = Math.min(
                            totalConsumed, Math.min(consumedPerCycle, recoveredPerCycle));
                    if (reusableSeed <= 0) continue;

                    long returnedUnits = Sat.mul(returnedState.amount(), consumerFirings);
                    long maxRefillFirings = returnedUnits / refill.input().amount();
                    long maximumRecovery = Sat.mul(refill.pattern().outputAmount(), maxRefillFirings);
                    long inherentNet = Math.max(0L, totalConsumed - Math.min(totalConsumed, maximumRecovery));

                    long actualRecovery = Sat.mul(
                            refill.pattern().outputAmount(),
                            plan.firings().getOrDefault(refill.pattern(), 0L));
                    long actualNet = Math.max(0L, totalConsumed - Math.min(totalConsumed, actualRecovery));
                    // If the chosen flow already leaves one recovery batch unused, its extra net input
                    // is the seed (balanced bucket loops commonly land here). Otherwise reserve it now.
                    long embeddedSeed = Math.max(0L, actualNet - inherentNet);
                    long extraSeed = Math.max(0L, reusableSeed - embeddedSeed);
                    if (extraSeed > 0) {
                        // The seed may be stored in the returned state instead. Record the alternative
                        // now and reserve it once after all consumers have been scanned; the same seed
                        // can bootstrap several patterns sequentially and must not be double-charged.
                        long seedRefillFirings = Sat.ceilDiv(
                                extraSeed, refill.pattern().outputAmount());
                        long returnedStateSeed = Sat.mul(
                                refill.input().amount(), seedRefillFirings);
                        SeedRequirement<K> candidate = new SeedRequirement<>(
                                extraSeed, returnedState.key(), returnedStateSeed);
                        seedRequirements.merge(consumed.key(), candidate,
                                CraftPlannerV2::largerSeedRequirement);
                    }
                }
            }
        }

        for (Map.Entry<K, SeedRequirement<K>> seed : seedRequirements.entrySet()) {
            K key = seed.getKey();
            SeedRequirement<K> requirement = seed.getValue();
            long returnedAlreadyUsed = used.getOrDefault(requirement.returnedState(), 0L);
            long returnedNeeded = Math.max(0L, requirement.returnedAmount() - returnedAlreadyUsed);
            long returnedAvailable = Math.max(
                    0L, graph.stock(requirement.returnedState()) - returnedAlreadyUsed);
            if (returnedNeeded <= returnedAvailable) {
                if (returnedNeeded > 0) {
                    used.merge(requirement.returnedState(), returnedNeeded, Sat::add);
                }
                continue;
            }

            long alreadyUsed = used.getOrDefault(key, 0L);
            long available = Math.max(0L, graph.stock(key) - alreadyUsed);
            long extracted = Math.min(requirement.consumedAmount(), available);
            if (extracted > 0) used.merge(key, extracted, Sat::add);
            if (extracted < requirement.consumedAmount()) {
                missing.merge(key, requirement.consumedAmount() - extracted, Sat::add);
            }
        }
    }

    private record SeedRequirement<K>(long consumedAmount, K returnedState, long returnedAmount) {
    }

    private static <K> SeedRequirement<K> largerSeedRequirement(
            SeedRequirement<K> left, SeedRequirement<K> right) {
        if (right.consumedAmount() > left.consumedAmount()) return right;
        if (right.consumedAmount() < left.consumedAmount()) return left;
        return right.returnedAmount() < left.returnedAmount() ? right : left;
    }

    private record DirectRefill<K>(CraftPattern<K> pattern, CraftInput<K> input) {
    }

    /** Returns the sole fired direct {@code returnedState -> consumedState} refill, or null if ambiguous. */
    private DirectRefill<K> uniqueDirectRefill(
            K consumedState,
            K returnedState,
            Map<K, List<CraftPattern<K>>> firedByOutput,
            Map<CraftPattern<K>, Long> fired) {
        DirectRefill<K> found = null;
        for (CraftPattern<K> producer : firedByOutput.getOrDefault(consumedState, List.of())) {
            if (fired.getOrDefault(producer, 0L) <= 0 || ordinaryInputCount(producer) != 1) continue;
            for (CraftInput<K> input : producer.inputs()) {
                if (input.returned() || input.remainder() != null
                        || !returnedState.equals(input.key())) {
                    continue;
                }
                if (found != null && found.pattern() != producer) return null;
                found = new DirectRefill<>(producer, input);
            }
        }
        return found;
    }

    private static <K> int ordinaryInputCount(CraftPattern<K> pattern) {
        int count = 0;
        for (CraftInput<K> input : pattern.inputs()) {
            if (!input.returned() && input.remainder() == null) count++;
        }
        return count;
    }

    private static long gcd(long a, long b) {
        a = Math.max(1L, a);
        b = Math.max(1L, b);
        while (b != 0) {
            long next = a % b;
            a = b;
            b = next;
        }
        return a;
    }

    /**
     * Finds direct byproduct feedback whose material state grows after one balanced round, for example
     * {@code A -> B + C; C -> 2 A}. Such a loop is intentionally not a capability of the ordinary
     * planner: it must first be compiled into one closed-loop macro pattern. We therefore keep the raw
     * member recipes visible, but do not let the growing feedback byproduct enter the shared pool.
     * Existing stock of the returned state remains usable, so finite non-feedback crafts still work.
     *
     * <p>This is a linear, deliberately local guard matching the local feedback optimization below;
     * arbitrary SCC coefficient solving remains exclusively in the closed-loop analyzer.
     */
    private void identifyPositiveFeedbackByproducts(K target) {
        Set<K> seen = new LinkedHashSet<>();
        Deque<K> queue = new ArrayDeque<>();
        seen.add(target);
        queue.add(target);

        while (!queue.isEmpty()) {
            K output = queue.remove();
            for (CraftPattern<K> consumer : graph.patternsFor(output)) {
                for (CraftInput<K> input : consumer.inputs()) {
                    if (seen.add(input.key())) queue.add(input.key());
                }
                for (CraftOutput<K> byproduct : consumer.byproducts()) {
                    if (seen.add(byproduct.key())) queue.add(byproduct.key());
                }

                Set<K> ordinaryInputs = new LinkedHashSet<>();
                for (CraftInput<K> input : consumer.inputs()) {
                    if (!input.returned() && input.remainder() == null) {
                        ordinaryInputs.add(input.key());
                    }
                }
                for (K consumedKey : ordinaryInputs) {
                    long consumedAmount = ordinaryInputAmount(consumer, consumedKey);
                    if (consumedAmount <= 0) continue;
                    for (CraftOutput<K> byproduct : consumer.byproducts()) {
                        long returnedAmount = byproductAmount(consumer, byproduct.key());
                        if (returnedAmount <= 0) continue;
                        for (CraftPattern<K> refill : graph.patternsFor(consumedKey)) {
                            long refillInput = ordinaryInputAmount(refill, byproduct.key());
                            if (refillInput <= 0) continue;
                            long common = gcd(returnedAmount, refillInput);
                            long consumerBatch = refillInput / common;
                            long refillBatch = returnedAmount / common;
                            long consumedPerRound = Sat.mul(consumedAmount, consumerBatch);
                            long recoveredPerRound = Sat.mul(refill.outputAmount(), refillBatch);
                            if (recoveredPerRound > consumedPerRound) {
                                suppressedPositiveFeedbackOutputs
                                        .computeIfAbsent(consumer, ignored -> new LinkedHashSet<>())
                                        .add(byproduct.key());
                            }
                        }
                    }
                }
            }
        }
    }

    private static <K> long ordinaryInputAmount(CraftPattern<K> pattern, K key) {
        long result = 0L;
        for (CraftInput<K> input : pattern.inputs()) {
            if (!input.returned() && input.remainder() == null && key.equals(input.key())) {
                result = Sat.add(result, input.amount());
            }
        }
        return result;
    }

    private static <K> long byproductAmount(CraftPattern<K> pattern, K key) {
        long result = 0L;
        for (CraftOutput<K> output : pattern.byproducts()) {
            if (key.equals(output.key())) result = Sat.add(result, output.amount());
        }
        return result;
    }

    private boolean mayReuseByproduct(CraftPattern<K> pattern, K key) {
        return !suppressedPositiveFeedbackOutputs
                .getOrDefault(pattern, Set.of())
                .contains(key);
    }

    /**
     * The linear pass aggregates all byproducts before resolving their consumers. For a path such as
     * {@code full -> intermediate -> empty}, crediting every returned {@code empty} lets the path
     * bootstrap itself from zero physical containers. Withhold one returned batch so ordinary demand
     * obtains that bootstrap from stock or a real producer; the remaining returns still serve the
     * rest of the batch.
     */
    private void indexLinearContainerBootstrapReserves() {
        linearContainerBootstrapReserves.clear();
        for (List<CraftPattern<K>> patterns : patternsByOutput.values()) {
            for (CraftPattern<K> consumer : patterns) {
                Map<K, Long> reserves = new HashMap<>();
                for (CraftInput<K> transition : consumer.inputs()) {
                    K remainder = transition.remainder();
                    if (remainder == null || transition.key().equals(remainder)) continue;
                    long returned = byproductAmount(consumer, remainder);
                    if (returned <= 0 || !dependsOn(transition.key(), remainder)) continue;
                    reserves.merge(remainder, transition.amount(), Sat::add);
                }
                reserves.replaceAll((key, required) ->
                        Math.min(required, byproductAmount(consumer, key)));
                reserves.values().removeIf(amount -> amount <= 0);
                if (!reserves.isEmpty()) {
                    linearContainerBootstrapReserves.put(consumer, Map.copyOf(reserves));
                }
            }
        }
    }

    private boolean dependsOn(K output, K requiredInput) {
        Set<K> seen = new HashSet<>();
        Deque<K> queue = new ArrayDeque<>();
        seen.add(output);
        queue.add(output);
        while (!queue.isEmpty()) {
            K current = queue.removeFirst();
            for (CraftPattern<K> pattern : patternsByOutput.getOrDefault(current, List.of())) {
                for (CraftInput<K> input : pattern.inputs()) {
                    if (requiredInput.equals(input.key())) return true;
                    if (seen.add(input.key())) queue.addLast(input.key());
                }
            }
        }
        return false;
    }

    private boolean hasExternalBootstrapProducer(
            Set<K> states,
            Map<K, List<CraftPattern<K>>> firedByOutput) {
        for (K state : states) {
            for (CraftPattern<K> producer : firedByOutput.getOrDefault(state, List.of())) {
                boolean consumesCycleState = producer.inputs().stream()
                        .anyMatch(input -> states.contains(input.key()));
                if (!consumesCycleState) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- graph construction ------------------------------------------------

    private static final int GRAY = 1; // on the current DFS path (an ancestor)
    private static final int BLACK = 2; // fully expanded

    private static final class Frame<K> {
        final K node;
        final List<K> children;
        int i;

        Frame(K node, List<K> children) {
            this.node = node;
            this.children = children;
        }
    }

    /**
     * Iterative DFS from {@code target} that records each node's <em>acyclic</em> recipe set into
     * {@link #patternsByOutput} and emits a post-order. A recipe is kept only if none of its inputs is an
     * ancestor currently on the DFS path ({@code GRAY}); such a recipe would close a cycle ("去头尾"), so
     * it is dropped and the input is satisfied from stock/another recipe instead. Each node and edge is
     * touched once → {@code O(n + E)}; iterative (not recursive) so deep graphs can't overflow the stack.
     */
    private void buildDag(
            K target,
            List<K> priorityRoots,
            List<K> postOrderOut,
            Set<K> itemsOut) {
        Map<K, Integer> color = new HashMap<>();
        for (K priorityRoot : priorityRoots) {
            buildDagRoot(priorityRoot, color, postOrderOut, itemsOut);
        }
        buildDagRoot(target, color, postOrderOut, itemsOut);
    }

    private void buildDagRoot(
            K root,
            Map<K, Integer> color,
            List<K> postOrderOut,
            Set<K> itemsOut) {
        if (color.containsKey(root)) return;
        Deque<Frame<K>> stack = new ArrayDeque<>();
        color.put(root, GRAY);
        itemsOut.add(root);
        stack.push(frameFor(root, color, itemsOut));
        while (!stack.isEmpty()) {
            Frame<K> f = stack.peek();
            if (f.i < f.children.size()) {
                K c = f.children.get(f.i++);
                if (color.get(c) == null) { // WHITE -> descend (GRAY children are excluded by frameFor)
                    color.put(c, GRAY);
                    stack.push(frameFor(c, color, itemsOut));
                }
            } else {
                color.put(f.node, BLACK);
                postOrderOut.add(f.node);
                stack.pop();
            }
        }
    }

    private Frame<K> frameFor(K x, Map<K, Integer> color, Set<K> itemsOut) {
        List<CraftPattern<K>> all = graph.patternsFor(x);
        List<CraftPattern<K>> usable = new ArrayList<>(all.size());
        Set<K> children = new LinkedHashSet<>();
        for (CraftPattern<K> p : all) {
            for (CraftOutput<K> byproduct : p.byproducts()) {
                reachableByproductKeys.add(byproduct.key());
            }
            List<CraftInput<K>> backEdges = new ArrayList<>(1);
            for (CraftInput<K> in : p.inputs()) {
                if (in.returned() && in.uses() == CraftInput.INFINITE_USES) {
                    if (isSelfReturnedSeed(p, in) || in.reusableStockSource() != null) {
                        requiresSeedOrderedPlanning = true;
                    } else {
                        ordinaryReturnedSeedKeys.add(in.key());
                    }
                }
                if (isSelfReturnedSeed(p, in) || isHostBackedReusableSeed(in)) continue;
                Integer col = color.get(in.key());
                if (col != null && col == GRAY) { // input is an ancestor being made -> back-edge, cut it
                    backEdges.add(in);
                }
            }

            List<FeedbackSeedBootstrap<K>> resolvedBackEdges =
                    new ArrayList<>(backEdges.size());
            int resolvedBackEdgeCount = 0;
            for (CraftInput<K> backEdge : backEdges) {
                List<FeedbackSeedBootstrap<K>> direct =
                        directFeedbackSeedBootstraps(p, backEdge);
                if (!direct.isEmpty()) {
                    resolvedBackEdges.addAll(direct);
                    resolvedBackEdgeCount++;
                    continue;
                }
                FeedbackSeedBootstrap<K> bootstrap =
                        feedbackSeedBootstrapFromConverter(p, backEdge);
                if (bootstrap == null) {
                    break;
                }
                resolvedBackEdges.add(bootstrap);
                resolvedBackEdgeCount++;
            }
            if (resolvedBackEdgeCount != backEdges.size()) {
                cutOutputs.add(x);
                continue;
            }
            for (FeedbackSeedBootstrap<K> bootstrap : resolvedBackEdges) {
                addFeedbackSeedBootstrap(bootstrap);
            }
            usable.add(p);
            for (CraftInput<K> in : p.inputs()) {
                if (isSelfReturnedSeed(p, in)
                        || isHostBackedReusableSeed(in)
                        || isFeedbackSeed(p, in)
                        || isFeedbackConverterInput(p, in)) {
                    continue;
                }
                children.add(in.key());
                itemsOut.add(in.key());
            }
        }
        patternsByOutput.put(x, usable);
        return new Frame<>(x, new ArrayList<>(children));
    }

    /**
     * Marks only the consumer-side cone above seed-order-sensitive patterns. The topological order is
     * target first, so scanning it backwards lets the marker flow from an input to every output that
     * may resolve it. Dependency cones below a sensitive pattern are deliberately left unmarked and
     * can still use {@link #obtainAggregate}.
     */
    private void indexSeedOrderedDependencyCone(List<K> order) {
        if (!requiresSeedOrderedPlanning) {
            return;
        }
        for (int i = order.size() - 1; i >= 0; i--) {
            K output = order.get(i);
            boolean affected = directlyRequiresSeedOrder(output);
            if (!affected) {
                outer:
                for (CraftPattern<K> pattern
                        : patternsByOutput.getOrDefault(output, List.of())) {
                    for (CraftInput<K> input : pattern.inputs()) {
                        if (seedOrderedDependencyCone.contains(input.key())) {
                            affected = true;
                            break outer;
                        }
                    }
                }
            }
            if (affected) {
                seedOrderedDependencyCone.add(output);
            }
        }
    }

    /** The exact local conditions that raise the graph-wide seed-order safety gate. */
    private boolean directlyRequiresSeedOrder(K output) {
        for (CraftPattern<K> pattern
                : patternsByOutput.getOrDefault(output, List.of())) {
            if (craftableConservativeFeedbackPatterns.contains(pattern)) return true;
            for (CraftInput<K> input : pattern.inputs()) {
                if (!input.returned() || input.uses() != CraftInput.INFINITE_USES) {
                    continue;
                }
                if (isSelfReturnedSeed(pattern, input)
                        || input.reusableStockSource() != null
                        || reachableByproductKeys.contains(input.key())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A complete private-host seed is already an acyclic startup source. It must not be treated as a
     * graph edge back to an ancestor merely because the same state is also craftable downstream.
     */
    private boolean isHostBackedReusableSeed(CraftInput<K> input) {
        return input.returned()
                && input.uses() == CraftInput.INFINITE_USES
                && input.reusableStockSource() != null
                && graph.reusableStock(input.reusableStockSource(), input.key()) >= input.amount();
    }

    private void addFeedbackSeedBootstrap(FeedbackSeedBootstrap<K> bootstrap) {
        List<FeedbackSeedBootstrap<K>> seeds = feedbackSeedBootstraps.computeIfAbsent(
                bootstrap.loopPattern(), ignored -> new ArrayList<>());
        if (!seeds.contains(bootstrap)) {
            seeds.add(bootstrap);
        }
        List<FeedbackSeedBootstrap<K>> converters = feedbackSeedConverters.computeIfAbsent(
                bootstrap.converter(), ignored -> new ArrayList<>());
        if (!converters.contains(bootstrap)) {
            converters.add(bootstrap);
        }
    }

    /**
     * Proves the only feedback shape handled inside the ordinary planner: a contracted loop pattern
     * with reusable seed {@code B} produces {@code A}, while an already-kept ordinary pattern converts
     * {@code A} back into {@code B}. This does not admit a general SCC; it merely records the
     * executable one-step bootstrap that the target-first DAG cut would otherwise hide.
     */
    private List<FeedbackSeedBootstrap<K>> directFeedbackSeedBootstraps(
            CraftPattern<K> loopPattern, CraftInput<K> seedInput) {
        if (!seedInput.returned()
                || seedInput.uses() != CraftInput.INFINITE_USES
                || seedInput.reusableStockSource() == null) {
            return List.of();
        }

        List<FeedbackSeedBootstrap<K>> result = new ArrayList<>();
        for (CraftPattern<K> candidate
                : patternsByOutput.getOrDefault(seedInput.key(), List.of())) {
            CraftInput<K> input = feedbackConverterInput(
                    candidate, loopPattern.output(), seedInput.key());
            if (input == null) continue;
            result.add(new FeedbackSeedBootstrap<>(
                    loopPattern, seedInput, candidate, input));
        }
        return result;
    }

    /**
     * Recognizes the same proof when DFS encounters the ordinary {@code A -> B} converter as the
     * back-edge. This is the orientation used by a downstream request such as
     * {@code charged A -> A -> loop(B seed, net A)}.
     */
    private FeedbackSeedBootstrap<K> feedbackSeedBootstrapFromConverter(
            CraftPattern<K> converter, CraftInput<K> converterInput) {
        for (CraftPattern<K> loopPattern
                : patternsByOutput.getOrDefault(converterInput.key(), List.of())) {
            for (CraftInput<K> seedInput : loopPattern.inputs()) {
                if (seedInput.key().equals(converter.output())
                        && seedInput.returned()
                        && seedInput.uses() == CraftInput.INFINITE_USES
                        && seedInput.reusableStockSource() != null
                        && feedbackConverterInput(
                                converter, loopPattern.output(), seedInput.key())
                                == converterInput) {
                    return new FeedbackSeedBootstrap<>(
                            loopPattern, seedInput, converter, converterInput);
                }
            }
        }
        return null;
    }

    /**
     * The single loop-state input of a candidate bootstrap converter, or null when the candidate
     * cannot serve as one. Exactly one ordinary input consumes the loop's output state; every other
     * input must be an ordinary material from outside the cycle (e.g. the reaction chamber's water),
     * obtained normally when the bootstrap fires. Byproducts stay excluded so the converter's only
     * effect on the cycle is the state conversion itself.
     */
    private CraftInput<K> feedbackConverterInput(
            CraftPattern<K> candidate, K loopOutput, K seedKey) {
        if (!candidate.byproducts().isEmpty()) return null;
        CraftInput<K> loopInput = null;
        for (CraftInput<K> input : candidate.inputs()) {
            if (input.returned() || input.remainder() != null
                    || input.reusableStockSource() != null) {
                return null;
            }
            if (input.key().equals(loopOutput)) {
                if (loopInput != null) return null;
                loopInput = input;
            } else if (input.key().equals(seedKey)) {
                return null;
            }
        }
        return loopInput;
    }

    /** cap[X] = stock + max recipe-producible (reverse-topo, byproducts ignored = optimistic upper bound). */
    private Map<K, Long> capacityFromOrder(List<K> order, int sizeHint) {
        Map<K, Long> cap = new HashMap<>(sizeHint * 2);
        for (int i = order.size() - 1; i >= 0; i--) {
            K x = order.get(i);
            long best = 0;
            for (CraftPattern<K> p : patternsByOutput.getOrDefault(x, List.of())) {
                best = Math.max(best, producibleVia(p, cap));
                if (Sat.isSaturated(best)) {
                    break;
                }
            }
            cap.put(x, Sat.add(graph.stock(x), best));
        }
        return cap;
    }

    /** Computes the longest acyclic dependency chain beneath every candidate recipe. */
    private void computeRouteDepth(List<K> order) {
        routeDepth.clear();
        Map<K, Integer> itemDepth = new HashMap<>();
        for (int i = order.size() - 1; i >= 0; i--) {
            K item = order.get(i);
            int best = 0;
            for (CraftPattern<K> pattern : patternsByOutput.getOrDefault(item, List.of())) {
                int depthForPattern = 1;
                for (CraftInput<K> input : pattern.inputs()) {
                    depthForPattern = Math.max(
                            depthForPattern, 1 + itemDepth.getOrDefault(input.key(), 0));
                }
                routeDepth.put(pattern, depthForPattern);
                best = Math.max(best, depthForPattern);
            }
            itemDepth.put(item, best);
        }
    }

    /**
     * Gives simple deterministic recipe trees a canonical material-transformation id. Intermediate
     * item names disappear from the shape, so {@code E→C→A1} and {@code E→D→A2} receive the same id,
     * while exact batch sizes and branching structure remain part of it.
     *
     * <p>This is deliberately a proof, not a heuristic. A craftable intermediate with direct stock or
     * possible dynamic pool credit keeps its own identity, and patterns with returned inputs,
     * remainders, reusable hosts or byproducts are left unclassified. Those routes still search
     * normally; only a pair with identical classified ids may skip a repeated failed expansion.
     */
    private void indexEquivalentMaterialFootprints(List<K> order) {
        Set<K> dynamicPoolKeys = new HashSet<>();
        for (Map.Entry<K, List<CraftPattern<K>>> entry : patternsByOutput.entrySet()) {
            for (CraftPattern<K> pattern : entry.getValue()) {
                if (pattern.outputAmount() > 1) {
                    dynamicPoolKeys.add(pattern.output());
                }
                for (CraftOutput<K> output : pattern.byproducts()) {
                    dynamicPoolKeys.add(output.key());
                }
                for (CraftInput<K> input : pattern.inputs()) {
                    if (input.returned() || input.remainder() != null
                            || input.reusableStockSource() != null) {
                        dynamicPoolKeys.add(input.key());
                        if (input.remainder() != null) {
                            dynamicPoolKeys.add(input.remainder());
                        }
                    }
                }
            }
        }

        FootprintInterner interner = new FootprintInterner();
        Map<K, Integer> footprintByKey = new HashMap<>();
        for (int i = order.size() - 1; i >= 0; i--) {
            K key = order.get(i);
            List<CraftPattern<K>> patterns = patternsByOutput.getOrDefault(key, List.of());
            if (patterns.isEmpty()) {
                footprintByKey.put(key, interner.intern(new MaterialLeaf(key)));
                continue;
            }

            Integer common = null;
            boolean allEquivalent = true;
            for (CraftPattern<K> pattern : patterns) {
                Integer footprint = materialFootprint(pattern, footprintByKey, interner);
                if (footprint != null) {
                    materialFootprintByPattern.put(pattern, footprint);
                }
                if (footprint == null) {
                    allEquivalent = false;
                } else if (common == null) {
                    common = footprint;
                } else if (!common.equals(footprint)) {
                    allEquivalent = false;
                }
            }

            // Direct stock and dynamic surplus/byproduct credit belong to this concrete intermediate,
            // not merely to its production tree. Keep its identity when it is used by a parent.
            if (graph.stock(key) > 0 || dynamicPoolKeys.contains(key) || !allEquivalent) {
                footprintByKey.put(key, interner.intern(new MaterialLeaf(key)));
            } else {
                footprintByKey.put(key, common);
            }
        }
    }

    private Integer materialFootprint(
            CraftPattern<K> pattern,
            Map<K, Integer> footprintByKey,
            FootprintInterner interner) {
        if (!pattern.byproducts().isEmpty()) {
            return null;
        }

        Map<Integer, Long> amounts = new HashMap<>();
        for (CraftInput<K> input : pattern.inputs()) {
            if (input.returned() || input.remainder() != null
                    || input.reusableStockSource() != null) {
                return null;
            }
            Integer inputFootprint = footprintByKey.get(input.key());
            if (inputFootprint == null) {
                return null;
            }
            long previous = amounts.getOrDefault(inputFootprint, 0L);
            if (Long.MAX_VALUE - previous < input.amount()) {
                return null; // exact proof only; never merge two different saturated totals
            }
            amounts.put(inputFootprint, previous + input.amount());
        }

        List<MaterialTerm> terms = new ArrayList<>(amounts.size());
        for (Map.Entry<Integer, Long> entry : amounts.entrySet()) {
            terms.add(new MaterialTerm(entry.getKey(), entry.getValue()));
        }
        terms.sort((left, right) -> Integer.compare(left.footprint(), right.footprint()));
        return interner.intern(new MaterialRecipe(pattern.outputAmount(), List.copyOf(terms)));
    }

    private long producibleVia(CraftPattern<K> p, Map<K, Long> cap) {
        return producibleVia(p, cap, null);
    }

    private long producibleVia(
            CraftPattern<K> p,
            Map<K, Long> cap,
            Map<CraftPattern<K>, Long> memo) {
        if (memo != null) {
            Long cached = memo.get(p);
            if (cached != null) {
                return cached;
            }
        }
        long bound = Sat.SAT;
        boolean feedbackSeedsBootstrappable = canBootstrapAllFeedbackSeeds(p, cap);
        for (CraftInput<K> in : p.inputs()) {
            long c;
            if (in.reusableStockSource() != null) {
                c = Sat.add(
                        graph.reusableStock(in.reusableStockSource(), in.key()),
                        cap.getOrDefault(in.key(), 0L));
                if (feedbackSeedsBootstrappable
                        && feedbackSeedBootstrap(p, in, cap) != null) {
                    // One physical output-state token can be converted into the reusable seed. Once
                    // that seed exists it supports every firing, exactly like a host-owned catalyst.
                    c = Math.max(c, in.amount());
                }
            } else if (isSelfReturnedSeed(p, in)) {
                c = graph.stock(in.key());
                for (CraftPattern<K> alternative : patternsByOutput.getOrDefault(in.key(), List.of())) {
                    if (alternative == p || hasSelfReturnedSeed(alternative)) continue;
                    c = Sat.add(c, producibleVia(alternative, cap, memo));
                    if (c >= in.amount()) break;
                }
            } else {
                c = cap.getOrDefault(in.key(), 0L);
            }
            bound = Math.min(bound, in.firingsFrom(c)); // finite-use tools bound by uses·units
            if (bound == 0) {
                if (memo != null) {
                    memo.put(p, 0L);
                }
                return 0;
            }
        }
        long result = Sat.mul(bound, p.outputAmount());
        if (memo != null) {
            memo.put(p, result);
        }
        return result;
    }

    private void indexCapacityOrder() {
        capacityScoreByPattern.clear();
        capacityOrderByOutput.clear();
        for (Map.Entry<K, List<CraftPattern<K>>> entry : patternsByOutput.entrySet()) {
            List<CraftPattern<K>> ordered = new ArrayList<>(entry.getValue());
            for (CraftPattern<K> pattern : ordered) {
                capacityScore(pattern);
            }
            ordered = groupedCapacityOrder(
                    ordered, this::capacityScore, this::preexistingStockCapacity);
            capacityOrderByOutput.put(entry.getKey(), List.copyOf(ordered));
        }
    }

    private long capacityScore(CraftPattern<K> pattern) {
        Long cached = capacityScoreByPattern.get(pattern);
        if (cached != null) {
            return cached;
        }
        return producibleVia(pattern, capacity, capacityScoreByPattern);
    }

    /**
     * Capacity supplied directly by the immutable inventory snapshot, without recursively counting
     * craftable inputs. The AE2 adapter emits one concrete route per accepted fuzzy/NBT variant and
     * gives all of those routes the same source object. Within that source group, stocked variants
     * must be consumed before a declared variant that merely has a larger upstream crafting capacity.
     */
    private long preexistingStockCapacity(CraftPattern<K> pattern) {
        long bound = Sat.SAT;
        for (CraftInput<K> input : pattern.inputs()) {
            bound = Math.min(bound, input.firingsFrom(graph.stock(input.key())));
            if (bound == 0) return 0L;
        }
        return Sat.mul(bound, pattern.outputAmount());
    }

    private void indexDirectRawConsumables() {
        directRawConsumablesByPattern.clear();
        for (List<CraftPattern<K>> patterns : patternsByOutput.values()) {
            for (CraftPattern<K> pattern : patterns) {
                Map<K, Long> perFiring = new HashMap<>();
                for (CraftInput<K> input : pattern.inputs()) {
                    if (input.returned() || input.reusableStockSource() != null
                            || !patternsByOutput.getOrDefault(input.key(), List.of()).isEmpty()) {
                        continue;
                    }
                    perFiring.merge(input.key(), input.amount(), Sat::add);
                }
                if (!perFiring.isEmpty()) {
                    directRawConsumablesByPattern.put(pattern, Map.copyOf(perFiring));
                }
            }
        }
    }

    private List<CraftPattern<K>> capacityOrder(K key) {
        List<CraftPattern<K>> ordered = capacityOrderByOutput.getOrDefault(
                key, patternsByOutput.getOrDefault(key, List.of()));
        return promotePreferredRoute(key, ordered);
    }

    private List<CraftPattern<K>> promotePreferredRoute(
            K key, List<CraftPattern<K>> ordered) {
        CraftPattern<K> preferred = routePreferences.get(key);
        int index = preferred == null ? -1 : ordered.indexOf(preferred);
        if (index <= 0) {
            return ordered;
        }
        List<CraftPattern<K>> promoted = new ArrayList<>(ordered.size());
        promoted.add(preferred);
        for (CraftPattern<K> pattern : ordered) {
            if (pattern != preferred) {
                promoted.add(pattern);
            }
        }
        return promoted;
    }

    private boolean canBootstrapAllFeedbackSeeds(
            CraftPattern<K> pattern, Map<K, Long> materialCapacity) {
        List<FeedbackSeedBootstrap<K>> bootstraps = feedbackSeedBootstraps.get(pattern);
        if (bootstraps == null || bootstraps.isEmpty()) return false;
        long requiredOutput = 0L;
        long availableOutput = graph.stock(pattern.output());
        Set<Object> countedStorageScopes = new HashSet<>();
        boolean foundSeed = false;
        for (CraftInput<K> seed : pattern.inputs()) {
            FeedbackSeedBootstrap<K> bootstrap =
                    feedbackSeedBootstrap(pattern, seed, materialCapacity);
            if (bootstrap == null) continue;
            foundSeed = true;
            long hostAvailable = graph.reusableStock(seed.reusableStockSource(), seed.key());
            long seedShortfall = Math.max(0L, seed.amount() - hostAvailable);
            if (feedbackBootstrapSeedCapacity(bootstrap, materialCapacity) < seedShortfall) {
                return false;
            }
            requiredOutput = Sat.add(
                    requiredOutput, bootstrap.outputUnitsFor(seedShortfall));
            Object storageScope = seed.reusableStockSource().storageScope();
            if (countedStorageScopes.add(storageScope)) {
                availableOutput = Sat.add(
                        availableOutput, graph.reusableStock(storageScope, pattern.output()));
            }
        }
        return foundSeed && availableOutput >= requiredOutput;
    }

    // ---- linear backbone: one topological aggregation pass, each item resolved once -------------

    /**
     * Resolves the whole request in a single topological pass (target → leaves). Each item is visited
     * once, its full demand already aggregated, then split across recipes by current remaining
     * capacity. Capacity is reserved with O(1) deduction ({@code need} doubles as the reservation
     * counter), so there is no recompute loop — this is the {@code O(n + E)} clean backbone. If it
     * comes back feasible the plan is exact and contention never bound; otherwise the caller escalates
     * to the bounded search on the contended cone.
     */
    private CraftPlan<K> linearPass(List<K> order, K target, long amount) {
        LinearPassState<K> pass = linearPassState(order, target, amount, Map.of());
        boolean feasible = pass.miss().isEmpty();
        return new CraftPlan<>(true, feasible, pass.fired(), pass.used(), Map.of(), pass.miss(),
                pass.gross(), pass.done(), false);
    }

    /** Everything one aggregation sweep learned; the allocation-repair loop reads it back. */
    private record LinearPassState<K>(
            Map<K, Long> need,
            Map<CraftPattern<K>, Long> fired,
            Map<K, Long> used,
            Map<K, Long> miss,
            Map<K, Long> gross,
            Map<CraftPattern<K>, Long> allocatedUnits,
            int done) {
    }

    /**
     * Anytime allocation search whose decision variables are ONLY the contended outputs' route
     * allocations. Each iteration is one O(E) aggregation sweep under per-route unit caps; when the
     * sweep is infeasible, the largest raw shortfall is attributed backward through the realized
     * demand flow to the contended route that pulled most of it, that route's cap is tightened by
     * the attributed amount, and the freed demand re-splits across sibling routes on the next sweep.
     * This finds mixed allocations (partially route A, remainder route B) that the all-or-nothing
     * recursive search cannot express, and its cost scales with the number of contended outputs
     * rather than with root-to-leaf path counts. Returns {@code null} when repair gave up — the
     * recursive search then runs unchanged.
     */
    private CraftPlan<K> allocationRepair(List<K> order, K target, long amount) {
        int contended = preparedGraph.contendedOutputCount;
        if (contended == 0) {
            return null; // nothing to reallocate; the plain pass already had the only answer
        }
        int maxIterations = (int) Math.min(64L, 4L + 2L * contended);
        Map<CraftPattern<K>, Long> caps = new IdentityHashMap<>();
        Set<CraftPattern<K>> ineffective =
                java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < maxIterations; i++) {
            if (!searchBudget.tryConsume()) {
                return null;
            }
            diagnostics.recordDynamicCapacityEvaluation();
            LinearPassState<K> pass = linearPassState(order, target, amount, caps);
            if (pass.miss().isEmpty()) {
                return new CraftPlan<>(true, true, pass.fired(), pass.used(), Map.of(), Map.of(),
                        pass.gross(), pass.done(), false);
            }
            if (!tightenBlamedRoute(pass, caps, ineffective)) {
                return null;
            }
        }
        return null;
    }

    /**
     * Attributes the largest leaf shortfall backward through this sweep's realized demand flow and
     * tightens the cap of the contended route that pulled the biggest share of it. Blame fractions
     * are heuristic step sizes only — every resulting allocation is re-validated by the next full
     * sweep, so precision here affects convergence speed, never correctness.
     *
     * @return false when no cap can make further progress
     */
    private boolean tightenBlamedRoute(
            LinearPassState<K> pass,
            Map<CraftPattern<K>, Long> caps,
            Set<CraftPattern<K>> ineffective) {
        K shortLeaf = null;
        long shortfall = 0L;
        for (Map.Entry<K, Long> entry : pass.miss().entrySet()) {
            if (entry.getValue() > shortfall) {
                shortLeaf = entry.getKey();
                shortfall = entry.getValue();
            }
        }
        if (shortLeaf == null) {
            return false;
        }

        // consumers[i] = the fired patterns that demanded item i this sweep, with their unit draw.
        Map<K, List<Map.Entry<CraftPattern<K>, Long>>> consumers = new HashMap<>();
        for (Map.Entry<CraftPattern<K>, Long> firing : pass.fired().entrySet()) {
            CraftPattern<K> r = firing.getKey();
            for (CraftInput<K> in : r.inputs()) {
                long units = in.unitsFor(firing.getValue());
                if (units > 0) {
                    consumers.computeIfAbsent(in.key(), ignored -> new ArrayList<>())
                            .add(Map.entry(r, units));
                }
            }
        }

        // Backward blame propagation, leaves toward the target (reverse topological order).
        Map<K, Double> blameByItem = new HashMap<>();
        blameByItem.put(shortLeaf, (double) shortfall);
        CraftPattern<K> blamed = null;
        double blamedShare = 0.0;
        List<K> order = preparedGraph.order;
        for (int i = order.size() - 1; i >= 0; i--) {
            K item = order.get(i);
            Double blame = blameByItem.get(item);
            if (blame == null || blame <= 0.0) {
                continue;
            }
            long demand = pass.need().getOrDefault(item, 0L);
            if (demand <= 0) {
                continue;
            }
            for (Map.Entry<CraftPattern<K>, Long> consumer
                    : consumers.getOrDefault(item, List.of())) {
                CraftPattern<K> r = consumer.getKey();
                double share = blame * consumer.getValue() / (double) demand;
                if (share <= 0.0) {
                    continue;
                }
                if (isContendedOutput(r.output()) && !ineffective.contains(r) && share > blamedShare) {
                    blamed = r;
                    blamedShare = share;
                }
                blameByItem.merge(r.output(), share, Double::sum);
            }
        }
        if (blamed == null) {
            return false;
        }

        long allocated = pass.allocatedUnits().getOrDefault(blamed, 0L);
        long leafDemand = pass.need().getOrDefault(shortLeaf, 0L);
        // contribution of the blamed route to the leaf ≈ (blamedShare/shortfall)·leafDemand;
        // removing Δ allocation units removes Δ·contribution/allocated leaf units.
        double contribution = blamedShare / (double) shortfall * (double) leafDemand;
        long delta = contribution <= 0.0
                ? allocated
                : (long) Math.ceil((double) shortfall * (double) allocated / contribution);
        long newCap = Math.max(0L, allocated - Math.max(1L, delta));
        Long existing = caps.get(blamed);
        if (existing != null && existing <= newCap) {
            ineffective.add(blamed);
            return tightenBlamedRoute(pass, caps, ineffective);
        }
        caps.put(blamed, newCap);
        return true;
    }

    private boolean isContendedOutput(K output) {
        List<CraftPattern<K>> ps = patternsByOutput.getOrDefault(output, List.of());
        return ps.size() > 1 && distinctMaterialBranches(capacityOrder(output)).size() > 1;
    }

    /**
     * One topological aggregation sweep. {@code capUnits} (possibly empty) bounds how many units of
     * its own output each pattern may be allocated; the bound is soft — demand nobody has capacity
     * or cap room for is still pushed down the primary recipe so shortfalls always surface at raw
     * leaves, never as silently dropped demand.
     */
    private LinearPassState<K> linearPassState(
            List<K> order, K target, long amount, Map<CraftPattern<K>, Long> capUnits) {
        Map<K, Long> need = new HashMap<>();
        Map<K, Long> bp = new HashMap<>();       // byproduct / surplus pool
        Map<K, Long> stockL = new HashMap<>();   // remaining inventory
        Map<K, Long> used = new HashMap<>();
        Map<K, Long> miss = new HashMap<>();
        Map<K, Long> gross = new HashMap<>();
        // One unchanged catalyst can serve every compatible pattern sequentially. Track the largest
        // seed reserve separately so ordinary consumption of the same key is still added on top.
        Map<K, Long> returnedSeedReserve = new HashMap<>();
        Map<CraftPattern<K>, Long> fired = new IdentityHashMap<>();
        Map<CraftPattern<K>, Long> allocatedUnits = new IdentityHashMap<>();
        need.put(target, amount);
        int done = 0;

        for (K x : order) {
            long d = need.getOrDefault(x, 0L);
            if (d <= 0) {
                continue;
            }
            done++;
            gross.put(x, d);

            long fromBp = Math.min(d, lget(bp, x));
            if (fromBp > 0) {
                bp.put(x, lget(bp, x) - fromBp);
                d -= fromBp;
            }
            long fromStock = Math.min(d, stockL.computeIfAbsent(x, graph::stock));
            if (fromStock > 0) {
                stockL.put(x, lget(stockL, x) - fromStock);
                used.merge(x, fromStock, Sat::add);
                d -= fromStock;
            }
            if (d <= 0) {
                continue;
            }

            List<CraftPattern<K>> ps = patternsByOutput.getOrDefault(x, List.of());
            if (ps.isEmpty()) {
                miss.merge(x, d, Sat::add);
                continue;
            }
            allocateLinear(x, d, ps, need, bp, returnedSeedReserve, fired, capUnits, allocatedUnits);
        }

        return new LinearPassState<>(need, fired, used, miss, gross, allocatedUnits, done);
    }

    /** Split {@code d} of {@code x} across recipes by current remaining capacity (dynamic balance). */
    private void allocateLinear(K x, long d, List<CraftPattern<K>> ps,
                                Map<K, Long> need, Map<K, Long> bp,
                                Map<K, Long> returnedSeedReserve,
                                Map<CraftPattern<K>, Long> fired,
                                Map<CraftPattern<K>, Long> capUnits,
                                Map<CraftPattern<K>, Long> allocatedUnits) {
        List<CraftPattern<K>> ordered = groupedCapacityOrder(
                ps,
                pattern -> capRemainingVia(pattern, need),
                pattern -> preexistingStockRemainingCapacity(pattern, need));

        for (CraftPattern<K> r : ordered) {
            if (d <= 0) {
                break;
            }
            long p = capRemainingVia(r, need);
            Long cap = capUnits.get(r);
            if (cap != null) {
                p = io.github.langqi99.aeallpattern.util.CompatMath.clamp(cap - allocatedUnits.getOrDefault(r, 0L), 0L, p);
            }
            if (p <= 0) {
                continue;
            }
            long make = Math.min(d, p);
            long t = Sat.ceilDiv(make, r.outputAmount());
            long consumed = Math.min(d, Sat.mul(t, r.outputAmount()));
            allocatedUnits.merge(r, consumed, Sat::add);
            fireLinear(x, r, t, consumed, need, bp, returnedSeedReserve, fired);
            d -= consumed;
        }
        // Leftover nobody had capacity for: push demand down the primary recipe; the deficit surfaces
        // at the raw leaves (same optimistic behaviour as AE2's simulation).
        if (d > 0) {
            CraftPattern<K> r0 = ps.get(0);
            long t = Sat.ceilDiv(d, r0.outputAmount());
            allocatedUnits.merge(r0, d, Sat::add);
            fireLinear(x, r0, t, d, need, bp, returnedSeedReserve, fired);
        }
    }

    private void fireLinear(K x, CraftPattern<K> r, long t, long consumedOwn,
                            Map<K, Long> need, Map<K, Long> bp,
                            Map<K, Long> returnedSeedReserve,
                            Map<CraftPattern<K>, Long> fired) {
        long previousFirings = fired.getOrDefault(r, 0L);
        fired.merge(r, t, Sat::add);
        for (CraftInput<K> in : r.inputs()) {
            long amt = in.unitsFor(t); // closed form: normal=amount·t, catalyst=amount, finite-use=amount·ceil(t/uses)
            if (in.returned()
                    && in.uses() == CraftInput.INFINITE_USES
                    && in.reusableStockSource() == null) {
                long previousReserve = returnedSeedReserve.getOrDefault(in.key(), 0L);
                if (amt > previousReserve) {
                    need.merge(in.key(), amt - previousReserve, Sat::add);
                    returnedSeedReserve.put(in.key(), amt);
                }
            } else {
                need.merge(in.key(), amt, Sat::add);
            }
        }
        Map<K, Long> bootstrapReserves = new HashMap<>(
                linearContainerBootstrapReserves.getOrDefault(r, Map.of()));
        bootstrapReserves.replaceAll((key, reserve) -> Math.max(
                0L, reserve - Math.min(
                        reserve, Sat.mul(byproductAmount(r, key), previousFirings))));
        for (CraftOutput<K> out : r.byproducts()) {
            if (mayReuseByproduct(r, out.key())) {
                long produced = Sat.mul(out.amount(), t);
                long withheld = Math.min(
                        produced, bootstrapReserves.getOrDefault(out.key(), 0L));
                if (withheld > 0) {
                    bootstrapReserves.put(out.key(),
                            bootstrapReserves.get(out.key()) - withheld);
                }
                if (produced > withheld) {
                    bp.merge(out.key(), produced - withheld, Sat::add);
                }
            }
        }
        long surplus = Sat.mul(t, r.outputAmount()) - consumedOwn;
        if (surplus > 0) {
            bp.merge(x, surplus, Sat::add);
        }
    }

    /** capRemaining(input) = static capacity − already-reserved demand; combined over a recipe's inputs. */
    private long capRemainingVia(CraftPattern<K> r, Map<K, Long> need) {
        long bound = Sat.SAT;
        for (CraftInput<K> in : r.inputs()) {
            long cr = Math.max(0L, capacity.getOrDefault(in.key(), 0L) - need.getOrDefault(in.key(), 0L));
            bound = Math.min(bound, in.firingsFrom(cr));
            if (bound == 0) {
                return 0;
            }
        }
        return Sat.mul(bound, r.outputAmount());
    }

    private long preexistingStockRemainingCapacity(
            CraftPattern<K> pattern, Map<K, Long> need) {
        long bound = Sat.SAT;
        for (CraftInput<K> input : pattern.inputs()) {
            long remaining = Math.max(
                    0L, graph.stock(input.key()) - need.getOrDefault(input.key(), 0L));
            bound = Math.min(bound, input.firingsFrom(remaining));
            if (bound == 0) return 0L;
        }
        return Sat.mul(bound, pattern.outputAmount());
    }

    /**
     * Orders real recipes by total capacity as before, but treats concrete expansions of the same
     * source recipe as one route group. The group's best total capacity determines its position
     * relative to unrelated recipes; inside the group, direct inventory capacity wins. This keeps
     * ordinary recipe preference unchanged while making ignore-NBT/fuzzy expansions consume an
     * already-stocked sibling variant before recursively crafting the pattern's declared variant.
     */
    private List<CraftPattern<K>> groupedCapacityOrder(
            List<CraftPattern<K>> patterns,
            ToLongFunction<CraftPattern<K>> totalCapacity,
            ToLongFunction<CraftPattern<K>> directStockCapacity) {
        if (patterns.size() < 2) {
            return patterns;
        }

        List<List<CraftPattern<K>>> groups = new ArrayList<>();
        IdentityHashMap<Object, List<CraftPattern<K>>> bySource = new IdentityHashMap<>();
        for (CraftPattern<K> pattern : patterns) {
            Object source = pattern.source();
            if (source == null) {
                groups.add(new ArrayList<>(List.of(pattern)));
                continue;
            }
            List<CraftPattern<K>> group = bySource.get(source);
            if (group == null) {
                group = new ArrayList<>();
                bySource.put(source, group);
                groups.add(group);
            }
            group.add(pattern);
        }

        for (List<CraftPattern<K>> group : groups) {
            if (group.size() > 1) {
                group.sort((left, right) -> {
                    int byStock = Long.compare(
                            directStockCapacity.applyAsLong(right),
                            directStockCapacity.applyAsLong(left));
                    return byStock != 0
                            ? byStock
                            : Long.compare(
                                    totalCapacity.applyAsLong(right),
                                    totalCapacity.applyAsLong(left));
                });
            }
        }
        groups.sort((left, right) -> compareRouteGroups(
                left, right, totalCapacity, directStockCapacity));

        List<CraftPattern<K>> ordered = new ArrayList<>(patterns.size());
        for (List<CraftPattern<K>> group : groups) {
            ordered.addAll(group);
        }
        return ordered;
    }

    private long maxCapacity(
            List<CraftPattern<K>> patterns,
            ToLongFunction<CraftPattern<K>> capacity) {
        long best = 0L;
        for (CraftPattern<K> pattern : patterns) {
            best = Math.max(best, capacity.applyAsLong(pattern));
        }
        return best;
    }

    /** Lower means the left real recipe is preferred. Fuzzy variants remain grouped by source. */
    private int compareRouteGroups(
            List<CraftPattern<K>> left,
            List<CraftPattern<K>> right,
            ToLongFunction<CraftPattern<K>> totalCapacity,
            ToLongFunction<CraftPattern<K>> directStockCapacity) {
        CraftPattern<K> a = left.get(0);
        CraftPattern<K> b = right.get(0);
        long capacityA = maxCapacity(left, totalCapacity);
        long capacityB = maxCapacity(right, totalCapacity);

        // Calls made without a route policy keep the imported planner's original ordering.
        // A non-null policy is supplied only by the router-aware AE adapter (or explicit callers).
        if (routePolicy == null) {
            return Long.compare(capacityB, capacityA);
        }

        int byAggregatePriority = Integer.compare(routePriority(b), routePriority(a));
        if (byAggregatePriority != 0) {
            return byAggregatePriority;
        }
        for (int position = 0; position < CraftingRoutePolicy.CRITERION_COUNT; position++) {
            int comparison = switch (routePolicy.criterionAt(position)) {
                case CraftingRoutePolicy.CRITERION_PATH -> comparePath(a, b);
                case CraftingRoutePolicy.CRITERION_STOCK_SURPLUS -> compareDirected(
                        routePolicy.stockSurplusPreference(),
                        capacityA,
                        capacityB);
                case CraftingRoutePolicy.CRITERION_HIGH_YIELD -> compareDirected(
                        routePolicy.yieldPreference(), a.outputAmount(), b.outputAmount());
                case CraftingRoutePolicy.CRITERION_FAST -> routePolicy.preferFast()
                        ? compareWaitingTime(a, b)
                        : 0;
                default -> 0;
            };
            if (comparison != 0) {
                return comparison;
            }
        }
        int byCapacity = Long.compare(capacityB, capacityA);
        if (byCapacity != 0) {
            return byCapacity;
        }
        return stableRouteId(a).compareTo(stableRouteId(b));
    }

    private int routePriority(CraftPattern<K> pattern) {
        return pattern.source() instanceof RoutingPatternMetadata metadata
                && metadata.isAggregatePattern()
                ? routePolicy.aggregatePriority()
                : 0;
    }

    /** Positive prefers larger, negative prefers smaller, zero ignores the criterion. */
    private static int compareDirected(int preference, long valueA, long valueB) {
        return preference > 0
                ? Long.compare(valueB, valueA)
                : preference < 0 ? Long.compare(valueA, valueB) : 0;
    }

    private int comparePath(CraftPattern<K> a, CraftPattern<K> b) {
        int preference = routePolicy.pathPreference();
        if (preference == 0) {
            return 0;
        }
        int depthA = routeDepth.getOrDefault(a, 1);
        int depthB = routeDepth.getOrDefault(b, 1);
        return preference < 0
                ? Integer.compare(depthA, depthB)
                : Integer.compare(depthB, depthA);
    }

    private int compareWaitingTime(CraftPattern<K> a, CraftPattern<K> b) {
        if (a.idleProviderCount() >= 0 && b.idleProviderCount() >= 0) {
            boolean canStartA = a.idleProviderCount() > 0;
            boolean canStartB = b.idleProviderCount() > 0;
            if (canStartA != canStartB) {
                return canStartA ? -1 : 1;
            }
            int byIdle = Integer.compare(b.idleProviderCount(), a.idleProviderCount());
            if (byIdle != 0) {
                return byIdle;
            }
            int byTotal = Integer.compare(b.providerCount(), a.providerCount());
            if (byTotal != 0) {
                return byTotal;
            }
        }
        return Integer.compare(processingTicks(a), processingTicks(b));
    }

    private static int processingTicks(CraftPattern<?> pattern) {
        return pattern.source() instanceof RoutingPatternMetadata metadata
                ? Math.max(1, metadata.processingTicks())
                : 1;
    }

    private static String stableRouteId(CraftPattern<?> pattern) {
        return pattern.source() instanceof RoutingPatternMetadata metadata
                ? metadata.stableRouteId()
                : String.valueOf(pattern.source());
    }

    private long lget(Map<K, Long> m, K k) {
        Long v = m.get(k);
        return v == null ? 0L : v;
    }

    // ---- core: obtain d units of x, consuming from pool/stock, crafting the rest ----------------

    /**
     * @param commitFailure whether an exhausted route must commit its greedy partial plan and concrete
     *                      missing leaves. Speculative parents pass {@code false}: they only need a
     *                      non-zero result before rolling the branch back.
     * @return the amount of {@code x} that could not be obtained
     */
    private long obtain(K x, long d, boolean commitFailure) {
        if (d <= 0) {
            return 0;
        }
        bump(grossDemand, x, d);
        reserveSelfSeed(x);
        reserveFeedbackSeedOutput(x, d);
        d -= drawPools(x, d);
        if (d <= 0) {
            return 0;
        }

        // This is a branch-local safety guard. Report only the unstocked remainder as missing so the
        // parent may roll this branch back and try another route; never invalidate unrelated nodes.
        if (depth >= MAX_OBTAIN_DEPTH) {
            if (commitFailure) addMissing(x, d);
            return d;
        }

        List<CraftPattern<K>> ps = patternsByOutput.getOrDefault(x, List.of());
        if (ps.isEmpty()) {
            if (commitFailure) addMissing(x, d);
            return d;
        }

        // An uncontended cone needs no route search: resolve it with one topological demand
        // aggregation instead of per-edge recursion. Recursion multiplies obtain() calls by the
        // number of root-to-node paths (exponential when a deep chain's tiers share nodes), so the
        // recursive search below is reserved for genuinely contended nodes.
        if (isAggregable(x)) {
            return obtainAggregate(x, d, commitFailure);
        }

        // Depth is part of the proof identity: a route rejected only because it reached the stack
        // guard must remain eligible when the same node is later reached through a shorter parent path.
        SearchFailure<K> failureKey = new SearchFailure<>(x, d, availabilityState, depth);
        if (!commitFailure && failedSpeculativeSearches.contains(failureKey)) {
            diagnostics.recordFailureMemoHit();
            return d;
        }

        // Stateful/cyclic boundaries cannot use the aggregate sweep and therefore need their own
        // bounded resolution guard. This is deliberately separate from alternative-search work: a
        // single returned seed or container is deterministic and must not consume search budget.
        if (!diagnostics.tryConsumeResolutionWork()) {
            return commitFailure ? commitBudgetFallback(x, d) : d;
        }

        if (processed < Integer.MAX_VALUE) {
            processed++;
        }

        // A single recipe needs no alternate search, but its descendants still resolve their own
        // contention normally. It consumes resolution work above, never alternative-search work.
        if (ps.size() == 1) {
            long unmet = fire(x, ps.get(0), d, !commitFailure);
            if (!commitFailure && unmet > 0
                    && !searchBudget.exhausted() && !diagnostics.resolutionExhausted()) {
                failedSpeculativeSearches.add(failureKey);
            }
            return unmet;
        }

        List<CraftPattern<K>> ordered = capacityOrder(x);
        List<CraftPattern<K>> distinctBranches = distinctMaterialBranches(ordered);
        if (distinctBranches.size() == 1) {
            // There is no materially different alternative to discover. Commit the representative
            // once instead of speculatively expanding it, rolling it back, and expanding it again.
            long unmet = fire(x, distinctBranches.get(0), d, !commitFailure);
            if (!commitFailure && unmet > 0
                    && !searchBudget.exhausted() && !diagnostics.resolutionExhausted()) {
                failedSpeculativeSearches.add(failureKey);
            }
            return unmet;
        }

        // Only a materially contended node reaches this point. Once alternative-search work is
        // exhausted, finish its selected capacity-first route through the separately bounded tail.
        if (searchBudget.exhausted()) {
            return commitFailure ? commitBudgetFallback(x, d) : d;
        }

        int v = visit.getOrDefault(x, 0);
        if (v >= visitCap) {
            diagnostics.recordHotNodeVisit();
            return obtainHot(x, d, commitFailure, failureKey);
        }
        visit.put(x, v + 1);

        for (CraftPattern<K> r : distinctBranches) {
            if (hasProvenDirectConsumableShortfall(r, d)) {
                continue;
            }
            if (!searchBudget.tryConsume()) {
                return commitFailure ? commitBestEffort(distinctBranches, x, d) : d;
            }
            int mark = trail.size();
            long beforeMissing = missingTotal;
            recordRouteDecision(x, r, distinctBranches);
            long unmet = fire(x, r, d, true);
            if (searchBudget.exhausted() || diagnostics.resolutionExhausted()) {
                rollback(mark);
                return commitFailure ? commitBestEffort(distinctBranches, x, d) : d;
            }
            if (unmet == 0 && missingTotal == beforeMissing) {
                return unmet; // this recipe satisfied d without introducing any shortfall
            }
            rollback(mark); // restores pool/firings/missing(+total); try the next recipe
        }
        if (!commitFailure) {
            failedSpeculativeSearches.add(failureKey);
            return d;
        }
        // Root/final route: commit the highest-capacity one and record its concrete missing leaves.
        return commitBestEffort(distinctBranches, x, d);
    }

    /**
     * A node revisited under many parent alternatives is re-ranked against the exact current pools.
     * Every materially distinct route remains eligible: the plan-wide work budget, rather than a
     * per-node candidate cutoff, is the only search bound.
     */
    private long obtainHot(
            K x, long d, boolean commitFailure, SearchFailure<K> failureKey) {
        List<CraftPattern<K>> distinctBranches = hotRouteOrder(x);
        if (searchBudget.exhausted()) {
            return commitFailure ? commitBestEffort(distinctBranches, x, d) : d;
        }
        for (CraftPattern<K> route : distinctBranches) {
            if (hasProvenDirectConsumableShortfall(route, d)) {
                continue;
            }
            if (!searchBudget.tryConsume()) {
                return commitFailure ? commitBestEffort(distinctBranches, x, d) : d;
            }
            int mark = trail.size();
            long beforeMissing = missingTotal;
            recordRouteDecision(x, route, distinctBranches);
            long unmet = fire(x, route, d, true);
            if (searchBudget.exhausted() || diagnostics.resolutionExhausted()) {
                rollback(mark);
                return commitFailure ? commitBestEffort(distinctBranches, x, d) : d;
            }
            if (unmet == 0 && missingTotal == beforeMissing) {
                return 0L;
            }
            rollback(mark);
        }
        if (!commitFailure) {
            failedSpeculativeSearches.add(failureKey);
            return d;
        }
        return commitBestEffort(distinctBranches, x, d);
    }

    /**
     * Re-ranks the immutable capacity order using the inventory/byproduct pools of this exact branch.
     * Only ordinary DAG material edges participate in the dynamic estimate; returned/reusable inputs
     * retain their already-proven static score because their availability has separate reservation
     * semantics. Sorting is stable, so equal estimates preserve the caller-defined preference order.
     */
    private List<CraftPattern<K>> hotRouteOrder(K x) {
        List<CraftPattern<K>> routes =
                new ArrayList<>(distinctMaterialBranches(capacityOrder(x)));
        if (routes.size() < 2) {
            return routes;
        }

        Map<K, Long> currentCapacity = new HashMap<>();
        Map<CraftPattern<K>, Long> scores = new IdentityHashMap<>();
        for (CraftPattern<K> route : routes) {
            scores.put(route, currentCapacityVia(
                    route, currentCapacity, new HashSet<>()));
            if (searchBudget.exhausted()) {
                return routes;
            }
        }
        routes.sort((left, right) ->
                Long.compare(scores.get(right), scores.get(left)));
        return promotePreferredRoute(x, routes);
    }

    /**
     * Consumable-bound no-good proof for this exact rollback-restored pool. Full material footprints
     * need not match: {@code 5 A + 8 C} and {@code 4 B + 8 C} are both impossible when fewer than
     * {@code 8 C} remain and C has no producer. Quantity is aggregated across duplicate slots, so a
     * route needing only one available C is never pruned by another route's eight-C shortfall.
     *
     * <p>Only ordinary, directly consumed raw leaves participate. Returned/reusable inputs and
     * craftable intermediates keep their normal reservation and recursive-search semantics.
     */
    private boolean hasProvenDirectConsumableShortfall(CraftPattern<K> pattern, long demand) {
        long times = Sat.ceilDiv(demand, pattern.outputAmount());
        for (Map.Entry<K, Long> entry
                : directRawConsumablesByPattern.getOrDefault(pattern, Map.of()).entrySet()) {
            long required = Sat.mul(entry.getValue(), times);
            ConsumableProofState<K> state =
                    new ConsumableProofState<>(entry.getKey(), availabilityState);
            Long unavailableFrom = provenDirectConsumableShortfalls.get(state);
            if (unavailableFrom != null && required >= unavailableFrom) {
                return true;
            }
            long available = Sat.add(
                    get(stockLeft, entry.getKey()), get(bpPool, entry.getKey()));
            if (available < required) {
                provenDirectConsumableShortfalls.merge(state, required, Math::min);
                return true;
            }
        }
        return false;
    }

    private long currentCapacityVia(
            CraftPattern<K> pattern,
            Map<K, Long> memo,
            Set<K> evaluating) {
        diagnostics.recordDynamicCapacityEvaluation();
        if (!searchBudget.tryConsume()) {
            return 0L;
        }
        long bound = Sat.SAT;
        for (CraftInput<K> input : pattern.inputs()) {
            if (input.returned() || input.reusableStockSource() != null) {
                return capacityScore(pattern);
            }
            long available = currentCapacity(input.key(), memo, evaluating);
            bound = Math.min(bound, input.firingsFrom(available));
            if (bound == 0) {
                return 0L;
            }
        }
        return Sat.mul(bound, pattern.outputAmount());
    }

    private long currentCapacity(
            K key,
            Map<K, Long> memo,
            Set<K> evaluating) {
        Long cached = memo.get(key);
        if (cached != null) {
            return cached;
        }

        long immediate = Sat.add(get(stockLeft, key), get(bpPool, key));
        if (!evaluating.add(key)) {
            return immediate;
        }
        long bestCrafted = 0L;
        for (CraftPattern<K> pattern
                : patternsByOutput.getOrDefault(key, List.of())) {
            bestCrafted = Math.max(
                    bestCrafted,
                    currentCapacityVia(pattern, memo, evaluating));
            if (searchBudget.exhausted()) {
                evaluating.remove(key);
                return immediate;
            }
            if (Sat.isSaturated(bestCrafted)) {
                break;
            }
        }
        evaluating.remove(key);

        long result = Sat.add(immediate, bestCrafted);
        memo.put(key, result);
        return result;
    }

    private List<CraftPattern<K>> distinctMaterialBranches(List<CraftPattern<K>> ordered) {
        if (ordered.size() < 2 || materialFootprintByPattern.isEmpty()) {
            return ordered;
        }
        Set<Integer> seen = new HashSet<>();
        List<CraftPattern<K>> distinct = new ArrayList<>(ordered.size());
        for (CraftPattern<K> pattern : ordered) {
            Integer footprint = materialFootprintByPattern.get(pattern);
            if (footprint == null || seen.add(footprint)) {
                distinct.add(pattern);
            }
        }
        diagnostics.recordEquivalentRoutesPruned(ordered.size() - distinct.size());
        return distinct;
    }

    /**
     * True when {@code y}'s own resolution is fully deterministic: at most one materially distinct
     * route, ordinary consumed inputs only (no catalysts, containers, tools or reusable-stock
     * seeds), no byproducts, and no cycle/feedback bookkeeping. Such a node never benefits from the
     * branching search, so its demand can be folded into an aggregate sweep. Descendants are NOT
     * required to be aggregable — a sweep hands their aggregated demand back to {@link #obtain}.
     */
    private boolean isAggregable(K y) {
        if (seedOrderedDependencyCone.contains(y)) {
            return false;
        }
        Boolean cached = aggregableMemo.get(y);
        if (cached != null) {
            return cached;
        }
        boolean result = computeAggregable(y);
        aggregableMemo.put(y, result);
        return result;
    }

    private boolean computeAggregable(K y) {
        if (cutOutputs.contains(y)) {
            return false;
        }
        List<CraftPattern<K>> ps = patternsByOutput.getOrDefault(y, List.of());
        if (ps.isEmpty()) {
            return false; // raw leaf: obtain()'s ordinary path already handles it in O(1)
        }
        if (ps.size() > 1 && distinctMaterialBranches(capacityOrder(y)).size() > 1) {
            return false;
        }
        for (CraftPattern<K> r : ps) {
            if (!r.byproducts().isEmpty()
                    || suppressedPositiveFeedbackOutputs.containsKey(r)
                    || feedbackSeedBootstraps.containsKey(r)
                    || feedbackSeedConverters.containsKey(r)) {
                return false;
            }
            for (CraftInput<K> in : r.inputs()) {
                if (in.returned() || in.remainder() != null || in.reusableStockSource() != null) {
                    return false;
                }
                // A byproduct-feedable input depends on sibling firing order, which the
                // topologically ordered sweep would change; leave such nodes to the recursion.
                if (byproductFeedableKeys().contains(in.key())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Keys some reachable pattern emits as a byproduct; consumers of these stay on the recursion. */
    private Set<K> byproductFeedableKeys() {
        if (byproductFeedableKeys == null) {
            Set<K> keys = new HashSet<>();
            for (List<CraftPattern<K>> patterns : patternsByOutput.values()) {
                for (CraftPattern<K> pattern : patterns) {
                    for (CraftOutput<K> out : pattern.byproducts()) {
                        keys.add(out.key());
                    }
                }
            }
            byproductFeedableKeys = keys;
        }
        return byproductFeedableKeys;
    }

    /**
     * Resolves the cone under an uncontended node by one topological demand-aggregation sweep.
     * Demand for every item is summed across all of its consumers before the item is expanded, so a
     * node shared by many parents is fired exactly once per sweep instead of once per root-to-node
     * path (which is exponential in chain depth). Contended or otherwise non-aggregable descendants
     * are handed their <em>total</em> aggregated demand through an ordinary {@link #obtain} boundary
     * call, confining the branching search to the contended subgraph. All mutations go through the
     * trail-logged helpers, so a speculative caller can roll the whole sweep back like any branch.
     *
     * <p>{@code x}'s own gross demand, seed reservations and pool draw were already performed by the
     * calling {@link #obtain}.
     */
    private long obtainAggregate(K x, long d, boolean commitFailure) {
        Map<K, Long> need = new HashMap<>();
        need.put(x, d);
        long unmet = 0L;
        boolean reached = false;
        for (K y : preparedGraph.order) {
            if (!reached) {
                if (!y.equals(x)) continue;
                reached = true;
            }
            long dy = need.getOrDefault(y, 0L);
            if (dy <= 0) {
                continue;
            }
            if (!y.equals(x)) {
                bump(grossDemand, y, dy);
                reserveSelfSeed(y);
                reserveFeedbackSeedOutput(y, dy);
                dy -= drawPools(y, dy);
                if (dy <= 0) {
                    continue;
                }
                List<CraftPattern<K>> psy = patternsByOutput.getOrDefault(y, List.of());
                if (psy.isEmpty()) {
                    unmet = Sat.add(unmet, dy);
                    if (!commitFailure) {
                        return unmet; // shortfall: the speculative caller rolls the sweep back
                    }
                    addMissing(y, dy);
                    continue;
                }
                if (!isAggregable(y)) {
                    depth++;
                    long u;
                    try {
                        u = obtain(y, dy, commitFailure);
                    } finally {
                        depth--;
                    }
                    unmet = Sat.add(unmet, u);
                    if (!commitFailure && (u > 0 || searchBudget.exhausted()
                            || diagnostics.resolutionExhausted())) {
                        return unmet;
                    }
                    continue;
                }
            }
            List<CraftPattern<K>> psy = patternsByOutput.getOrDefault(y, List.of());
            CraftPattern<K> r = psy.size() == 1 ? psy.get(0) : capacityOrder(y).get(0);
            if (processed < Integer.MAX_VALUE) {
                processed++;
            }
            long times = Sat.ceilDiv(dy, r.outputAmount());
            bumpFiring(r, times);
            for (CraftInput<K> in : r.inputs()) {
                need.merge(in.key(), in.unitsFor(times), Sat::add);
            }
            long surplus = Sat.mul(times, r.outputAmount()) - dy;
            if (surplus > 0) {
                bump(bpPool, y, surplus);
            }
        }
        return unmet;
    }

    private long commitBudgetFallback(K x, long d) {
        if (!fallbackBudget.tryConsume()) {
            addMissing(x, d);
            return d;
        }
        List<CraftPattern<K>> routes = distinctMaterialBranches(capacityOrder(x));
        if (routes.isEmpty()) {
            addMissing(x, d);
            return d;
        }
        return commitBestEffort(routes, x, d);
    }

    private long commitBestEffort(List<CraftPattern<K>> ps, K x, long d) {
        recordRouteDecision(x, ps.get(0), ps);
        return fire(x, ps.get(0), d, false);
    }

    /**
     * Fire {@code r} enough times to make {@code d} of {@code x}, obtaining its inputs recursively and
     * injecting outputs (surplus + byproducts) into the pool.
     *
     * @param search if true, abort accounting is meaningful: returns total input shortfall so the
     *               caller can decide to roll back and try another recipe.
     * @return input shortfall (0 means this recipe fully satisfied d).
     */
    private long fire(K x, CraftPattern<K> r, long d, boolean search) {
        long entryMissing = missingTotal;
        long times = Sat.ceilDiv(d, r.outputAmount());
        bumpFiring(r, times);

        boolean detectSiblingConflict = !search && r.inputs().size() > 1;
        Map<K, Long> usedAtEntry =
                detectSiblingConflict ? new HashMap<>(usedStock) : Map.of();
        int decisionsAtEntry = routeDecisions.size();
        long inputUnmet = 0;
        for (CraftInput<K> in : r.inputs()) {
            int decisionsBeforeInput = routeDecisions.size();
            Map<K, Long> missingBeforeInput =
                    detectSiblingConflict && decisionsBeforeInput > decisionsAtEntry
                            ? new HashMap<>(missing)
                            : Map.of();
            long amt = in.unitsFor(times); // closed form per flavour
            long unmet;
            ReusableSeedAcquisition reusableAcquisition = null;
            if (in.reusableStockSource() != null) {
                reusableAcquisition = obtainReusableSeed(r, in, amt, search);
                unmet = reusableAcquisition.unmet();
            } else if (isSelfReturnedSeed(r, in)) {
                long obtained = drawReservedSelfSeed(in.key(), amt);
                if (obtained < amt) {
                    obtained = Sat.add(obtained, drawPools(in.key(), amt - obtained));
                }
                long stillNeeded = amt - obtained;
                unmet = stillNeeded > 0
                        ? craftSelfSeedFromAlternative(in.key(), stillNeeded, r)
                        : 0L;
                if (!search && unmet > 0) addMissing(in.key(), unmet);
            } else {
                depth++;
                try {
                    unmet = obtain(in.key(), amt, !search);
                } finally {
                    depth--;
                }
            }
            inputUnmet = Sat.add(inputUnmet, unmet);
            if (search && (searchBudget.exhausted() || diagnostics.resolutionExhausted())) {
                return inputUnmet;
            }
            if (detectSiblingConflict
                    && decisionsBeforeInput > decisionsAtEntry
                    && hasSiblingStockConflict(usedAtEntry, missingBeforeInput)) {
                rememberReplayDecisions(decisionsAtEntry, decisionsBeforeInput);
            }
            if (in.returned() && in.uses() == CraftInput.INFINITE_USES) {
                // true catalyst/container: the seed is handed back, net consumption zero —
                // return what we actually got into the pool for reuse downstream. A finite-use
                // tool is degraded (consumed) by these firings, so nothing goes back.
                long returned = amt - unmet;
                if (returned > 0) {
                    if (in.reusableStockSource() != null) {
                        var source = in.reusableStockSource();
                        var route = new ReusableStockRouteKey<K>(source, in.key());
                        if (reusableAcquisition.sharedReturnable() > 0) {
                            bump(reusablePool,
                                    new ReusableStockKey<>(source.poolScope(), in.key()),
                                    reusableAcquisition.sharedReturnable());
                        }
                        if (reusableAcquisition.privateReturnable() > 0) {
                            bump(reusablePrivatePool, route,
                                    reusableAcquisition.privateReturnable());
                        }
                    } else {
                        bump(bpPool, in.key(), returned);
                    }
                }
            }
            if (search && (inputUnmet > 0 || missingTotal > entryMissing)) {
                return inputUnmet; // a shortfall appeared; bail early, the caller will roll back
            }
        }

        long produced = Sat.mul(times, r.outputAmount());
        long surplus = produced - d;
        if (surplus > 0) {
            bump(bpPool, x, surplus);
        }
        for (CraftOutput<K> out : r.byproducts()) {
            if (mayReuseByproduct(r, out.key())) {
                bump(bpPool, out.key(), Sat.mul(out.amount(), times));
            }
        }
        return inputUnmet;
    }

    private boolean hasSiblingStockConflict(
            Map<K, Long> usedAtEntry, Map<K, Long> missingBeforeInput) {
        for (Map.Entry<K, Long> entry : missing.entrySet()) {
            long beforeMissing = missingBeforeInput.getOrDefault(entry.getKey(), 0L);
            if (entry.getValue() <= beforeMissing) {
                continue;
            }
            long beforeUsed = usedAtEntry.getOrDefault(entry.getKey(), 0L);
            if (get(usedStock, entry.getKey()) > beforeUsed) {
                return true;
            }
        }
        return false;
    }

    private void rememberReplayDecisions(int fromInclusive, int toExclusive) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            RouteDecision<K> decision = routeDecisions.get(i);
            boolean alreadyRemembered = false;
            for (RouteDecision<K> remembered : replayRouteDecisions) {
                if (remembered.key().equals(decision.key())
                        && remembered.selected() == decision.selected()) {
                    alreadyRemembered = true;
                    break;
                }
            }
            if (!alreadyRemembered) {
                replayRouteDecisions.add(decision);
            }
        }
    }

    /**
     * Draws a reusable seed only through its logical loop pool. A pool first reuses its own returned
     * state, then borrows from the shared physical host inventory, and finally falls back to normal
     * network stock/crafting. Ordinary recipes can never see either private layer.
     */
    private ReusableSeedAcquisition obtainReusableSeed(
            CraftPattern<K> pattern, CraftInput<K> input, long amount, boolean search) {
        var source = input.reusableStockSource();
        if (source == null || amount <= 0) {
            return new ReusableSeedAcquisition(Math.max(0L, amount), 0L, 0L);
        }

        var route = new ReusableStockRouteKey<K>(source, input.key());
        long fromPrivate = Math.min(amount, get(reusablePrivatePool, route));
        if (fromPrivate > 0) {
            put(reusablePrivatePool, route, get(reusablePrivatePool, route) - fromPrivate);
        }

        long remaining = amount - fromPrivate;
        var poolKey = new ReusableStockKey<K>(source.poolScope(), input.key());
        long fromPool = Math.min(remaining, get(reusablePool, poolKey));
        if (fromPool > 0) {
            put(reusablePool, poolKey, get(reusablePool, poolKey) - fromPool);
        }

        remaining -= fromPool;
        long borrowedExact = 0L;
        long borrowedPrivate = 0L;
        if (remaining > 0) {
            var borrowed = borrowReusableStock(source, input.key(), remaining);
            if (borrowed.amount() > 0) {
                borrowedExact = borrowed.pinnedExactAmount();
                borrowedPrivate = borrowed.amount() - borrowedExact;
                remaining -= borrowed.amount();
            }
        }

        long externalExact = 0L;
        if (remaining > 0 && isSelfReturnedSeed(pattern, input)) {
            // A normal-network seed is reserved before ordinary demand can consume the same key.
            // Host stock still has priority; this path is only the fallback when the private host
            // could not provide the complete bootstrap state.
            long reserved = drawReservedSelfSeed(input.key(), remaining);
            remaining -= reserved;
            externalExact = reserved;
        }
        if (remaining > 0 && isFeedbackSeed(pattern, input)) {
            long bootstrapped = consumeFeedbackSeedBootstrap(pattern, input, remaining);
            remaining -= bootstrapped;
            externalExact = Sat.add(externalExact, bootstrapped);
        }
        if (remaining <= 0) {
            return new ReusableSeedAcquisition(
                    0L,
                    Sat.add(Sat.add(fromPool, borrowedExact), externalExact),
                    Sat.add(fromPrivate, borrowedPrivate));
        }
        long unmet;
        long externalRequest = remaining;
        if (isSelfReturnedSeed(pattern, input)) {
            unmet = craftSelfSeedFromAlternative(input.key(), remaining, pattern);
            if (unmet > 0) addMissing(input.key(), unmet);
        } else if (isFeedbackSeed(pattern, input)) {
            // Following this key recursively would reopen the ancestor request that this exact
            // two-node bootstrap closes. If neither host stock nor the held output token sufficed,
            // report the seed itself as missing instead of recursing until the depth guard.
            unmet = remaining;
            if (!search) addMissing(input.key(), unmet);
        } else {
            depth++;
            try {
                unmet = obtain(input.key(), remaining, !search);
            } finally {
                depth--;
            }
        }
        externalExact = Sat.add(externalExact, externalRequest - unmet);
        return new ReusableSeedAcquisition(
                unmet,
                Sat.add(Sat.add(fromPool, borrowedExact), externalExact),
                Sat.add(fromPrivate, borrowedPrivate));
    }

    /**
     * Adds as much demand as the global physical-variant matching problem can satisfy. Every probe
     * re-solves all still-private routes together, so an earlier fuzzy request may be reassigned when
     * a later, more constrained request arrives. An exact allocation is removed from that rematchable
     * set as soon as its returned catalyst is exposed as shared credit; future probes subtract the
     * pinned physical units first. This keeps route matching order-independent without invalidating a
     * shared credit that a later pattern may already have consumed.
     */
    private BorrowedReusableSeed borrowReusableStock(
            ReusableStockSource source, K plannedKey, long requested) {
        if (requested <= 0) return new BorrowedReusableSeed(0L, 0L);
        var route = new ReusableStockRouteKey<K>(source, plannedKey);
        long existing = get(reusableBorrowedDemand, route);

        long low = 0L;
        long high = requested;
        if (!isReusableDemandFeasible(route, addNonNegative(existing, high))) {
            while (low < high) {
                long distance = high - low;
                long middle = low + (distance >>> 1) + (distance & 1L);
                if (isReusableDemandFeasible(route, addNonNegative(existing, middle))) {
                    low = middle;
                } else {
                    high = middle - 1L;
                }
            }
        } else {
            low = high;
        }
        if (low <= 0) return new BorrowedReusableSeed(0L, 0L);

        var demands = new HashMap<>(reusableBorrowedDemand);
        demands.put(route, addNonNegative(existing, low));
        var allocation = ReusableStockMatcher.allocate(
                availableReusableStock(), demands,
                candidate -> graph.reusableStockCandidates(candidate.source(), candidate.plannedKey()));
        if (!allocation.feasible()) {
            throw new IllegalStateException("feasible reusable-stock probe produced no allocation");
        }

        var matchedUsage = reusableUsage(allocation);
        var exactUsage = new ReusableStockUsageKey<K>(
                source.storageScope(), source.poolScope(), source.routingScope(),
                plannedKey, plannedKey);
        long pinnedExact = Math.min(low, get(matchedUsage, exactUsage));
        if (pinnedExact > 0) {
            put(pinnedExactReusableStock, exactUsage,
                    Sat.add(get(pinnedExactReusableStock, exactUsage), pinnedExact));
        }
        put(reusableBorrowedDemand, route, demands.get(route) - pinnedExact);

        // Removing the just-pinned exact edge and the same amount of route demand preserves the
        // feasible residual allocation. Re-solving keeps every still-private fuzzy assignment free
        // to move while the exposed exact shared credit is permanently excluded from host supply.
        allocation = ReusableStockMatcher.allocate(
                availableReusableStock(), reusableBorrowedDemand,
                candidate -> graph.reusableStockCandidates(candidate.source(), candidate.plannedKey()));
        if (!allocation.feasible()) {
            throw new IllegalStateException("pinning an exact reusable allocation broke residual matching");
        }
        matchedUsage = reusableUsage(allocation);
        var desiredUsage = new HashMap<ReusableStockUsageKey<K>, Long>(pinnedExactReusableStock);
        for (var entry : matchedUsage.entrySet()) {
            desiredUsage.merge(entry.getKey(), entry.getValue(), CraftPlannerV2::addNonNegative);
        }
        replaceTracked(usedReusableStock, desiredUsage);
        return new BorrowedReusableSeed(low, pinnedExact);
    }

    private Map<ReusableStockUsageKey<K>, Long> reusableUsage(
            ReusableStockMatcher.Result<K> allocation) {
        var desiredUsage = new HashMap<ReusableStockUsageKey<K>, Long>();
        for (var entry : allocation.allocation().entrySet()) {
            var allocationKey = entry.getKey();
            var allocationRoute = allocationKey.route();
            var allocationSource = allocationRoute.source();
            var usage = new ReusableStockUsageKey<K>(
                    allocationSource.storageScope(),
                    allocationSource.poolScope(),
                    allocationSource.routingScope(),
                    allocationRoute.plannedKey(),
                    allocationKey.actualKey());
            desiredUsage.merge(usage, entry.getValue(), CraftPlannerV2::addNonNegative);
        }
        return desiredUsage;
    }

    /** Physical host snapshot with exact shared credits removed from future max-flow probes. */
    private Map<ReusableStockKey<K>, Long> availableReusableStock() {
        var available = new HashMap<ReusableStockKey<K>, Long>(graph.reusableStock());
        for (var pinned : pinnedExactReusableStock.entrySet()) {
            var physical = new ReusableStockKey<K>(
                    pinned.getKey().storageScope(), pinned.getKey().actualKey());
            long left = get(available, physical) - pinned.getValue();
            if (left > 0) available.put(physical, left);
            else available.remove(physical);
        }
        return available;
    }

    private boolean isReusableDemandFeasible(ReusableStockRouteKey<K> route, long routeDemand) {
        var demands = new HashMap<>(reusableBorrowedDemand);
        if (routeDemand > 0) demands.put(route, routeDemand);
        return ReusableStockMatcher.allocate(
                availableReusableStock(), demands,
                candidate -> graph.reusableStockCandidates(candidate.source(), candidate.plannedKey()))
                .feasible();
    }

    private <T> void replaceTracked(Map<T, Long> target, Map<T, Long> replacement) {
        var keys = new HashSet<T>();
        keys.addAll(target.keySet());
        keys.addAll(replacement.keySet());
        for (var key : keys) {
            long next = get(replacement, key);
            if (get(target, key) != next) {
                put(target, key, next);
            }
        }
    }

    private static long addNonNegative(long left, long right) {
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private record ReusableSeedAcquisition(
            long unmet, long sharedReturnable, long privateReturnable) {
    }

    private record BorrowedReusableSeed(long amount, long pinnedExactAmount) {
    }

    private static <K> boolean isSelfReturnedSeed(CraftPattern<K> pattern, CraftInput<K> input) {
        return input.returned()
                && input.uses() == CraftInput.INFINITE_USES
                && pattern.output().equals(input.key());
    }

    private boolean isFeedbackSeed(CraftPattern<K> pattern, CraftInput<K> input) {
        return feedbackSeedBootstrap(pattern, input) != null;
    }

    private FeedbackSeedBootstrap<K> feedbackSeedBootstrap(
            CraftPattern<K> pattern, CraftInput<K> input) {
        return feedbackSeedBootstrap(pattern, input, capacity);
    }

    private FeedbackSeedBootstrap<K> feedbackSeedBootstrap(
            CraftPattern<K> pattern,
            CraftInput<K> input,
            Map<K, Long> materialCapacity) {
        FeedbackSeedBootstrap<K> best = null;
        long bestCapacity = -1L;
        long bestStateCost = Long.MAX_VALUE;
        for (FeedbackSeedBootstrap<K> bootstrap
                : feedbackSeedBootstraps.getOrDefault(pattern, List.of())) {
            if (bootstrap.seedInput() != input) continue;
            long candidateCapacity =
                    feedbackBootstrapSeedCapacity(bootstrap, materialCapacity);
            long candidateStateCost = bootstrap.outputUnitsFor(input.amount());
            if (best == null
                    || candidateCapacity > bestCapacity
                    || (candidateCapacity == bestCapacity
                    && candidateStateCost < bestStateCost)) {
                best = bootstrap;
                bestCapacity = candidateCapacity;
                bestStateCost = candidateStateCost;
            }
        }
        return best;
    }

    /**
     * Seed output supported by a converter's non-loop inputs. The loop-state input is reserved
     * separately, so including it here would make the feedback edge prove its own capacity.
     */
    private long feedbackBootstrapSeedCapacity(
            FeedbackSeedBootstrap<K> bootstrap, Map<K, Long> materialCapacity) {
        CraftInput<K> seed = bootstrap.seedInput();
        long availableLoopState = graph.stock(bootstrap.loopPattern().output());
        if (seed.reusableStockSource() != null) {
            availableLoopState = Sat.add(
                    availableLoopState,
                    graph.reusableStock(
                            seed.reusableStockSource().storageScope(),
                            bootstrap.loopPattern().output()));
        }
        long firings = bootstrap.converterInput().firingsFrom(availableLoopState);
        for (CraftInput<K> auxiliary : bootstrap.converter().inputs()) {
            if (auxiliary == bootstrap.converterInput()) continue;
            long available = materialCapacity == null
                    ? graph.stock(auxiliary.key())
                    : materialCapacity.getOrDefault(
                            auxiliary.key(), graph.stock(auxiliary.key()));
            firings = Math.min(firings, auxiliary.firingsFrom(available));
            if (firings == 0) return 0L;
        }
        return Sat.mul(firings, bootstrap.converter().outputAmount());
    }

    private boolean isFeedbackConverterInput(CraftPattern<K> pattern, CraftInput<K> input) {
        for (FeedbackSeedBootstrap<K> bootstrap
                : feedbackSeedConverters.getOrDefault(pattern, List.of())) {
            if (bootstrap.converterInput() == input) return true;
        }
        return false;
    }

    private static <K> boolean hasSelfReturnedSeed(CraftPattern<K> pattern) {
        for (CraftInput<K> input : pattern.inputs()) {
            if (isSelfReturnedSeed(pattern, input)) return true;
        }
        return false;
    }

    /** Crafts only the catalyst seed via a non-self alternative; the gain macro itself is excluded. */
    private long craftSelfSeedFromAlternative(K key, long amount, CraftPattern<K> excluded) {
        List<CraftPattern<K>> alternatives = new ArrayList<>();
        for (CraftPattern<K> pattern : patternsByOutput.getOrDefault(key, List.of())) {
            if (pattern != excluded && !hasSelfReturnedSeed(pattern)) alternatives.add(pattern);
        }
        alternatives.sort((a, b) -> Long.compare(capacityScore(b), capacityScore(a)));
        alternatives = new ArrayList<>(promotePreferredRoute(key, alternatives));
        boolean competing = alternatives.size() > 1;
        for (CraftPattern<K> alternative : alternatives) {
            boolean admitted = competing
                    ? searchBudget.tryConsume()
                    : diagnostics.tryConsumeResolutionWork();
            if (!admitted) {
                // The outer committed route will retain this deterministic dependency tree; a
                // speculative parent will roll the same writes back at its own mark.
                if (!fallbackBudget.tryConsume()) {
                    return amount;
                }
                fire(key, alternative, amount, false);
                return 0L;
            }
            int mark = trail.size();
            long beforeMissing = missingTotal;
            recordRouteDecision(key, alternative, alternatives);
            long unmet = fire(key, alternative, amount, true);
            if (searchBudget.exhausted() || diagnostics.resolutionExhausted()) {
                rollback(mark);
                return amount;
            }
            if (unmet == 0 && missingTotal == beforeMissing) return 0L;
            rollback(mark);
        }
        return amount;
    }

    /** Holds a self-output catalyst aside before ordinary demand can consume it as finished output. */
    private void reserveSelfSeed(K key) {
        long required = 0L;
        for (CraftPattern<K> pattern : patternsByOutput.getOrDefault(key, List.of())) {
            for (CraftInput<K> input : pattern.inputs()) {
                if (isSelfReturnedSeed(pattern, input)) {
                    if (input.reusableStockSource() != null
                            && input.reusableStockSource().storageScope() instanceof OrdinaryStockAlias) {
                        continue;
                    }
                    long hostAvailable = input.reusableStockSource() == null
                            ? 0L
                            : graph.reusableStock(input.reusableStockSource(), input.key());
                    required = Math.max(required, Math.max(0L, input.amount() - hostAvailable));
                }
            }
        }
        long alreadyReserved = get(reservedSelfSeeds, key);
        long additional = Math.max(0L, required - alreadyReserved);
        if (additional <= 0) return;
        long available = get(stockLeft, key);
        long held = Math.min(additional, available);
        if (held > 0) {
            put(stockLeft, key, available - held);
            put(reservedSelfSeeds, key, Sat.add(alreadyReserved, held));
        }
    }

    private long drawReservedSelfSeed(K key, long amount) {
        long available = get(reservedSelfSeeds, key);
        long drawn = Math.min(amount, available);
        if (drawn > 0) {
            put(reservedSelfSeeds, key, available - drawn);
            put(usedStock, key, Sat.add(get(usedStock, key), drawn));
        }
        return drawn;
    }

    /**
     * Holds the physical output-state token only when ordinary demand would exhaust that state and the
     * feedback loop is the currently preferred way to cover the shortfall. The held item is not charged
     * to {@code usedStock} until the explicit converter actually consumes it.
     */
    private void reserveFeedbackSeedOutput(K key, long demand) {
        long immediatelyAvailable = Sat.add(get(bpPool, key), get(stockLeft, key));
        if (demand <= immediatelyAvailable) return;

        List<CraftPattern<K>> patterns = patternsByOutput.getOrDefault(key, List.of());
        if (patterns.isEmpty()) return;
        CraftPattern<K> preferred = capacityOrder(key).get(0);
        List<FeedbackSeedBootstrap<K>> bootstraps = feedbackSeedBootstraps.get(preferred);
        if (bootstraps == null || bootstraps.isEmpty()) return;

        Map<FeedbackSeedBootstrap<K>, Long> additionalBySeed = new HashMap<>();
        long totalAdditional = 0L;
        for (CraftInput<K> seed : preferred.inputs()) {
            FeedbackSeedBootstrap<K> bootstrap =
                    feedbackSeedBootstrap(preferred, seed);
            if (bootstrap == null) continue;
            long hostAvailable = graph.reusableStock(seed.reusableStockSource(), seed.key());
            long seedShortfall = Math.max(0L, seed.amount() - hostAvailable);
            long required = bootstrap.outputUnitsFor(seedShortfall);
            long alreadyReserved = get(reservedFeedbackSeedOutputs, bootstrap);
            long additional = Math.max(0L, required - alreadyReserved);
            if (additional > 0) {
                additionalBySeed.put(bootstrap, additional);
                totalAdditional = Sat.add(totalAdditional, additional);
            }
        }
        if (totalAdditional <= 0) return;
        int mark = trail.size();
        long availableStock = get(stockLeft, key);
        for (Map.Entry<FeedbackSeedBootstrap<K>, Long> entry : additionalBySeed.entrySet()) {
            FeedbackSeedBootstrap<K> bootstrap = entry.getKey();
            long additional = entry.getValue();
            long ordinary = Math.min(additional, availableStock);
            availableStock -= ordinary;
            long hostNeeded = additional - ordinary;
            long hostBorrowed = 0L;
            if (hostNeeded > 0) {
                var borrowed = borrowReusableStock(
                        bootstrap.bootstrapSource(), key, hostNeeded);
                hostBorrowed = borrowed.amount();
                if (hostBorrowed < hostNeeded) {
                    rollback(mark);
                    return;
                }
            }
            put(reservedFeedbackSeedOutputs, bootstrap,
                    Sat.add(get(reservedFeedbackSeedOutputs, bootstrap), additional));
            if (hostBorrowed > 0) {
                put(reservedFeedbackSeedHostOutputs, bootstrap,
                        Sat.add(get(reservedFeedbackSeedHostOutputs, bootstrap), hostBorrowed));
            }
        }
        put(stockLeft, key, availableStock);
    }

    /**
     * Executes the proven ordinary converter against the held output-state stock and exposes its
     * product as the loop's reusable seed. The converter firing is recorded normally, so AE2 schedules
     * the same real pattern once for bootstrap plus however many times final output requires.
     */
    private long consumeFeedbackSeedBootstrap(
            CraftPattern<K> pattern, CraftInput<K> input, long requested) {
        FeedbackSeedBootstrap<K> bootstrap = feedbackSeedBootstrap(pattern, input);
        if (bootstrap == null || requested <= 0) return 0L;

        long firings = Sat.ceilDiv(requested, bootstrap.converter().outputAmount());
        long requiredOutput = bootstrap.converterInput().unitsFor(firings);
        long reserved = get(reservedFeedbackSeedOutputs, bootstrap);
        if (requiredOutput > reserved) return 0L;

        // Auxiliary converter inputs (e.g. the reaction chamber's water) are ordinary materials
        // from outside the cycle; obtain them for the bootstrap firings before committing, and
        // abandon the bootstrap cleanly when any of them cannot be covered.
        int mark = trail.size();
        long beforeMissing = missingTotal;
        for (CraftInput<K> auxiliary : bootstrap.converter().inputs()) {
            if (auxiliary == bootstrap.converterInput()) continue;
            long unmet;
            depth++;
            try {
                unmet = obtain(auxiliary.key(), auxiliary.unitsFor(firings), false);
            } finally {
                depth--;
            }
            if (unmet > 0 || missingTotal > beforeMissing) {
                rollback(mark);
                return 0L;
            }
        }

        put(reservedFeedbackSeedOutputs, bootstrap, reserved - requiredOutput);
        long reservedFromHost = get(reservedFeedbackSeedHostOutputs, bootstrap);
        long fromHost = Math.min(requiredOutput, reservedFromHost);
        if (fromHost > 0) {
            put(reservedFeedbackSeedHostOutputs, bootstrap, reservedFromHost - fromHost);
        }
        long fromOrdinaryStock = requiredOutput - fromHost;
        if (fromOrdinaryStock > 0) {
            bump(usedStock, pattern.output(), fromOrdinaryStock);
        }
        bump(grossDemand, pattern.output(), requiredOutput);
        bumpFiring(bootstrap.converter(), firings);

        long produced = Sat.mul(firings, bootstrap.converter().outputAmount());
        long supplied = Math.min(requested, produced);
        long surplus = produced - supplied;
        if (surplus > 0) {
            bump(bpPool, input.key(), surplus);
        }
        return supplied;
    }

    /** Draw up to {@code d} of {@code x}: byproduct pool first, then inventory (counted as used stock). */
    private long drawPools(K x, long d) {
        long got = 0;
        long bp = Math.min(d, get(bpPool, x));
        if (bp > 0) {
            put(bpPool, x, get(bpPool, x) - bp);
            got += bp;
            d -= bp;
        }
        long st = Math.min(d, get(stockLeft, x));
        if (st > 0) {
            put(stockLeft, x, get(stockLeft, x) - st);
            put(usedStock, x, Sat.add(get(usedStock, x), st));
            got += st;
        }
        return got;
    }

    // ---- trail-logged mutation helpers -----------------------------------------------------------

    private static <T> long get(Map<T, Long> m, T k) {
        Long v = m.get(k);
        return v == null ? 0L : v;
    }

    private <T> void put(Map<T, Long> m, T k, long newVal) {
        Long old = m.get(k);
        long oldAvailabilityState = availabilityState;
        boolean tracksAvailability = tracksAvailability(m);
        trail.push(() -> {
            if (old == null) {
                m.remove(k);
            } else {
                m.put(k, old);
            }
            if (tracksAvailability) {
                availabilityState = oldAvailabilityState;
            }
        });
        if (tracksAvailability) {
            availabilityState = ++nextAvailabilityState;
        }
        if (newVal == 0) {
            m.remove(k);
        } else {
            m.put(k, newVal);
        }
    }

    private boolean tracksAvailability(Map<?, Long> map) {
        return map == bpPool
                || map == stockLeft
                || map == reservedSelfSeeds
                || map == reservedFeedbackSeedOutputs
                || map == reservedFeedbackSeedHostOutputs
                || map == reusableBorrowedDemand
                || map == reusablePrivatePool
                || map == reusablePool
                || map == pinnedExactReusableStock;
    }

    private <T> void bump(Map<T, Long> m, T k, long delta) {
        if (delta == 0) {
            return;
        }
        put(m, k, Sat.add(get(m, k), delta));
    }

    private void addMissing(K k, long amt) {
        if (amt <= 0) {
            return;
        }
        put(missing, k, Sat.add(get(missing, k), amt));
        long old = missingTotal;
        trail.push(() -> missingTotal = old);
        missingTotal = Sat.add(missingTotal, amt);
    }

    private void bumpFiring(CraftPattern<K> r, long delta) {
        Long old = firings.get(r);
        trail.push(() -> {
            if (old == null) {
                firings.remove(r);
            } else {
                firings.put(r, old);
            }
        });
        firings.put(r, Sat.add(old == null ? 0L : old, delta));
    }

    private void recordRouteDecision(
            K key, CraftPattern<K> selected, List<CraftPattern<K>> candidates) {
        if (candidates.size() < 2) {
            return;
        }
        int oldSize = routeDecisions.size();
        trail.push(() -> {
            while (routeDecisions.size() > oldSize) {
                routeDecisions.remove(routeDecisions.size() - 1);
            }
        });
        routeDecisions.add(new RouteDecision<>(key, selected, List.copyOf(candidates)));
    }

    /**
     * Generates chronological-backtracking deviations from the committed path. The latest decision
     * is offered first; candidates after the selected heuristic route precede candidates that already
     * failed before it. One route preference applies to every visit of that key during a replay, which
     * is intentionally coarser and much cheaper than cloning every mutable inventory state.
     */
    private RouteAlternatives<K> routeAlternatives(int limit) {
        List<RouteAlternative<K>> alternatives = new ArrayList<>();
        Set<K> emittedKeys = new HashSet<>();
        boolean truncated = false;
        for (int decisionIndex = replayRouteDecisions.size() - 1;
                decisionIndex >= 0;
                decisionIndex--) {
            RouteDecision<K> decision = replayRouteDecisions.get(decisionIndex);
            if (!emittedKeys.add(decision.key())) {
                continue;
            }
            List<CraftPattern<K>> candidates = decision.candidates();
            int selected = candidates.indexOf(decision.selected());
            if (selected < 0) {
                continue;
            }
            List<CraftPattern<K>> defaultOrder = capacityOrderByOutput.getOrDefault(
                    decision.key(), patternsByOutput.getOrDefault(decision.key(), List.of()));
            CraftPattern<K> defaultPattern =
                    defaultOrder.isEmpty() ? decision.selected() : defaultOrder.get(0);
            for (int i = selected + 1; i < candidates.size(); i++) {
                if (alternatives.size() >= limit) {
                    truncated = true;
                    break;
                }
                alternatives.add(new RouteAlternative<>(
                        decision.key(), candidates.get(i), defaultPattern));
            }
            if (truncated) {
                break;
            }
            for (int i = 0; i < selected; i++) {
                if (alternatives.size() >= limit) {
                    truncated = true;
                    break;
                }
                alternatives.add(new RouteAlternative<>(
                        decision.key(), candidates.get(i), defaultPattern));
            }
            if (truncated) {
                break;
            }
        }
        return new RouteAlternatives<>(List.copyOf(alternatives), truncated);
    }

    private void rollback(int mark) {
        while (trail.size() > mark) {
            trail.pop().run();
        }
    }

    private record MaterialLeaf(Object key) {
    }

    private record MaterialTerm(int footprint, long amount) {
    }

    private record MaterialRecipe(long outputAmount, List<MaterialTerm> inputs) {
    }

    private record SearchFailure<K>(K key, long amount, long availabilityState, int depth) {
    }

    private record ConsumableProofState<K>(K key, long availabilityState) {
    }

    private record RouteDecision<K>(
            K key, CraftPattern<K> selected, List<CraftPattern<K>> candidates) {
    }

    private record RouteAlternative<K>(
            K key, CraftPattern<K> pattern, CraftPattern<K> defaultPattern) {
    }

    private record RouteAlternatives<K>(
            List<RouteAlternative<K>> alternatives, boolean truncated) {
    }

    private record EnqueueResult(long sequence, boolean truncated) {
    }

    private record VariantKey<K>(
            List<K> priorityRoots, Map<K, CraftPattern<K>> routePreferences) {
    }

    private record PlanVariant<K>(
            List<K> priorityRoots,
            Map<K, CraftPattern<K>> routePreferences,
            int discrepancies,
            long sequence) {

        private PlanVariant {
            priorityRoots = List.copyOf(priorityRoots);
            routePreferences = Map.copyOf(routePreferences);
        }

        private VariantKey<K> key() {
            return new VariantKey<>(priorityRoots, routePreferences);
        }
    }

    /**
     * Immutable output of graph discovery/cycle cutting for one priority-root orientation.
     */
        private record PreparedGraph<K>(CraftGraph<K> graph, List<K> order, Set<K> items, Set<K> cutOutputs,
                                        Map<K, List<CraftPattern<K>>> patternsByOutput,
                                        Map<CraftPattern<K>, Set<K>> suppressedPositiveFeedbackOutputs,
                                        Map<CraftPattern<K>, Map<K, Long>> linearContainerBootstrapReserves,
                                        Map<CraftPattern<K>, List<FeedbackSeedBootstrap<K>>> feedbackSeedBootstraps,
                                        Map<CraftPattern<K>, List<FeedbackSeedBootstrap<K>>> feedbackSeedConverters,
                                        List<ConservativeFeedbackAnalysis.Component<K>> conservativeFeedbackComponents,
                                        boolean seedOrdered, Set<K> seedOrderedDependencyCone, Map<K, Long> capacity,
                                        Map<CraftPattern<K>, Long> capacityScoreByPattern,
                                        Map<K, List<CraftPattern<K>>> capacityOrderByOutput,
                                        Map<CraftPattern<K>, Map<K, Long>> directRawConsumablesByPattern,
                                        Map<CraftPattern<K>, Integer> materialFootprintByPattern, int patternCount,
                                        int inputCount, int contendedOutputCount) {
            private PreparedGraph(
                    CraftGraph<K> graph,
                    List<K> order,
                    Set<K> items,
                    Set<K> cutOutputs,
                    Map<K, List<CraftPattern<K>>> patternsByOutput,
                    Map<CraftPattern<K>, Set<K>> suppressedPositiveFeedbackOutputs,
                    Map<CraftPattern<K>, Map<K, Long>> linearContainerBootstrapReserves,
                    Map<CraftPattern<K>, List<FeedbackSeedBootstrap<K>>> feedbackSeedBootstraps,
                    Map<CraftPattern<K>, List<FeedbackSeedBootstrap<K>>> feedbackSeedConverters,
                    List<ConservativeFeedbackAnalysis.Component<K>> conservativeFeedbackComponents,
                    boolean seedOrdered,
                    Set<K> seedOrderedDependencyCone,
                    Map<K, Long> capacity,
                    Map<CraftPattern<K>, Long> capacityScoreByPattern,
                    Map<K, List<CraftPattern<K>>> capacityOrderByOutput,
                    Map<CraftPattern<K>, Map<K, Long>> directRawConsumablesByPattern,
                    Map<CraftPattern<K>, Integer> materialFootprintByPattern,
                    int patternCount,
                    int inputCount,
                    int contendedOutputCount) {
                this.graph = graph;
                this.order = order;
                this.items = items;
                this.cutOutputs = cutOutputs;
                this.patternsByOutput = patternsByOutput;
                this.suppressedPositiveFeedbackOutputs = suppressedPositiveFeedbackOutputs;
                this.linearContainerBootstrapReserves = linearContainerBootstrapReserves;
                this.feedbackSeedBootstraps = feedbackSeedBootstraps;
                this.feedbackSeedConverters = feedbackSeedConverters;
                this.conservativeFeedbackComponents = List.copyOf(conservativeFeedbackComponents);
                this.seedOrdered = seedOrdered;
                this.seedOrderedDependencyCone = seedOrderedDependencyCone;
                this.capacity = capacity;
                this.capacityScoreByPattern = capacityScoreByPattern;
                this.capacityOrderByOutput = capacityOrderByOutput;
                this.directRawConsumablesByPattern = directRawConsumablesByPattern;
                this.materialFootprintByPattern = materialFootprintByPattern;
                this.patternCount = patternCount;
                this.inputCount = inputCount;
                this.contendedOutputCount = contendedOutputCount;
            }
        }

    /** Mutable counters shared by all route replays; converted to a record without extra graph work. */
    private static final class DiagnosticsCollector {
        private final int reachableWorkEstimate;
        private final int configuredSearchBudget;
        private final int configuredResolutionBudget;
        private final int configuredFallbackBudget;
        private int reachableItems;
        private int reachablePatterns;
        private int inputEdges;
        private int contendedOutputs;
        private int cycleCuts;
        private boolean seedOrdered;
        private int planRuns;
        private int compiledOrientations;
        private int reusedCompilations;
        private int hotNodeVisits;
        private int dynamicCapacityEvaluations;
        private int equivalentRoutesPruned;
        private int failureMemoHits;
        private int frontierPeak;
        private boolean searchCutoff;
        private int consumedResolutionBudget;
        private boolean resolutionCutoff;
        private int consumedFallbackBudget;
        private boolean fallbackCutoff;
        private long graphCompileNanos;
        private long linearPassNanos;
        private long searchNanos;

        private DiagnosticsCollector(int reachableWorkEstimate, int configuredSearchBudget) {
            this.reachableWorkEstimate = reachableWorkEstimate;
            this.configuredSearchBudget = Math.max(1, configuredSearchBudget);
            this.configuredResolutionBudget = fallbackWorkBudget(reachableWorkEstimate);
            this.configuredFallbackBudget = fallbackWorkBudget(reachableWorkEstimate);
        }

        private int fallbackBudgetLimit() {
            return configuredFallbackBudget;
        }

        private void recordCompilation(PreparedGraph<?> prepared, long nanos) {
            compiledOrientations = increment(compiledOrientations);
            reachableItems = Math.max(reachableItems, prepared.items.size());
            reachablePatterns = Math.max(reachablePatterns, prepared.patternCount);
            inputEdges = Math.max(inputEdges, prepared.inputCount);
            contendedOutputs = Math.max(contendedOutputs, prepared.contendedOutputCount);
            cycleCuts = Math.max(cycleCuts, prepared.cutOutputs.size());
            seedOrdered |= prepared.seedOrdered;
            graphCompileNanos = addNanos(graphCompileNanos, nanos);
        }

        private void recordCompilationReuse() {
            reusedCompilations = increment(reusedCompilations);
        }

        private void recordPlanRun() {
            planRuns = increment(planRuns);
        }

        private void recordHotNodeVisit() {
            hotNodeVisits = increment(hotNodeVisits);
        }

        private void recordDynamicCapacityEvaluation() {
            dynamicCapacityEvaluations = increment(dynamicCapacityEvaluations);
        }

        private void recordEquivalentRoutesPruned(int count) {
            if (count > 0) equivalentRoutesPruned = add(equivalentRoutesPruned, count);
        }

        private void recordFailureMemoHit() {
            failureMemoHits = increment(failureMemoHits);
        }

        private void recordFrontierSize(int size) {
            frontierPeak = Math.max(frontierPeak, size);
        }

        private void recordSearchCutoff() {
            searchCutoff = true;
        }

        private boolean tryConsumeResolutionWork() {
            if (consumedResolutionBudget >= configuredResolutionBudget) {
                resolutionCutoff = true;
                return false;
            }
            consumedResolutionBudget = increment(consumedResolutionBudget);
            return true;
        }

        private boolean resolutionExhausted() {
            return resolutionCutoff;
        }

        private void recordFallbackWork() {
            consumedFallbackBudget = increment(consumedFallbackBudget);
        }

        private void recordFallbackCutoff() {
            fallbackCutoff = true;
        }

        private void addLinearPassNanos(long nanos) {
            linearPassNanos = addNanos(linearPassNanos, nanos);
        }

        private void addSearchNanos(long nanos) {
            searchNanos = addNanos(searchNanos, nanos);
        }

        private PlanningDiagnostics finish(long started, SearchBudget budget) {
            int consumed = budget == null ? 0 : budget.consumed();
            return new PlanningDiagnostics(
                    reachableWorkEstimate,
                    reachableItems,
                    reachablePatterns,
                    inputEdges,
                    contendedOutputs,
                    cycleCuts,
                    seedOrdered,
                    configuredSearchBudget,
                    consumed,
                    configuredResolutionBudget,
                    consumedResolutionBudget,
                    configuredFallbackBudget,
                    consumedFallbackBudget,
                    planRuns,
                    compiledOrientations,
                    reusedCompilations,
                    hotNodeVisits,
                    dynamicCapacityEvaluations,
                    equivalentRoutesPruned,
                    failureMemoHits,
                    frontierPeak,
                    searchCutoff,
                    resolutionCutoff,
                    fallbackCutoff,
                    graphCompileNanos,
                    linearPassNanos,
                    searchNanos,
                    Math.max(0L, System.nanoTime() - started));
        }

        private static int increment(int value) {
            return value == Integer.MAX_VALUE ? value : value + 1;
        }

        private static int add(int left, int right) {
            return left > Integer.MAX_VALUE - right ? Integer.MAX_VALUE : left + right;
        }

        private static long addNanos(long left, long right) {
            long nonNegativeRight = Math.max(0L, right);
            return left > Long.MAX_VALUE - nonNegativeRight
                    ? Long.MAX_VALUE
                    : left + nonNegativeRight;
        }
    }

    /**
     * Monotonic plan-wide alternative-search guard. Once a request is denied, callers stop exploring
     * alternatives and finish the current plan through a deterministic capacity-first route.
     */
    private static final class SearchBudget {
        private final int initial;
        private int remaining;
        private final DiagnosticsCollector diagnostics;
        private boolean exhausted;

        private SearchBudget(int work, DiagnosticsCollector diagnostics) {
            this.initial = Math.max(1, work);
            this.remaining = initial;
            this.diagnostics = diagnostics;
        }

        private boolean tryConsume() {
            return tryConsume(1);
        }

        private boolean tryConsume(int work) {
            int requested = Math.max(1, work);
            if (remaining < requested) {
                if (!exhausted) {
                    exhausted = true;
                    diagnostics.recordSearchCutoff();
                }
                return false;
            }
            remaining -= requested;
            return true;
        }

        private boolean exhausted() {
            return exhausted;
        }

        private int remaining() {
            return remaining;
        }

        private int consumed() {
            return initial - remaining;
        }
    }

    /** Bounds the deterministic tail after alternative search has stopped. */
    private static final class FallbackBudget {
        private int remaining;
        private final DiagnosticsCollector diagnostics;

        private FallbackBudget(int work, DiagnosticsCollector diagnostics) {
            this.remaining = Math.max(1, work);
            this.diagnostics = diagnostics;
        }

        private boolean tryConsume() {
            if (remaining <= 0) {
                diagnostics.recordFallbackCutoff();
                return false;
            }
            remaining--;
            diagnostics.recordFallbackWork();
            return true;
        }
    }

    private record FeedbackSeedBootstrap<K>(
            CraftPattern<K> loopPattern,
            CraftInput<K> seedInput,
            CraftPattern<K> converter,
            CraftInput<K> converterInput) {

        long outputUnitsFor(long seedAmount) {
            long firings = Sat.ceilDiv(seedAmount, converter.outputAmount());
            return converterInput.unitsFor(firings);
        }

        ReusableStockSource bootstrapSource() {
            ReusableStockSource owner = seedInput.reusableStockSource();
            return new ReusableStockSource(
                    owner.storageScope(),
                    owner.poolScope(),
                    new ReusableBootstrapRoute<>(owner.routingScope(), seedInput.key()));
        }
    }

    private static final class FootprintInterner {
        private final Map<Object, Integer> ids = new HashMap<>();

        private int intern(Object shape) {
            Integer existing = ids.get(shape);
            if (existing != null) {
                return existing;
            }
            int id = ids.size() + 1;
            ids.put(shape, id);
            return id;
        }
    }
}
