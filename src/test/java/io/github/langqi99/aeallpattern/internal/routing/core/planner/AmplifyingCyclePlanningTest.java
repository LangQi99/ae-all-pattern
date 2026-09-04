package io.github.langqi99.aeallpattern.internal.routing.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** End-to-end tests from a compiled gain recipe through the complete v2 planning result. */
@Tag("amplifying-cycle-planning")
class AmplifyingCyclePlanningTest {

    @Test
    void roundsUpWhenEachOperationAddsSeveralItems() {
        Cycle cycle = cycle(5, 2, List.of(CraftInput.of("D", 2)), List.of(), "grow_a");
        CraftPattern<String> finish = pattern("B", 1, CraftInput.of("A", 1_000));
        CraftPlan<String> plan = plan(cycle, finish, "B", 1,
                stock("A", 2), stock("D", 666));

        assertTrue(plan.feasible(), plan::toString);
        assertEquals(333L, plan.firings().get(cycle.pattern()));
        assertEquals(1L, plan.firings().get(finish));
        assertEquals(666L, plan.usedStock().get("D"));
    }

    @Test
    void scalesAnIntermediateAcrossAStackSizedFinalOrder() {
        Cycle cycle = cycle(2, 1, List.of(CraftInput.of("D", 1)), List.of(), "grow_a");
        CraftPattern<String> finish = pattern("B", 4, CraftInput.of("A", 25));
        CraftPlan<String> plan = plan(cycle, finish, "B", 64,
                stock("A", 1), stock("D", 399));

        assertTrue(plan.feasible(), plan::toString);
        assertEquals(399L, plan.firings().get(cycle.pattern()));
        assertEquals(16L, plan.firings().get(finish));
    }

    @Test
    void chargesEveryExternalInputForEveryGrowthOperation() {
        Cycle cycle = cycle(2, 1,
                List.of(CraftInput.of("D", 2), CraftInput.of("E", 3)),
                List.of(), "multi_input_growth");
        CraftPattern<String> finish = pattern("B", 1, CraftInput.of("A", 10));
        CraftPlan<String> plan = plan(cycle, finish, "B", 1,
                stock("A", 1), stock("D", 18), stock("E", 27));

        assertTrue(plan.feasible(), plan::toString);
        assertEquals(9L, plan.firings().get(cycle.pattern()));
        assertEquals(18L, plan.usedStock().get("D"));
        assertEquals(27L, plan.usedStock().get("E"));
    }

    @Test
    void reportsTheExactExternalMaterialShortfall() {
        Cycle cycle = cycle(2, 1, List.of(CraftInput.of("D", 1)), List.of(), "grow_a");
        CraftPattern<String> finish = pattern("B", 1, CraftInput.of("A", 10));
        CraftPlan<String> plan = plan(cycle, finish, "B", 1,
                stock("A", 1), stock("D", 8));

        assertFalse(plan.feasible());
        assertEquals(1L, plan.missing().get("D"));
    }

    @Test
    void existingIntermediateStockReducesOnlyTheGrowthWork() {
        Cycle cycle = cycle(2, 1, List.of(CraftInput.of("D", 1)), List.of(), "grow_a");
        CraftPattern<String> finish = pattern("B", 1, CraftInput.of("A", 1_000));
        CraftPlan<String> plan = plan(cycle, finish, "B", 1,
                stock("A", 400), stock("D", 600));

        assertTrue(plan.feasible(), plan::toString);
        assertEquals(600L, plan.firings().get(cycle.pattern()));
        assertEquals(400L, plan.usedStock().get("A"));
    }

    @Test
    void directOrderDoesNotCountThePrivateSeedAsRequestedOutput() {
        Cycle cycle = cycle(2, 1, List.of(CraftInput.of("D", 1)), List.of(), "grow_a");
        CraftPlan<String> plan = plan(cycle, null, "A", 1_000, stock("D", 1_000));

        assertTrue(plan.feasible(), plan::toString);
        assertEquals(1_000L, plan.firings().get(cycle.pattern()));
        assertEquals(1_000L, plan.usedStock().get("D"));
        assertEquals(1L, totalReusableSeed(plan));
    }

