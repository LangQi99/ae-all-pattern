package io.github.langqi99.aeallpattern.aggregate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AggregatePatternOptionsTest {
    @Test
    void ignoreInputNbtDefaultsOffAndOldSavedOptionsStayStrict() {
        assertFalse(AggregatePatternOptions.DEFAULT.ignoreInputComponents());
        var old = AggregatePatternOptions.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                new com.google.gson.JsonObject()).result().orElseThrow();
        assertFalse(old.ignoreInputComponents());
        assertFalse(new AggregatePatternOptions(true, false).ignoreInputComponents());
        for (int flags = 0; flags < 8192; flags++) {
            assertFalse(AggregatePatternOptions.fromFlags(flags).ignoreInputComponents());
        }
    }

    @Test
    void allFourteenFlagsRoundTripWithoutChangingExistingBits() {
        for (int flags = 0; flags < 16384; flags++) {
            org.junit.jupiter.api.Assertions.assertEquals(flags, AggregatePatternOptions.fromFlags(flags).flags());
        }
    }

    @Test
    void enabledInputOptionPersistsIndependentlyOfOutputOption() {
        var options = AggregatePatternOptions.fromFlags(8192);
        var json = AggregatePatternOptions.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, options)
                .result().orElseThrow();
        var decoded = AggregatePatternOptions.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json)
                .result().orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(options, decoded);
        assertTrue(decoded.ignoreInputComponents());
        assertFalse(decoded.ignoreOutputComponents());
    }

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
