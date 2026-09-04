package io.github.langqi99.aeallpattern.mixin;


import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregateProviderExpansion;
import io.github.langqi99.aeallpattern.util.Reflect;
import java.util.List;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds aggregate children to ExtendedAE Plus super assembler matrices. */
@Pseudo
@Mixin(targets = "com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCluster", remap = false)
public abstract class ExtendedAePlusSuperAssemblerMatrixMixin {
    @org.spongepowered.asm.mixin.Unique
    private final Runnable aeallpattern$refresh = this::aeallpattern$refreshCraftingProvider;

    @Inject(method = "getAvailablePatterns", at = @At("RETURN"), cancellable = true, remap = false)
    private void aeallpattern$expandAggregatePatterns(
            CallbackInfoReturnable<List<IPatternDetails>> callback) {
        try {
            Object core = Reflect.field(this, "core");
            if (core == null) {
                return;
            }
            Level level = (Level) Reflect.invoke(core, "getLevel");
            @SuppressWarnings("unchecked")
            List<Object> sources = (List<Object>) Reflect.invoke(this, "getPatternInventorySources");
            List<IPatternDetails> patterns = new java.util.ArrayList<>(callback.getReturnValue());
            for (Object source : sources) {
                InternalInventory inventory = (InternalInventory) Reflect.invoke(source, "inventory");
                AggregateProviderExpansion.expandSlots(inventory, level, aeallpattern$refresh, pattern -> {
                    if (pattern instanceof IMolecularAssemblerSupportedPattern) {
                        patterns.add(pattern);
                    }
                });
            }
            callback.setReturnValue(List.copyOf(patterns));
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug("Could not expand super assembler-matrix aggregate patterns", error);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private void aeallpattern$refreshCraftingProvider() {
        try {
            Reflect.invoke(this, "refreshCraftingProvider");
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug("Could not refresh super assembler matrix", error);
        }
    }
}
