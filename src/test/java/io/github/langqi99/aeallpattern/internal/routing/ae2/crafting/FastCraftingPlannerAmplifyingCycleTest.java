package io.github.langqi99.aeallpattern.internal.routing.ae2.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.langqi99.aeallpattern.internal.routing.core.planner.CraftInput;
import io.github.langqi99.aeallpattern.internal.routing.core.planner.CraftPattern;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Verifies the AE adapter's effective-policy gate before a gain loop enters the planner graph. */
@Tag("amplifying-cycle-adapter")
class FastCraftingPlannerAmplifyingCycleTest {

    @Test
    void defaultRouterPolicyCompilesTheRealRecipeSource() {
        Object source = new Object();
        CraftPattern<String> raw = new CraftPattern<>(
                "A", 2, List.of(CraftInput.of("A", 1), CraftInput.of("D", 1)), source);

        var compiled = FastCraftingPlanner.compileAmplifyingCycleIfEnabled(
                raw,
                CraftingRoutePolicy.DEFAULT.allowAmplifyingCycles(),
                amount -> CraftInput.returned("A", amount));

        assertNotNull(compiled);
        assertEquals(source, compiled.pattern().source());
        assertEquals(1L, compiled.pattern().outputAmount());
    }

    @Test
    void perOrderOverrideCanDisableCompilationWithoutTouchingTheRecipe() {
        CraftPattern<String> raw = new CraftPattern<>(
                "A", 2, List.of(CraftInput.of("A", 1), CraftInput.of("D", 1)), "gain");
        CraftingRoutePolicy disabled = CraftingRoutePolicy.DEFAULT.withAmplifyingCycles(false);
        AtomicBoolean seedFactoryCalled = new AtomicBoolean();

        var compiled = FastCraftingPlanner.compileAmplifyingCycleIfEnabled(
                raw,
                disabled.allowAmplifyingCycles(),
                amount -> {
                    seedFactoryCalled.set(true);
                    return CraftInput.returned("A", amount);
                });

        assertNull(compiled);
        assertFalse(seedFactoryCalled.get(), "disabled policy still inspected or rewrote the pattern");
        assertEquals(2L, raw.outputAmount());
        assertEquals(List.of(CraftInput.of("A", 1), CraftInput.of("D", 1)), raw.inputs());
    }

    @Test
    void enabledPolicyStillRejectsABalancedLoop() {
        CraftPattern<String> raw = new CraftPattern<>(
                "A", 1, List.of(CraftInput.of("A", 1), CraftInput.of("D", 1)), "balanced");

        assertNull(FastCraftingPlanner.compileAmplifyingCycleIfEnabled(
                raw, true, amount -> CraftInput.returned("A", amount)));
    }

    @Test
    void togglingOtherRoutingPreferencesDoesNotSilentlyDisableTheGate() {
        CraftPattern<String> raw = new CraftPattern<>(
                "A", 3, List.of(CraftInput.of("A", 1), CraftInput.of("D", 1)), "gain");
        CraftingRoutePolicy reordered = CraftingRoutePolicy.DEFAULT
                .withPathPreference(1)
                .withStockSurplusPreference(-1)
                .withYieldPreference(-1)
                .withFast(false)
                .moveCriterion(0, 3);

        assertNotNull(FastCraftingPlanner.compileAmplifyingCycleIfEnabled(
                raw,
                reordered.allowAmplifyingCycles(),
                amount -> CraftInput.returned("A", amount)));
    }
}
