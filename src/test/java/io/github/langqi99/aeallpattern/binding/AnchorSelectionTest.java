package io.github.langqi99.aeallpattern.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.serialization.JsonOps;
import io.github.langqi99.aeallpattern.TestMinecraftBootstrap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AnchorSelectionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        TestMinecraftBootstrap.initialize();
    }

    @Test
    void persistentCodecRoundTripsSelection() {
        AnchorSelection selection = AnchorSelection.create(
                UUID.randomUUID(),
                GlobalPos.of(Level.OVERWORLD, new BlockPos(3, 70, -4)),
                "aeallpattern:pattern_linker",
                42L);

        var encoded = AnchorSelection.CODEC.encodeStart(JsonOps.INSTANCE, selection)
                .getOrThrow(false, message -> {});
        var decoded = AnchorSelection.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(false, message -> {});

        assertEquals(selection, decoded);
    }

    @Test
    void rejectsInvalidSchemas() {
        assertThrows(IllegalArgumentException.class, () -> new AnchorSelection(
                0,
                UUID.randomUUID(),
                GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO),
                "anchor",
                0L));
    }
}
