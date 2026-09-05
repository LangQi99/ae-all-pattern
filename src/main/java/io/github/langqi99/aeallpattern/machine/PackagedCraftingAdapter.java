package io.github.langqi99.aeallpattern.machine;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.crafting.IPatternDetails;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternExpander;
import io.github.langqi99.aeallpattern.aggregate.AggregateInputSlot;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternKind;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternLibrary;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternOptions;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternRef;
import io.github.langqi99.aeallpattern.aggregate.AggregateRecipe;
import io.github.langqi99.aeallpattern.binding.BindingRecord;
import io.github.langqi99.aeallpattern.recipe.RecipeCatalog;
import io.github.langqi99.aeallpattern.recipe.RecipeFingerprint;
import io.github.langqi99.aeallpattern.recipe.RecipeSnapshot;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/** Optional, dependency-free bridge to PackagedAuto's atomic crafting-machine protocol. */
final class PackagedCraftingAdapter implements MachineAdapter {
    private static final ResourceLocation ID = id("aeallpattern", "packaged_crafting");
    private static final Map<ResourceLocation, Spec> MACHINES = machineSpecs();
    private static final Map<ResourceLocation, ResourceLocation> AGGREGATE_CATALYST_ALIASES = Map.ofEntries(
            alias("extendedcrafting", "basic_table", "packagedexcrafting", "basic_crafter"),
            alias("extendedcrafting", "advanced_table", "packagedexcrafting", "advanced_crafter"),
            alias("extendedcrafting", "elite_table", "packagedexcrafting", "elite_crafter"),
            alias("extendedcrafting", "ultimate_table", "packagedexcrafting", "ultimate_crafter"),
            alias("extendedcrafting", "ender_crafter", "packagedexcrafting", "ender_crafter"),
            alias("extendedcrafting", "flux_crafter", "packagedexcrafting", "flux_crafter"),
            alias("extendedcrafting", "crafting_core", "packagedexcrafting", "combination_crafter"),
            alias("applied_extended_crafting", "table_basic_pattern_provider", "packagedexcrafting", "basic_crafter"),
            alias("applied_extended_crafting", "table_advanced_pattern_provider", "packagedexcrafting", "advanced_crafter"),
            alias("applied_extended_crafting", "table_elite_pattern_provider", "packagedexcrafting", "elite_crafter"),
            alias("applied_extended_crafting", "table_ultimate_pattern_provider", "packagedexcrafting", "ultimate_crafter"),
            alias("applied_extended_crafting", "ender_crafter_pattern_provider", "packagedexcrafting", "ender_crafter"),
            alias("applied_extended_crafting", "flux_crafter_pattern_provider", "packagedexcrafting", "flux_crafter"),
            alias("applied_extended_crafting", "crafter_core_pattern_provider", "packagedexcrafting", "combination_crafter"),
            alias("avaritia", "sculk_crafting_table", "packagedavaritia", "sculk_crafter"),
            alias("avaritia", "nether_crafting_table", "packagedavaritia", "nether_crafter"),
            alias("avaritia", "end_crafting_table", "packagedavaritia", "end_crafter"),
            alias("avaritia", "extreme_crafting_table", "packagedavaritia", "extreme_crafter"));

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
        return MACHINES.containsKey(BuiltInRegistries.BLOCK.getKey(target.getBlockState().getBlock()));
    }

    @Override
    public RecipeCatalog discoverRecipes(ServerLevel level, BlockEntity target, long generation) {
        Spec spec = spec(target);
        if (spec == null) {
            return new RecipeCatalog(generation, List.of(), 0);
        }
        RecipeType<?> recipeType = BuiltInRegistries.RECIPE_TYPE.getOptional(spec.recipeType).orElse(null);
        if (recipeType == null) {
            return new RecipeCatalog(generation, List.of(), 0);
        }
        List<RecipeSnapshot> snapshots = new ArrayList<>();
        int filtered = 0;
        List<Recipe<?>> holders = recipes(level, recipeType);
        for (Recipe<?> recipe : holders.stream()
                .sorted(Comparator.comparing(candidate -> candidate.getId().toString())).toList()) {
            try {
                if (spec.tier > 0 && !supportsTier(spec.tier, invokeInt(recipe, "getTier"))) {
                    continue;
                }
                RecipeLayout layout = layout(spec, recipe);
                Object info = createRecipeInfo(level, spec, recipe.getId(), layout, layout.primaryInputs());
                if (!invokeBoolean(info, "isValid")) {
                    filtered++;
                    continue;
                }
                ItemStack output = firstOutput(info);
                if (output.isEmpty()) {
                    filtered++;
                    continue;
                }
                String normalizedInput = layout.inputAlternatives.stream()
                        .map(alternatives -> alternatives.stream().map(PackagedCraftingAdapter::normalize).toList().toString())
                        .toList().toString();
                RecipeFingerprint fingerprint = new RecipeFingerprint(
                        id().toString(), recipe.getId().toString(), normalizedInput, normalize(output), schemaVersion());
                snapshots.add(RecipeSnapshot.withAlternatives(
                        recipe.getId(), layout.inputAlternatives, output, fingerprint, processingTicks(info)));
            } catch (ReflectiveOperationException | RuntimeException error) {
                filtered++;
                AeAllPattern.LOGGER.debug("Skipping unsupported packaged recipe {}", recipe.getId(), error);
            }
        }
        snapshots.sort(Comparator.comparing(snapshot -> snapshot.fingerprint().stableKey()));
        return new RecipeCatalog(generation, snapshots, filtered);
    }

    @Override
    public boolean insert(ServerLevel level, BindingRecord binding, ItemStack stack) {
        return false;
    }

    @Override
    public boolean insertRecipe(
            ServerLevel level, BindingRecord binding, RecipeSnapshot snapshot, List<ItemStack> inputs) {
        BlockEntity target = level.getBlockEntity(binding.target().pos());
        Spec spec = target == null ? null : spec(target);
        if (spec == null) {
            return false;
        }
        try {
            Recipe<?> recipe = level.getRecipeManager().byKey(snapshot.recipeId()).orElse(null);
            if (recipe == null
                    || (spec.tier > 0 && !supportsTier(spec.tier, invokeInt(recipe, "getTier")))) {
                return false;
            }
            RecipeLayout layout = layout(spec, recipe);
            if (inputs.size() != layout.inputAlternatives.size()) {
                return false;
            }
            Object info = createRecipeInfo(level, spec, recipe.getId(), layout, inputs);
            ItemStack actualOutput = firstOutput(info);
            ItemStack expectedOutput = snapshot.output();
            if (!invokeBoolean(info, "isValid")
                    || !ItemStack.isSameItemSameTags(actualOutput, expectedOutput)
                    || actualOutput.getCount() != expectedOutput.getCount()) {
                return false;
            }
            Method accept = java.util.Arrays.stream(target.getClass().getMethods())
                    .filter(method -> method.getName().equals("acceptPackage") && method.getParameterCount() == 3)
                    .findFirst().orElse(null);
            if (accept == null) {
                return false;
            }
            @SuppressWarnings("unchecked")
            List<ItemStack> condensed = (List<ItemStack>) info.getClass().getMethod("getInputs").invoke(info);
            return Boolean.TRUE.equals(accept.invoke(target, info, condensed, binding.clickedSide()));
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug("Packaged machine rejected recipe {}", snapshot.recipeId(), error);
            return false;
        }
    }

    @Override
    public ItemStack extractAnyOutput(ServerLevel level, BindingRecord binding, boolean simulate) {
        for (Direction side : preferredFirst(binding.clickedSide())) {
            ItemStack extracted = ItemHandlerTransfer.extractAny(
                    ItemHandlerTransfer.find(level, binding.target().pos(), side), simulate);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemHandlerTransfer.extractAny(
                ItemHandlerTransfer.find(level, binding.target().pos(), null), simulate);
    }

    static List<Object> packageRecipeInfos(ServerLevel level, ItemStack aggregate) {
        AggregatePatternRef ref = ModDataComponents.getAggregatePattern(aggregate);
        if (ref == null) {
            return List.of();
        }
        ResourceLocation machineId = AGGREGATE_CATALYST_ALIASES.getOrDefault(ref.catalystId(), ref.catalystId());
        Spec spec = MACHINES.get(machineId);
        List<AggregateRecipe> recipes = AggregatePatternLibrary.get(level.getServer())
                .recipes(level.getServer(), ref.libraryId()).orElse(List.of());
        AggregatePatternOptions savedOptions = ModDataComponents.getAggregatePatternOptions(aggregate);
        AggregatePatternOptions options = savedOptions == null ? AggregatePatternOptions.DEFAULT : savedOptions;
        List<Object> result = new ArrayList<>(recipes.size());
        for (AggregateRecipe aggregateRecipe : recipes) {
            Object info = spec == null
                    ? processingRecipeInfo(level, options, aggregateRecipe)
                    : specializedRecipeInfo(level, spec, aggregateRecipe);
            try {
                if (info != null && invokeBoolean(info, "isValid")) {
                    result.add(info);
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                AeAllPattern.LOGGER.debug(
                        "Skipping aggregate package recipe {}", aggregateRecipe.recipeId(), error);
            }
        }
        return List.copyOf(result);
    }

    private static Object specializedRecipeInfo(
            ServerLevel level, Spec spec, AggregateRecipe aggregateRecipe) {
        try {
            ResourceLocation recipeId = serverRecipeId(aggregateRecipe.recipeId());
            Recipe<?> recipe = level.getRecipeManager().byKey(recipeId).orElse(null);
            if (recipe == null
                    || (spec.tier > 0 && !supportsTier(spec.tier, invokeInt(recipe, "getTier")))) {
                return null;
            }
            RecipeLayout layout = layout(spec, recipe);
            Object info = createRecipeInfo(level, spec, recipe.getId(), layout, layout.primaryInputs());
            return invokeBoolean(info, "isValid") && matchesOutput(info, aggregateRecipe) ? info : null;
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug(
                    "Unable to rebuild specialized package recipe {}", aggregateRecipe.recipeId(), error);
            return null;
        }
    }

    private static ResourceLocation serverRecipeId(ResourceLocation recipeId) {
        if (!recipeId.getNamespace().equals("toomanyrecipeviewers") || !recipeId.getPath().startsWith("/")) {
            return recipeId;
        }
        String value = recipeId.getPath().substring(1);
        int separator = value.indexOf('/');
        return separator > 0 && separator < value.length() - 1
                ? new ResourceLocation(value.substring(0, separator), value.substring(separator + 1))
                : recipeId;
    }

    private static Object processingRecipeInfo(
            ServerLevel level, AggregatePatternOptions options, AggregateRecipe recipe) {
        if (recipe.kind() != AggregatePatternKind.PROCESSING) {
            return null;
        }
        try {
            IPatternDetails details = AggregatePatternExpander.expandRecipe(
                    recipe, options, level, "aggregate-package:" + recipe.patternId());
            if (details == null) {
                return null;
            }
            List<ItemStack> inputs = new ArrayList<>(details.getInputs().length);
            for (IPatternDetails.IInput input : details.getInputs()) {
                GenericStack[] possible = input.getPossibleInputs();
                if (possible.length == 0) {
                    return null;
                }
                ItemStack stack = itemStack(possible[0], input.getMultiplier());
                if (stack.isEmpty()) {
                    return null;
                }
                inputs.add(stack);
            }
            List<ItemStack> outputs = new ArrayList<>(details.getOutputs().length);
            for (GenericStack output : details.getOutputs()) {
                ItemStack stack = itemStack(output, 1);
                if (stack.isEmpty()) {
                    return null;
                }
                outputs.add(stack);
            }
            Class<?> infoClass = Class.forName("thelm.packagedauto.recipe.ProcessingPackageRecipeInfo");
            try {
                return infoClass.getConstructor(List.class, List.class).newInstance(inputs, outputs);
            } catch (NoSuchMethodException ignored) {
                Object info = infoClass.getConstructor().newInstance();
                infoClass.getMethod("generateFromStacks", List.class, List.class, net.minecraft.world.level.Level.class)
                        .invoke(info, inputs, outputs, level);
                return info;
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            AeAllPattern.LOGGER.debug(
                    "Unable to rebuild processing package recipe {}", recipe.recipeId(), error);
            return null;
        }
    }

    private static ItemStack itemStack(GenericStack stack, long multiplier) {
        if (!(stack.what() instanceof AEItemKey key)) {
            return ItemStack.EMPTY;
        }
        long amount = Math.multiplyExact(stack.amount(), multiplier);
        return amount > 0 && amount <= Integer.MAX_VALUE ? key.toStack((int) amount) : ItemStack.EMPTY;
    }

    private static boolean matchesOutput(Object info, AggregateRecipe recipe) throws ReflectiveOperationException {
        if (!(recipe.outputs().get(0).what() instanceof AEItemKey key)
                || recipe.outputs().get(0).amount() > Integer.MAX_VALUE) {
            return false;
        }
        ItemStack actual = firstOutput(info);
        ItemStack expected = key.toStack((int) recipe.outputs().get(0).amount());
        return ItemStack.isSameItemSameTags(actual, expected) && actual.getCount() == expected.getCount();
    }

    private static RecipeLayout layout(Spec spec, Recipe<?> recipe) throws ReflectiveOperationException {
        List<Ingredient> ingredients = new ArrayList<>();
        boolean combination = spec.kind == Kind.COMBINATION;
        if (combination) {
            ingredients.add((Ingredient) recipe.getClass().getMethod("getInput").invoke(recipe));
        }
        ingredients.addAll(recipe.getIngredients());

        List<List<ItemStack>> alternatives = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        int offset = combination ? 1 : 0;
        for (int index = 0; index < ingredients.size(); index++) {
            Ingredient ingredient = ingredients.get(index);
            ItemStack[] variants = ingredient.getItems();
            if (variants.length == 0) {
                continue;
            }
            List<ItemStack> choices = java.util.Arrays.stream(variants)
                    .map(stack -> stack.copyWithCount(Math.max(1, stack.getCount())))
                    .sorted(Comparator.comparing(PackagedCraftingAdapter::normalize))
                    .limit(AggregateInputSlot.configuredAlternativeLimit())
                    .toList();
            alternatives.add(choices);
            positions.add(index - offset);
        }
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("recipe has no item inputs");
        }
        int width = combination ? 0 : invokeIntOr(recipe, "getWidth", spec.gridSize);
        int height = combination ? 0 : invokeIntOr(recipe, "getHeight", spec.gridSize);
        return new RecipeLayout(List.copyOf(alternatives), List.copyOf(positions), width, height);
    }

    private static Object createRecipeInfo(
            ServerLevel level, Spec spec, ResourceLocation recipeId, RecipeLayout layout, List<ItemStack> supplied)
            throws ReflectiveOperationException {
        Class<?> infoClass = Class.forName(spec.recipeInfoClass);
        try {
            if (spec.kind == Kind.COMBINATION) {
                Constructor<?> constructor = infoClass.getConstructor(ResourceLocation.class, ItemStack.class, List.class);
                return constructor.newInstance(
                        recipeId, supplied.get(0).copy(), copy(supplied.subList(1, supplied.size())));
            }
            Constructor<?> constructor = infoClass.getConstructor(
                    ResourceLocation.class, int.class, int.class, List.class);
            return constructor.newInstance(recipeId, layout.width, layout.height, craftingMatrix(layout, supplied));
        } catch (NoSuchMethodException ignored) {
            // PackagedExCrafting 1.20.1 builds their recipe info through the encoder's
            // 9x9 slot layout instead of the constructors used by newer releases.
            Object info = infoClass.getConstructor().newInstance();
            Method generate = infoClass.getMethod("generateFromStacks", List.class, List.class, net.minecraft.world.level.Level.class);
            generate.invoke(info, encoderStacks(spec, layout, supplied), List.of(), level);
            return info;
        }
    }

    private static List<ItemStack> craftingMatrix(RecipeLayout layout, List<ItemStack> supplied) {
        int size = Math.multiplyExact(layout.width, layout.height);
        List<ItemStack> matrix = new ArrayList<>(java.util.Collections.nCopies(size, ItemStack.EMPTY));
        for (int index = 0; index < supplied.size(); index++) {
            int position = layout.positions.get(index);
            if (position < 0 || position >= matrix.size()) {
                throw new IllegalArgumentException("recipe input lies outside its crafting grid");
            }
            matrix.set(position, supplied.get(index).copy());
        }
        return matrix;
    }

    private static List<ItemStack> encoderStacks(Spec spec, RecipeLayout layout, List<ItemStack> supplied) {
        List<ItemStack> encoder = new ArrayList<>(java.util.Collections.nCopies(81, ItemStack.EMPTY));
        if (spec.kind == Kind.COMBINATION) {
            encoder.set(40, supplied.get(0).copy());
            int suppliedIndex = 1;
            for (int row = 1; row < 8 && suppliedIndex < supplied.size(); row++) {
                for (int column = 1; column < 8 && suppliedIndex < supplied.size(); column++) {
                    int slot = row * 9 + column;
                    if (slot != 40) {
                        encoder.set(slot, supplied.get(suppliedIndex++).copy());
                    }
                }
            }
            return encoder;
        }

        List<ItemStack> matrix = craftingMatrix(layout, supplied);
        int offset = (9 - spec.gridSize) / 2;
        for (int position = 0; position < matrix.size(); position++) {
            int row = position / layout.width;
            int column = position % layout.width;
            encoder.set((row + offset) * 9 + column + offset, matrix.get(position).copy());
        }
        return encoder;
    }

    private static ItemStack firstOutput(Object info) throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        List<ItemStack> outputs = (List<ItemStack>) info.getClass().getMethod("getOutputs").invoke(info);
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).copy();
    }

    private static int processingTicks(Object info) {
        try {
            return Math.max(1, invokeInt(info, "getTimeRequired"));
        } catch (ReflectiveOperationException ignored) {
            return 200;
        }
    }

    private static int invokeInt(Object target, String method) throws ReflectiveOperationException {
        return ((Number) target.getClass().getMethod(method).invoke(target)).intValue();
    }

    private static int invokeIntOr(Object target, String method, int fallback) {
        try {
            return invokeInt(target, method);
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    private static boolean invokeBoolean(Object target, String method) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(target.getClass().getMethod(method).invoke(target));
    }

    private static Spec spec(BlockEntity target) {
        return MACHINES.get(BuiltInRegistries.BLOCK.getKey(target.getBlockState().getBlock()));
    }

    @SuppressWarnings({"unchecked", "rawtypes", "cast"})
    private static List<Recipe<?>> recipes(ServerLevel level, RecipeType<?> type) {
        return (List) level.getRecipeManager().getAllRecipesFor((RecipeType) type);
    }

    private static List<ItemStack> copy(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    private static List<Direction> preferredFirst(Direction preferred) {
        List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
        sides.remove(preferred);
        sides.add(0, preferred);
        return sides;
    }

    private static String normalize(ItemStack stack) {
        return AEItemKey.of(stack) + "*" + stack.getCount();
    }

    static boolean supportsTier(int machineTier, int recipeTier) {
        return recipeTier <= machineTier;
    }

    private static Map<ResourceLocation, Spec> machineSpecs() {
        Map<ResourceLocation, Spec> specs = new LinkedHashMap<>();
        addGrid(specs, "packagedexcrafting", "basic_crafter", "extendedcrafting:table",
                "thelm.packagedexcrafting.recipe.BasicPackageRecipeInfo", 1, 3);
        addGrid(specs, "packagedexcrafting", "advanced_crafter", "extendedcrafting:table",
                "thelm.packagedexcrafting.recipe.AdvancedPackageRecipeInfo", 2, 5);
        addGrid(specs, "packagedexcrafting", "elite_crafter", "extendedcrafting:table",
                "thelm.packagedexcrafting.recipe.ElitePackageRecipeInfo", 3, 7);
        addGrid(specs, "packagedexcrafting", "ultimate_crafter", "extendedcrafting:table",
                "thelm.packagedexcrafting.recipe.UltimatePackageRecipeInfo", 4, 9);
        addGrid(specs, "packagedexcrafting", "ender_crafter", "extendedcrafting:ender_crafter",
                "thelm.packagedexcrafting.recipe.EnderPackageRecipeInfo", 0, 3);
        addGrid(specs, "packagedexcrafting", "flux_crafter", "extendedcrafting:flux_crafter",
                "thelm.packagedexcrafting.recipe.FluxPackageRecipeInfo", 0, 3);
        specs.put(id("packagedexcrafting", "combination_crafter"), new Spec(
                id("extendedcrafting", "combination"),
                "thelm.packagedexcrafting.recipe.CombinationPackageRecipeInfo", 0, 0, Kind.COMBINATION));
        addGrid(specs, "packagedavaritia", "sculk_crafter", "avaritia:crafting_table_recipe",
                "thelm.packagedavaritia.recipe.SculkPackageRecipeInfo", 1, 3);
        addGrid(specs, "packagedavaritia", "nether_crafter", "avaritia:crafting_table_recipe",
                "thelm.packagedavaritia.recipe.NetherPackageRecipeInfo", 2, 5);
        addGrid(specs, "packagedavaritia", "end_crafter", "avaritia:crafting_table_recipe",
                "thelm.packagedavaritia.recipe.EndPackageRecipeInfo", 3, 7);
        addGrid(specs, "packagedavaritia", "extreme_crafter", "avaritia:crafting_table_recipe",
                "thelm.packagedavaritia.recipe.ExtremePackageRecipeInfo", 4, 9);
        return Map.copyOf(specs);
    }

    private static void addGrid(
            Map<ResourceLocation, Spec> specs,
            String namespace,
            String path,
            String recipeType,
            String recipeInfoClass,
            int tier,
            int gridSize) {
        specs.put(id(namespace, path), new Spec(
                new ResourceLocation(recipeType), recipeInfoClass, tier, gridSize, Kind.GRID));
    }

    private static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    private static Map.Entry<ResourceLocation, ResourceLocation> alias(
            String sourceNamespace, String sourcePath, String targetNamespace, String targetPath) {
        return Map.entry(id(sourceNamespace, sourcePath), id(targetNamespace, targetPath));
    }

    private enum Kind {
        GRID,
        COMBINATION
    }

    private record Spec(
            ResourceLocation recipeType, String recipeInfoClass, int tier, int gridSize, Kind kind) {
    }

    private record RecipeLayout(
            List<List<ItemStack>> inputAlternatives, List<Integer> positions, int width, int height) {
        private List<ItemStack> primaryInputs() {
            return inputAlternatives.stream().map(alternatives -> alternatives.get(0)).map(ItemStack::copy).toList();
        }
    }
}
