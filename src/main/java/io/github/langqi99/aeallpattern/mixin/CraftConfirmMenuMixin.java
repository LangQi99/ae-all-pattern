package io.github.langqi99.aeallpattern.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.CraftingRoutePolicy;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.CraftingRoutePolicyContext;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.ByproductPlanWarnings;
import io.github.langqi99.aeallpattern.tianshu.CraftConfirmRoutingMenu;
import io.github.langqi99.aeallpattern.tianshu.TianshuRoutingPolicies;
import java.util.concurrent.Future;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class CraftConfirmMenuMixin implements CraftConfirmRoutingMenu {
    @Unique
    private static final String AEALLPATTERN_UPDATE_ROUTE_POLICY = "aeallpattern:updateRoutePolicy";

    @Shadow
    private AEKey whatToCraft;

    @Shadow
    private int amount;

    @Shadow
    private ICraftingPlan result;

    @Shadow
    public abstract boolean planJob(AEKey what, int amount, CalculationStrategy strategy);

    @Unique
    @GuiSync(40)
    public boolean aeallpattern$routingAvailable;

    @Unique
    @GuiSync(41)
    public int aeallpattern$aggregatePriority = -1;

    @Unique
    @GuiSync(42)
    public int aeallpattern$requireFeasible = 1;

    @Unique
    @GuiSync(43)
    public int aeallpattern$pathPreference;

    @Unique
    @GuiSync(44)
    public int aeallpattern$preferenceFlags = 90;

    @Unique
    @GuiSync(45)
    public int aeallpattern$preferenceOrder = CraftingRoutePolicy.DEFAULT_PREFERENCE_ORDER;

    @Unique
    @GuiSync(46)
    public GenericStack aeallpattern$byproductWarning;

    @Unique
    @GuiSync(47)
    public int aeallpattern$byproductWarningKinds;

    @Unique
    private boolean aeallpattern$policyInitialized;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void aeallpattern$registerRoutingAction(CallbackInfo ci) {
        ((AEBaseMenuAccessor) this).aeallpattern$registerClientAction(
                AEALLPATTERN_UPDATE_ROUTE_POLICY,
                String.class,
                serialized -> aeallpattern$applyPolicy(
                        CraftingRoutePolicy.deserialize(serialized), true));
    }

    @Inject(method = "planJob", at = @At("HEAD"))
    private void aeallpattern$loadNetworkDefault(
            AEKey what, int amount, CalculationStrategy strategy,
            CallbackInfoReturnable<Boolean> cir) {
        CraftConfirmMenu self = (CraftConfirmMenu) (Object) this;
        if (self.isClientSide()) {
            return;
        }
        aeallpattern$byproductWarning = null;
        aeallpattern$byproductWarningKinds = 0;
        IGrid grid = aeallpattern$getGrid(self);
        aeallpattern$routingAvailable = TianshuRoutingPolicies.isAvailable(grid);
        if (!aeallpattern$policyInitialized) {
            aeallpattern$policyInitialized = true;
            aeallpattern$setFields(TianshuRoutingPolicies.resolve(grid));
        }
    }

    @WrapOperation(
            method = "planJob",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingService;beginCraftingCalculation("
                            + "Lnet/minecraft/world/level/Level;"
                            + "Lappeng/api/networking/crafting/ICraftingSimulationRequester;"
                            + "Lappeng/api/stacks/AEKey;J"
                            + "Lappeng/api/networking/crafting/CalculationStrategy;)"
                            + "Ljava/util/concurrent/Future;"))
    private Future<ICraftingPlan> aeallpattern$planWithPolicy(
            ICraftingService service,
            net.minecraft.world.level.Level level,
            ICraftingSimulationRequester requester,
            AEKey what,
            long requestedAmount,
            CalculationStrategy strategy,
            Operation<Future<ICraftingPlan>> original) {
        if (!aeallpattern$routingAvailable) {
            return original.call(service, level, requester, what, requestedAmount, strategy);
        }
        return CraftingRoutePolicyContext.withPolicy(
                aeallpattern$getRoutePolicy(),
                () -> original.call(service, level, requester, what, requestedAmount, strategy));
    }

    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void aeallpattern$captureByproductWarning(CallbackInfo ci) {
        CraftConfirmMenu self = (CraftConfirmMenu) (Object) this;
        if (self.isClientSide()) {
            return;
        }
        java.util.List<GenericStack> extras = ByproductPlanWarnings.get(result);
        aeallpattern$byproductWarning = extras.isEmpty() ? null : extras.getFirst();
        aeallpattern$byproductWarningKinds = extras.size();
    }

    @Override
    public boolean aeallpattern$isRoutingAvailable() {
        return aeallpattern$routingAvailable;
    }

    @Override
    public CraftingRoutePolicy aeallpattern$getRoutePolicy() {
        return new CraftingRoutePolicy(
                aeallpattern$aggregatePriority,
                aeallpattern$requireFeasible != 0,
                aeallpattern$pathPreference,
                aeallpattern$unpackDirection(aeallpattern$preferenceFlags, 0),
                aeallpattern$unpackDirection(aeallpattern$preferenceFlags, 2),
                (aeallpattern$preferenceFlags & 16) != 0,
                aeallpattern$preferenceOrder,
                (aeallpattern$preferenceFlags & 32) != 0,
                (aeallpattern$preferenceFlags & 64) != 0);
    }

    @Override
    public void aeallpattern$updateRoutePolicy(CraftingRoutePolicy policy) {
        CraftingRoutePolicy normalized = aeallpattern$forceFeasible(policy);
        aeallpattern$setFields(normalized);
        CraftConfirmMenu self = (CraftConfirmMenu) (Object) this;
        self.setPlan(null);
        if (self.isClientSide()) {
            ((AEBaseMenuAccessor) this).aeallpattern$sendClientAction(
                    AEALLPATTERN_UPDATE_ROUTE_POLICY, normalized.serialize());
        } else {
            aeallpattern$replan();
        }
    }

    @Override
    public GenericStack aeallpattern$getByproductWarning() {
        return aeallpattern$byproductWarning;
    }

    @Override
    public int aeallpattern$getByproductWarningKinds() {
        return aeallpattern$byproductWarningKinds;
    }

    @Unique
    private void aeallpattern$applyPolicy(CraftingRoutePolicy policy, boolean replan) {
        aeallpattern$policyInitialized = true;
        aeallpattern$setFields(aeallpattern$forceFeasible(policy));
        ((CraftConfirmMenu) (Object) this).setPlan(null);
        if (replan) {
            aeallpattern$replan();
        }
    }

    @Unique
    private void aeallpattern$replan() {
        if (whatToCraft != null) {
            planJob(whatToCraft, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
        }
    }

    @Unique
    private void aeallpattern$setFields(CraftingRoutePolicy policy) {
        aeallpattern$aggregatePriority = policy.aggregatePriority();
        aeallpattern$requireFeasible = policy.requireFeasible() ? 1 : 0;
        aeallpattern$pathPreference = policy.pathPreference();
        aeallpattern$preferenceFlags = aeallpattern$packDirection(policy.stockSurplusPreference(), 0)
                | aeallpattern$packDirection(policy.yieldPreference(), 2)
                | (policy.preferFast() ? 16 : 0)
                | (policy.allowByproductOrders() ? 32 : 0)
                | (policy.allowAmplifyingCycles() ? 64 : 0);
        aeallpattern$preferenceOrder = policy.preferenceOrder();
    }

    @Unique
    private static int aeallpattern$packDirection(int direction, int shift) {
        return (Math.clamp(direction, -1, 1) + 1) << shift;
    }

    @Unique
    private static int aeallpattern$unpackDirection(int flags, int shift) {
        return ((flags >>> shift) & 3) - 1;
    }

    @Unique
    private static CraftingRoutePolicy aeallpattern$forceFeasible(CraftingRoutePolicy policy) {
        CraftingRoutePolicy value = policy == null ? CraftingRoutePolicy.DEFAULT : policy;
        return new CraftingRoutePolicy(
                value.aggregatePriority(), true, value.pathPreference(),
                value.stockSurplusPreference(), value.yieldPreference(), value.preferFast(),
                value.preferenceOrder(), value.allowByproductOrders(), value.allowAmplifyingCycles());
    }

    @Unique
    private static IGrid aeallpattern$getGrid(CraftConfirmMenu menu) {
        if (menu.getTarget() instanceof IActionHost host && host.getActionableNode() != null) {
            return host.getActionableNode().getGrid();
        }
        return null;
    }
}
