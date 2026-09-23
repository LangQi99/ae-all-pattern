package io.github.langqi99.aeallpattern.linker;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LinkerOperationOptionsTest {
    @Test void defaultsAndFlags() {
        assertFalse(LinkerOperationOptions.DEFAULT.blocking());
        assertTrue(LinkerOperationOptions.DEFAULT.smartBatching());
        assertTrue(LinkerOperationOptions.DEFAULT.autoReturn());
        for (int flags = 0; flags < 8; flags++) {
            var options = LinkerOperationOptions.fromFlags(flags);
            assertEquals(flags, options.flags());
            assertEquals(!options.blocking(), options.allowsParallelQueue());
        }
    }

    @Test void rampIsBoundedAndBacksOffOnRejection() {
        var budget = new AdaptiveDispatchBudget();
        for (int expected : new int[]{1, 2, 4, 8, 16, 32, 64, 64}) {
            assertEquals(expected, budget.limit("a", true));
            budget.completed(expected, false, true);
        }
        budget.completed(0, true, true);
        assertEquals(32, budget.limit("a", true));
        for (int i = 0; i < 10; i++) budget.completed(0, true, true);
        assertEquals(1, budget.limit("a", true));
    }

    @Test void scarcitySwitchOffAndRecipeChangesDoNotRamp() {
        var budget = new AdaptiveDispatchBudget();
        budget.limit("a", true);
        budget.completed(1, false, true);
        budget.completed(1, false, true);
        assertEquals(2, budget.limit("a", true));
        assertEquals(1, budget.limit("b", true));
        budget.completed(1, false, true);
        assertEquals(1, budget.limit("b", false));
        budget.completed(64, false, false);
        assertEquals(1, budget.limit("b", true));
    }
}
