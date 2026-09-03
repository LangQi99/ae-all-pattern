package io.github.langqi99.aeallpattern.aggregate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;

/** Client-facing metadata cache. It deliberately contains no recipe payloads. */
public final class AggregateMetadataView {
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final AtomicLong REVISION = new AtomicLong();

    private AggregateMetadataView() {
    }

    public static void replace(Collection<Entry> entries) {
        ENTRIES.clear();
        entries.forEach(entry -> ENTRIES.put(entry.libraryId(), entry));
        REVISION.incrementAndGet();
    }

    public static Optional<Entry> find(UUID libraryId) {
        return Optional.ofNullable(ENTRIES.get(libraryId));
    }

    public static Collection<Entry> entries() {
        return List.copyOf(ENTRIES.values());
    }

    public static long revision() {
        return REVISION.get();
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
            int totalRecipeCount,
            boolean startupRefreshRequired) {
    }
}