    @Test
    void canCraftAStartupSeedThroughSeveralAcyclicSteps() {
        Cycle cycle = cycle(2, 1, List.of(CraftInput.of("D", 1)), List.of(), "grow_a");
        CraftPattern<String> rawToBlank = pattern("blank", 1, CraftInput.of("raw", 2));
        CraftPattern<String> blankToSeed = pattern("A", 1, CraftInput.of("blank", 1));
        CraftPattern<String> finish = pattern("B", 1, CraftInput.of("A", 20));
        CraftGraph<String> graph = CraftGraph.<String>builder()
                .pattern(cycle.pattern())
                .pattern(rawToBlank)
                .pattern(blankToSeed)
                .pattern(finish)
                .stock("raw", 2)
                .stock("D", 20)
                .build();

        CraftPlan<String> plan = CraftPlannerV2.plan(graph, "B", 1);

        assertTrue(plan.feasible(), plan::toString);
        assertEquals(1L, plan.firings().get(rawToBlank));
        assertEquals(1L, plan.firings().get(blankToSeed));
        assertEquals(20L, plan.firings().get(cycle.pattern()));
    }

    @Test
    void growthByproductsCanSatisfyALaterInputOfTheSameOrder() {
        Cycle cycle = cycle(2, 1, List.of(CraftInput.of("D", 1)),
                List.of(CraftOutput.of("slag", 1)), "grow_with_slag");
        CraftPattern<String> finish = new CraftPattern<>(
                "B", 1, List.of(CraftInput.of("A", 10), CraftInput.of("slag", 9)), "finish");
        CraftPlan<String> plan = plan(cycle, finish, "B", 1,
                stock("A", 1), stock("D", 9));

        assertTrue(plan.feasible(), plan::toString);
        assertEquals(9L, plan.firings().get(cycle.pattern()));
        assertFalse(plan.missing().containsKey("slag"));
    }

    @Test
    void twoIndependentGainLoopsCanFeedOneFinalRecipe() {
        Cycle a = cycle("A", 2, 1, List.of(CraftInput.of("D", 1)), "grow_a");
        Cycle x = cycle("X", 3, 1, List.of(CraftInput.of("E", 2)), "grow_x");
        CraftPattern<String> finish = new CraftPattern<>(
                "B", 1, List.of(CraftInput.of("A", 10), CraftInput.of("X", 7)), "finish");
        CraftGraph<String> graph = baseGraph(a)
                .pattern(x.pattern())
                .reusableStock(x.scope(), "X", 1)
                .reusableStockRoute(x.source(), "X", List.of("X"))
                .pattern(finish)
                .stock("A", 1)
                .stock("X", 1)
                .stock("D", 9)
                .stock("E", 6)
                .build();

        CraftPlan<String> plan = CraftPlannerV2.plan(graph, "B", 1);

        assertTrue(plan.feasible(), plan::toString);
        assertEquals(9L, plan.firings().get(a.pattern()));
        assertEquals(3L, plan.firings().get(x.pattern()));
        assertEquals(2L, totalReusableSeed(plan));
    }

    @Test
    void oneGainLoopCanSupplyTheFuelForAnotherGainLoop() {
        Cycle a = cycle("A", 2, 1, List.of(CraftInput.of("D", 1)), "grow_a");
        Cycle b = cycle("B", 2, 1, List.of(CraftInput.of("A", 1)), "grow_b");
        CraftPattern<String> finish = pattern("C", 1, CraftInput.of("B", 10));
        CraftGraph<String> graph = baseGraph(a)
                .pattern(b.pattern())
                .reusableStock(b.scope(), "B", 1)
                .reusableStockRoute(b.source(), "B", List.of("B"))
                .pattern(finish)
                .stock("A", 1)
                .stock("B", 1)
                .stock("D", 8)
                .build();

        CraftPlan<String> plan = CraftPlannerV2.plan(graph, "C", 1);

        assertTrue(plan.feasible(), plan::toString);
        assertEquals(8L, plan.firings().get(a.pattern()));
        assertEquals(9L, plan.firings().get(b.pattern()));
    }

