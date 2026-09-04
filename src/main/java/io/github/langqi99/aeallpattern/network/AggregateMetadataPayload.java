package io.github.langqi99.aeallpattern.network;

import io.github.langqi99.aeallpattern.aggregate.AggregateMetadataView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import io.github.langqi99.aeallpattern.network.FriendlyStreamCodec;

public record AggregateMetadataPayload(List<AggregateMetadataView.Entry> entries) {
    private static final int MAX_ENTRIES = 4096;
    public static final FriendlyStreamCodec<AggregateMetadataPayload> STREAM_CODEC = FriendlyStreamCodec.of(
            AggregateMetadataPayload::encode, AggregateMetadataPayload::decode);

    public AggregateMetadataPayload {
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("too many aggregate metadata entries");
        }
    }

    private static void encode(FriendlyByteBuf buffer, AggregateMetadataPayload payload) {
        buffer.writeVarInt(payload.entries().size());
        for (var entry : payload.entries()) {
            buffer.writeUUID(entry.libraryId());
            buffer.writeResourceLocation(entry.catalystId());
            buffer.writeUtf(entry.machineTranslationKey(), 256);
            buffer.writeUtf(entry.contentHash(), 64);
            buffer.writeVarInt(entry.recipeCount());
            buffer.writeUtf(entry.seriesHash(), 64);
            buffer.writeVarInt(entry.batchSize());
            buffer.writeVarInt(entry.batchIndex());
            buffer.writeVarInt(entry.batchCount());
            buffer.writeVarInt(entry.totalRecipeCount());
            buffer.writeBoolean(entry.startupRefreshRequired());
        }
    }

    private static AggregateMetadataPayload decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("invalid aggregate metadata count: " + count);
        }
        List<AggregateMetadataView.Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new AggregateMetadataView.Entry(
                    buffer.readUUID(), buffer.readResourceLocation(), buffer.readUtf(256),
                    buffer.readUtf(64), buffer.readVarInt(), buffer.readUtf(64), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()));
        }
        return new AggregateMetadataPayload(entries);
    }
}
