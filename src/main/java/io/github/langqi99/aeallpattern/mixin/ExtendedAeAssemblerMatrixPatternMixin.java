package io.github.langqi99.aeallpattern.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternMarkerDetails;
import io.github.langqi99.aeallpattern.aggregate.AggregateProviderExpansion;
import io.github.langqi99.aeallpattern.util.Reflect;
import java.util.List;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Expands aggregate patterns stored in ExtendedAE assembler-matrix pattern cores. */
@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern",
        remap = false)
public abstract class ExtendedAeAssemblerMatrixPatternMixin {
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
            Level level = (Level) Reflect.invoke(this, "getLevel");
            List<IPatternDetails> patterns = Reflect.field(this, "patterns", List.class);
            InternalInventory inventory = Reflect.field(this, "patternInventory", InternalInventory.class);
            if (level == null || patterns == null || inventory == null) {
                return;
            }
            patterns.removeIf(AggregatePatternMarkerDetails.class::isInstance);
            Runnable rerun = aeallpattern$rerunning ? null : this::aeallpattern$rerunUpdatePatterns;
            AggregateProviderExpansion.expandSlots(inventory, level, rerun, pattern -> {
                if (pattern instanceof IMolecularAssemblerSupportedPattern) {
                    patterns.add(pattern);
                }
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug("Could not expand assembler-matrix aggregate patterns", error);
        }
    }

    @Unique
    private void aeallpattern$rerunUpdatePatterns() {
        aeallpattern$rerunning = true;
        try {
            Reflect.invoke(this, "updatePatterns");
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug("Could not refresh assembler-matrix patterns", error);
        } finally {
            aeallpattern$rerunning = false;
        }
    }
}
