package io.github.langqi99.aeallpattern.internal.routing.ae2.crafting;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

/** Keeps non-item output jobs on AE2's native planner so processing inputs remain pushable. */
public final class TianshuFastPlanningPolicy {
    private TianshuFastPlanningPolicy() {
    }

    public static boolean supportsOutput(AEKey output) {
        return output instanceof AEItemKey;
    }
}
