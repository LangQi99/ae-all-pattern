package io.github.langqi99.aeallpattern.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AggregateRecipeInputLimitTest {
    @Test
    void supportsConfiguredTagExpansionCapacity() {
        assertEquals(81, AggregateRecipe.MAX_INPUTS);
        assertEquals(27, AggregateRecipe.MAX_OUTPUTS);
        assertEquals(Integer.MAX_VALUE, AggregateInputSlot.MAX_ALTERNATIVES);
        assertEquals(Integer.MAX_VALUE, AggregateRecipe.MAX_TOTAL_INPUT_ALTERNATIVES);
        assertEquals(1024, io.github.langqi99.aeallpattern.config.AeAllPatternCommonConfig.TAG_EXPANSION_LIMIT.getDefault());
    }
}
