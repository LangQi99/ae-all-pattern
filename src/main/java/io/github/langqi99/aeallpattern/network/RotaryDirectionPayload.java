package io.github.langqi99.aeallpattern.network;

import io.github.langqi99.aeallpattern.AeAllPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Server reply carrying the authoritative rotary direction.
 *
 * <p>{@code known} is false when the machine could not be read; callers must then treat the
 * direction as unknown instead of assuming condensing.</p>
 */
public record RotaryDirectionPayload(BlockPos pos, boolean known, boolean condensentrating)
        implements CustomPacketPayload {
    public static final Type<RotaryDirectionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeAllPattern.MOD_ID, "rotary_direction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RotaryDirectionPayload> STREAM_CODEC =
            StreamCodec.of(RotaryDirectionPayload::encode, RotaryDirectionPayload::decode);

    public RotaryDirectionPayload {
        pos = pos.immutable();
    }

    private static void encode(RegistryFriendlyByteBuf buffer, RotaryDirectionPayload payload) {
        buffer.writeBlockPos(payload.pos());
        buffer.writeBoolean(payload.known());
        buffer.writeBoolean(payload.condensentrating());
    }

    private static RotaryDirectionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new RotaryDirectionPayload(
                buffer.readBlockPos(), buffer.readBoolean(), buffer.readBoolean());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
