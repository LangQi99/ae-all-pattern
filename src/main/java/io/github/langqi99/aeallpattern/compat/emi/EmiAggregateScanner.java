package io.github.langqi99.aeallpattern.compat.emi;

import appeng.api.stacks.GenericStack;
import appeng.api.integrations.emi.EmiStackConverters;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.stack.*;
import dev.nolij.toomanyrecipeviewers.impl.ingredient.TMRVStack;
import io.github.langqi99.aeallpattern.aggregate.*;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.client.ClientRecipeMachineResolver;
import io.github.langqi99.aeallpattern.client.ClientJeiAggregateScanner;
import io.github.langqi99.aeallpattern.recipe.RecipeFingerprint;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import io.github.langqi99.aeallpattern.network.FriendlyStreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

public final class EmiAggregateScanner {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private EmiAggregateScanner() {}

    public static boolean isBusy() {
        return RUNNING.get();
    }

    public static boolean scan(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return true;
        BlockPos machinePos = ClientRecipeMachineResolver.resolvePosition(minecraft.level, pos);
        Block block = minecraft.level.getBlockState(machinePos).getBlock();
        ItemStack machine = ClientRecipeMachineResolver.recipeViewerCatalyst(minecraft.level, machinePos);
        if (machine.isEmpty()) return false;
        EmiRecipeManager manager = EmiApi.getRecipeManager();
        EmiStack machineStack = EmiStack.of(machine);
        List<EmiRecipeCategory> categories = manager.getCategories().stream()
                .filter(category -> isCraftingMachine(machine, category)
                        || manager.getWorkstations(category).stream().flatMap(i -> i.getEmiStacks().stream())
                        .anyMatch(stack -> stack.isEqual(machineStack))).toList();
        if (categories.isEmpty()) return false;
        ResourceLocation catalystItemId = BuiltInRegistries.ITEM.getKey(machine.getItem());
        ResourceLocation categoryId = ClientJeiAggregateScanner.pickCategoryId(
                categories.stream().map(EmiRecipeCategory::getId).toList(), catalystItemId);
        if (categoryId == null || !ClientJeiAggregateScanner.allowsCategory(catalystItemId, categoryId)) return false;
        List<EmiRecipe> candidates = categories.stream().filter(category -> category.getId().equals(categoryId))
                .flatMap(c -> manager.getRecipes(c).stream())
                .toList();
        if (candidates.isEmpty() || !RUNNING.compareAndSet(false, true)) return false;
        var connection = minecraft.getConnection();
        if (connection == null) { RUNNING.set(false); return true; }
        ResourceLocation catalystId = BuiltInRegistries.BLOCK.getKey(block);
        CompletableFuture.runAsync(() -> buildAndSend(machinePos, catalystId, block.getDescriptionId(), null,
                candidates))
                .whenComplete((ignored, error) -> RUNNING.set(false));
        return true;
    }

