package io.github.langqi99.aeallpattern.compat.mekanism;

import appeng.api.stacks.AEItemKey;
import io.github.langqi99.aeallpattern.binding.BindingRecord;
import io.github.langqi99.aeallpattern.machine.MachineAdapter;
import io.github.langqi99.aeallpattern.machine.ItemHandlerTransfer;
import io.github.langqi99.aeallpattern.recipe.RecipeCatalog;
import io.github.langqi99.aeallpattern.recipe.RecipeFingerprint;
import io.github.langqi99.aeallpattern.recipe.RecipeSnapshot;

import java.util.*;
import java.util.function.Supplier;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/** Deterministic Mekanism one-item-input adapter using public recipe and capability APIs. */
final class MekanismItemToItemAdapter implements MachineAdapter {
    private static final int MAX_INGREDIENT_VARIANTS = 64;
    private static final int MAX_PATTERNS = 4096;
    private final ResourceLocation id;
    private final Supplier<RecipeType<ItemStackToItemStackRecipe>> recipeType;
    private final boolean vanillaSmelting;
    private final String machineNamespace;
    private final String singleMachinePath;
    private final String factorySuffix;

    MekanismItemToItemAdapter(
            String idPath,
            Supplier<RecipeType<ItemStackToItemStackRecipe>> recipeType,
            String singleMachinePath,
            String factorySuffix) {
        this.id = new ResourceLocation("mekanism", idPath);
        this.recipeType = recipeType;
        this.vanillaSmelting = false;
        this.machineNamespace = "mekanism";
        this.singleMachinePath = singleMachinePath;
        this.factorySuffix = factorySuffix;
    }

    MekanismItemToItemAdapter(String idPath, String singleMachinePath, String factorySuffix) {
        this.id = new ResourceLocation("mekanism", idPath);
        this.recipeType = null;
        this.vanillaSmelting = true;
        this.machineNamespace = "mekanism";
        this.singleMachinePath = singleMachinePath;
        this.factorySuffix = factorySuffix;
    }

    MekanismItemToItemAdapter(
            String idPath,
            Supplier<RecipeType<ItemStackToItemStackRecipe>> recipeType,
            String machineNamespace,
            String singleMachinePath,
            String factorySuffix) {
        this.id = new ResourceLocation("mekanism", idPath);
        this.recipeType = recipeType;
        this.vanillaSmelting = false;
        this.machineNamespace = machineNamespace;
        this.singleMachinePath = singleMachinePath;
        this.factorySuffix = factorySuffix;
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public boolean supports(ServerLevel level, BlockEntity target) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(target.getBlockState().getBlock());
        return blockId.getNamespace().equals(machineNamespace)
                && (blockId.getPath().equals(singleMachinePath) || blockId.getPath().endsWith("_" + factorySuffix));
    }

