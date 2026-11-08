package io.github.langqi99.aeallpattern.compat.mioicif;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class MioIcifMetalFormerAdapterTest {
    @Test
    void eachModeMapsToItsOwnRecipeType() {
        assertEquals("metal_former_rolling", MioIcifMetalFormerAdapter.recipeTypePath("ROLLING"));
        assertEquals("metal_former_cutting", MioIcifMetalFormerAdapter.recipeTypePath("CUTTING"));
        assertEquals("metal_former_extruding", MioIcifMetalFormerAdapter.recipeTypePath("EXTRUDING"));
    }

    @Test
    void unknownModeHasNoRecipeType() {
        assertNull(MioIcifMetalFormerAdapter.recipeTypePath(null));
        assertNull(MioIcifMetalFormerAdapter.recipeTypePath("POLISHING"));
    }

    @Test
    void supportsBothMetalFormerBlocksOnly() {
        assertTrue(MioIcifMetalFormerAdapter.matchesBlock(
                ResourceLocation.fromNamespaceAndPath("mio_icif", "producer/block_metal_former")));
        assertTrue(MioIcifMetalFormerAdapter.matchesBlock(
                ResourceLocation.fromNamespaceAndPath("mio_icif", "producer/block_metal_former_advanced")));
        assertFalse(MioIcifMetalFormerAdapter.matchesBlock(
                ResourceLocation.fromNamespaceAndPath("mio_icif", "producer/block_compressor_elc")));
        assertFalse(MioIcifMetalFormerAdapter.matchesBlock(
                ResourceLocation.fromNamespaceAndPath("minecraft", "furnace")));
    }
}
