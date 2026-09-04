package io.github.langqi99.aeallpattern.client;

import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregateMetadataView;
import io.github.langqi99.aeallpattern.compat.emi.EmiAggregateScanner;
import io.github.langqi99.aeallpattern.compat.jei.AeAllPatternJeiPlugin;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.TickEvent;

/** Refreshes the catalogs that existed when a client entered the world. */
public final class AggregateStartupRefreshService {
    private static final int VIEWER_SETTLE_TICKS = 100;
    private static final Deque<AggregateMetadataView.Entry> QUEUE = new ArrayDeque<>();
    private static int connectedTicks;
    private static boolean initialized;
    private static long metadataRevisionAtReset;
    private static long awaitingMetadataRevision = -1;
    private static int acknowledgementTicks;

    private AggregateStartupRefreshService() {
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        boolean useEmi = ModList.get().isLoaded("emi")
                && ModList.get().isLoaded("toomanyrecipeviewers");
        if (++connectedTicks < VIEWER_SETTLE_TICKS
                || AggregateMetadataView.revision() <= metadataRevisionAtReset
                || (!useEmi && AeAllPatternJeiPlugin.runtime().isEmpty())) {
            return;
        }
        if (!initialized) {
            entriesNeedingRefresh(AggregateMetadataView.entries()).forEach(QUEUE::addLast);
            initialized = true;
            AeAllPattern.LOGGER.info("Queued {} aggregate catalogs for world-entry refresh", QUEUE.size());
        }
        if (awaitingMetadataRevision >= 0) {
            if (AggregateMetadataView.revision() > awaitingMetadataRevision) {
                awaitingMetadataRevision = -1;
                acknowledgementTicks = 0;
            } else if (++acknowledgementTicks < 600) {
                return;
            } else {
                AeAllPattern.LOGGER.warn("Timed out waiting for an aggregate startup-refresh acknowledgement");
                awaitingMetadataRevision = -1;
                acknowledgementTicks = 0;
            }
        }
        // Another player may have completed an entry after this client took its initial
        // snapshot. Drop it instead of performing a redundant scan.
        while (!QUEUE.isEmpty()) {
            var current = AggregateMetadataView.find(QUEUE.peekFirst().libraryId());
            if (current.isPresent() && current.orElseThrow().startupRefreshRequired()) {
                QUEUE.removeFirst();
                QUEUE.addFirst(current.orElseThrow());
                break;
            }
            QUEUE.removeFirst();
        }

        if (QUEUE.isEmpty() || ClientJeiAggregateScanner.isBusy()
                || (useEmi && EmiAggregateScanner.isBusy())) {
            return;
        }

        AggregateMetadataView.Entry entry = QUEUE.peekFirst();
        boolean started;
        if (useEmi) {
            started = EmiAggregateScanner.refresh(entry);
        } else {
            started = ClientJeiAggregateScanner.startRefresh(entry);
        }
        if (started) {
            QUEUE.removeFirst();
            awaitingMetadataRevision = AggregateMetadataView.revision();
            acknowledgementTicks = 0;
        }
    }

    static List<AggregateMetadataView.Entry> entriesNeedingRefresh(
            Collection<AggregateMetadataView.Entry> entries) {
        return entries.stream()
                .filter(entry -> entry.batchCount() == 1 && entry.startupRefreshRequired())
                .toList();
    }

    public static void reset() {
        QUEUE.clear();
        connectedTicks = 0;
        initialized = false;
        metadataRevisionAtReset = AggregateMetadataView.revision();
        awaitingMetadataRevision = -1;
        acknowledgementTicks = 0;
    }
}
