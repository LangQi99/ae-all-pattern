package io.github.langqi99.aeallpattern.internal.routing.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("amplifying-cycle-compiler")
class AmplifyingCycleCompilerTest {

    @Test
    void growsAnIntermediateFromOneSeedUntilAThousandAreAvailable() {
        Object scope = new TestStockAlias();
        ReusableStockSource source = new ReusableStockSource(scope, scope);
        CraftPattern<String> rawGain = new CraftPattern<>(
                "A", 2, List.of(CraftInput.of("A", 1), CraftInput.of("D", 1)), "gain");
        CraftPattern<String> makeB = new CraftPattern<>(
                "B", 1, List.of(CraftInput.of("A", 1_000)), "make_b");
        var compiled = AmplifyingCycleCompiler.compile(
                rawGain, amount -> CraftInput.returnedFrom("A", amount, source));

        CraftGraph<String> graph = CraftGraph.<String>builder()
                .pattern(compiled.pattern())
                .pattern(makeB)
                .stock("A", 1)
                .stock("D", 999)
                .reusableStock(scope, "A", 1)
                .reusableStockRoute(source, "A", List.of("A"))
                .build();
        CraftPlan<String> plan = CraftPlannerV2.plan(graph, "B", 1);

        assertTrue(plan.feasible(), plan::toString);
        assertEquals(999L, plan.firings().get(compiled.pattern()));
        assertEquals(1L, plan.firings().get(makeB));
        assertEquals(1L, plan.usedStock().get("A"));
        assertEquals(999L, plan.usedStock().get("D"));
        assertEquals(1L, plan.usedReusableStock().values().stream()
                .mapToLong(Long::longValue).sum());
    }

    @Test
    void refusesToBootstrapItselfFromNothing() {
        Object scope = new TestStockAlias();
        ReusableStockSource source = new ReusableStockSource(scope, scope);
        CraftPattern<String> rawGain = new CraftPattern<>(
                "A", 2, List.of(CraftInput.of("A", 1), CraftInput.of("D", 1)), "gain");
        var compiled = AmplifyingCycleCompiler.compile(
                rawGain, amount -> CraftInput.returnedFrom("A", amount, source));
        CraftGraph<String> graph = CraftGraph.<String>builder()
                .pattern(compiled.pattern())
                .stock("D", 1_000)
                .build();

        CraftPlan<String> plan = CraftPlannerV2.plan(graph, "A", 1_000);

        assertFalse(plan.feasible());
        assertEquals(1L, plan.missing().get("A"));
    }

    @Test
    void requiresTheWholeTwoItemStartupSeed() {
        Object scope = new TestStockAlias();
        ReusableStockSource source = new ReusableStockSource(scope, scope);
        CraftPattern<String> rawGain = new CraftPattern<>(
                "A", 3, List.of(CraftInput.of("A", 2), CraftInput.of("D", 1)), "gain");
        var compiled = AmplifyingCycleCompiler.compile(
                rawGain, amount -> CraftInput.returnedFrom("A", amount, source));
        CraftGraph<String> graph = CraftGraph.<String>builder()
                .pattern(compiled.pattern())
                .stock("A", 1)
                .stock("D", 10)
                .reusableStock(scope, "A", 1)
                .reusableStockRoute(source, "A", List.of("A"))
                .build();

        CraftPlan<String> plan = CraftPlannerV2.plan(graph, "A", 10);

        assertFalse(plan.feasible());
        assertEquals(1L, plan.usedStock().get("A"));
        assertEquals(1L, plan.missing().get("A"));
    }

    @Test
    void rootOrderUsesPrivateSeedWithoutCountingItTowardRequestedOutput() {
        Object scope = new TestStockAlias();
        ReusableStockSource source = new ReusableStockSource(scope, scope);
        CraftPattern<String> rawGain = new CraftPattern<>(
                "A", 2, List.of(CraftInput.of("A", 1), CraftInput.of("D", 1)), "gain");
        var compiled = AmplifyingCycleCompiler.compile(
                rawGain, amount -> CraftInput.returnedFrom("A", amount, source));
        CraftGraph<String> graph = CraftGraph.<String>builder()
                .pattern(compiled.pattern())
                .stock("D", 1_000)
                .reusableStock(scope, "A", 1)
                .reusableStockRoute(source, "A", List.of("A"))
                .build();

        CraftPlan<String> plan = CraftPlannerV2.plan(graph, "A", 1_000);

        assertTrue(plan.feasible(), plan::toString);
        assertEquals(1_000L, plan.firings().get(compiled.pattern()));
        assertEquals(1_000L, plan.usedStock().get("D"));
        assertEquals(1L, plan.usedReusableStock().values().stream()
                .mapToLong(Long::longValue).sum());
    }

