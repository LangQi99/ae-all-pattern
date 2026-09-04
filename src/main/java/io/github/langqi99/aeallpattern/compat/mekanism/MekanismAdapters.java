package io.github.langqi99.aeallpattern.compat.mekanism;

import io.github.langqi99.aeallpattern.machine.MachineAdapterRegistry;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fml.ModList;

/** Loaded only when Mekanism is present. */
public final class MekanismAdapters {
    private MekanismAdapters() {
    }

    public static void registerAll() {
        MachineAdapterRegistry.register(new MekanismItemToItemAdapter(
                "smelting", "energized_smelter", "smelting_factory"));
        MachineAdapterRegistry.register(new MekanismItemToItemAdapter(
                "crushing", () -> recipeType("crushing"),
                "crusher", "crushing_factory"));
        MachineAdapterRegistry.register(new MekanismItemToItemAdapter(
                "enriching", () -> recipeType("enriching"),
                "enrichment_chamber", "enriching_factory"));
        if (ModList.get().isLoaded("mekmm")) {
            MachineAdapterRegistry.register(new MekanismItemToItemAdapter(
                    "lathing", () -> recipeType("lathing"),
                    "mekmm", "cnc_lathe", "lathing_factory"));
            MachineAdapterRegistry.register(new MekanismItemToItemAdapter(
                    "rolling_mill", () -> recipeType("rolling_mill"),
                    "mekmm", "cnc_rolling_mill", "rolling_mill_factory"));
        }
    }

    @SuppressWarnings({"unchecked"})
    private static RecipeType<ItemStackToItemStackRecipe> recipeType(String path) {
        return (RecipeType<ItemStackToItemStackRecipe>) (RecipeType<?>) BuiltInRegistries.RECIPE_TYPE.get(
                new ResourceLocation("mekanism", path));
    }
}
