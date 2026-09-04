package io.github.langqi99.aeallpattern.mixin;


import io.github.langqi99.aeallpattern.machine.PackagedAutoAggregateCompat;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "thelm.packagedauto.integration.appeng.blockentity.AEPackagingProviderBlockEntity", remap = false)
public abstract class PackagedAutoPackagingProviderBlockEntityMixin {
    @Inject(method = "getAvailablePatterns", at = @At("RETURN"), cancellable = true)
    private void aeallpattern$usePackageWorkflow(CallbackInfoReturnable<List<?>> callback) {
        callback.setReturnValue(PackagedAutoAggregateCompat.usePackageWorkflow(this, callback.getReturnValue()));
    }
}
