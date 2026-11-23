package io.github.langqi99.aeallpattern.compat.emi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.langqi99.aeallpattern.aggregate.AggregatePatternSelection;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("emi")
class EmiAggregateScannerTest {
    @Test
    void shortPatternIdsRemainReadable() {
        assertEquals("minecraft:crafting/minecraft:stick", EmiAggregateScanner.patternId(
                "minecraft:crafting/minecraft:stick"));
    }

    @Test
    void longPatternIdsAreStableUniqueAndProtocolSafe() {
        String prefix = "category:" + "a".repeat(170);
        String first = EmiAggregateScanner.patternId(prefix + "/recipe:first");
        String second = EmiAggregateScanner.patternId(prefix + "/recipe:second");

        assertEquals(AggregatePatternSelection.MAX_ID_LENGTH, first.length());
        assertEquals(first, EmiAggregateScanner.patternId(prefix + "/recipe:first"));
        assertNotEquals(first, second);
    }

    @Test
    void everyAllowedCategoryIsScannedForOneMachine() {
        ResourceLocation catalyst = ResourceLocation.fromNamespaceAndPath(
                "goldentweaks", "advanced_chemical_plant_controller");
        List<ResourceLocation> categoryIds = List.of(
                ResourceLocation.fromNamespaceAndPath("goldentweaks", "chemical_reaction"),
                ResourceLocation.fromNamespaceAndPath("goldentweaks", "advanced_chemical_reaction"));

        assertEquals(categoryIds, EmiAggregateScanner.selectCategoryIds(categoryIds, catalyst, null));
    }

    @Test
    void unrelatedMekanismCategoriesStayFiltered() {
        ResourceLocation catalyst = ResourceLocation.fromNamespaceAndPath(
                "mekanism", "advanced_infusing_factory");
        List<ResourceLocation> categoryIds = List.of(
                ResourceLocation.fromNamespaceAndPath("mekanism", "metallurgic_infusing"),
                ResourceLocation.fromNamespaceAndPath("mekanism", "infusion_conversion"));

        assertEquals(List.of(categoryIds.getFirst()),
                EmiAggregateScanner.selectCategoryIds(categoryIds, catalyst, null));
    }

    @Test
    void rotaryCondensentratorStillPicksTheCurrentDirection() {
        ResourceLocation catalyst = ResourceLocation.fromNamespaceAndPath(
                "mekanism", "rotary_condensentrator");
        List<ResourceLocation> categoryIds = List.of(
                ResourceLocation.fromNamespaceAndPath("mekanism", "condensentrating"),
                ResourceLocation.fromNamespaceAndPath("mekanism", "decondensentrating"));

        assertEquals(List.of(categoryIds.getFirst()),
                EmiAggregateScanner.selectCategoryIds(categoryIds, catalyst, true));
        assertEquals(List.of(categoryIds.getLast()),
                EmiAggregateScanner.selectCategoryIds(categoryIds, catalyst, false));
    }
}
