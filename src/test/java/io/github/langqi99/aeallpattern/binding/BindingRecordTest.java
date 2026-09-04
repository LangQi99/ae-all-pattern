package io.github.langqi99.aeallpattern.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.langqi99.aeallpattern.TestMinecraftBootstrap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BindingRecordTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        TestMinecraftBootstrap.initialize();
    }

    @Test
    void nbtRoundTripPreservesStableReferences() {
        BindingRecord original = new BindingRecord(
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 2)),
                GlobalPos.of(Level.OVERWORLD, new BlockPos(5, 64, 6)),
                Direction.NORTH,
                "aeallpattern:pattern_linker",
                "minecraft:furnace",
                "minecraft:furnace",
                1,
                100L,
                120L);

        assertEquals(original, BindingRecord.fromTag(original.toTag()));
    }

    @Test
    void refusesUnknownSchemasInsteadOfSilentlyReinterpretingThem() {
        assertThrows(IllegalArgumentException.class, () -> new BindingRecord(
                2,
                UUID.randomUUID(),
                UUID.randomUUID(),
                GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO),
                GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO),
                Direction.UP,
                "anchor",
                "target",
                "adapter",
                1,
                0L,
                0L));
    }
}
