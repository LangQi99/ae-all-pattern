package io.github.langqi99.aeallpattern.compat.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Guards the direction contract Mekanism and its add-ons expose.
 *
 * <p>The base machine keeps a single boolean: {@code getMode() == true} means decondensentrating
 * (evaporation) and the package-private {@code isCondensentrating()} is its inverse. Both
 * accessors must map to the same semantic direction, and a machine whose mode cannot be read must
 * report "unknown" instead of silently guessing a direction.</p>
 */
@Tag("jei")
class RotaryCondensentratorSupportTest {
    @Test
    void recognisesBaseMachineAndAddonVariants() {
        assertTrue(RotaryCondensentratorSupport.isRotaryCondensentrator(
                id("mekanism:rotary_condensentrator")));
        assertTrue(RotaryCondensentratorSupport.isRotaryCondensentrator(
                id("mekmm:large_rotary_condensentrator")));
        assertFalse(RotaryCondensentratorSupport.isRotaryCondensentrator(
                id("mekanism:chemical_oxidizer")));
        assertFalse(RotaryCondensentratorSupport.isRotaryCondensentrator(null));
    }

    @Test
    void publicModeAccessorIsInverted() {
        // getMode() == false is condensing, getMode() == true is evaporating.
        assertEquals(Boolean.TRUE,
                RotaryCondensentratorSupport.condensentrating(new PackagedMode(false)));
        assertEquals(Boolean.FALSE,
                RotaryCondensentratorSupport.condensentrating(new PackagedMode(true)));
    }

    @Test
    void packagePrivateSemanticAccessorIsUsedAsFallback() {
        assertEquals(Boolean.FALSE,
                RotaryCondensentratorSupport.condensentrating(new SemanticOnly(false)));
        assertEquals(Boolean.TRUE,
                RotaryCondensentratorSupport.condensentrating(new SemanticOnly(true)));
    }

    @Test
    void unknownModeIsReportedAsUnknown() {
        assertNull(RotaryCondensentratorSupport.condensentrating(new NoMode()));
        assertNull(RotaryCondensentratorSupport.condensentrating(null));
    }

    @Test
    void serverReportedDirectionIsRememberedUntilCleared() {
        BlockPos pos = new BlockPos(12, 70, -4);
        try {
            assertNull(RotaryCondensentratorSupport.knownDirection(pos));
            RotaryCondensentratorSupport.rememberDirection(pos, false);
            assertEquals(Boolean.FALSE, RotaryCondensentratorSupport.knownDirection(pos));
        } finally {
            RotaryCondensentratorSupport.clearDirections();
        }
        assertNull(RotaryCondensentratorSupport.knownDirection(pos));
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    /** Mirrors Mekanism's public {@code getMode()} accessor. */
    public static final class PackagedMode {
        private final boolean mode;

        PackagedMode(boolean mode) {
            this.mode = mode;
        }

        public boolean getMode() {
            return mode;
        }
    }

    /** Mirrors Mekanism's package-private {@code isCondensentrating()} helper. */
    public static final class SemanticOnly {
        private final boolean condensentrating;

        SemanticOnly(boolean condensentrating) {
            this.condensentrating = condensentrating;
        }

        boolean isCondensentrating() {
            return condensentrating;
        }
    }

    public static final class NoMode {
    }
}
