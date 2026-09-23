package io.github.langqi99.aeallpattern.aggregate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.level.Level;

/** Relaxes only item identity; never strips data from the actual material being consumed. */
public record IgnoreInputNbtInput(IPatternDetails.IInput delegate) implements IPatternDetails.IInput {
    @Override
    public GenericStack[] getPossibleInputs() { return delegate.getPossibleInputs(); }

    @Override
    public long getMultiplier() { return delegate.getMultiplier(); }

    @Override
    public boolean isValid(AEKey key, Level level) {
        if (key instanceof AEItemKey item) {
            for (GenericStack template : getPossibleInputs()) {
                if (template.what() instanceof AEItemKey expected && item.getItem() == expected.getItem()) {
                    return true;
                }
            }
            return false;
        }
        return delegate.isValid(key, level);
    }

    @Override
    public AEKey getRemainingKey(AEKey key) { return delegate.getRemainingKey(key); }
}
