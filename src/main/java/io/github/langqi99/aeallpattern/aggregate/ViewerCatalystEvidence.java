package io.github.langqi99.aeallpattern.aggregate;

import java.util.HashSet;
import java.util.List;

/** Viewer declarations only: never infer reusability by comparing recipe inputs and outputs. */
public final class ViewerCatalystEvidence {
    private ViewerCatalystEvidence() {}

    public static <T> boolean explicitlyDeclared(List<T> input, List<List<T>> catalysts) {
        if (input.isEmpty()) return false;
        var candidates = new HashSet<>(input);
        return catalysts.stream().anyMatch(c -> !c.isEmpty() && candidates.equals(new HashSet<>(c)));
    }

    /** The viewer must supply a guaranteed, exact remainder for every candidate. */
    public static <T> boolean exactRemainder(T input, T remainder, float inputChance, float remainderChance) {
        return input != null && input.equals(remainder) && inputChance == 1F && remainderChance == 1F;
    }
}
