package io.github.langqi99.aeallpattern.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternMarkerDetails;
import io.github.langqi99.aeallpattern.aggregate.AggregateProviderExpansion;
import io.github.langqi99.aeallpattern.aggregate.AggregateProviderRefreshService;
import io.github.langqi99.aeallpattern.util.Reflect;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes every aggregate child through "闪电科技：封包"'s packaged / wireless packaged pattern
 * providers.
 *
 * <p>{@code StablePatternProviderLogic} extends AE2's own {@code PatternProviderLogic} but
 * overrides {@code updatePatterns} without calling {@code super}, so {@link
 * PatternProviderLogicMixin} never runs for it and the provider saw only the first child of an
 * aggregate. The override mirrors AE2's own bookkeeping, so the watched inputs are kept up to date
 * alongside the pattern list.</p>
 */
@Mixin(targets = "com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderLogic", remap = false)
public abstract class StablePatternProviderLogicMixin {
    @Unique private boolean aeallpattern$rerunning;

    @Inject(method = "updatePatterns",
            at = @At(value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingProvider;"
                            + "requestUpdate(Lappeng/api/networking/IManagedGridNode;)V",
                    shift = At.Shift.BEFORE),
            remap = false)
    @SuppressWarnings("unchecked")
    private void aeallpattern$expandAggregatePatterns(CallbackInfo callback) {
        try {
            Object self = this;
            Level level = level();
            if (level == null) {
                return;
            }
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                AggregateProviderRefreshService.track(
                        serverLevel.getServer(), this,
                        owner -> ((StablePatternProviderLogicMixin) owner).aeallpattern$rerunUpdatePatterns());
            }
            List<IPatternDetails> patterns = Reflect.field(self, "patterns", List.class);
            InternalInventory inventory = Reflect.field(self, "patternInventory", InternalInventory.class);
            if (patterns == null || inventory == null) {
                return;
            }
            // Drop the single-child marker the host just decoded; the full expansion replaces it.
            patterns.removeIf(AggregatePatternMarkerDetails.class::isInstance);

            Set<?> patternInputs = Reflect.field(self, "patternInputs", Set.class);
            Runnable rerun = aeallpattern$rerunning ? null : this::aeallpattern$rerunUpdatePatterns;
            boolean pending = AggregateProviderExpansion.expandSlots(
                    inventory, level, rerun, pattern -> publish(pattern, patterns, patternInputs));
            if (pending) {
                AeAllPattern.LOGGER.debug("Packaged pattern provider: scheduled aggregate expansion pending");
            }
        } catch (RuntimeException error) {
            AeAllPattern.LOGGER.debug("Could not expand aggregate patterns for a packaged provider", error);
        }
    }

    @SuppressWarnings("unchecked")
    @Unique
    private void publish(IPatternDetails pattern, List<IPatternDetails> patterns, Set<?> patternInputs) {
        patterns.add(pattern);
        if (patternInputs != null) {
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                for (appeng.api.stacks.GenericStack possible : input.getPossibleInputs()) {
                    ((Set<appeng.api.stacks.AEKey>) patternInputs).add(possible.what().dropSecondary());
                }
            }
        }
    }

    /** The logic reaches the level through its block entity host. */
    @Unique
    private Level level() {
        Object host = Reflect.field(this, "providerHost");
        return host instanceof BlockEntity entity ? entity.getLevel() : null;
    }

    /** Re-runs the host refresh so the completed scheduled expansion gets published. */
    @Unique
    private void aeallpattern$rerunUpdatePatterns() {
        aeallpattern$rerunning = true;
        try {
            java.lang.reflect.Method method = ((Object) this).getClass().getMethod("updatePatterns");
            method.setAccessible(true);
            method.invoke(this);
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug("Could not re-run packaged provider updatePatterns", error);
        } finally {
            aeallpattern$rerunning = false;
        }
    }
}
