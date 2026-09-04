package io.github.langqi99.aeallpattern.aggregate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** Registers aggregate patterns as encoded patterns and supplies a marker for expansion. */
public final class AggregatePatternDecoder implements IPatternDetailsDecoder {
    public static void register() {
        PatternDetailsHelper.registerDecoder(new AggregatePatternDecoder());
    }

    @Override
    public boolean isEncodedPattern(ItemStack stack) {
        return stack.is(ModItems.AGGREGATE_PATTERN.get())
                && ModDataComponents.hasAggregatePattern(stack);
    }

    @Override
    public IPatternDetails decodePattern(AEItemKey key, Level level) {
        // AE2 asks every registered decoder to decode empty molecular-assembler
        // pattern slots as well. AEItemKey.of(ItemStack.EMPTY) is null.
        if (key == null) {
            return null;
        }
        ItemStack stack = key.toStack();
        if (!isEncodedPattern(stack)) {
            return null;
        }
        // Only the first child is needed for a marker: expanding every child here would make
        // slot-validity checks pay the full aggregate cost on every decode (thousands of
        // recipes), which caused "cannot insert when all selected" timeouts on addon hosts.
        IPatternDetails first = AggregatePatternExpander.expandFirst(stack, level);
        if (first != null) {
            return new AggregatePatternMarkerDetails(key, first);
        }
        // Nothing is published right now (e.g. every child was deselected). The stand-in keeps
        // the item valid inside pattern slots; it is flagged so providers never publish it.
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.COBBLESTONE)))},
                new GenericStack[]{Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.STONE)))});
        IPatternDetails standIn = encoded.isEmpty() ? null : PatternDetailsHelper.decodePattern(encoded, level);
        return standIn == null ? null : new AggregatePatternMarkerDetails(key, standIn, true);
    }

    @Override
    public IPatternDetails decodePattern(ItemStack stack, Level level, boolean tryRecovery) {
        AEItemKey key = AEItemKey.of(stack);
        return key == null ? null : decodePattern(key, level);
    }
}
