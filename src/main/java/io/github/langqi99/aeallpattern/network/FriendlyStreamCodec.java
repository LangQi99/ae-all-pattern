package io.github.langqi99.aeallpattern.network;

import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;

/** Small 1.20.1 equivalent for the stream codec shape used by the newer branch. */
public interface FriendlyStreamCodec<T> {
    void encode(FriendlyByteBuf buffer, T value);

    T decode(FriendlyByteBuf buffer);

    static <T> FriendlyStreamCodec<T> of(
            BiConsumer<FriendlyByteBuf, T> encoder,
            Function<FriendlyByteBuf, T> decoder) {
        return new FriendlyStreamCodec<>() {
            @Override
            public void encode(FriendlyByteBuf buffer, T value) {
                encoder.accept(buffer, value);
            }

            @Override
            public T decode(FriendlyByteBuf buffer) {
                return decoder.apply(buffer);
            }
        };
    }
}
