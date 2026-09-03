package io.github.langqi99.aeallpattern.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AeAllPatternCommonConfigTest {
    @Test
    void aggregateRecipeLimitHasRequestedRangeAndDefault() {
        assertEquals(16384, AeAllPatternCommonConfig.AGGREGATE_RECIPE_LIMIT.getDefault());
        assertTrue(AeAllPatternCommonConfig.AGGREGATE_RECIPE_LIMIT.getSpec().test(1));
        assertTrue(AeAllPatternCommonConfig.AGGREGATE_RECIPE_LIMIT.getSpec().test(16384));
        assertFalse(AeAllPatternCommonConfig.AGGREGATE_RECIPE_LIMIT.getSpec().test(16385));
        assertFalse(AeAllPatternCommonConfig.AGGREGATE_RECIPE_LIMIT.getSpec().test(0));
    }
}
