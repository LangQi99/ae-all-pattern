package io.github.langqi99.aeallpattern.internal.routing.core.planner;

/** Pure accounting helpers for normal-network reusable-seed fallbacks. */
public final class ReusableStockFallback {
    /**
     * Returns the seed stock hidden from the ordinary planning snapshot that still needs publishing.
     * A dependency key is already visible as normal stock and must not be counted a second time.
     */
    public static long supplementalSelfSeedStock(
            long required, long seedSnapshotAmount, long ordinaryVisibleAmount) {
        long positiveRequired = Math.max(0L, required);
        long available = io.github.langqi99.aeallpattern.util.CompatMath.clamp(seedSnapshotAmount, 0L, positiveRequired);
        long ordinaryVisible = io.github.langqi99.aeallpattern.util.CompatMath.clamp(ordinaryVisibleAmount, 0L,
                positiveRequired);
        return Math.max(0L, available - ordinaryVisible);
    }

    private ReusableStockFallback() {
    }
}
