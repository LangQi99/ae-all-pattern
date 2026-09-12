package io.github.langqi99.aeallpattern.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.emi.emi.api.stack.*;
import dev.nolij.toomanyrecipeviewers.impl.ingredient.TMRVStack;
import dev.nolij.toomanyrecipeviewers.impl.jei.api.gui.ingredient.TMRVSlotWidget;
import io.github.langqi99.aeallpattern.client.ClientJeiAggregateScanner;
import io.github.langqi99.aeallpattern.compat.emi.EmiAggregateScanner;
import java.util.Objects;
import java.lang.reflect.Constructor;
import java.util.stream.Stream;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientJeiAggregateScanner.class)
public abstract class ClientJeiAggregateScannerMixin {
    @Redirect(method = {"chooseStack", "chooseInputSlot"}, at = @At(value = "INVOKE",
            target = "Lmezz/jei/api/gui/ingredient/IRecipeSlotView;getAllIngredients()Ljava/util/stream/Stream;"))
    private static Stream<?> tmrvIngredients(IRecipeSlotView slot) {
        if (slot instanceof TMRVSlotWidget widget) {
            EmiIngredient ingredient = widget.getStack();
            return Stream.concat(slot.getAllIngredients(), ingredient.getEmiStacks().stream()
                    .map(ClientJeiAggregateScannerMixin::aeallpattern$typed).filter(Objects::nonNull));
        }
        return slot.getAllIngredients();
    }

    @Unique @SuppressWarnings({"rawtypes", "unchecked"})
    private static ITypedIngredient<?> aeallpattern$typed(EmiStack stack) {
        if (stack instanceof TMRVStack tmrv) return aeallpattern$typed(tmrv.type, tmrv.ingredient);
        ItemStack item = stack.getItemStack();
        return item.isEmpty() ? null : aeallpattern$typed(VanillaTypes.ITEM_STACK, item);
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static <T> ITypedIngredient<T> aeallpattern$typed(IIngredientType<T> type, T ingredient) {
        try {
            Class<?> impl = Class.forName("mezz.jei.library.ingredients.TypedIngredient");
            Constructor<?> constructor = impl.getDeclaredConstructor(IIngredientType.class, Object.class);
            constructor.setAccessible(true);
            return (ITypedIngredient<T>) constructor.newInstance(type, ingredient);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    @WrapOperation(method = "dispatchScan", at = @At(value = "INVOKE",
            target = "Lio/github/langqi99/aeallpattern/client/ClientJeiAggregateScanner;startScan(Lmezz/jei/api/runtime/IJeiRuntime;Lnet/minecraft/core/BlockPos;)V"))
    private static void preferEmi(IJeiRuntime runtime, BlockPos pos, Operation<Void> original) {
        if (ModList.get().isLoaded("toomanyrecipeviewers")) {
            try { if (EmiAggregateScanner.scan(pos)) return; } catch (RuntimeException ignored) {}
        }
        try { original.call(runtime, pos); } catch (RuntimeException ignored) {}
    }
}
