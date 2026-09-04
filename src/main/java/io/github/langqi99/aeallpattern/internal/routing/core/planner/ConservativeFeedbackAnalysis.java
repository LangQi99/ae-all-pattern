package io.github.langqi99.aeallpattern.internal.routing.core.planner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds ordinary, non-growing feedback state machines before any aggregate planning pass runs.
 *
 * <p>The analysis deliberately proves only a narrow marked-graph shape. Every state in a component
 * has exactly one internal producer and one internal consumer, every participating pattern moves one
 * internal state to one other internal state, and firing every transition once never increases any
 * state. This covers balanced raw catalysts such as
 * {@code A -> 2B; 2B + C -> E + D; D -> A} and lossy feedback such as
 * {@code 3A -> 2B; 2B -> D + 2A}, without admitting gain loops such as {@code A -> 2A}.
 * Anything more complicated stays on the planner's existing conservative paths.
 */
final class ConservativeFeedbackAnalysis<K> {

    /** State that must remain after ordinary net loss has already been charged. */
    record SeedOption<K>(Map<K, Long> amounts) {
        SeedOption {
            amounts = Map.copyOf(amounts);
        }
    }

    record Component<K>(Set<K> states, List<CraftPattern<K>> patterns,
                        Map<K, Long> lossPerRound,
                        boolean hasExternalProducer,
                        List<SeedOption<K>> seedOptions) {
        Component {
            states = Set.copyOf(states);
            patterns = List.copyOf(patterns);
            lossPerRound = Map.copyOf(lossPerRound);
            seedOptions = List.copyOf(seedOptions);
        }

        boolean lossy() {
            return !lossPerRound.isEmpty();
        }
    }

    private record Transition<K>(CraftPattern<K> pattern, K input, long inputAmount,
                                 K output, long outputAmount) {
    }

    private ConservativeFeedbackAnalysis() {
    }

    static <K> List<Component<K>> analyze(
            List<K> itemOrder,
            Map<K, List<CraftPattern<K>>> patternsByOutput) {
        List<CraftPattern<K>> patterns = stablePatterns(itemOrder, patternsByOutput);
        Map<K, LinkedHashSet<K>> mutableAdjacency = new LinkedHashMap<>();
        Set<K> nodes = new LinkedHashSet<>();

        for (CraftPattern<K> pattern : patterns) {
            PlanningCancellation.check();
            List<K> outputs = outputKeys(pattern);
            nodes.addAll(outputs);
            for (CraftInput<K> input : pattern.inputs()) {
                nodes.add(input.key());
                if (!isOrdinary(input)) continue;
                LinkedHashSet<K> adjacent = mutableAdjacency.computeIfAbsent(
                        input.key(), ignored -> new LinkedHashSet<>());
                adjacent.addAll(outputs);
            }
        }
        for (K node : nodes) {
            mutableAdjacency.computeIfAbsent(node, ignored -> new LinkedHashSet<>());
        }

        Map<K, List<K>> adjacency = new LinkedHashMap<>();
        mutableAdjacency.forEach((key, value) -> adjacency.put(key, List.copyOf(value)));
        List<Set<K>> stronglyConnected = stronglyConnectedComponents(nodes, adjacency);
        Map<K, Integer> itemRank = new HashMap<>();
        for (int i = 0; i < itemOrder.size(); i++) itemRank.putIfAbsent(itemOrder.get(i), i);

        List<Component<K>> result = new ArrayList<>();
        for (Set<K> states : stronglyConnected) {
            PlanningCancellation.check();
            boolean selfLoop = states.size() == 1
                    && adjacency.getOrDefault(states.iterator().next(), List.of())
                            .contains(states.iterator().next());
            if (states.size() <= 1 && !selfLoop) continue;
            Component<K> component = classify(states, patterns, itemRank);
            if (component != null) result.add(component);
        }
        return List.copyOf(result);
    }

    private static <K> List<CraftPattern<K>> stablePatterns(
            List<K> itemOrder,
            Map<K, List<CraftPattern<K>>> patternsByOutput) {
        Set<CraftPattern<K>> seen = new LinkedHashSet<>();
        List<CraftPattern<K>> result = new ArrayList<>();
        for (K item : itemOrder) {
            for (CraftPattern<K> pattern : patternsByOutput.getOrDefault(item, List.of())) {
                if (seen.add(pattern)) result.add(pattern);
            }
        }
        return result;
    }

