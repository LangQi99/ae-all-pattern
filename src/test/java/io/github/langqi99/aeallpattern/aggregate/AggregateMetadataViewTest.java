package io.github.langqi99.aeallpattern.aggregate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AggregateMetadataViewTest {
    private static final ResourceLocation CATALYST =
            new ResourceLocation("aeallpattern", "batch_test");
    private static final String SERIES = "a".repeat(64);

    @AfterEach
    void clearMetadata() {
        AggregateMetadataView.replace(List.of());
    }

    @Test
    void acceptsLegacySingleEntryForSaveCompatibility() {
        int legacyRecipeCount = 18000;
        assertDoesNotThrow(() -> new AggregatePatternLibrary.Entry(
                UUID.randomUUID(), CATALYST, "block.test", SERIES, legacyRecipeCount,
                (legacyRecipeCount + AggregatePatternLibrary.PAGE_SIZE - 1)
                        / AggregatePatternLibrary.PAGE_SIZE,
                SERIES, legacyRecipeCount, 0, 1, legacyRecipeCount));
    }

    @Test
    void replacementAdvancesRevisionAndRemovesDeletedCatalogs() {
        long before = AggregateMetadataView.revision();
        var firstId = UUID.randomUUID();
        var deletedId = UUID.randomUUID();
        var first = entry(firstId, 2);
        var deleted = entry(deletedId, 7);
        AggregateMetadataView.replace(List.of(first, deleted));

        long populatedRevision = AggregateMetadataView.revision();
        assertTrue(populatedRevision > before);
        assertEquals(2, AggregateMetadataView.entries().size());

        var refreshed = entry(firstId, 3);
        AggregateMetadataView.replace(List.of(refreshed));
        assertTrue(AggregateMetadataView.revision() > populatedRevision);
        assertEquals(3, AggregateMetadataView.find(firstId).orElseThrow().recipeCount());
        assertTrue(AggregateMetadataView.find(deletedId).isEmpty());
    }

    @Test
    void entriesReturnsASnapshotInsteadOfTheMutableBackingMap() {
        var old = entry(UUID.randomUUID(), 1);
        AggregateMetadataView.replace(List.of(old));
        var snapshot = AggregateMetadataView.entries();

        AggregateMetadataView.replace(List.of(entry(UUID.randomUUID(), 2)));

        assertEquals(List.of(old), snapshot.stream().toList());
    }

    private static AggregateMetadataView.Entry entry(UUID id, int recipeCount) {
        return new AggregateMetadataView.Entry(
                id, CATALYST, "block.test", SERIES, recipeCount,
                SERIES, Math.max(1, recipeCount), 0, 1, recipeCount, true);
    }

}
