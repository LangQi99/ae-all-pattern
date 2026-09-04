package io.github.langqi99.aeallpattern.aggregate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import io.github.langqi99.aeallpattern.network.FriendlyStreamCodec;
import net.minecraft.resources.ResourceLocation;

/** Tiny item payload that points at the server-side virtual pattern library. */
public record AggregatePatternRef(
        UUID libraryId,
        ResourceLocation catalystId) {
    public static final Codec<AggregatePatternRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("library_id").forGetter(AggregatePatternRef::libraryId),
            ResourceLocation.CODEC.fieldOf("catalyst_id").forGetter(AggregatePatternRef::catalystId)
    ).apply(instance, AggregatePatternRef::new));
    public static final FriendlyStreamCodec<AggregatePatternRef> STREAM_CODEC = FriendlyStreamCodec.of(
            (buffer, ref) -> {
                buffer.writeUUID(ref.libraryId());
                buffer.writeResourceLocation(ref.catalystId());
            },
            buffer -> new AggregatePatternRef(
                    buffer.readUUID(),
                    buffer.readResourceLocation()));
}
