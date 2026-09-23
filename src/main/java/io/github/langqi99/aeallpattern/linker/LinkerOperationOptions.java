package io.github.langqi99.aeallpattern.linker;

/** Machine dispatch settings, independent of per-pattern encoding preferences. */
public record LinkerOperationOptions(boolean blocking, boolean smartBatching, boolean autoReturn) {
    public static final LinkerOperationOptions DEFAULT = new LinkerOperationOptions(false, true, true);

    public int flags() {
        return (blocking ? 1 : 0) | (smartBatching ? 2 : 0) | (autoReturn ? 4 : 0);
    }

    public static LinkerOperationOptions fromFlags(int flags) {
        return new LinkerOperationOptions((flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
    }

    public boolean allowsParallelQueue() { return !blocking; }
}