    @Override
    public RecipeCatalog discoverRecipes(ServerLevel level, BlockEntity target, long generation) {
        if (vanillaSmelting) {
            return discoverVanillaSmelting(level, generation);
        }
        List<RecipeSnapshot> snapshots = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        int filtered = 0;
        List<ItemStackToItemStackRecipe> holders = new ArrayList<>(
                level.getRecipeManager().getAllRecipesFor(recipeType.get()));
        holders.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
        for (ItemStackToItemStackRecipe recipe : holders) {
            List<ItemStack> variants = recipe.getInput().getRepresentations().stream()
                    .sorted(Comparator.comparing(MekanismItemToItemAdapter::normalize))
                    .toList();
            if (variants.isEmpty() || variants.size() > MAX_INGREDIENT_VARIANTS) {
                filtered++;
                continue;
            }
            for (ItemStack representation : variants) {
                if (snapshots.size() >= MAX_PATTERNS) {
                    filtered++;
                    break;
                }
                long needed = recipe.getInput().getNeededAmount(representation);
                if (needed < 1 || needed > representation.getMaxStackSize()) {
                    filtered++;
                    continue;
                }
                ItemStack input = representation.copyWithCount((int) needed);
                ItemStack output = recipe.getOutput(input).copy();
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
                        id.toString(), recipe.getId().toString(), normalizedInput, normalizedOutput, schemaVersion());
                snapshots.add(new RecipeSnapshot(recipe.getId(), input, output, fingerprint, 200));
            }
        }
        snapshots.sort(Comparator.comparing(snapshot -> snapshot.fingerprint().stableKey()));
        return new RecipeCatalog(generation, snapshots, filtered);
    }

    private RecipeCatalog discoverVanillaSmelting(ServerLevel level, long generation) {
        List<RecipeSnapshot> snapshots = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        int filtered = 0;
        var holders = new ArrayList<>(level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING));
        holders.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
        for (net.minecraft.world.item.crafting.SmeltingRecipe recipe : holders) {
            List<ItemStack> variants = java.util.Arrays.stream(
                            recipe.getIngredients().get(0).getItems())
                    .sorted(Comparator.comparing(MekanismItemToItemAdapter::normalize))
                    .toList();
            if (variants.isEmpty() || variants.size() > MAX_INGREDIENT_VARIANTS) {
                filtered++;
                continue;
            }
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
                if (AEItemKey.of(input).equals(AEItemKey.of(output))
                        || !seen.add(List.of(normalizedInput, normalizedOutput))) {
                    filtered++;
                    continue;
                }
                RecipeFingerprint fingerprint = new RecipeFingerprint(
                        id.toString(), recipe.getId().toString(), normalizedInput, normalizedOutput, schemaVersion());
                snapshots.add(new RecipeSnapshot(
                        recipe.getId(), input, output, fingerprint, recipe.getCookingTime()));
            }
        }
        snapshots.sort(Comparator.comparing(snapshot -> snapshot.fingerprint().stableKey()));
        return new RecipeCatalog(generation, snapshots, filtered);
    }

    @Override
    public boolean insert(ServerLevel level, BindingRecord binding, ItemStack stack) {
        for (Direction side : preferredFirst(binding.clickedSide())) {
            var handler = ItemHandlerTransfer.find(level, binding.target().pos(), side);
            if (ItemHandlerTransfer.insertFully(handler, stack)) {
                return true;
            }
        }
        // Setblock-created machines and some packs intentionally expose no configured side.
        // Mekanism's unsided automation handler still enforces per-slot insertion rules.
        if (ItemHandlerTransfer.insertFully(
                ItemHandlerTransfer.find(level, binding.target().pos(), null), stack)) {
            return true;
        }
        return forceInsertIntoMekanismInput(level, binding, stack);
    }

    @Override
    public ItemStack extractAnyOutput(
            ServerLevel level, BindingRecord binding, boolean simulate) {
        for (Direction side : preferredFirst(binding.clickedSide())) {
            var handler = ItemHandlerTransfer.find(level, binding.target().pos(), side);
            ItemStack extracted = ItemHandlerTransfer.extractAny(handler, simulate);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        // The unsided handler keeps input slots non-extractable and exposes completed outputs.
        ItemStack extracted = ItemHandlerTransfer.extractAny(
                ItemHandlerTransfer.find(level, binding.target().pos(), null), simulate);
        if (!extracted.isEmpty()) {
            return extracted;
        }
        return forceExtractMekanismOutput(level, binding, simulate);
    }

    /**
     * Deliberately bypasses Mekanism's side configuration while retaining the machine's own
     * input-slot recipe validation. This is the Linker's "force input" contract.
     */
    private static boolean forceInsertIntoMekanismInput(
            ServerLevel level, BindingRecord binding, ItemStack stack) {
        BlockEntity target = level.getBlockEntity(binding.target().pos());
        if (!(target instanceof IMekanismInventory inventory)) {
            return false;
        }
        ItemStack simulatedRemainder = insertIntoInputSlots(
                inventory, stack.copy(), Action.SIMULATE);
        if (!simulatedRemainder.isEmpty()) {
            return false;
        }
        return insertIntoInputSlots(inventory, stack.copy(), Action.EXECUTE).isEmpty();
    }

    private static ItemStack insertIntoInputSlots(
            IMekanismInventory inventory, ItemStack stack, Action action) {
        ItemStack remainder = stack;
        for (IInventorySlot slot : inventory.getInventorySlots(null)) {
            if (remainder.isEmpty()) {
                break;
            }
            if (isSlotType(slot, "mekanism.common.inventory.slot.InputInventorySlot")
                    || !isSlotType(slot, "mekanism.common.inventory.slot.OutputInventorySlot")) {
                remainder = slot.insertItem(remainder, action, AutomationType.MANUAL);
            }
        }
        return remainder;
    }

    /** Drains only real Mekanism output slots, independent of configured output faces. */
    private static ItemStack forceExtractMekanismOutput(
            ServerLevel level, BindingRecord binding, boolean simulate) {
        BlockEntity target = level.getBlockEntity(binding.target().pos());
        if (!(target instanceof IMekanismInventory inventory)) {
            return ItemStack.EMPTY;
        }
        for (IInventorySlot slot : inventory.getInventorySlots(null)) {
            if (isSlotType(slot, "mekanism.common.inventory.slot.OutputInventorySlot") && !slot.isEmpty()) {
                ItemStack extracted = slot.extractItem(
                        slot.getCount(), simulate ? Action.SIMULATE : Action.EXECUTE, AutomationType.MANUAL);
                if (!extracted.isEmpty()) {
                    return extracted;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isSlotType(IInventorySlot slot, String className) {
        for (Class<?> type = slot.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    private static List<Direction> preferredFirst(Direction preferred) {
        List<Direction> directions = new ArrayList<>(Direction.values().length);
        directions.add(preferred);
        for (Direction side : Direction.values()) {
            if (side != preferred) {
                directions.add(side);
            }
        }
        return directions;
    }

    private static String normalize(ItemStack stack) {
        return AEItemKey.of(stack) + "*" + stack.getCount();
    }
}
