package io.github.langqi99.aeallpattern.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import io.github.langqi99.aeallpattern.binding.BindingPatternKey;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Gives otherwise identical encoded patterns a stable binding-specific identity. */
public final class VirtualPatternDetails implements IPatternDetails {
    private final BindingPatternKey key;
    private final IPatternDetails delegate;

    public VirtualPatternDetails(BindingPatternKey key, IPatternDetails delegate) {
        this.key = Objects.requireNonNull(key, "key");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public BindingPatternKey key() {
        return key;
    }

    @Override
    public AEItemKey getDefinition() {
        return delegate.getDefinition();
    }

    @Override
    public IInput[] getInputs() {
        return delegate.getInputs();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return delegate.getOutputs();
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return delegate.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink sink) {
        delegate.pushInputsToExternalInventory(inputHolder, sink);
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        return delegate.getTooltip(level, flags);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof VirtualPatternDetails pattern && key.equals(pattern.key)
                && getDefinition().equals(pattern.getDefinition());
    }

    @Override
    public int hashCode() {
        return 31 * key.hashCode() + getDefinition().hashCode();
    }
}
