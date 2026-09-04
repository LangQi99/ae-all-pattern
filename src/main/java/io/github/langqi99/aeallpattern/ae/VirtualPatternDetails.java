package io.github.langqi99.aeallpattern.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import io.github.langqi99.aeallpattern.binding.BindingPatternKey;
import java.util.Objects;

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
    public GenericStack[] getOutputs() {
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
    public boolean equals(Object other) {
        return this == other || other instanceof VirtualPatternDetails pattern && key.equals(pattern.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}
