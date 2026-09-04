package io.github.langqi99.aeallpattern.machine;

import appeng.api.networking.IManagedGridNode;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.registry.ModItems;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Dependency-free bridge for aggregate patterns in PackagedAuto's ME packaging provider. */
public final class PackagedAutoAggregateCompat {
    private PackagedAutoAggregateCompat() {
    }

    public static boolean isAggregatePattern(ItemStack stack) {
        return stack.is(ModItems.AGGREGATE_PATTERN.get())
                && ModDataComponents.hasAggregatePattern(stack);
    }

    public static boolean hasAggregatePattern(Object provider) {
        try {
            Object handler = provider.getClass().getMethod("getItemHandler").invoke(provider);
            ItemStack stack = (ItemStack) handler.getClass()
                    .getMethod("getStackInSlot", int.class).invoke(handler, 0);
            return isAggregatePattern(stack);
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug("Failed to inspect ME packaging provider pattern", error);
            return false;
        }
    }

    public static void refreshRecipeList(Object handler) {
        try {
            ItemStack stack = (ItemStack) handler.getClass()
                    .getMethod("getStackInSlot", int.class).invoke(handler, 0);
            if (!isAggregatePattern(stack)) {
                return;
            }
            Field blockEntityField = handler.getClass().getField("blockEntity");
            Object provider = blockEntityField.get(handler);
            if (!(provider instanceof BlockEntity blockEntity)
                    || !(blockEntity.getLevel() instanceof ServerLevel level)) {
                return;
            }
            @SuppressWarnings("unchecked")
            List<Object> recipeList = (List<Object>) provider.getClass().getField("recipeList").get(provider);
            recipeList.clear();
            recipeList.addAll(PackagedCraftingAdapter.packageRecipeInfos(level, stack));
            provider.getClass().getMethod("postPatternChange").invoke(provider);
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug("Failed to refresh aggregate recipes in ME packaging provider", error);
        }
    }

    public static List<?> usePackageWorkflow(Object provider, List<?> originalPatterns) {
        if (!hasAggregatePattern(provider)) {
            return originalPatterns;
        }
        try {
            Object nodeObject = provider.getClass().getMethod("getMainNode").invoke(provider);
            if (!(nodeObject instanceof IManagedGridNode node) || !node.isActive()
                    || !(provider instanceof BlockEntity blockEntity)
                    || !(blockEntity.getLevel() instanceof ServerLevel level)) {
                return List.of();
            }

            ClassLoader loader = provider.getClass().getClassLoader();
            Class<?> recipeInfoType = Class.forName("thelm.packagedauto.api.IPackageRecipeInfo", false, loader);
            Class<?> packagePatternType = Class.forName("thelm.packagedauto.api.IPackagePattern", false, loader);
            Constructor<?> packageDetails = Class.forName(
                            "thelm.packagedauto.integration.appeng.recipe.PackageCraftingPatternDetails", false, loader)
                    .getConstructor(packagePatternType, HolderLookup.Provider.class);
            Constructor<?> recipeDetails = Class.forName(
                            "thelm.packagedauto.integration.appeng.recipe.RecipeCraftingPatternDetails", false, loader)
                    .getConstructor(recipeInfoType, HolderLookup.Provider.class);
            Method isCraftable = recipeInfoType.getMethod("isCraftable");
            Method isPackageable = recipeInfoType.getMethod("isPackageable");
            Method getPatterns = recipeInfoType.getMethod("getPatterns");
            Method getExtraPatterns = recipeInfoType.getMethod("getExtraPatterns");

            List<?> recipes = (List<?>) provider.getClass().getField("recipeList").get(provider);
            List<Object> result = new ArrayList<>();
            for (Object recipe : recipes) {
                if ((boolean) isPackageable.invoke(recipe)) {
                    addPackagePatterns(result, (List<?>) getPatterns.invoke(recipe), packageDetails,
                            level.registryAccess());
                    addPackagePatterns(result, (List<?>) getExtraPatterns.invoke(recipe), packageDetails,
                            level.registryAccess());
                }
                if ((boolean) isCraftable.invoke(recipe)) {
                    result.add(recipeDetails.newInstance(recipe, level.registryAccess()));
                }
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug("Failed to publish aggregate package workflow", error);
            return originalPatterns;
        }
    }

    private static void addPackagePatterns(
            List<Object> result,
            List<?> patterns,
            Constructor<?> packageDetails,
            HolderLookup.Provider registries) throws ReflectiveOperationException {
        for (Object pattern : patterns) {
            result.add(packageDetails.newInstance(pattern, registries));
        }
    }
}
