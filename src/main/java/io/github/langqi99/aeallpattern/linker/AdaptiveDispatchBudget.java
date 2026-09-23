package io.github.langqi99.aeallpattern.linker;

/** Bounded 1/2/4/... ramp of already-owned crafts. Never creates or multiplies materials. */
public final class AdaptiveDispatchBudget {
    public static final int MAX = 64;
    private String pattern;
    private int limit = 1;

    public int limit(String patternKey, boolean enabled) {
        if (!enabled || !patternKey.equals(pattern)) {
            pattern = patternKey;
            limit = 1;
        }
        return limit;
    }

    public void completed(int accepted, boolean blocked, boolean enabled) {
        if (!enabled) limit = 1;
        else if (blocked) limit = Math.max(1, limit / 2);
        else if (accepted >= limit) limit = Math.min(MAX, limit * 2);
    }
}
