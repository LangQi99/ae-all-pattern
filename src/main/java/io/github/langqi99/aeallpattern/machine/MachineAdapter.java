package io.github.langqi99.aeallpattern.machine;

import io.github.langqi99.aeallpattern.binding.BindingRecord;
import io.github.langqi99.aeallpattern.recipe.RecipeCatalog;
import io.github.langqi99.aeallpattern.recipe.RecipeSnapshot;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Explicit contract for discoverable recipes and lossless machine transfer. */
public interface MachineAdapter {
    ResourceLocation id();

    int schemaVersion();

    boolean supports(ServerLevel level, BlockEntity target);

    RecipeCatalog discoverRecipes(ServerLevel level, BlockEntity target, long generation);

    /** Returns true only when the complete stack was accepted. */
    boolean insert(ServerLevel level, BindingRecord binding, ItemStack stack);

    /** Atomically accepts every input for one recipe. */
    default boolean insertRecipe(
            ServerLevel level, BindingRecord binding, RecipeSnapshot recipe, List<ItemStack> inputs) {
        return inputs.size() == 1 && insert(level, binding, inputs.get(0));
    }

    /** Extracts one complete stack exposed by the machine's output capability. */
    ItemStack extractAnyOutput(ServerLevel level, BindingRecord binding, boolean simulate);
}
