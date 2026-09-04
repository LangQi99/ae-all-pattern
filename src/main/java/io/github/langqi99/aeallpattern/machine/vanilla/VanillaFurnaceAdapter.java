package io.github.langqi99.aeallpattern.machine.vanilla;

import appeng.api.stacks.AEItemKey;
import io.github.langqi99.aeallpattern.binding.BindingRecord;
import io.github.langqi99.aeallpattern.machine.MachineAdapter;
import io.github.langqi99.aeallpattern.machine.ItemHandlerTransfer;
import io.github.langqi99.aeallpattern.recipe.RecipeCatalog;
import io.github.langqi99.aeallpattern.recipe.RecipeFingerprint;
import io.github.langqi99.aeallpattern.recipe.RecipeSnapshot;

import java.util.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public final class VanillaFurnaceAdapter implements MachineAdapter {
    public static final ResourceLocation ID = new ResourceLocation("minecraft", "furnace");
    private static final int MAX_INGREDIENT_VARIANTS = 64;
    private static final int MAX_PATTERNS = 4096;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public boolean supports(ServerLevel level, BlockEntity target) {
        return target instanceof AbstractFurnaceBlockEntity;
    }

    @Override
    public RecipeCatalog discoverRecipes(ServerLevel level, BlockEntity target, long generation) {
        RecipeType<? extends AbstractCookingRecipe> type = recipeType(target);
        List<? extends AbstractCookingRecipe> holders = recipes(level, type).stream()
                .sorted(Comparator.comparing(recipe -> recipe.getId().toString()))
                .toList();
        List<RecipeSnapshot> snapshots = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        int filtered = 0;

        for (AbstractCookingRecipe recipe : holders) {
            ItemStack[] variants = recipe.getIngredients().get(0).getItems();
            if (variants.length == 0 || variants.length > MAX_INGREDIENT_VARIANTS) {
                filtered++;
                continue;
            }
            Arrays.sort(variants, Comparator.comparing(VanillaFurnaceAdapter::normalize));
            for (ItemStack variant : variants) {
                if (snapshots.size() >= MAX_PATTERNS) {
                    filtered++;
                    break;
                }
                ItemStack input = variant.copyWithCount(Math.max(1, variant.getCount()));
                ItemStack output = recipe
                        .assemble(new SimpleContainer(input), level.registryAccess())
                        .copy();
                if (output.isEmpty()) {
                    filtered++;
                    continue;
                }
                String normalizedInput = normalize(input);
                String normalizedOutput = normalize(output);
                if (Objects.equals(AEItemKey.of(input), AEItemKey.of(output))
                        || !seen.add(List.of(normalizedInput, normalizedOutput))) {
                    filtered++;
                    continue;
                }
                RecipeFingerprint fingerprint = new RecipeFingerprint(
                        id().toString(),
                        recipe.getId().toString(),
                        normalizedInput,
                        normalizedOutput,
                        schemaVersion());
                snapshots.add(new RecipeSnapshot(
                        recipe.getId(), input, output, fingerprint, recipe.getCookingTime()));
            }
        }

        snapshots.sort(Comparator.comparing(snapshot -> snapshot.fingerprint().stableKey()));
        return new RecipeCatalog(generation, snapshots, filtered);
    }

    @Override
    public boolean insert(ServerLevel level, BindingRecord binding, ItemStack stack) {
        var handler = ItemHandlerTransfer.find(level, binding.target().pos(), Direction.UP);
        return ItemHandlerTransfer.insertFully(handler, stack);
    }

    @Override
    public ItemStack extractAnyOutput(
            ServerLevel level, BindingRecord binding, boolean simulate) {
        var handler = ItemHandlerTransfer.find(level, binding.target().pos(), Direction.DOWN);
        return ItemHandlerTransfer.extractAny(handler, simulate);
    }

    private static String normalize(ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        return key + "*" + stack.getCount();
    }

    private static RecipeType<? extends AbstractCookingRecipe> recipeType(BlockEntity target) {
        if (target instanceof BlastFurnaceBlockEntity) {
            return RecipeType.BLASTING;
        }
        if (target instanceof SmokerBlockEntity) {
            return RecipeType.SMOKING;
        }
        return RecipeType.SMELTING;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<? extends AbstractCookingRecipe> recipes(
            ServerLevel level, RecipeType<? extends AbstractCookingRecipe> type) {
        return (List) level.getRecipeManager().getAllRecipesFor(type);
    }
}
