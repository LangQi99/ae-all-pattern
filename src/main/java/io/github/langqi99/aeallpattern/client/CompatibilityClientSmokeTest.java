package io.github.langqi99.aeallpattern.client;

import guideme.Guides;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import guideme.indices.ItemIndex;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.compat.jei.AeAllPatternJeiPlugin;
import io.github.langqi99.aeallpattern.guide.PatternGuideItem;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Runs in an isolated test save under a virtual display, never during normal play. */
final class CompatibilityClientSmokeTest {
    private static final List<String> PAGES = List.of("index.md", "aggregate_patterns.md", "pattern_linker.md",
            "tianshu_router.md", "multiblocks.md");
    private static boolean encoded;
    private static int ticks;

    private CompatibilityClientSmokeTest() {}

    static boolean tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || AeAllPatternJeiPlugin.runtime().isEmpty()) return false;
        if (!encoded) {
            var runtime = AeAllPatternJeiPlugin.runtime().orElseThrow();
            var expected = Set.of(id("aap_test:alpha"), id("aap_test:beta"));
            for (String controller : List.of("mm:aap_test_primary", "mm:aap_test_secondary")) {
                var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(
                        mezz.jei.api.recipe.RecipeIngredientRole.CATALYST, mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                        BuiltInRegistries.ITEM.get(id(controller)).getDefaultInstance());
                long oldCount = runtime.getRecipeManager().createRecipeCategoryLookup().limitFocus(List.of(focus)).get()
                        .filter(io.github.langqi99.aeallpattern.compat.masterful.MasterfulMachineryBridge::isProcessingCategory).count();
                require(oldCount == (controller.endsWith("secondary") ? 0 : 2),
                        "Fixture no longer reproduces the missing secondary-controller catalyst");
                var recipes = ClientJeiAggregateScanner.encodeMasterfulFixture(runtime, id(controller));
                require(recipes.size() == 2, "Expected two encoded MM recipes for " + controller + ", got " + recipes.size());
                require(recipes.stream().map(recipe -> recipe.recipeId()).collect(Collectors.toSet()).equals(expected),
                        "Wrong MM recipe IDs for " + controller);
                require(recipes.stream().allMatch(recipe -> recipe.inputs().get(0).amount() == 2
                        && recipe.outputs().get(0).amount() == 1), "MM ingredient counts changed");
                AeAllPattern.LOGGER.info("MASTERFUL_CLIENT_ENCODE_PASSED: controller={}, oldCatalystCategories={}, encoded={}",
                        controller, oldCount, recipes.size());
            }
            var guide = Guides.getById(PatternGuideItem.GUIDE_ID);
            require(guide != null, "GuideME did not load the guide definition");
            require(guide.getPages().size() == PAGES.size(), "Translated pages must replace originals, not duplicate navigation");
            require(guide.getParsedPage(id("aeallpattern:index.md")).getLanguage()
                    .equals(minecraft.getLanguageManager().getSelected()), "Wrong guide language");
            for (String name : PAGES) require(guide.getPage(id("aeallpattern:" + name)) != null, "Missing guide page " + name);
            for (String item : List.of("guide", "pattern_linker", "pattern_binder", "tianshu_pattern_selector",
                    "all_pattern_generator", "aggregate_pattern")) {
                require(guide.getIndex(ItemIndex.class).get(id("aeallpattern:" + item)) != null, "Missing guide item shortcut " + item);
            }
            encoded = true;
        }
        int page = ticks / 20;
        if (page >= PAGES.size()) {
            AeAllPattern.LOGGER.info("COMPATIBILITY_CLIENT_SMOKE_TEST_PASSED");
            return true;
        }
        if (ticks % 20 == 0) {
            GuidesCommon.openGuide(minecraft.player, PatternGuideItem.GUIDE_ID,
                    new PageAnchor(id("aeallpattern:" + PAGES.get(page)), null));
        }
        if (ticks % 20 == 15) {
            require(minecraft.screen != null && minecraft.screen.getClass().getSimpleName().equals("GuideScreen"),
                    "GuideME screen did not open");
            Screenshot.grab(minecraft.gameDirectory, "aap-guide-" + page + ".png", minecraft.getMainRenderTarget(),
                    message -> AeAllPattern.LOGGER.info("Guide screenshot: {}", message.getString()));
        }
        ticks++;
        return false;
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
