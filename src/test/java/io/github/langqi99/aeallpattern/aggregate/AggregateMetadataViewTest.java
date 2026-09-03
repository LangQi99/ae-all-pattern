package io.github.langqi99.aeallpattern.aggregate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AggregateMetadataViewTest {
    private static final ResourceLocation CATALYST =
            ResourceLocation.fromNamespaceAndPath("aeallpattern", "batch_test");
    private static final String SERIES = "a".repeat(64);

    @AfterEach
    void clearMetadata() {
        AggregateMetadataView.replace(List.of());
    }

    @Test
    void selectsFirstMissingBatchAndStopsWhenSeriesIsComplete() {
        AggregateMetadataView.replace(List.of(entry(0), entry(2)));
        assertEquals(1, AggregateMetadataView.nextMissingBatch(CATALYST, SERIES, 16384, 3));

        AggregateMetadataView.replace(List.of(entry(0), entry(1), entry(2)));
        assertEquals(3, AggregateMetadataView.nextMissingBatch(CATALYST, SERIES, 16384, 3));
    }

    @Test
    void ignoresPartsFromAnotherCatalogSeries() {
        AggregateMetadataView.Entry other = new AggregateMetadataView.Entry(
                UUID.randomUUID(), CATALYST, "block.test", "b".repeat(64), 16384,
                "b".repeat(64), 16384, 0, 3, 40000);
        AggregateMetadataView.replace(List.of(other));

        assertEquals(0, AggregateMetadataView.nextMissingBatch(CATALYST, SERIES, 16384, 3));
    }

    @Test
    void acceptsOversizedLegacySingleEntryForSaveCompatibility() {
        int legacyRecipeCount = 18000;
        assertDoesNotThrow(() -> new AggregatePatternLibrary.Entry(
                UUID.randomUUID(), CATALYST, "block.test", SERIES, legacyRecipeCount,
                Math.ceilDiv(legacyRecipeCount, AggregatePatternLibrary.PAGE_SIZE),
                SERIES, legacyRecipeCount, 0, 1, legacyRecipeCount));
    }

    private static AggregateMetadataView.Entry entry(int batchIndex) {
        int recipeCount = batchIndex == 2 ? 7232 : 16384;
        return new AggregateMetadataView.Entry(
                UUID.randomUUID(), CATALYST, "block.test", SERIES, recipeCount,
                SERIES, 16384, batchIndex, 3, 40000);
    }
}
