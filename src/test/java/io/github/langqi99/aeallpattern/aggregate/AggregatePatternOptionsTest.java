package io.github.langqi99.aeallpattern.aggregate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AggregatePatternOptionsTest {
    @Test
    void probabilitySafeguardsAreEnabledByDefault() {
        assertFalse(AggregatePatternOptions.DEFAULT.splitSameItems());
        assertTrue(AggregatePatternOptions.DEFAULT.ignoreOutputComponents());
        assertTrue(AggregatePatternOptions.DEFAULT.skipProbabilisticMainOutput());
        assertTrue(AggregatePatternOptions.DEFAULT.ignoreProbabilisticByproducts());
        assertFalse(AggregatePatternOptions.DEFAULT.removeProcessingCatalysts());
        assertTrue(AggregatePatternOptions.DEFAULT.allowItemSubstitutions());
        assertTrue(AggregatePatternOptions.DEFAULT.allowFluidSubstitutions());
        assertFalse(AggregatePatternOptions.DEFAULT.removeInputFluids());
        assertFalse(AggregatePatternOptions.DEFAULT.removeOutputFluids());
        assertFalse(AggregatePatternOptions.DEFAULT.removeInputChemicals());
        assertFalse(AggregatePatternOptions.DEFAULT.removeOutputChemicals());
        assertFalse(AggregatePatternOptions.DEFAULT.swapFirstAndLastInputs());
        assertTrue(AggregatePatternOptions.DEFAULT.skipDurabilityConsumingRecipes());
    }

    @Test
    void flagsRoundTripAllThirteenOptions() {
        var options = new AggregatePatternOptions(
                true, false, false, true, true, false, true,
                true, false, true, false, true, false);
        var decoded = AggregatePatternOptions.fromFlags(options.flags());

        assertTrue(decoded.splitSameItems());
        assertFalse(decoded.ignoreOutputComponents());
        assertFalse(decoded.skipProbabilisticMainOutput());
        assertTrue(decoded.ignoreProbabilisticByproducts());
        assertTrue(decoded.removeProcessingCatalysts());
        assertFalse(decoded.allowItemSubstitutions());
        assertTrue(decoded.allowFluidSubstitutions());
        assertTrue(decoded.removeInputFluids());
        assertFalse(decoded.removeOutputFluids());
        assertTrue(decoded.removeInputChemicals());
        assertFalse(decoded.removeOutputChemicals());
        assertTrue(decoded.swapFirstAndLastInputs());
        assertFalse(decoded.skipDurabilityConsumingRecipes());
    }
}
