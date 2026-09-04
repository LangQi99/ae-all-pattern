package io.github.langqi99.aeallpattern.aggregate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.RoutingPatternMetadata;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.Level;

/** A single child processing pattern expanded from an aggregate pattern item. */
public final class AggregatePatternDetails implements IPatternDetails, RoutingPatternMetadata {
    private final String patternId;
    private final AEItemKey definition;
    private final IPatternDetails delegate;
    private final int processingTicks;
    private final IInput[] configuredInputs;

    public AggregatePatternDetails(
            String patternId,
            AEItemKey definition,
            IPatternDetails delegate,
            int processingTicks,
            List<AggregateInputSlot> configuredSlots) {
        this.patternId = Objects.requireNonNull(patternId, "patternId");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.processingTicks = Math.max(1, processingTicks);
        this.configuredInputs = configuredSlots == null || configuredSlots.isEmpty()
                ? null
                : configuredSlots.stream().map(AlternativeInput::new).toArray(IInput[]::new);
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    /**
     * The AE2 pattern this aggregate child was expanded into.
     *
     * <p>Addon tools that inspect patterns by concrete type (Advanced AE's pattern encoder only
     * understands {@code AEProcessingPattern} and {@code AdvProcessingPattern}) need to look
     * through the wrapper to recognise an aggregate child.</p>
     */
    public IPatternDetails delegate() {
        return delegate;
    }

    @Override
    public IInput[] getInputs() {
        return configuredInputs == null ? delegate.getInputs() : configuredInputs;
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
        if (configuredInputs == null) {
            delegate.pushInputsToExternalInventory(inputHolders, sink);
        } else {
            IPatternDetails.super.pushInputsToExternalInventory(inputHolders, sink);
        }
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
        return this == other || other instanceof AggregatePatternDetails pattern
                && definition.equals(pattern.definition)
                && patternId.equals(pattern.patternId);
    }

    @Override
    public int hashCode() {
        return 31 * definition.hashCode() + patternId.hashCode();
    }

    private record AlternativeInput(GenericStack[] possibleInputs, long multiplier) implements IInput {
            private AlternativeInput(AggregateInputSlot slot) {
                this(normalize(slot.alternatives()), slot.primary().amount());
            }

            @Override
            public GenericStack[] getPossibleInputs() {
                return possibleInputs;
            }

            @Override
            public long getMultiplier() {
                return multiplier;
            }

            @Override
            public boolean isValid(AEKey key, Level level) {
                for (GenericStack candidate : possibleInputs) {
                    if (key.matches(candidate)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public AEKey getRemainingKey(AEKey key) {
                return null;
            }

            private static GenericStack[] normalize(List<GenericStack> alternatives) {
                return alternatives.stream()
                        .map(stack -> new GenericStack(stack.what(), 1))
                        .toArray(GenericStack[]::new);
            }
        }
}
