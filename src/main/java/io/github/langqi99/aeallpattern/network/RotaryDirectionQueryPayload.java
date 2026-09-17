package io.github.langqi99.aeallpattern.network;

import io.github.langqi99.aeallpattern.AeAllPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Asks the server which way a rotary condensentrator is currently set.
 *
 * <p>Mekanism syncs the machine's {@code mode} only through its container while the GUI is open
 * (the block entity update tag is empty), so the client side copy is not trustworthy. The server
 * owns the value, so the scan waits for this round trip before picking a recipe direction.</p>
 */
public record RotaryDirectionQueryPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<RotaryDirectionQueryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeAllPattern.MOD_ID, "rotary_direction_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RotaryDirectionQueryPayload> STREAM_CODEC =
            StreamCodec.of(RotaryDirectionQueryPayload::encode, RotaryDirectionQueryPayload::decode);

    public RotaryDirectionQueryPayload {
        pos = pos.immutable();
    }

    private static void encode(RegistryFriendlyByteBuf buffer, RotaryDirectionQueryPayload payload) {
        buffer.writeBlockPos(payload.pos());
    }

    private static RotaryDirectionQueryPayload decode(RegistryFriendlyByteBuf buffer) {
        return new RotaryDirectionQueryPayload(buffer.readBlockPos());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
