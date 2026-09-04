package io.github.langqi99.aeallpattern.aggregate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.RoutingPatternMetadata;
import java.util.Objects;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Keeps AE2's molecular-assembler behavior while giving an aggregate child a unique definition. */
public final class AggregateAssemblerPatternDetails
        implements IMolecularAssemblerSupportedPattern, RoutingPatternMetadata {
    private final String patternId;
    private final AEItemKey definition;
    private final IMolecularAssemblerSupportedPattern delegate;
    private final int processingTicks;

    public AggregateAssemblerPatternDetails(
            String patternId,
            AEItemKey definition,
            IMolecularAssemblerSupportedPattern delegate,
            int processingTicks) {
        this.patternId = Objects.requireNonNull(patternId, "patternId");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.processingTicks = Math.max(1, processingTicks);
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IPatternDetails.IInput[] getInputs() {
        return delegate.getInputs();
    }

    @Override
    public GenericStack[] getOutputs() {
        return delegate.getOutputs();
    }

    @Override
    public ItemStack assemble(Container input, Level level) {
        return delegate.assemble(input, level);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
        return delegate.getRemainingItems(input);
    }

    @Override
    public boolean isItemValid(int slot, AEItemKey item, Level level) {
        return delegate.isItemValid(slot, item, level);
    }

    @Override
    public boolean isSlotEnabled(int slot) {
        return delegate.isSlotEnabled(slot);
    }

    @Override
    public void fillCraftingGrid(KeyCounter[] inputHolders, CraftingGridAccessor grid) {
        delegate.fillCraftingGrid(inputHolders, grid);
    }

    @Override
    public boolean isAggregatePattern() {
        return true;
    }

    @Override
    public int processingTicks() {
        return processingTicks;
    }

    @Override
    public String stableRouteId() {
        return patternId;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AggregateAssemblerPatternDetails pattern
                && definition.equals(pattern.definition)
                && patternId.equals(pattern.patternId);
    }

    @Override
    public int hashCode() {
        return 31 * definition.hashCode() + patternId.hashCode();
    }
}
