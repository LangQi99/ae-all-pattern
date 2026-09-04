package io.github.langqi99.aeallpattern.mixin;


import appeng.api.crafting.IPatternDetails;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternExpander;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternMarkerDetails;
import io.github.langqi99.aeallpattern.aggregate.AggregateProviderRefreshService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes every aggregate child through AE2 Lightning Tech's matter warping matrix pattern
 * storage (the "天枢物质扭曲矩阵" multiblock). The storage only accepts and caches patterns that
 * support the molecular assembler, so the expansion here mirrors {@code
 * ECOCraftingPatternBusBlockEntityMixin}: expand each aggregate slot into its assembler
 * children instead of the single decode marker the host would otherwise cache.
 *
 * <p>Field/method names are resolved reflectively because the target class is compiled against
 * a specific ae2lt version; a hard-coded {@code @Shadow} of the wrong inventory type made the
 * mixin fail to apply and crashed the game at startup (MixinApplyError).</p>
 *
 * <p>Large aggregates are expanded through the scheduled path so opening the matrix terminal
 * never stalls the server thread on a full 18k-pattern expansion; the completion callback
 * re-runs {@code rebuildPatternCache} once the whole list is ready.</p>
 */
@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.blockentity.MatrixPatternStorageBlockEntity", remap = false)
public abstract class MatrixPatternStorageBlockEntityMixin {
    @Inject(method = "rebuildPatternCache", at = @At("TAIL"), remap = false)
    private void aeallpattern$expandAggregatePatterns(CallbackInfo callback) {
        try {
            BlockEntity self = (BlockEntity) (Object) this;
            Level level = self.getLevel();
            if (level == null) {
                return;
            }
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                AggregateProviderRefreshService.track(
                        serverLevel.getServer(), this,
                        owner -> ((MatrixPatternStorageBlockEntityMixin) owner).aeallpattern$rerunRebuild());
            }
            @SuppressWarnings("unchecked")
            List<IPatternDetails> cached = (List<IPatternDetails>) field(self, "cachedPatterns");
            cached.removeIf(AggregatePatternMarkerDetails.class::isInstance);

            Object inventory = invoke(self, "getInventory");
            if (inventory == null) {
                return;
            }
            int slots = ((Number) invoke(inventory, "getSlots")).intValue();
            boolean cold = false;
            for (int slot = 0; slot < slots; slot++) {
                ItemStack stack = (ItemStack) invoke(inventory, "getStackInSlot", slot);
                List<IPatternDetails> expanded = AggregatePatternExpander.expandScheduled(
                        stack, level, this::aeallpattern$rerunRebuild);
                if (expanded.isEmpty()) {
                    cold = true;
                }
                for (IPatternDetails pattern : expanded) {
                    if (pattern instanceof IMolecularAssemblerSupportedPattern) {
                        cached.add(pattern);
                    }
                }
            }
            if (cold) {
                // The scheduled job re-runs this rebuild once its full result is ready.
                AeAllPattern.LOGGER.debug("Matrix pattern storage: scheduled aggregate expansion pending");
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug(
                    "Could not expand aggregate patterns for a matrix pattern storage", error);
        }
    }

    /** Re-runs the host's rebuild so the completed scheduled expansion gets published. */
    @org.spongepowered.asm.mixin.Unique
    private void aeallpattern$rerunRebuild() {
        try {
            Method method = ((Object) this).getClass().getDeclaredMethod("rebuildPatternCache");
            method.setAccessible(true);
            method.invoke(this);
            Method notifyPort = ((Object) this).getClass().getDeclaredMethod("notifyPortPatternsChanged");
            notifyPort.setAccessible(true);
            notifyPort.invoke(this);
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug("Could not re-run matrix pattern storage rebuild", error);
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object invoke(Object target, String name, Object... args)
            throws ReflectiveOperationException {
        Class<?>[] types = new Class<?>[args.length];
        for (int index = 0; index < args.length; index++) {
            types[index] = primitive(args[index].getClass());
        }
        Method method = target.getClass().getMethod(name, types);
        return method.invoke(target, args);
    }

    private static Class<?> primitive(Class<?> type) {
        if (type == Integer.class) return int.class;
        if (type == Boolean.class) return boolean.class;
        if (type == Long.class) return long.class;
        if (type == Double.class) return double.class;
        if (type == Float.class) return float.class;
        if (type == Short.class) return short.class;
        if (type == Byte.class) return byte.class;
        if (type == Character.class) return char.class;
        return type;
    }
}
