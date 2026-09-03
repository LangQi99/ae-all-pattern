package io.github.langqi99.aeallpattern.compat.emi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.langqi99.aeallpattern.aggregate.AggregatePatternSelection;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("emi")
class EmiAggregateScannerTest {
    @Test
    void shortPatternIdsRemainReadable() {
        assertEquals("minecraft:crafting/minecraft:stick", EmiAggregateScanner.patternId(
                "minecraft:crafting/minecraft:stick"));
    }

    @Test
    void longPatternIdsAreStableUniqueAndProtocolSafe() {
        String prefix = "category:" + "a".repeat(170);
        String first = EmiAggregateScanner.patternId(prefix + "/recipe:first");
        String second = EmiAggregateScanner.patternId(prefix + "/recipe:second");

        assertEquals(AggregatePatternSelection.MAX_ID_LENGTH, first.length());
        assertEquals(first, EmiAggregateScanner.patternId(prefix + "/recipe:first"));
        assertNotEquals(first, second);
    }
}
