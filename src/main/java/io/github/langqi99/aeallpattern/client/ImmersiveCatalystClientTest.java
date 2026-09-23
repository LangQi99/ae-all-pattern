package io.github.langqi99.aeallpattern.client;

import appeng.api.stacks.AEItemKey;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternExpander;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternOptions;
import io.github.langqi99.aeallpattern.compat.jei.AeAllPatternJeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Real IE + JEI client regression, enabled only by the isolated Gradle test profile. */
final class ImmersiveCatalystClientTest {
    private static int ticks;
    private static boolean completed;
    private ImmersiveCatalystClientTest() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean tick(Minecraft minecraft) {
        if (completed) return true;
        if (++ticks > 6000) throw new IllegalStateException("IE catalyst test timed out waiting for world/JEI");
        if (minecraft.level == null || AeAllPatternJeiPlugin.runtime().isEmpty()) return false;
        var runtime = AeAllPatternJeiPlugin.runtime().orElseThrow();
        var category = runtime.getRecipeManager().createRecipeCategoryLookup().get()
                .filter(c -> c.getRecipeType().getUid().toString().equals("immersiveengineering:metal_press"))
                .findFirst().orElseThrow(() -> new IllegalStateException("IE metal press category absent"));
        var focus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        var recipes = runtime.getRecipeManager().createRecipeLookup(category.getRecipeType()).get().toList();
        require(!recipes.isEmpty(), "No real IE recipes");
        var mold = BuiltInRegistries.ITEM.get(ResourceLocation.parse("immersiveengineering:mold_plate"));
        require(mold != net.minecraft.world.item.Items.AIR, "Plate mold missing");
        int plateLayouts = 0;
        for (Object recipe : recipes) {
            var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(
                    (mezz.jei.api.recipe.category.IRecipeCategory) category, recipe, focus).orElseThrow();
            var slots = ((mezz.jei.api.gui.IRecipeLayoutDrawable<?>) layout).getRecipeSlotsView();
            boolean input = slots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                    .anyMatch(s -> s.getIngredients(VanillaTypes.ITEM_STACK).anyMatch(stack -> stack.is(mold)));
            boolean catalyst = slots.getSlotViews(RecipeIngredientRole.CATALYST).stream()
                    .anyMatch(s -> s.getIngredients(VanillaTypes.ITEM_STACK).anyMatch(stack -> stack.is(mold)));
            if (!input && !catalyst) continue;
            require(catalyst && !input, "Pinned IE mold role changed");
            plateLayouts++;
        }
        require(plateLayouts > 0, "No plate mold layouts tested");
        var encoded = ClientJeiAggregateScanner.encodeImmersiveFixture(runtime, category);
        var iron = encoded.stream().filter(r -> r.recipeId().toString()
                .equals("immersiveengineering:metalpress/plate_iron")).findFirst().orElseThrow();
        var server = java.util.Objects.requireNonNull(minecraft.getSingleplayerServer());
        server.submit(() -> {
        var level = server.overworld();
        for (int flags : new int[]{0, 16}) {
            var pattern = AggregatePatternExpander.expandRecipe(iron, AggregatePatternOptions.fromFlags(flags),
                    level, "ie-catalyst-test-" + flags);
            require(pattern != null, "Iron plate failed to encode");
            boolean needsMold = java.util.Arrays.stream(pattern.getInputs())
                    .anyMatch(i -> i.isValid(AEItemKey.of(mold), level));
            require(needsMold == false, "Unexpected mold requirement");
            require(java.util.Arrays.stream(pattern.getInputs()).anyMatch(i ->
                    i.isValid(AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), level)),
                    "Consumed iron was removed");
            require(pattern.getInputs().length == 1, "Unexpected input count");
            var output = pattern.getOutputs().getFirst();
            require(output.amount() == 1 && output.what().equals(AEItemKey.of(
                    BuiltInRegistries.ITEM.get(ResourceLocation.parse("immersiveengineering:plate_iron")))),
                    "Iron plate output changed");
            AeAllPattern.LOGGER.info("IE_CATALYST_CASE: flags={}, needsMold={}, inputs={}", flags, needsMold, pattern.getInputs().length);
        }
        }).join();
        AeAllPattern.LOGGER.info("IE_CATALYST_CLIENT_TEST_PASSED: plateLayouts={}, encoded={}, moldRole=CATALYST",
                plateLayouts, encoded.size());
        completed = true;
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
