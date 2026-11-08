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

    /** Non-mutating blocking check. Unknown inventories fail closed in blocking mode. */
    default boolean isInputBlocked(ServerLevel level, BindingRecord binding) {
        var target = level.getBlockEntity(binding.target().pos());
        if (target == null) return true;
        boolean found = false;
        java.util.List<net.minecraft.core.Direction> sides =
                new java.util.ArrayList<>(java.util.Arrays.asList(net.minecraft.core.Direction.values()));
        sides.add(null);
        for (var side : sides) {
            var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, binding.target().pos(), side);
            if (handler == null) continue;
            found = true;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && handler.isItemValid(slot, stack)) return true;
            }
        }
        return !found;
    }

    /** Atomically accepts every input for one recipe. */
    default boolean insertRecipe(
            ServerLevel level, BindingRecord binding, RecipeSnapshot recipe, List<ItemStack> inputs) {
        return inputs.size() == 1 && insert(level, binding, inputs.getFirst());
    }

    /**
     * Machine state that selects between different recipe sets within one recipe generation.
     *
     * <p>The discovered catalog is cached per adapter and block-entity type, and an aggregate
     * library entry is keyed by catalyst plus variant. An adapter whose recipe set depends on
     * mutable machine state (a selectable operating mode, for example) must report that state here:
     * otherwise the first state scanned would be reused for the others, and the states would
     * overwrite one another's catalog. Return the empty string for machines whose recipes depend
     * only on the machine type.</p>
     */
    default String catalogVariant(ServerLevel level, BlockEntity target) {
        return "";
    }

    /** Extracts one complete stack exposed by the machine's output capability. */
    ItemStack extractAnyOutput(ServerLevel level, BindingRecord binding, boolean simulate);
}
