package io.github.langqi99.aeallpattern.aggregate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Decoder marker that lets AE2 accept the custom item in encoded-pattern slots.
 *
 * <p>It also implements {@link IMolecularAssemblerSupportedPattern} so hosts that only accept
 * molecular-assembler patterns (e.g. AE2 Lightning Tech's matter warping matrix pattern
 * storage) pass the slot validity check. Every assembler method is forwarded to the wrapped
 * child pattern when that child supports assembly, otherwise it degrades to a safe no-op.
 */
public final class AggregatePatternMarkerDetails
        implements IMolecularAssemblerSupportedPattern {
    private final AEItemKey definition;
    private final IPatternDetails delegate;
    private final boolean placeholder;

    public AggregatePatternMarkerDetails(AEItemKey definition, IPatternDetails delegate) {
        this(definition, delegate, false);
    }

    public AggregatePatternMarkerDetails(
            AEItemKey definition, IPatternDetails delegate, boolean placeholder) {
        this.definition = definition;
        this.delegate = delegate;
        this.placeholder = placeholder;
    }

    /**
     * True when the aggregate currently has no publishable children (e.g. everything was
     * deselected). The marker keeps the item valid inside pattern slots, but providers must
     * never publish the underlying stand-in pattern to the network.
     */
    public boolean isPlaceholder() {
        return placeholder;
    }

    /** The child pattern this marker stands in for. */
    public IPatternDetails delegate() {
        return delegate;
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
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
    public void pushInputsToExternalInventory(KeyCounter[] inputHolders, PatternInputSink sink) {
        delegate.pushInputsToExternalInventory(inputHolders, sink);
    }

    @Override
    public ItemStack assemble(Container input, Level level) {
        return delegate instanceof IMolecularAssemblerSupportedPattern assembler
                ? assembler.assemble(input, level)
                : ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
        return delegate instanceof IMolecularAssemblerSupportedPattern assembler
                ? assembler.getRemainingItems(input)
                : NonNullList.create();
    }

    @Override
    public boolean isItemValid(int slot, AEItemKey item, Level level) {
        return delegate instanceof IMolecularAssemblerSupportedPattern assembler
                && assembler.isItemValid(slot, item, level);
    }

    @Override
    public boolean isSlotEnabled(int slot) {
        return delegate instanceof IMolecularAssemblerSupportedPattern assembler
                && assembler.isSlotEnabled(slot);
    }

    @Override
    public void fillCraftingGrid(KeyCounter[] inputHolders, CraftingGridAccessor grid) {
        if (delegate instanceof IMolecularAssemblerSupportedPattern assembler) {
            assembler.fillCraftingGrid(inputHolders, grid);
        }
    }

}
