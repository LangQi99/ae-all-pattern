package io.github.langqi99.aeallpattern.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.util.inv.AppEngInternalInventory;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternExpander;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternMarkerDetails;
import io.github.langqi99.aeallpattern.aggregate.AggregateProviderRefreshService;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes aggregate children through AE2 Lightning Tech's overloaded provider catalog.
 *
 * <p>The overloaded provider overrides AE2's normal pattern refresh method, so the generic
 * {@link PatternProviderLogicMixin} hook is intentionally bypassed. Expanding at the catalog
 * boundary also lets every virtual child retain the physical aggregate slot it came from.
 *
 * <p>Large aggregates go through the scheduled path so a rebuild never blocks the server
 * thread on a full 18k-pattern expansion; the completion callback re-runs the rebuild once
 * the whole list is ready.</p>
 */
@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.logic.OverloadedProviderPatternCatalog", remap = false)
public abstract class OverloadedProviderPatternCatalogMixin {
    @Shadow
    abstract void register(IPatternDetails pattern, int slot);

    @Invoker("rebuild")
    abstract void aeallpattern$invokeRebuild(
            AppEngInternalInventory patternInventory,
            Level level,
            List<IPatternDetails> patterns,
            Set<AEKey> patternInputs);

    @org.spongepowered.asm.mixin.Unique
    private AppEngInternalInventory aeallpattern$lastInventory;
    @org.spongepowered.asm.mixin.Unique
    private Level aeallpattern$lastLevel;
    @org.spongepowered.asm.mixin.Unique
    private List<IPatternDetails> aeallpattern$lastPatterns;
    @org.spongepowered.asm.mixin.Unique
    private Set<AEKey> aeallpattern$lastInputs;

    @Inject(method = "rebuild", at = @At("TAIL"))
    private void aeallpattern$expandAggregatePatterns(
            AppEngInternalInventory patternInventory,
            Level level,
            List<IPatternDetails> patterns,
            Set<AEKey> patternInputs,
            CallbackInfo callback) {
        aeallpattern$lastInventory = patternInventory;
        aeallpattern$lastLevel = level;
        aeallpattern$lastPatterns = patterns;
        aeallpattern$lastInputs = patternInputs;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            AggregateProviderRefreshService.track(
                    serverLevel.getServer(), this,
                    owner -> ((OverloadedProviderPatternCatalogMixin) owner).aeallpattern$rerunRebuild());
        }
        patterns.removeIf(AggregatePatternMarkerDetails.class::isInstance);

        boolean cold = false;
        for (int slot = 0; slot < patternInventory.size(); slot++) {
            var expanded = AggregatePatternExpander.expandScheduled(
                    patternInventory.getStackInSlot(slot), level, this::aeallpattern$rerunRebuild);
            if (expanded.isEmpty()) {
                cold = true;
            }
            for (IPatternDetails pattern : expanded) {
                patterns.add(pattern);
                register(pattern, slot);

                for (IPatternDetails.IInput input : pattern.getInputs()) {
                    for (var possibleInput : input.getPossibleInputs()) {
                        patternInputs.add(possibleInput.what().dropSecondary());
                    }
                }
            }
        }
        if (cold) {
            AeAllPattern.LOGGER.debug("Overloaded catalog: scheduled aggregate expansion pending");
        }
    }

    /** Re-runs the catalog rebuild once the scheduled aggregate expansion completed. */
    @org.spongepowered.asm.mixin.Unique
    private void aeallpattern$rerunRebuild() {
        aeallpattern$invokeRebuild(
                aeallpattern$lastInventory, aeallpattern$lastLevel,
                aeallpattern$lastPatterns, aeallpattern$lastInputs);
    }
}
