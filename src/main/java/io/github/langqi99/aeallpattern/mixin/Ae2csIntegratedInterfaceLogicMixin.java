package io.github.langqi99.aeallpattern.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEKey;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternMarkerDetails;
import io.github.langqi99.aeallpattern.aggregate.AggregateProviderExpansion;
import io.github.langqi99.aeallpattern.aggregate.AggregateProviderRefreshService;
import io.github.langqi99.aeallpattern.util.Reflect;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publishes every aggregate child through AE2CS integrated interfaces. */
@Mixin(targets = "io.github.lounode.ae2cs.common.me.logic.IntegratedInterfaceLogic", remap = false)
public abstract class Ae2csIntegratedInterfaceLogicMixin {
    @Unique private boolean aeallpattern$rerunning;

    @Inject(method = "updatePatterns", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;requestUpdate(Lappeng/api/networking/IManagedGridNode;)V",
            shift = At.Shift.BEFORE))
    @SuppressWarnings("unchecked")
    private void aeallpattern$expandAggregatePatterns(CallbackInfo callback) {
        Object host = Reflect.field(this, "host");
        if (host == null) return;
        try {
            Object rawEntity = Reflect.invoke(host, "getBlockEntity");
            if (!(rawEntity instanceof BlockEntity entity)) return;
            Level level = entity.getLevel();
            if (level == null) return;
            if (level instanceof ServerLevel serverLevel) {
                AggregateProviderRefreshService.track(serverLevel.getServer(), this,
                        owner -> ((Ae2csIntegratedInterfaceLogicMixin) owner).aeallpattern$rerunUpdatePatterns());
            }

            List<IPatternDetails> patterns = Reflect.field(this, "patterns", List.class);
            Set<AEKey> patternInputs = Reflect.field(this, "patternInputs", Set.class);
            InternalInventory inventory = Reflect.field(this, "patternInventory", InternalInventory.class);
            if (patterns == null || patternInputs == null || inventory == null) return;
            patterns.removeIf(AggregatePatternMarkerDetails.class::isInstance);
            Runnable rerun = aeallpattern$rerunning ? null : this::aeallpattern$rerunUpdatePatterns;
            AggregateProviderExpansion.expandSlots(inventory, level, rerun, pattern -> {
                patterns.add(pattern);
                for (IPatternDetails.IInput input : pattern.getInputs()) {
                    for (var possible : input.getPossibleInputs()) {
                        patternInputs.add(possible.what().dropSecondary());
                    }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    @Unique
    private void aeallpattern$rerunUpdatePatterns() {
        aeallpattern$rerunning = true;
        try {
            Reflect.invoke(this, "updatePatterns");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        } finally {
            aeallpattern$rerunning = false;
        }
    }
}
