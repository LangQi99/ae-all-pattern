package io.github.langqi99.aeallpattern.mixin;


import io.github.langqi99.aeallpattern.machine.PackagedAutoAggregateCompat;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "thelm.packagedauto.inventory.PackagingProviderItemHandler", remap = false)
public abstract class PackagedAutoPackagingProviderItemHandlerMixin {
    @Inject(method = "isItemValid", at = @At("RETURN"), cancellable = true)
    private void aeallpattern$acceptAggregatePattern(
            int slot, ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue() && PackagedAutoAggregateCompat.isAggregatePattern(stack)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "updateRecipeList", at = @At("RETURN"))
    private void aeallpattern$expandAggregateRecipes(CallbackInfo callback) {
        PackagedAutoAggregateCompat.refreshRecipeList(this);
    }
}
