package io.github.langqi99.aeallpattern.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.util.inv.AppEngInternalInventory;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternExpander;
import io.github.langqi99.aeallpattern.aggregate.AggregateProviderRefreshService;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ECO 21.2 beta3 moved the pattern inventory logic into a separate catalog. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusCatalog", remap = false)
public abstract class ECOCraftingPatternBusCatalogMixin {
    @Shadow @Final private AppEngInternalInventory inventory;
    @Shadow @Final private List<IPatternDetails> patternDetails;
    @Invoker("requestPatternDetailsRefresh")
    public abstract void aeallpattern$requestRefresh();

    @Unique
    private BlockEntity aeallpattern$host() {
        try {
            var field = ((Object) this).getClass().getDeclaredField("host");
            field.setAccessible(true);
            return (BlockEntity) field.get(this);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("ECO catalog host changed", e); }
    }

    @Inject(method = "allowInsert", at = @At("HEAD"), cancellable = true)
    private void aeallpattern$allowAggregate(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        if (!ModDataComponents.hasAggregatePattern(stack)) return;
        var host = aeallpattern$host();
        try {
            int count = (int) host.getClass().getMethod("getPatternSlotCount").invoke(host);
            ci.setReturnValue(slot >= 0 && slot < count);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    @Inject(method = "updatePatternDetails", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;requestUpdate(Lappeng/api/networking/IManagedGridNode;)V"))
    private void aeallpattern$expand(CallbackInfo ci) {
        var host = aeallpattern$host();
        if (!(host.getLevel() instanceof ServerLevel level)) return;
        AggregateProviderRefreshService.track(level.getServer(), this,
                owner -> ((ECOCraftingPatternBusCatalogMixin) owner).aeallpattern$requestRefresh());
        patternDetails.removeIf(io.github.langqi99.aeallpattern.aggregate.AggregatePatternMarkerDetails.class::isInstance);
        for (var stack : inventory) {
            for (var pattern : AggregatePatternExpander.expandScheduled(stack, level, this::aeallpattern$requestRefresh)) {
                if (pattern instanceof IMolecularAssemblerSupportedPattern) patternDetails.add(pattern);
            }
        }
    }

    @Inject(method = "updatePatternDetails", at = @At("HEAD"), cancellable = true)
    private void aeallpattern$waitForLiveNode(CallbackInfo ci) {
        var host = aeallpattern$host();
        // A queued cold expansion can outlive its chunk's node. ECO's own validation
        // dereferences getGridNode(); onReady will rebuild after the node is recreated.
        try {
            var node = (appeng.api.networking.IManagedGridNode) host.getClass().getMethod("getMainNode").invoke(host);
            if (host.isRemoved() || node.getNode() == null) ci.cancel();
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
