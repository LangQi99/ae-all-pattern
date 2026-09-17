package io.github.langqi99.aeallpattern.gametest;

import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AeAllPattern.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuideGameTests {
    private GuideGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void guideRecipeLoadsOnDedicatedServer(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(new ResourceLocation("aeallpattern", "guide"));
        helper.assertTrue(recipe.isPresent(), "Guide crafting recipe must load");
        helper.assertTrue(recipe.orElseThrow().getResultItem(helper.getLevel().registryAccess()).is(ModItems.GUIDE.get()),
                "Guide recipe must produce the registered guide item");
        helper.succeed();
    }
}
