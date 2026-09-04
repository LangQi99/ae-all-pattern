package io.github.langqi99.aeallpattern.util;

/** Java 17 replacement for the clamp overloads added to {@link Math} in newer JDKs. */
public final class CompatMath {
    private CompatMath() {
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
