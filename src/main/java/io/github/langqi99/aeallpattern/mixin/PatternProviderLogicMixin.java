package io.github.langqi99.aeallpattern.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternExpander;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternMarkerDetails;
import io.github.langqi99.aeallpattern.aggregate.AggregateProviderRefreshService;
import io.github.langqi99.aeallpattern.compat.TechStartPatternCompat;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Expands one aggregate item into every child recipe published by the provider. */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicMixin {
    @Shadow
    @Final
    private PatternProviderLogicHost host;

    @Shadow
    @Final
    private AppEngInternalInventory patternInventory;

    @Shadow
    @Final
    private List<IPatternDetails> patterns;

    @Shadow
    @Final
    private Set<AEKey> patternInputs;

    @Shadow @Final private IManagedGridNode mainNode;

    @Invoker("updatePatterns")
    public abstract void aeallpattern$rerunUpdatePatterns();

    @org.spongepowered.asm.mixin.Unique
    private List<IPatternDetails> aeallpattern$lastPublished = List.of();

    @Inject(method = "updatePatterns", at = @At("HEAD"), cancellable = true)
    private void aeallpattern$expandAggregatePatterns(CallbackInfo callback) {
        var blockEntity = host.getBlockEntity();
        if (blockEntity.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            AggregateProviderRefreshService.track(
                    serverLevel.getServer(), this,
                    owner -> ((PatternProviderLogicMixin) owner).aeallpattern$rerunUpdatePatterns());
        }
        patterns.clear();
        patternInputs.clear();
        patterns.removeIf(AggregatePatternMarkerDetails.class::isInstance);
        var level = blockEntity.getLevel();
        if (level != null) {
            for (var stack : patternInventory) {
                var expanded = TechStartPatternCompat.expand(stack, level);
                if (expanded.isEmpty()) {
                    // Cold expansions of large aggregates are spread across ticks with a tiny
                    // budget; the callback re-runs this refresh once the complete list is ready.
                    expanded = AggregatePatternExpander.expandScheduled(
                            stack, level, this::aeallpattern$rerunUpdatePatterns);
                }
                if (expanded.isEmpty()) {
                    var decoded = PatternDetailsHelper.decodePattern(stack, level);
                    // A fully deselected aggregate decodes to a placeholder marker: it keeps the
                    // slot valid but must never reach the network as a real pattern.
                    if (decoded instanceof AggregatePatternMarkerDetails marker && marker.isPlaceholder()) {
                        continue;
                    }
                    if (decoded != null) patterns.add(decoded);
                } else {
                    patterns.addAll(expanded);
                }
            }
        }

        patternInputs.clear();
        for (IPatternDetails pattern : patterns) {
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                for (var possibleInput : input.getPossibleInputs()) {
                    patternInputs.add(possibleInput.what().dropSecondary());
                }
            }
        }
        // Skip the network refresh when nothing actually changed: with an 18k-pattern aggregate
        // every unconditional requestUpdate forces AE2 to rebuild its whole crafting index on
        // the next terminal open, which is the reported lag. Only publish on real changes.
        if (!aeallpattern$lastPublished.equals(patterns)) {
            aeallpattern$lastPublished = List.copyOf(patterns);
            ICraftingProvider.requestUpdate(mainNode);
        }
        callback.cancel();
    }
}
