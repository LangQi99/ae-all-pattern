package io.github.langqi99.aeallpattern.gametest;

import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.compat.masterful.MasterfulMachineryBridge;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AeAllPattern.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MasterfulMachineryGameTests {
    private MasterfulMachineryGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void realControllersResolveAllAndOnlyTheirRecipes(GameTestHelper helper) throws Exception {
        if (!Boolean.getBoolean("aeallpattern.masterfulFixture")) {
            helper.succeed();
            return;
        }
        var expected = Set.of(id("aap_test:alpha"), id("aap_test:beta"));
        helper.assertTrue(MasterfulMachineryBridge.structuresForController(id("mm:aap_test_primary")).equals(expected),
                "Primary controller must resolve both structures");
        helper.assertTrue(MasterfulMachineryBridge.structuresForController(id("mm:aap_test_secondary")).equals(expected),
                "Secondary controller omitted by upstream JEI must still resolve both structures");
        helper.assertTrue(MasterfulMachineryBridge.structuresForController(id("minecraft:stone")).isEmpty(),
                "Do not claim unrelated blocks");
        Map<?, ?> recipes = (Map<?, ?>) Class.forName("io.ticticboom.mods.mm.recipe.MachineRecipeManager")
                .getField("RECIPES").get(null);
        var matched = recipes.entrySet().stream()
                .filter(entry -> MasterfulMachineryBridge.recipeMatches(entry.getValue(), expected))
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        helper.assertTrue(matched.equals(expected), "Include both owned recipes and exclude unrelated recipe: " + matched);

        BlockPos controller = new BlockPos(3, 2, 3);
        helper.setBlock(controller, BuiltInRegistries.BLOCK.get(id("mm:aap_test_secondary")));
        helper.setBlock(controller.west(), BuiltInRegistries.BLOCK.get(id("mm:aap_test_items_input")));
        helper.setBlock(controller.east(), BuiltInRegistries.BLOCK.get(id("mm:aap_test_items_output")));
        Map<?, ?> structures = (Map<?, ?>) Class.forName("io.ticticboom.mods.mm.structure.StructureManager")
                .getField("STRUCTURES").get(null);
        Object structure = structures.get(id("aap_test:alpha"));
        boolean formed = (boolean) structure.getClass().getMethod("formed", Level.class, BlockPos.class)
                .invoke(structure, helper.getLevel(), helper.absolutePos(controller));
        helper.assertTrue(formed, "The real three-block test multiblock must form");
        AeAllPattern.LOGGER.info("MASTERFUL_FIXTURE_PASSED: secondary controller, two structures, unrelated recipe excluded, real multiblock formed");
        helper.succeed();
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