    private static <K> List<K> outputKeys(CraftPattern<K> pattern) {
        Set<K> outputs = new LinkedHashSet<>();
        outputs.add(pattern.output());
        for (CraftOutput<K> byproduct : pattern.byproducts()) outputs.add(byproduct.key());
        return List.copyOf(outputs);
    }

    private static <K> boolean isOrdinary(CraftInput<K> input) {
        return !input.returned()
                && input.remainder() == null
                && input.reusableStockSource() == null;
    }

    private static <K> Component<K> classify(
            Set<K> states,
            List<CraftPattern<K>> patterns,
            Map<K, Integer> itemRank) {
        List<Transition<K>> transitions = new ArrayList<>();
        boolean hasExternalProducer = false;
        for (CraftPattern<K> pattern : patterns) {
            Map<K, Long> inputs = new LinkedHashMap<>();
            boolean invalidInternalInput = false;
            for (CraftInput<K> input : pattern.inputs()) {
                if (!states.contains(input.key())) continue;
                if (!isOrdinary(input)) {
                    invalidInternalInput = true;
                    break;
                }
                if (!mergeExact(inputs, input.key(), input.amount())) return null;
            }
            if (invalidInternalInput) return null;

            Map<K, Long> outputs = new LinkedHashMap<>();
            if (states.contains(pattern.output())
                    && !mergeExact(outputs, pattern.output(), pattern.outputAmount())) {
                return null;
            }
            for (CraftOutput<K> byproduct : pattern.byproducts()) {
                if (states.contains(byproduct.key())
                        && !mergeExact(outputs, byproduct.key(), byproduct.amount())) {
                    return null;
                }
            }

            // An acyclic producer may inject the first state and a sink may consume the final state.
            // Neither is a transition of the feedback machine itself.
            if (inputs.isEmpty() && !outputs.isEmpty()) {
                hasExternalProducer = true;
                continue;
            }
            if (inputs.isEmpty() || outputs.isEmpty()) continue;
            if (inputs.size() != 1 || outputs.size() != 1) return null;
            Map.Entry<K, Long> input = inputs.entrySet().iterator().next();
            Map.Entry<K, Long> output = outputs.entrySet().iterator().next();
            transitions.add(new Transition<>(
                    pattern, input.getKey(), input.getValue(), output.getKey(), output.getValue()));
        }
        if (transitions.isEmpty()) return null;

        Map<K, Transition<K>> consumerByState = new HashMap<>();
        Map<K, Transition<K>> producerByState = new HashMap<>();
        Map<K, Long> consumed = new HashMap<>();
        Map<K, Long> produced = new HashMap<>();
        for (Transition<K> transition : transitions) {
            if (consumerByState.putIfAbsent(transition.input(), transition) != null
                    || producerByState.putIfAbsent(transition.output(), transition) != null
                    || !mergeExact(consumed, transition.input(), transition.inputAmount())
                    || !mergeExact(produced, transition.output(), transition.outputAmount())) {
                return null;
            }
        }
        if (!consumerByState.keySet().equals(states)
                || !producerByState.keySet().equals(states)) {
            return null;
        }
        Map<K, Long> lossPerRound = new LinkedHashMap<>();
        for (K state : states) {
            long consumedAmount = consumed.getOrDefault(state, 0L);
            long producedAmount = produced.getOrDefault(state, 0L);
            if (producedAmount > consumedAmount) return null;
            if (producedAmount < consumedAmount) {
                lossPerRound.put(state, consumedAmount - producedAmount);
            }
        }

        List<Transition<K>> cycle = new ArrayList<>(transitions.size());
        Set<CraftPattern<K>> visited = new HashSet<>();
        Transition<K> current = transitions.get(0);
        while (visited.add(current.pattern())) {
            cycle.add(current);
            current = consumerByState.get(current.output());
            if (current == null) return null;
        }
        if (current.pattern() != cycle.get(0).pattern() || cycle.size() != transitions.size()) {
            return null;
        }

        List<SeedOption<K>> options = seedOptions(cycle, producerByState);
        if (options.isEmpty()) return null;
        options.sort((left, right) -> {
            int byLoss = Long.compare(
                    seedLoss(right, lossPerRound), seedLoss(left, lossPerRound));
            if (byLoss != 0) return byLoss;
            int byAmount = Long.compare(seedAmount(left), seedAmount(right));
            return byAmount != 0
                    ? byAmount
                    : Integer.compare(seedRank(left, itemRank), seedRank(right, itemRank));
        });
        List<CraftPattern<K>> cyclePatterns = new ArrayList<>(cycle.size());
        for (Transition<K> transition : cycle) cyclePatterns.add(transition.pattern());
        return new Component<>(
                states, cyclePatterns, lossPerRound, hasExternalProducer, options);
    }

