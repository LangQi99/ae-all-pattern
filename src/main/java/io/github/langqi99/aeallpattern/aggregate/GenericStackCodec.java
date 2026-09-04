package io.github.langqi99.aeallpattern.aggregate;

import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;

/** Persistent GenericStack codec used by the 1.20.1 NBT metadata bridge. */
final class GenericStackCodec {
    static final Codec<GenericStack> CODEC = CompoundTag.CODEC.comapFlatMap(
            tag -> {
                GenericStack stack = GenericStack.readTag(tag);
                return stack == null
                        ? DataResult.error(() -> "invalid generic stack")
                        : DataResult.success(stack);
            },
            GenericStack::writeTag);

    private GenericStackCodec() {
    }
}
