package io.github.langqi99.aeallpattern.internal.routing.ae2.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("amplifying-cycle-policy")
class CraftingRoutePolicyAmplifyingCyclesTest {
    @Test
    void defaultsToEnabledAndRoundTrips() {
        assertTrue(CraftingRoutePolicy.DEFAULT.allowAmplifyingCycles());
        assertTrue(CraftingRoutePolicy.deserialize(
                CraftingRoutePolicy.DEFAULT.serialize()).allowAmplifyingCycles());
        assertFalse(CraftingRoutePolicy.deserialize(
                CraftingRoutePolicy.DEFAULT.withAmplifyingCycles(false).serialize())
                .allowAmplifyingCycles());
    }

    @Test
    void oldSerializedPoliciesMigrateToEnabled() {
        assertTrue(CraftingRoutePolicy.deserialize("-1,1,-1,1,1,1,8241,0")
                .allowAmplifyingCycles());
    }

    @Test
    void otherPreferenceChangesPreserveDisabledState() {
        CraftingRoutePolicy disabled = CraftingRoutePolicy.DEFAULT.withAmplifyingCycles(false);
        assertFalse(disabled.withPathPreference(1).allowAmplifyingCycles());
        assertFalse(disabled.withStockSurplusPreference(-1).allowAmplifyingCycles());
        assertFalse(disabled.withYieldPreference(-1).allowAmplifyingCycles());
        assertFalse(disabled.withFast(false).allowAmplifyingCycles());
        assertFalse(disabled.withByproductOrders(true).allowAmplifyingCycles());
    }
}