    private static <K> long seedLoss(SeedOption<K> option, Map<K, Long> lossPerRound) {
        long result = 0L;
        for (K key : option.amounts().keySet()) {
            result = Sat.add(result, lossPerRound.getOrDefault(key, 0L));
        }
        return result;
    }

    private static <K> long seedAmount(SeedOption<K> option) {
        long result = 0L;
        for (long amount : option.amounts().values()) result = Sat.add(result, amount);
        return result;
    }

    private static <K> int seedRank(SeedOption<K> option, Map<K, Integer> itemRank) {
        int result = Integer.MAX_VALUE;
        for (K key : option.amounts().keySet()) {
            result = Math.min(result, itemRank.getOrDefault(key, Integer.MAX_VALUE));
        }
        return result;
    }

    private static <K> List<SeedOption<K>> seedOptions(
            List<Transition<K>> cycle,
            Map<K, Transition<K>> producerByState) {
        List<SeedOption<K>> result = new ArrayList<>(cycle.size());
        Set<Map<K, Long>> seen = new HashSet<>();
        // Choosing a transition as the first step leaves one predecessor output batch after a full
        // round. Ordinary accounting already charges any per-round deficit; this retained batch is
        // the additional physical marking that algebraic cancellation would otherwise erase.
        for (Transition<K> transition : cycle) {
            Transition<K> producer = producerByState.get(transition.input());
            Map<K, Long> option = Map.of(transition.input(), producer.outputAmount());
            if (seen.add(option)) result.add(new SeedOption<>(option));
        }
        return result;
    }

    private static <K> boolean mergeExact(Map<K, Long> values, K key, long amount) {
        try {
            values.put(key, Math.addExact(values.getOrDefault(key, 0L), amount));
            return true;
        } catch (ArithmeticException ignored) {
            return false;
        }
    }

    private static final class DfsFrame<K> {
        private final K node;
        private final List<K> children;
        private int index;

        private DfsFrame(K node, List<K> children) {
            this.node = node;
            this.children = children;
        }
    }

    private static <K> List<Set<K>> stronglyConnectedComponents(
            Set<K> nodes,
            Map<K, List<K>> adjacency) {
        List<K> finished = new ArrayList<>(nodes.size());
        Set<K> visited = new HashSet<>();
        for (K root : nodes) {
            if (!visited.add(root)) continue;
            Deque<DfsFrame<K>> stack = new ArrayDeque<>();
            stack.push(new DfsFrame<>(root, adjacency.getOrDefault(root, List.of())));
            while (!stack.isEmpty()) {
                PlanningCancellation.check();
                DfsFrame<K> frame = stack.peek();
                if (frame.index < frame.children.size()) {
                    K child = frame.children.get(frame.index++);
                    if (visited.add(child)) {
                        stack.push(new DfsFrame<>(child, adjacency.getOrDefault(child, List.of())));
                    }
                } else {
                    finished.add(frame.node);
                    stack.pop();
                }
            }
        }

        Map<K, List<K>> reverse = new LinkedHashMap<>();
        for (K node : nodes) reverse.put(node, new ArrayList<>());
        for (Map.Entry<K, List<K>> entry : adjacency.entrySet()) {
            for (K child : entry.getValue()) {
                reverse.computeIfAbsent(child, ignored -> new ArrayList<>()).add(entry.getKey());
            }
        }

        List<Set<K>> result = new ArrayList<>();
        Set<K> assigned = new HashSet<>();
        for (int i = finished.size() - 1; i >= 0; i--) {
            K root = finished.get(i);
            if (!assigned.add(root)) continue;
            Set<K> component = new LinkedHashSet<>();
            Deque<K> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                PlanningCancellation.check();
                K node = stack.pop();
                component.add(node);
                for (K previous : reverse.getOrDefault(node, List.of())) {
                    if (assigned.add(previous)) stack.push(previous);
                }
            }
            result.add(component);
        }
        return result;
    }
}