    @Test
    void fallsBackToAnAvailableGainRecipeWhenThePreferredOneHasNoFuel() {
        Cycle unavailable = cycle("A", 2, 1, List.of(CraftInput.of("rare", 1)), "rare_growth");
        Cycle available = cycle("A", 3, 1, List.of(CraftInput.of("common", 1)), "common_growth");
        CraftPattern<String> finish = pattern("B", 1, CraftInput.of("A", 10));
        CraftGraph<String> graph = baseGraph(unavailable)
                .pattern(available.pattern())
                .reusableStock(available.scope(), "A", 1)
                .reusableStockRoute(available.source(), "A", List.of("A"))
                .pattern(finish)
                .stock("A", 1)
                .stock("common", 5)
                .build();

        CraftPlan<String> plan = CraftPlannerV2.plan(graph, "B", 1);

        assertTrue(plan.feasible(), plan::toString);
        assertFalse(plan.firings().containsKey(unavailable.pattern()));
        assertEquals(5L, plan.firings().get(available.pattern()));
    }

    @Test
    void veryLargeDemandIsSolvedInClosedForm() {
        long demand = 1_000_000_000_000L;
        Cycle cycle = cycle(2, 1, List.of(CraftInput.of("D", 1)), List.of(), "grow_a");
        CraftPattern<String> finish = pattern("B", 1, CraftInput.of("A", demand));
        CraftPlan<String> plan = plan(cycle, finish, "B", 1,
                stock("A", 1), stock("D", demand - 1));

        assertTrue(plan.feasible(), plan::toString);
        assertEquals(demand - 1, plan.firings().get(cycle.pattern()));
        assertTrue(plan.itemsProcessed() < 20,
                () -> "growth request unexpectedly scaled with item count: " + plan.itemsProcessed());
    }

    private static Cycle cycle(
            long outputAmount,
            long seedAmount,
            List<CraftInput<String>> externalInputs,
            List<CraftOutput<String>> byproducts,
            String sourceId) {
        return cycle("A", outputAmount, seedAmount, externalInputs, byproducts, sourceId);
    }

    private static Cycle cycle(
            String key,
            long outputAmount,
            long seedAmount,
            List<CraftInput<String>> externalInputs,
            String sourceId) {
        return cycle(key, outputAmount, seedAmount, externalInputs, List.of(), sourceId);
    }

    private static Cycle cycle(
            String key,
            long outputAmount,
            long seedAmount,
            List<CraftInput<String>> externalInputs,
            List<CraftOutput<String>> byproducts,
            String sourceId) {
        Object scope = new TestStockAlias(sourceId);
        ReusableStockSource seedSource = new ReusableStockSource(scope, scope);
        var inputs = new java.util.ArrayList<>(externalInputs);
        inputs.add(0, CraftInput.of(key, seedAmount));
        CraftPattern<String> raw = new CraftPattern<>(
                key, outputAmount, inputs, byproducts, sourceId);
        var compiled = AmplifyingCycleCompiler.compile(
                raw, amount -> CraftInput.returnedFrom(key, amount, seedSource));
        if (compiled == null) {
            throw new AssertionError("test fixture was not a growing self-loop");
        }
        return new Cycle(compiled.pattern(), compiled.seedAmount(), scope, seedSource);
    }

    private static CraftGraph.Builder<String> baseGraph(Cycle cycle) {
        return CraftGraph.<String>builder()
                .pattern(cycle.pattern())
                .reusableStock(cycle.scope(), cycle.pattern().output(), cycle.seedAmount())
                .reusableStockRoute(cycle.source(), cycle.pattern().output(),
                        List.of(cycle.pattern().output()));
    }

    private static CraftPlan<String> plan(
            Cycle cycle,
            CraftPattern<String> finish,
            String target,
            long amount,
            Stock... stocks) {
        CraftGraph.Builder<String> graph = baseGraph(cycle);
        if (finish != null) {
            graph.pattern(finish);
        }
        for (Stock stock : stocks) {
            graph.stock(stock.key(), stock.amount());
        }
        return CraftPlannerV2.plan(graph.build(), target, amount);
    }

    @SafeVarargs
    private static CraftPattern<String> pattern(
            String output, long outputAmount, CraftInput<String>... inputs) {
        return new CraftPattern<>(output, outputAmount, List.of(inputs), output);
    }

    private static Stock stock(String key, long amount) {
        return new Stock(key, amount);
    }

    private static long totalReusableSeed(CraftPlan<String> plan) {
        return plan.usedReusableStock().values().stream().mapToLong(Long::longValue).sum();
    }

    private record Cycle(
            CraftPattern<String> pattern,
            long seedAmount,
            Object scope,
            ReusableStockSource source) {
    }

    private record Stock(String key, long amount) {
    }

    private record TestStockAlias(String id) implements OrdinaryStockAlias {
    }
}
