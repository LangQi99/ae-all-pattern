package io.github.langqi99.aeallpattern.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import io.github.langqi99.aeallpattern.network.FriendlyStreamCodec;

/** Client request to re-filter the picker against the server's complete recipe list. */
public record AggregateSearchPayload(
        UUID requestId, String searchText, boolean searchOutputs, int resultPageIndex)
        {
    public static final FriendlyStreamCodec<AggregateSearchPayload> STREAM_CODEC =
            FriendlyStreamCodec.of(AggregateSearchPayload::encode, AggregateSearchPayload::decode);

    public AggregateSearchPayload {
        searchText = searchText == null ? "" : searchText;
        if (resultPageIndex < 0) {
            throw new IllegalArgumentException("negative aggregate result page");
        }
    }

    private static void encode(FriendlyByteBuf buffer, AggregateSearchPayload payload) {
        buffer.writeUUID(payload.requestId());
        buffer.writeUtf(payload.searchText(), 64);
        buffer.writeBoolean(payload.searchOutputs());
        buffer.writeVarInt(payload.resultPageIndex());
    }

    private static AggregateSearchPayload decode(FriendlyByteBuf buffer) {
        return new AggregateSearchPayload(
                buffer.readUUID(), buffer.readUtf(64), buffer.readBoolean(), buffer.readVarInt());
    }
}