    @Test
    void craftsOneSeedFromAnAcyclicRecipeBeforeGrowing() {
        Object scope = new TestStockAlias();
        ReusableStockSource source = new ReusableStockSource(scope, scope);
        CraftPattern<String> rawGain = new CraftPattern<>(
                "A", 2, List.of(CraftInput.of("A", 1), CraftInput.of("D", 1)), "gain");
        var compiled = AmplifyingCycleCompiler.compile(
                rawGain, amount -> CraftInput.returnedFrom("A", amount, source));
        CraftPattern<String> makeSeed = new CraftPattern<>(
                "A", 1, List.of(CraftInput.of("raw", 1)), "seed");
        CraftPattern<String> makeB = new CraftPattern<>(
                "B", 1, List.of(CraftInput.of("A", 1_000)), "make_b");
        CraftGraph<String> graph = CraftGraph.<String>builder()
                .pattern(compiled.pattern())
                .pattern(makeSeed)
                .pattern(makeB)
                .stock("raw", 1)
                .stock("D", 1_000)
                .build();

        CraftPlan<String> plan = CraftPlannerV2.plan(graph, "B", 1);

        assertTrue(plan.feasible(), plan::toString);
        assertEquals(1L, plan.firings().get(makeSeed));
        assertEquals(1_000L, plan.firings().get(compiled.pattern()));
        assertEquals(1L, plan.firings().get(makeB));
    }

    @Test
    void rejectsBalancedLossyAndAmbiguousSelfLoops() {
        assertNull(AmplifyingCycleCompiler.compile(
                new CraftPattern<>("A", 1, List.of(CraftInput.of("A", 1)), "balanced"),
                amount -> CraftInput.returned("A", amount)));
        assertNull(AmplifyingCycleCompiler.compile(
                new CraftPattern<>("A", 1, List.of(CraftInput.of("A", 2)), "lossy"),
                amount -> CraftInput.returned("A", amount)));
        assertNull(AmplifyingCycleCompiler.compile(
                new CraftPattern<>("A", 2, List.of(CraftInput.returned("A", 1)), "already_returned"),
                amount -> CraftInput.returned("A", amount)));
    }

    @Test
    void keepsExternalInputsByproductsAndProviderMetadata() {
        CraftPattern<String> raw = new CraftPattern<>(
                "A", 5,
                List.of(CraftInput.of("A", 2), CraftInput.of("D", 3)),
                List.of(CraftOutput.of("slag", 1)),
                "source", 2, 4);
        var compiled = AmplifyingCycleCompiler.compile(
                raw, amount -> CraftInput.returned("A", amount));

        assertEquals(3L, compiled.pattern().outputAmount());
        assertEquals(2L, compiled.seedAmount());
        assertEquals(List.of(CraftInput.of("D", 3), CraftInput.returned("A", 2)),
                compiled.pattern().inputs());
        assertEquals(raw.byproducts(), compiled.pattern().byproducts());
        assertEquals(2, compiled.pattern().idleProviderCount());
        assertEquals(4, compiled.pattern().providerCount());
    }

    @Test
    void combinesSeveralSelfInputSlotsIntoOneStartupSeed() {
        CraftPattern<String> raw = new CraftPattern<>(
                "A", 7,
                List.of(CraftInput.of("A", 2), CraftInput.of("D", 1), CraftInput.of("A", 3)),
                "split_self_inputs");

        var compiled = AmplifyingCycleCompiler.compile(
                raw, amount -> CraftInput.returned("A", amount));

        assertEquals(5L, compiled.seedAmount());
        assertEquals(2L, compiled.pattern().outputAmount());
        assertEquals(List.of(CraftInput.of("D", 1), CraftInput.returned("A", 5)),
                compiled.pattern().inputs());
    }

    @Test
    void rejectsRemainderAndFiniteUseSelfInputs() {
        assertNull(AmplifyingCycleCompiler.compile(
                new CraftPattern<>(
                        "A", 2, List.of(CraftInput.consumedReturning("A", 1, "empty")), "remainder"),
                amount -> CraftInput.returned("A", amount)));
        assertNull(AmplifyingCycleCompiler.compile(
                new CraftPattern<>(
                        "A", 2, List.of(CraftInput.finiteUse("A", 1, 10)), "finite_use"),
                amount -> CraftInput.returned("A", amount)));
    }

    @Test
    void rejectsRecipesWithoutASelfInput() {
        assertNull(AmplifyingCycleCompiler.compile(
                new CraftPattern<>("A", 2, List.of(CraftInput.of("D", 1)), "ordinary"),
                amount -> CraftInput.returned("A", amount)));
    }

    private record TestStockAlias() implements OrdinaryStockAlias {
    }
}
