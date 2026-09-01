package io.github.langqi99.aeallpattern.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternMarkerDetails;
import io.github.langqi99.aeallpattern.aggregate.AggregateProviderRefreshService;
import io.github.langqi99.aeallpattern.aggregate.AggregateProviderExpansion;
import io.github.langqi99.aeallpattern.util.Reflect;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes every aggregate child through AE2 Lightning Tech's pigmee pattern provider
 * (the "猪咪样板供应器"). The provider decodes each slot once via {@code
 * PatternDetailsHelper.decodePattern}, which only ever yields the aggregate's first child, so a
 * full aggregate looked like a single recipe.
 *
 * <p>Injection sits immediately before the host's own {@code requestUpdate} call so the complete
 * list is in place before the network is told to re-index. Members are resolved reflectively for
 * the same reason as {@link MatrixPatternStorageBlockEntityMixin}: a hard-coded {@code @Shadow}
 * type mismatch crashes startup on ae2lt builds that changed their field types.</p>
 */
@Mixin(targets = "com.moakiee.ae2lt.blockentity.PigmeePatternProviderBlockEntity", remap = false)
public abstract class PigmeePatternProviderBlockEntityMixin {
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
            BlockEntity self = (BlockEntity) (Object) this;
            Level level = self.getLevel();
            if (level == null) {
                return;
            }
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                AggregateProviderRefreshService.track(
                        serverLevel.getServer(), this,
                        owner -> ((PigmeePatternProviderBlockEntityMixin) owner)
                                .aeallpattern$rerunUpdatePatterns());
            }
            List<IPatternDetails> patterns = Reflect.field(self, "patterns", List.class);
            InternalInventory inventory = Reflect.field(self, "patternInventory", InternalInventory.class);
            if (patterns == null || inventory == null) {
                return;
            }
            // Drop the single-child marker the host just decoded; the full expansion replaces it.
            patterns.removeIf(AggregatePatternMarkerDetails.class::isInstance);
            Runnable rerun = aeallpattern$rerunning ? null : this::aeallpattern$rerunUpdatePatterns;
            if (AggregateProviderExpansion.expandSlots(inventory, level, rerun, patterns::add)) {
                AeAllPattern.LOGGER.debug("Pigmee pattern provider: scheduled aggregate expansion pending");
            }
        } catch (RuntimeException error) {
            AeAllPattern.LOGGER.debug("Could not expand aggregate patterns for a pigmee provider", error);
        }
    }

    /** Re-runs the host refresh so the completed scheduled expansion gets published. */
    @Unique
    private void aeallpattern$rerunUpdatePatterns() {
        aeallpattern$rerunning = true;
        try {
            java.lang.reflect.Method method = ((Object) this).getClass().getDeclaredMethod("updatePatterns");
            method.setAccessible(true);
            method.invoke(this);
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug("Could not re-run pigmee pattern provider updatePatterns", error);
        } finally {
            aeallpattern$rerunning = false;
        }
    }
}
