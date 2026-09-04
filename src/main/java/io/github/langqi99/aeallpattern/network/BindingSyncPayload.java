package io.github.langqi99.aeallpattern.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import io.github.langqi99.aeallpattern.network.FriendlyStreamCodec;
import net.minecraft.resources.ResourceKey;

public record BindingSyncPayload(List<BindingRenderEntry> entries) {
    private static final int MAX_ENTRIES = 4096;
    public static final FriendlyStreamCodec<BindingSyncPayload> STREAM_CODEC = FriendlyStreamCodec.of(
            BindingSyncPayload::encode,
            BindingSyncPayload::decode);

    public BindingSyncPayload {
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("too many binding render entries");
        }
    }

    private static void encode(FriendlyByteBuf buffer, BindingSyncPayload payload) {
        buffer.writeVarInt(payload.entries.size());
        for (BindingRenderEntry entry : payload.entries) {
            buffer.writeUUID(entry.bindingId());
            buffer.writeResourceLocation(entry.dimension().location());
            buffer.writeBlockPos(entry.pos());
            buffer.writeByte(entry.status());
        }
    }

    private static BindingSyncPayload decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("invalid binding render entry count: " + count);
        }
        List<BindingRenderEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new BindingRenderEntry(
                    buffer.readUUID(),
                    ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation()),
                    buffer.readBlockPos(),
                    buffer.readByte()));
        }
        return new BindingSyncPayload(entries);
    }
}
