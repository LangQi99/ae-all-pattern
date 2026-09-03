package io.github.langqi99.aeallpattern.aggregate;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/** Client-facing metadata cache. It deliberately contains no recipe payloads. */
public final class AggregateMetadataView {
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private AggregateMetadataView() {
    }

    public static void replace(Collection<Entry> entries) {
        ENTRIES.clear();
        entries.forEach(entry -> ENTRIES.put(entry.libraryId(), entry));
    }

    public static Optional<Entry> find(UUID libraryId) {
        return Optional.ofNullable(ENTRIES.get(libraryId));
    }

    /** Returns the first numbered part not yet known to the client, or {@code batchCount}. */
    public static int nextMissingBatch(
            ResourceLocation catalystId, String seriesHash, int batchSize, int batchCount) {
        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            final int wanted = batchIndex;
            boolean present = ENTRIES.values().stream().anyMatch(entry ->
                    entry.catalystId().equals(catalystId)
                            && entry.seriesHash().equals(seriesHash)
                            && entry.batchSize() == batchSize
                            && entry.batchIndex() == wanted);
            if (!present) {
                return batchIndex;
            }
        }
        return batchCount;
    }

    public record Entry(
            UUID libraryId,
            ResourceLocation catalystId,
            String machineTranslationKey,
            String contentHash,
            int recipeCount,
            String seriesHash,
            int batchSize,
            int batchIndex,
            int batchCount,
            int totalRecipeCount) {
    }
}
