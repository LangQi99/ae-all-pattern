package io.github.langqi99.aeallpattern.registry;

import com.mojang.serialization.Codec;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternOptions;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternRef;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternSelection;
import io.github.langqi99.aeallpattern.binding.AnchorSelection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** 1.20.1 bridge for item metadata that uses data components on newer Minecraft. */
public final class ModDataComponents {
    private static final String ROOT = AeAllPattern.MOD_ID;
    private static final String ANCHOR_SELECTION = "anchor_selection";
    private static final String VIRTUAL_PATTERN_ID = "virtual_pattern_id";
    private static final String AGGREGATE_PATTERN = "aggregate_pattern";
    private static final String AGGREGATE_PATTERN_OPTIONS = "aggregate_pattern_options";
    private static final String AGGREGATE_PATTERN_SELECTION = "aggregate_pattern_selection";

    private ModDataComponents() {
    }

    public static @Nullable AnchorSelection getAnchorSelection(ItemStack stack) {
        return read(stack, ANCHOR_SELECTION, AnchorSelection.CODEC);
    }

    public static void setAnchorSelection(ItemStack stack, AnchorSelection value) {
        write(stack, ANCHOR_SELECTION, AnchorSelection.CODEC, value);
    }

    public static boolean hasAnchorSelection(ItemStack stack) {
        return contains(stack, ANCHOR_SELECTION);
    }

    public static @Nullable AggregatePatternRef getAggregatePattern(ItemStack stack) {
        return read(stack, AGGREGATE_PATTERN, AggregatePatternRef.CODEC);
    }

    public static void setAggregatePattern(ItemStack stack, AggregatePatternRef value) {
        write(stack, AGGREGATE_PATTERN, AggregatePatternRef.CODEC, value);
    }

    public static boolean hasAggregatePattern(ItemStack stack) {
        return contains(stack, AGGREGATE_PATTERN);
    }

    public static @Nullable AggregatePatternOptions getAggregatePatternOptions(ItemStack stack) {
        return read(stack, AGGREGATE_PATTERN_OPTIONS, AggregatePatternOptions.CODEC);
    }

    public static AggregatePatternOptions getAggregatePatternOptionsOrDefault(ItemStack stack) {
        AggregatePatternOptions value = getAggregatePatternOptions(stack);
        return value == null ? AggregatePatternOptions.DEFAULT : value;
    }

    public static void setAggregatePatternOptions(ItemStack stack, AggregatePatternOptions value) {
        write(stack, AGGREGATE_PATTERN_OPTIONS, AggregatePatternOptions.CODEC, value);
    }

    public static @Nullable AggregatePatternSelection getAggregatePatternSelection(ItemStack stack) {
        return read(stack, AGGREGATE_PATTERN_SELECTION, AggregatePatternSelection.CODEC);
    }

    public static AggregatePatternSelection getAggregatePatternSelectionOrDefault(ItemStack stack) {
        AggregatePatternSelection value = getAggregatePatternSelection(stack);
        return value == null ? AggregatePatternSelection.ALL_ENABLED : value;
    }

    public static void setAggregatePatternSelection(ItemStack stack, AggregatePatternSelection value) {
        write(stack, AGGREGATE_PATTERN_SELECTION, AggregatePatternSelection.CODEC, value);
    }

    public static boolean hasAggregatePatternSelection(ItemStack stack) {
        return contains(stack, AGGREGATE_PATTERN_SELECTION);
    }

    public static void clearAggregatePatternSelection(ItemStack stack) {
        remove(stack, AGGREGATE_PATTERN_SELECTION);
    }

    public static @Nullable String getVirtualPatternId(ItemStack stack) {
        CompoundTag root = root(stack);
        return root != null && root.contains(VIRTUAL_PATTERN_ID, CompoundTag.TAG_STRING)
                ? root.getString(VIRTUAL_PATTERN_ID)
                : null;
    }

    public static void setVirtualPatternId(ItemStack stack, String value) {
        stack.getOrCreateTagElement(ROOT).putString(VIRTUAL_PATTERN_ID, value);
    }

    private static boolean contains(ItemStack stack, String key) {
        CompoundTag root = root(stack);
        return root != null && root.contains(key);
    }

    private static @Nullable CompoundTag root(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(ROOT, CompoundTag.TAG_COMPOUND) ? tag.getCompound(ROOT) : null;
    }

    private static <T> @Nullable T read(ItemStack stack, String key, Codec<T> codec) {
        CompoundTag root = root(stack);
        if (root == null || !root.contains(key)) {
            return null;
        }
        return codec.parse(NbtOps.INSTANCE, root.get(key))
                .resultOrPartial(message -> AeAllPattern.LOGGER.warn("Invalid item metadata {}: {}", key, message))
                .orElse(null);
    }

    private static <T> void write(ItemStack stack, String key, Codec<T> codec, T value) {
        codec.encodeStart(NbtOps.INSTANCE, value)
                .resultOrPartial(message -> AeAllPattern.LOGGER.warn("Could not encode item metadata {}: {}", key, message))
                .ifPresent(tag -> stack.getOrCreateTagElement(ROOT).put(key, tag));
    }

    private static void remove(ItemStack stack, String key) {
        CompoundTag root = root(stack);
        if (root == null) {
            return;
        }
        root.remove(key);
        if (root.isEmpty()) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(ROOT);
            }
        }
    }
}