    public static boolean refresh(AggregateMetadataView.Entry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null
                || entry.batchCount() != 1 || !RUNNING.compareAndSet(false, true)) {
            return false;
        }
        try {
            Block block = BuiltInRegistries.BLOCK.get(entry.catalystId());
            ItemStack machine = block == null ? ItemStack.EMPTY : block.asItem().getDefaultInstance();
            if (machine.isEmpty()
                    || (block == Blocks.AIR
                            && !entry.catalystId().equals(BuiltInRegistries.BLOCK.getKey(Blocks.AIR)))) {
                ClientJeiAggregateScanner.uploadRefresh(List.of(), entry);
                RUNNING.set(false);
                return true;
            }
            EmiRecipeManager manager = EmiApi.getRecipeManager();
            EmiStack machineStack = EmiStack.of(machine);
            List<EmiRecipeCategory> categories = manager.getCategories().stream()
                    .filter(category -> isCraftingMachine(machine, category)
                            || manager.getWorkstations(category).stream()
                                    .flatMap(i -> i.getEmiStacks().stream())
                                    .anyMatch(stack -> stack.isEqual(machineStack)))
                    .toList();
            ResourceLocation catalystItemId = BuiltInRegistries.ITEM.getKey(machine.getItem());
            ResourceLocation categoryId = ClientJeiAggregateScanner.pickCategoryId(
                    categories.stream().map(EmiRecipeCategory::getId).toList(), catalystItemId);
            if (categoryId == null || !ClientJeiAggregateScanner.allowsCategory(catalystItemId, categoryId)) {
                ClientJeiAggregateScanner.uploadRefresh(List.of(), entry);
                RUNNING.set(false);
                return true;
            }
            List<EmiRecipe> candidates = categories.stream()
                    .filter(category -> category.getId().equals(categoryId))
                    .flatMap(category -> manager.getRecipes(category).stream())
                    .toList();
            var connection = minecraft.getConnection();
            CompletableFuture.runAsync(() -> buildAndSend(
                    BlockPos.ZERO, entry.catalystId(), entry.machineTranslationKey(), entry.libraryId(),
                    candidates))
                    .whenComplete((ignored, error) -> RUNNING.set(false));
            return true;
        } catch (RuntimeException error) {
            RUNNING.set(false);
            AeAllPattern.LOGGER.debug("Could not start EMI aggregate refresh for {}", entry.libraryId(), error);
            return false;
        }
    }

    private static void buildAndSend(
            BlockPos pos, ResourceLocation catalystId, String machineName, UUID replacementLibraryId,
            List<EmiRecipe> candidates) {
        List<AggregateRecipe> recipes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int rejected = 0;
        int encodingFailed = 0;
        RuntimeException firstError = null;
        for (EmiRecipe recipe : candidates) {
            try {
                Optional<AggregateRecipe> converted = toAggregate(recipe, ids);
                if (converted.isEmpty()) {
                    rejected++;
                } else if (canEncode(converted.orElseThrow())) {
                    recipes.add(converted.orElseThrow());
                } else {
                    encodingFailed++;
                }
            } catch (RuntimeException error) {
                rejected++;
                if (firstError == null) firstError = error;
            }
        }
        AeAllPattern.LOGGER.info(
                "EMI aggregate scan {}: candidates={}, accepted={}, rejected={}, encodingFailed={}",
                catalystId, candidates.size(), recipes.size(), rejected, encodingFailed, firstError);
        if (recipes.isEmpty()) {
            Minecraft.getInstance().execute(() -> {
                if (replacementLibraryId == null) {
                    show("message.aeallpattern.generator.no_item_recipes");
                } else {
                    ClientJeiAggregateScanner.uploadRefresh(
                            List.of(), replacementLibraryId, catalystId, machineName);
                }
            });
            return;
        }
        Minecraft.getInstance().execute(() -> {
            if (replacementLibraryId == null) {
                ClientJeiAggregateScanner.upload(recipes, pos, machineName, catalystId);
            } else {
                ClientJeiAggregateScanner.uploadRefresh(
                        recipes, replacementLibraryId, catalystId, machineName);
            }
        });
    }

    private static boolean canEncode(AggregateRecipe recipe) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { AggregateRecipe.STREAM_CODEC.encode(buffer, recipe); return true; }
        catch (RuntimeException ignored) { return false; }
        finally { buffer.release(); }
    }

    private static boolean isCraftingMachine(ItemStack machine, EmiRecipeCategory category) {
        return category == VanillaEmiRecipeCategories.CRAFTING && (machine.is(Blocks.CRAFTING_TABLE.asItem())
                || BuiltInRegistries.ITEM.getKey(machine.getItem()).toString().equals("ae2:molecular_assembler"));
    }

    private static Optional<AggregateRecipe> toAggregate(EmiRecipe recipe, Set<String> ids) {
        List<EmiIngredient> recipeInputs = recipe.getInputs().stream()
                .filter(ingredient -> !ingredient.isEmpty())
                .toList();
        if (recipeInputs.isEmpty() || recipeInputs.size() > AggregateRecipe.MAX_INPUTS
                || recipe.getOutputs().isEmpty()) return Optional.empty();
        int limit = AggregateInputSlot.configuredAlternativeLimit();
        List<AggregateInputSlot> inputs = recipeInputs.stream()
                .map(i -> input(i, limit)).flatMap(Optional::stream).toList();
        List<ScannedOutput> scannedOutputs = recipe.getOutputs().stream()
                .map(EmiAggregateScanner::output).flatMap(Optional::stream)
                .limit(AggregateRecipe.MAX_OUTPUTS).toList();
        List<GenericStack> outputs = scannedOutputs.stream().map(ScannedOutput::stack).toList();
        if (inputs.size() != recipeInputs.size() || outputs.isEmpty()) return Optional.empty();
        Recipe<?> backing = recipe.getBackingRecipe();
        if (backing instanceof CraftingRecipe crafting && crafting.isSpecial()) {
            return Optional.empty();
        }
        ResourceLocation id = backing == null ? recipe.getId() : backing.getId();
        if (id == null) return Optional.empty();
        String patternId = patternId(recipe.getCategory().getId() + "/" + id);
        if (!ids.add(patternId)) return Optional.empty();
        return Optional.of(new AggregateRecipe(patternId, id, kind(backing),
                inputs.stream().map(AggregateInputSlot::primary).toList(), inputs, outputs,
                probabilisticOutputMask(scannedOutputs), 1));
    }

    static String patternId(String rawId) {
        if (rawId.length() <= AggregatePatternSelection.MAX_ID_LENGTH) return rawId;
        String hash = new RecipeFingerprint("emi", rawId, "", "", 1).stableKey();
        return rawId.substring(0, AggregatePatternSelection.MAX_ID_LENGTH - hash.length() - 1) + ":" + hash;
    }

    private static Optional<ScannedOutput> output(EmiStack output) {
        return stack(output).map(stack -> new ScannedOutput(stack, output.getChance() < 0.999_999F));
    }

    private static int probabilisticOutputMask(List<ScannedOutput> outputs) {
        int mask = 0;
        for (int index = 0; index < outputs.size(); index++) {
            if (outputs.get(index).probabilistic()) mask |= 1 << index;
        }
        return mask;
    }

    private static Optional<AggregateInputSlot> input(EmiIngredient ingredient, int limit) {
        List<GenericStack> alternatives = ingredient.getEmiStacks().stream()
                .map(s -> stack(s.copy().setAmount(ingredient.getAmount())))
                .flatMap(Optional::stream).limit(limit).toList();
        return alternatives.isEmpty() ? Optional.empty() : Optional.of(new AggregateInputSlot(alternatives, Optional.empty()));
    }

    private static Optional<GenericStack> stack(EmiStack stack) {
        if (stack.isEmpty() || stack.getAmount() <= 0) return Optional.empty();
        for (var converter : EmiStackConverters.getConverters()) {
            try {
                GenericStack converted = converter.toGenericStack(stack);
                if (converted != null) return Optional.of(converted);
            } catch (RuntimeException ignored) {}
        }
        ItemStack item = stack.getItemStack();
        if (!item.isEmpty()) { item.setCount((int) Math.min(Integer.MAX_VALUE, stack.getAmount())); return Optional.ofNullable(GenericStack.fromItemStack(item)); }
        if (stack.getKey() instanceof Fluid fluid) return Optional.ofNullable(GenericStack.fromFluidStack(
                new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, stack.getAmount()))));
        if (stack.getClass().getName().equals("mekanism.client.recipe_viewer.emi.ChemicalEmiStack")) {
            return mekanismChemical(stack);
        }
        return stack instanceof TMRVStack<?> tmrv ? registered(tmrv) : Optional.empty();
    }

    private static Optional<GenericStack> mekanismChemical(EmiStack stack) {
        try {
            Object chemicalStack = stack.getClass().getMethod("getStack").invoke(stack);
            Class<?> chemicalStackType = Class.forName("mekanism.api.chemical.ChemicalStack");
            Object key = Class.forName("me.ramidzkh.mekae2.ae2.MekanismKey")
                    .getMethod("of", chemicalStackType).invoke(null, chemicalStack);
            return Optional.of(new GenericStack((appeng.api.stacks.AEKey) key, stack.getAmount()));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<GenericStack> registered(TMRVStack<?> stack) {
        try {
            Class<?> converters = Class.forName("appeng.api.integrations.jei.IngredientConverters");
            Object converter = converters.getMethod("getConverter", mezz.jei.api.ingredients.IIngredientType.class).invoke(null, stack.type);
            if (converter == null) return Optional.empty();
            Method convert = Class.forName("appeng.api.integrations.jei.IngredientConverter")
                    .getMethod("getStackFromIngredient", Object.class);
            GenericStack result = (GenericStack) convert.invoke(converter, stack.ingredient);
            return result == null ? Optional.empty() : Optional.of(new GenericStack(result.what(), stack.getAmount()));
        } catch (ReflectiveOperationException | RuntimeException ignored) { return Optional.empty(); }
    }

    private static AggregatePatternKind kind(Recipe<?> recipe) {
        if (recipe instanceof CraftingRecipe) return AggregatePatternKind.CRAFTING;
        if (recipe instanceof StonecutterRecipe) return AggregatePatternKind.STONECUTTING;
        if (recipe instanceof SmithingRecipe) return AggregatePatternKind.SMITHING;
        return AggregatePatternKind.PROCESSING;
    }

    private static void show(String key) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
    }

    private record ScannedOutput(GenericStack stack, boolean probabilistic) {}
}
