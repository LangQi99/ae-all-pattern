package io.github.langqi99.aeallpattern.mixin;


import appeng.api.crafting.IPatternDetails;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.util.inv.AppEngInternalInventory;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternExpander;
import io.github.langqi99.aeallpattern.aggregate.AggregateProviderRefreshService;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity", remap = false)
public abstract class ECOCraftingPatternBusBlockEntityMixin {
    @Shadow @Final private AppEngInternalInventory inventory;
    @Shadow @Final private List<IPatternDetails> patternDetails;

    @Inject(method = "isExecutablePattern", at = @At("HEAD"), cancellable = true, require = 0)
    private void allowAggregate(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ModDataComponents.hasAggregatePattern(stack)) cir.setReturnValue(true);
    }

    @Inject(method = "updatePatternDetails", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;requestUpdate(Lappeng/api/networking/IManagedGridNode;)V",
            shift = At.Shift.BEFORE))
    private void addAggregatePatterns(CallbackInfo ci) {
        var level = ((BlockEntity) (Object) this).getLevel();
        if (level == null) return;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            AggregateProviderRefreshService.track(
                    serverLevel.getServer(), this,
                    owner -> ((ECOCraftingPatternBusBlockEntityMixin) owner)
                            .aeallpattern$rerunUpdatePatternDetails());
        }
        boolean cold = false;
        for (var stack : inventory) {
            var expanded = AggregatePatternExpander.expandScheduled(
                    stack, level, this::aeallpattern$rerunUpdatePatternDetails);
            if (expanded.isEmpty()) {
                cold = true;
            }
            for (var pattern : expanded) {
                if (pattern instanceof IMolecularAssemblerSupportedPattern) {
                    patternDetails.add(pattern);
                }
            }
        }
        if (cold) {
            AeAllPattern.LOGGER.debug("ECO pattern bus: scheduled aggregate expansion pending");
        }
    }

    /** Re-runs the host refresh once the scheduled aggregate expansion completed. */
    @org.spongepowered.asm.mixin.Unique
    private void aeallpattern$rerunUpdatePatternDetails() {
        try {
            java.lang.reflect.Method method = ((Object) this).getClass()
                    .getDeclaredMethod("updatePatternDetails");
            method.setAccessible(true);
            method.invoke(this);
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug("Could not re-run ECO pattern bus updatePatternDetails", error);
        }
    }
}
