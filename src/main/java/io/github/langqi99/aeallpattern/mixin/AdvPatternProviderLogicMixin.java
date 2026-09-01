package io.github.langqi99.aeallpattern.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IStackWatcher;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
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
 * Publishes every aggregate child through Advanced AE's pattern providers.
 *
 * <p>Both the "ME高级样板供应器" ({@code SmallAdvPatternProviderEntity}) and the
 * "ME高级扩展样板供应器" ({@code AdvPatternProviderEntity}, which the small one extends) drive
 * their pattern list through the shared {@code AdvPatternProviderLogic}, so targeting that single
 * class fixes both. Advanced AE copied AE2's provider logic instead of reusing it, which is why
 * {@link PatternProviderLogicMixin} never applied to them.</p>
 *
 * <p>The logic tracks more than the pattern list: a crafting watcher, an output cache and a set of
 * watched inputs keep AE2's "lock crafting until the output arrives" feature working. Those are
 * optional here — if a future release renames them, the aggregate children are still published and
 * only the auto-unlock bookkeeping degrades.</p>
 */
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic", remap = false)
public abstract class AdvPatternProviderLogicMixin {
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
                        owner -> ((AdvPatternProviderLogicMixin) owner).aeallpattern$rerunUpdatePatterns());
            }
            List<IPatternDetails> patterns = Reflect.field(self, "patterns", List.class);
            InternalInventory inventory = Reflect.field(self, "patternInventory", InternalInventory.class);
            if (patterns == null || inventory == null) {
                return;
            }
            // Drop the single-child marker the host just decoded; the full expansion replaces it.
            patterns.removeIf(AggregatePatternMarkerDetails.class::isInstance);

            IStackWatcher watcher = Reflect.field(self, "craftingWatcher", IStackWatcher.class);
            Set<AEKey> patternInputs = Reflect.field(self, "patternInputs", Set.class);
            Set<AEKey> outputCache = Reflect.field(self, "outputCache", Set.class);

            Runnable rerun = aeallpattern$rerunning ? null : this::aeallpattern$rerunUpdatePatterns;
            boolean pending = AggregateProviderExpansion.expandSlots(
                    inventory, level, rerun, pattern -> publish(pattern, patterns, watcher, patternInputs, outputCache));
            if (pending) {
                AeAllPattern.LOGGER.debug("Advanced AE pattern provider: scheduled aggregate expansion pending");
            }
        } catch (RuntimeException error) {
            AeAllPattern.LOGGER.debug("Could not expand aggregate patterns for an Advanced AE provider", error);
        }
    }

    /** Mirrors what the host does for every pattern it decodes itself. */
    @Unique
    private void publish(IPatternDetails pattern,
                         List<IPatternDetails> patterns,
                         IStackWatcher watcher,
                         Set<AEKey> patternInputs,
                         Set<AEKey> outputCache) {
        patterns.add(pattern);
        for (GenericStack output : pattern.getOutputs()) {
            AEKey key = output.what();
            if (watcher != null) {
                watcher.add(key);
            }
            if (outputCache != null) {
                outputCache.add(key);
            }
        }
        if (patternInputs != null) {
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                for (GenericStack possible : input.getPossibleInputs()) {
                    patternInputs.add(possible.what().dropSecondary());
                }
            }
        }
    }

    /** The logic reaches the level through its host block entity. */
    @Unique
    private Level level() {
        try {
            Object host = Reflect.field(this, "host");
            if (host == null) {
                return null;
            }
            Object blockEntity = Reflect.invoke(host, "getBlockEntity");
            return blockEntity instanceof BlockEntity entity ? entity.getLevel() : null;
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug("Could not resolve the level of an Advanced AE provider", error);
            return null;
        }
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
            AeAllPattern.LOGGER.debug("Could not re-run Advanced AE updatePatterns", error);
        } finally {
            aeallpattern$rerunning = false;
        }
    }
}
