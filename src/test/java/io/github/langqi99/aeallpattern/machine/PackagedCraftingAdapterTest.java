package io.github.langqi99.aeallpattern.machine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PackagedCraftingAdapterTest {
    @Test
    void higherTierCrafterSupportsItsOwnAndLowerTierRecipes() {
        assertTrue(PackagedCraftingAdapter.supportsTier(3, 1));
        assertTrue(PackagedCraftingAdapter.supportsTier(3, 2));
        assertTrue(PackagedCraftingAdapter.supportsTier(3, 3));
        assertFalse(PackagedCraftingAdapter.supportsTier(3, 4));
    }
}
