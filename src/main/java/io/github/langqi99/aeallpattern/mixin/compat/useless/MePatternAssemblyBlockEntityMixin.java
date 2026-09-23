package io.github.langqi99.aeallpattern.mixin.compat.useless;

import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Permit the aggregate container; the host still validates each expanded recipe. */
@Pseudo
@Mixin(targets = "com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity", remap = false)
public abstract class MePatternAssemblyBlockEntityMixin {
    @Inject(method = "isAcceptedPattern", at = @At("HEAD"), cancellable = true)
    private static void aeallpattern$allowAggregate(ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        if (stack.is(ModItems.AGGREGATE_PATTERN.get()) && stack.has(ModDataComponents.AGGREGATE_PATTERN.get())) {
            ci.setReturnValue(true);
        }
    }
}
