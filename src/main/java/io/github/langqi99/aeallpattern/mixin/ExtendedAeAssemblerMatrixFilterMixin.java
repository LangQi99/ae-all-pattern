package io.github.langqi99.aeallpattern.mixin;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the assembler-matrix GUI accept aggregate encoded patterns. */
@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern$Filter",
        remap = false)
public abstract class ExtendedAeAssemblerMatrixFilterMixin {
    @Shadow public abstract Supplier<Level> world();

    @Inject(method = "allowInsert", at = @At("HEAD"), cancellable = true, remap = false)
    private void aeallpattern$allowAggregate(InternalInventory inventory, int slot, ItemStack stack,
                                             CallbackInfoReturnable<Boolean> callback) {
        if (stack.has(ModDataComponents.AGGREGATE_PATTERN.get())) {
            callback.setReturnValue(PatternDetailsHelper.decodePattern(stack, world().get())
                    instanceof IMolecularAssemblerSupportedPattern);
        }
    }
}
