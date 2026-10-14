package io.github.langqi99.aeallpattern.network;

import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregateSearchMode;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** Client request to re-filter the picker against the server's complete recipe list. */
public record AggregateSearchPayload(
        UUID requestId, String searchText, AggregateSearchMode searchMode, int resultPageIndex)
        implements CustomPacketPayload {
    public static final Type<AggregateSearchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeAllPattern.MOD_ID, "aggregate_search"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AggregateSearchPayload> STREAM_CODEC =
            StreamCodec.of(AggregateSearchPayload::encode, AggregateSearchPayload::decode);

    public AggregateSearchPayload {
        searchText = searchText == null ? "" : searchText;
        Objects.requireNonNull(searchMode, "searchMode");
        if (resultPageIndex < 0) {
            throw new IllegalArgumentException("negative aggregate result page");
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, AggregateSearchPayload payload) {
        buffer.writeUUID(payload.requestId());
        buffer.writeUtf(payload.searchText(), 64);
        buffer.writeEnum(payload.searchMode());
        buffer.writeVarInt(payload.resultPageIndex());
    }

    private static AggregateSearchPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AggregateSearchPayload(
                buffer.readUUID(), buffer.readUtf(64), buffer.readEnum(AggregateSearchMode.class), buffer.readVarInt());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
