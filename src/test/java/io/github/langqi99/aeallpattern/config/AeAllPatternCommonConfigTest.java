package io.github.langqi99.aeallpattern.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.langqi99.aeallpattern.aggregate.AggregatePatternData;
import org.junit.jupiter.api.Test;

class AeAllPatternCommonConfigTest {
    @Test
    void aggregateRecipeLimitHasRequestedRangeAndDefault() {
        assertEquals(1_048_576, AggregatePatternData.MAX_RECIPES);
        assertEquals(1_048_576, AeAllPatternCommonConfig.AGGREGATE_RECIPE_LIMIT.getDefault());
        assertTrue(AeAllPatternCommonConfig.isAggregateRecipeLimitValid(1));
        assertTrue(AeAllPatternCommonConfig.isAggregateRecipeLimitValid(1_048_576));
        assertFalse(AeAllPatternCommonConfig.isAggregateRecipeLimitValid(1_048_577));
        assertFalse(AeAllPatternCommonConfig.isAggregateRecipeLimitValid(0));
    }
}
