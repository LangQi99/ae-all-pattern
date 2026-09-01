package io.github.langqi99.aeallpattern.mixin;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.CraftingRoutePolicy;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.FastCraftingPlanner;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.FastPlanningWatchdog;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.TianshuFastCraftingControl;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.TianshuFastPlanningPolicy;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Runs the private 1.0.6-derived planner only for calculations claimed by a Tianshu router.
 * With no policy this mixin is inert, leaving AE2 and any external planner mod untouched.
 */
@Mixin(value = CraftingCalculation.class, remap = false, priority = 2000)
public abstract class TianshuCraftingCalculationMixin implements TianshuFastCraftingControl {
    @Shadow
    private NetworkCraftingSimulationState networkInv;

    @Shadow
    private AEKey output;

    @Shadow
    private ICraftingSimulationRequester simRequester;

    @Shadow
    private boolean simulate;

    @Shadow
    private long requestedAmount;

    @Shadow
    abstract Level getLevel();

    @Unique
    @Nullable
    private CraftingRoutePolicy aeallpattern$routePolicy;

    @Unique
    @Nullable
    private CraftingPlan aeallpattern$cachedSimulationPlan;

    @Override
    public void aeallpattern$setRoutePolicy(@Nullable CraftingRoutePolicy policy) {
        this.aeallpattern$routePolicy = policy;
    }

    @Override
    @Nullable
    public CraftingRoutePolicy aeallpattern$getRoutePolicy() {
        return aeallpattern$routePolicy;
    }

    @Inject(method = "run", at = @At("HEAD"))
    private void aeallpattern$beginRouterCalculation(CallbackInfoReturnable<ICraftingPlan> cir) {
        aeallpattern$cachedSimulationPlan = null;
    }

    @Inject(method = "run", at = @At("RETURN"))
    private void aeallpattern$finishRouterCalculation(CallbackInfoReturnable<ICraftingPlan> cir) {
        aeallpattern$cachedSimulationPlan = null;
    }

    @Inject(method = "runCraftAttempt", at = @At("HEAD"), cancellable = true)
    private void aeallpattern$routeAttempt(
            boolean simulation,
            long amount,
            CallbackInfoReturnable<CraftingPlan> cir) {
        CraftingRoutePolicy policy = aeallpattern$routePolicy;
        if (policy == null || !TianshuFastPlanningPolicy.supportsOutput(output)) {
            return;
        }
        if (simulation && amount == requestedAmount && aeallpattern$cachedSimulationPlan != null) {
            this.simulate = true;
            cir.setReturnValue(aeallpattern$cachedSimulationPlan);
            return;
        }
        var node = simRequester.getGridNode();
        if (node == null) {
            return;
        }

        FastPlanningWatchdog.start(
                "router output=" + output + " requested=" + amount + " simulation=" + simulation);
        try {
            var attempt = FastCraftingPlanner.tryAttempt(
                    node.getGrid().getCraftingService(),
                    networkInv,
                    getLevel(),
                    output,
                    amount,
                    simulation,
                    null,
                    policy);
            if (!attempt.handled()) {
                return;
            }
            this.simulate = simulation;
            if (!simulation && amount == requestedAmount && attempt.simulationFallback() != null) {
                aeallpattern$cachedSimulationPlan = attempt.simulationFallback();
            }
            cir.setReturnValue(attempt.plan());
        } catch (Throwable throwable) {
            AeAllPattern.LOGGER.warn(
                    "Tianshu private planner failed; falling back to AE2: output={} amount={} simulation={}",
                    output,
                    amount,
                    simulation,
                    throwable);
        } finally {
            FastPlanningWatchdog.stop();
        }
    }

}
