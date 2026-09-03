package io.github.langqi99.aeallpattern.aggregate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.recipe.RecipeIndexService;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.registry.ModItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

public final class AggregatePatternExpander {
    static final int MAX_SPLIT_ITEM_INPUTS = 4096;
    private static final ResourceLocation CHEMICAL_KEY_TYPE =
            ResourceLocation.fromNamespaceAndPath("appmek", "chemical");
    static final TagKey<Item> PROCESSING_CATALYSTS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("aeallpattern", "processing_catalysts"));

    /**
     * Expanding one child costs an encode+decode round trip through encoded pattern NBT, so a
     * large aggregate would otherwise re-do thousands of those on every provider refresh, for
     * every provider. Expanded details are immutable and shareable, so cache them server-wide
     * keyed by everything the result can depend on. The cap is generous so multi-thousand
     * recipe aggregates (e.g. 18k patterns) survive the whole session without eviction: an
     * evicted aggregate would re-expand everything on the next terminal open and stall the
     * server again.
     */
    private static final int CACHE_MAX_ENTRIES = 256;
    private static final Map<MinecraftServer, LinkedHashMap<CacheKey, List<IPatternDetails>>> EXPANSION_CACHE =
            new WeakHashMap<>();

    /**
     * Single-child cache used by {@link #expandRecipe}. Live linker providers re-expand every
     * child on every refresh, so without this a 2000-recipe machine would re-do the whole
     * encode+decode round trip each time. Keyed by everything the result can depend on:
     * the virtual id prefix (caller), the recipe, the option flags and the recipe generation.
     * Sized well above any realistic aggregate recipe count (18k+ is common in kitchen-sink
     * packs) so a full expansion stays cached instead of thrashing.
     */
    private static final int RECIPE_CACHE_MAX_ENTRIES = 200_000;
    private static final Map<MinecraftServer, LinkedHashMap<RecipeCacheKey, IPatternDetails>> RECIPE_EXPANSION_CACHE =
            new WeakHashMap<>();

    /**
     * Cold expansions run across server ticks with a small budget per tick so a multi-thousand
     * recipe aggregate never freezes the server thread. Only the provider-facing path uses this;
     * tests and decoders keep the synchronous {@link #expand}.
     */
    private static final long SCHEDULED_BUDGET_NANOS = 2_000_000L;
    private static final int SCHEDULED_MAX_STEPS_PER_TICK = 256;
    private static final Map<MinecraftServer, List<PendingExpansion>> PENDING = new WeakHashMap<>();

    /** True in GameTest runs: the scheduled path then behaves like the synchronous one. */
    private static volatile boolean synchronousMode;

    private record CacheKey(
            UUID libraryId, long recipeGeneration, String contentHash, int recipeCount,
            int optionsFlags, int selectionHash) {
    }

    /** Key for the per-child cache: the caller's virtual id prefix plus the recipe identity. */
    private record RecipeCacheKey(String prefix, String patternId, int optionsFlags, long generation) {
    }

    /** Resolved inputs of one expansion request, shared by both paths. */
    private record ExpansionContext(
            AggregatePatternRef ref,
            net.minecraft.server.level.ServerLevel level,
            List<AggregateRecipe> recipes,
            AggregatePatternOptions options,
            AggregatePatternSelection selection,
            CacheKey key,
            Map<CacheKey, List<IPatternDetails>> cache) {
    }

    private static final class PendingExpansion {
        final CacheKey key;
        final long recipeGeneration;
        final net.minecraft.server.level.ServerLevel level;
        final AggregatePatternOptions options;
        final AggregatePatternSelection selection;
        final List<AggregateRecipe> recipes;
        final List<IPatternDetails> partial = new ArrayList<>();
        final List<Runnable> completionCallbacks = new ArrayList<>();
        int cursor;

        PendingExpansion(ExpansionContext context) {
            this.key = context.key();
            this.recipeGeneration = RecipeIndexService.generation();
            this.level = context.level();
            this.options = context.options();
            this.selection = context.selection();
            this.recipes = context.recipes();
        }
    }

    private AggregatePatternExpander() {
    }

    /** Drops every cached expansion; called when the recipe datapack generation advances. */
    public static void clearCaches() {
        EXPANSION_CACHE.clear();
        RECIPE_EXPANSION_CACHE.clear();
    }

    /** GameTest hook: forces the scheduled path to complete synchronously. */
    public static void setSynchronous(boolean value) {
        synchronousMode = value;
    }

    public static boolean isSynchronous() {
        return synchronousMode;
    }

    private static ExpansionContext resolveContext(ItemStack aggregateStack, Level level) {
        AggregatePatternRef ref = aggregateStack.get(ModDataComponents.AGGREGATE_PATTERN.get());
        if (!aggregateStack.is(ModItems.AGGREGATE_PATTERN.get()) || ref == null
                || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }
        MinecraftServer server = serverLevel.getServer();
        List<AggregateRecipe> recipes = AggregatePatternLibrary.get(server)
                .recipes(server, ref.libraryId()).orElse(List.of());
        AggregatePatternOptions savedOptions =
                aggregateStack.get(ModDataComponents.AGGREGATE_PATTERN_OPTIONS.get());
        AggregatePatternOptions options = savedOptions == null ? AggregatePatternOptions.DEFAULT : savedOptions;
        AggregatePatternSelection selection =
                aggregateStack.get(ModDataComponents.AGGREGATE_PATTERN_SELECTION.get());

        int selectionHash = selection == null || selection.isAllEnabled() ? 0 : selection.hashCode();
        CacheKey key = new CacheKey(
                ref.libraryId(), RecipeIndexService.generation(),
                AggregatePatternLibrary.get(server).find(ref.libraryId())
                        .map(AggregatePatternLibrary.Entry::contentHash).orElse("missing"),
                recipes.size(), options.flags(), selectionHash);
        Map<CacheKey, List<IPatternDetails>> cache =
                EXPANSION_CACHE.computeIfAbsent(server, ignored -> new LinkedHashMap<>(16, 0.75F, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<CacheKey, List<IPatternDetails>> eldest) {
                        return size() > CACHE_MAX_ENTRIES;
                    }
                });
        return new ExpansionContext(ref, serverLevel, recipes, options, selection, key, cache);
    }

    /** One child expansion shared by both the synchronous and the scheduled path. */
    private static IPatternDetails expandOne(
            AggregateRecipe recipe,
            AggregatePatternOptions options,
            AggregatePatternSelection selection,
            net.minecraft.server.level.ServerLevel level) {
        if (selection != null && !selection.isEnabled(recipe.patternId())) {
            return null;
        }
        // Folding the selection into the virtual id makes providers observe selection
        // changes the same way they observe option-flag changes.
        String virtualIdPrefix = "aggregate:" + recipe.patternId();
        if (selection != null && !selection.isAllEnabled()) {
            virtualIdPrefix += ":sel=" + Integer.toUnsignedString(selection.hashCode(), 16);
        }
        try {
            return expandRecipe(recipe, options, level, virtualIdPrefix);
        } catch (RuntimeException error) {
            AeAllPattern.LOGGER.debug(
                    "Failed to expand aggregate child {} as {}", recipe.recipeId(), recipe.kind(), error);
            return null;
        }
    }

    /**
     * Expands only the first publishable child (cached if possible). Decoders use this instead
     * of the full expansion so slot-validity checks never pay the cost of expanding every child.
     */
    public static IPatternDetails expandFirst(ItemStack aggregateStack, Level level) {
        ExpansionContext context = resolveContext(aggregateStack, level);
        if (context == null) {
            return null;
        }
        List<IPatternDetails> cached = context.cache().get(context.key());
        if (cached != null && !cached.isEmpty()) {
            return cached.getFirst();
        }
        for (AggregateRecipe recipe : context.recipes()) {
            IPatternDetails details = expandOne(recipe, context.options(), context.selection(), context.level());
            if (details != null) {
                return details;
            }
        }
        return null;
    }

    public static List<IPatternDetails> expand(ItemStack aggregateStack, Level level) {
        ExpansionContext context = resolveContext(aggregateStack, level);
        if (context == null) {
            return List.of();
        }
        List<IPatternDetails> cached = context.cache().get(context.key());
        if (cached != null) {
            return cached;
        }

        List<IPatternDetails> expanded = new ArrayList<>(context.recipes().size());
        for (AggregateRecipe recipe : context.recipes()) {
            IPatternDetails details = expandOne(recipe, context.options(), context.selection(), context.level());
            if (details != null) {
                expanded.add(details);
            }
        }
        List<IPatternDetails> frozen = List.copyOf(expanded);
        context.cache().put(context.key(), frozen);
        return frozen;
    }

    /**
     * Provider-facing expansion. Returns whatever children are published right now; a cold
     * expansion is spread over the following ticks with a tiny per-tick budget, and the given
     * callback re-runs the provider refresh once the complete list is ready. The synchronous
     * {@link #expand} path (and GameTest mode) keeps full immediacy.
     */
    public static List<IPatternDetails> expandScheduled(
            ItemStack aggregateStack, Level level, Runnable onCompletion) {
        ExpansionContext context = resolveContext(aggregateStack, level);
        if (context == null) {
            return List.of();
        }
        List<IPatternDetails> cached = context.cache().get(context.key());
        if (cached != null) {
            return cached;
        }
        if (synchronousMode) {
            return expand(aggregateStack, level);
        }

        MinecraftServer server = context.level().getServer();
        List<PendingExpansion> jobs = PENDING.computeIfAbsent(server, ignored -> new ArrayList<>());
        for (PendingExpansion job : jobs) {
            if (job.key.equals(context.key())) {
                if (onCompletion != null) {
                    job.completionCallbacks.add(onCompletion);
                }
                return List.copyOf(job.partial);
            }
        }
        PendingExpansion job = new PendingExpansion(context);
        if (onCompletion != null) {
            job.completionCallbacks.add(onCompletion);
        }
        jobs.add(job);
        return List.copyOf(job.partial);
    }

    /** Advances every pending scheduled expansion within the per-tick budget. Server thread only. */
    public static void tickServer(MinecraftServer server) {
        List<PendingExpansion> jobs = PENDING.get(server);
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        long deadline = System.nanoTime() + SCHEDULED_BUDGET_NANOS;
        List<Runnable> completions = new ArrayList<>();
        java.util.Iterator<PendingExpansion> iterator = jobs.iterator();
        while (iterator.hasNext()) {
            PendingExpansion job = iterator.next();
            if (job.recipeGeneration != RecipeIndexService.generation()) {
                iterator.remove();
                completions.addAll(job.completionCallbacks);
                continue;
            }
            int steps = 0;
            while (job.cursor < job.recipes.size() && steps < SCHEDULED_MAX_STEPS_PER_TICK) {
                AggregateRecipe recipe = job.recipes.get(job.cursor++);
                steps++;
                IPatternDetails details = expandOne(recipe, job.options, job.selection, job.level);
                if (details != null) {
                    job.partial.add(details);
                }
                if (System.nanoTime() >= deadline) {
                    runCompletions(completions);
                    return;
                }
            }
            if (job.cursor >= job.recipes.size()) {
                List<IPatternDetails> frozen = List.copyOf(job.partial);
                EXPANSION_CACHE.computeIfAbsent(server, ignored -> new LinkedHashMap<>(16, 0.75F, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<CacheKey, List<IPatternDetails>> eldest) {
                        return size() > CACHE_MAX_ENTRIES;
                    }
                }).put(job.key, frozen);
                iterator.remove();
                AeAllPattern.LOGGER.info(
                        "Aggregate expansion completed: library={}, recipes={}, callbacks={}",
                        job.key.libraryId(), job.recipes.size(), job.completionCallbacks.size());
                completions.addAll(job.completionCallbacks);
            }
        }
        runCompletions(completions);
    }

    private static void runCompletions(List<Runnable> completions) {
        for (Runnable callback : completions) {
            try {
                callback.run();
            } catch (RuntimeException error) {
                AeAllPattern.LOGGER.debug("Aggregate expansion completion callback failed", error);
            }
        }
    }

    /** Shared child-pattern path used by aggregate items and live linker providers. */
    public static IPatternDetails expandRecipe(
            AggregateRecipe recipe,
            AggregatePatternOptions options,
            net.minecraft.server.level.ServerLevel level,
            String virtualIdPrefix) {
        Map<RecipeCacheKey, IPatternDetails> cache =
                RECIPE_EXPANSION_CACHE.computeIfAbsent(level.getServer(), ignored -> new LinkedHashMap<>(256, 0.75F, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<RecipeCacheKey, IPatternDetails> eldest) {
                        return size() > RECIPE_CACHE_MAX_ENTRIES;
                    }
                });
        RecipeCacheKey key = new RecipeCacheKey(
                virtualIdPrefix, recipe.patternId(), options.flags(), RecipeIndexService.generation());
        IPatternDetails cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        IPatternDetails expanded = expandRecipeUncached(recipe, options, level, virtualIdPrefix);
        if (expanded != null) {
            cache.put(key, expanded);
        }
        return expanded;
    }

    private static IPatternDetails expandRecipeUncached(
            AggregateRecipe recipe,
            AggregatePatternOptions options,
            net.minecraft.server.level.ServerLevel level,
            String virtualIdPrefix) {
        if (options.skipProbabilisticMainOutput() && recipe.isProbabilisticOutput(0)) {
            return null;
        }
        if (recipe.kind() == AggregatePatternKind.PROCESSING
                && recipe.outputs().stream().allMatch(stack -> removeOutput(stack.what(), options))) {
            return null;
        }
        AggregateRecipe configuredRecipe = configureOutputs(recipe, options);
        List<AggregateInputSlot> configuredInputs = configuredProcessingInputs(
                configuredRecipe, options, level);
        if (filtersProcessingInputs(options)
                && configuredRecipe.kind() == AggregatePatternKind.PROCESSING
                && configuredInputs.isEmpty()) {
            return null;
        }
        ItemStack encoded = encode(configuredRecipe, level, options);
        if (encoded.isEmpty()) {
            return null;
        }
        IPatternDetails delegate = PatternDetailsHelper.decodePattern(encoded, level);
        if (delegate == null) {
            return null;
        }
        if (options.skipDurabilityConsumingRecipes()
                && (consumesDurability(delegate) || consumesDurability(configuredRecipe))) {
            return null;
        }

        encoded.set(ModDataComponents.VIRTUAL_PATTERN_ID.get(),
                virtualPatternId(virtualIdPrefix, options, configuredInputs));
        AEItemKey definition = AEItemKey.of(encoded);
        if (delegate instanceof IMolecularAssemblerSupportedPattern assemblerPattern) {
            return new AggregateAssemblerPatternDetails(
                    recipe.patternId(), definition, assemblerPattern, recipe.processingTicks());
        }
        return new AggregatePatternDetails(
                recipe.patternId(), definition, delegate, recipe.processingTicks(), configuredInputs);
    }

    /**
     * Uses AE2's resolved remainder semantics, so crafting recipes supplied by other mods are
     * detected without maintaining a recipe or tool whitelist.
     */
    static boolean consumesDurability(IPatternDetails details) {
        for (IPatternDetails.IInput input : details.getInputs()) {
            for (GenericStack candidate : input.getPossibleInputs()) {
                if (isDurabilityLoss(candidate.what(), input.getRemainingKey(candidate.what()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Processing integrations may expose the damaged tool as an ordinary recipe output. */
    static boolean consumesDurability(AggregateRecipe recipe) {
        for (AggregateInputSlot slot : recipe.inputSlots()) {
            for (GenericStack input : slot.alternatives()) {
                for (GenericStack output : recipe.outputs()) {
                    if (isDurabilityLoss(input.what(), output.what())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean isDurabilityLoss(AEKey input, AEKey remainder) {
        if (!(input instanceof AEItemKey before)
                || !(remainder instanceof AEItemKey after)
                || before.getItem() != after.getItem()) {
            return false;
        }
        ItemStack beforeStack = before.getReadOnlyStack();
        ItemStack afterStack = after.getReadOnlyStack();
        return beforeStack.isDamageableItem()
                && afterStack.isDamageableItem()
                && afterStack.getDamageValue() > beforeStack.getDamageValue();
    }

    private static AggregateRecipe configureOutputs(
            AggregateRecipe recipe, AggregatePatternOptions options) {
        if (!options.ignoreOutputComponents()
                && !options.ignoreProbabilisticByproducts()
                && !options.removeOutputFluids()
                && !options.removeOutputChemicals()) {
            return recipe;
        }

        List<GenericStack> outputs = new ArrayList<>(recipe.outputs().size());
        int probabilisticOutputMask = 0;
        for (int sourceIndex = 0; sourceIndex < recipe.outputs().size(); sourceIndex++) {
            boolean probabilistic = recipe.isProbabilisticOutput(sourceIndex);
            if (sourceIndex > 0 && probabilistic && options.ignoreProbabilisticByproducts()) {
                continue;
            }
            GenericStack stack = recipe.outputs().get(sourceIndex);
            if (recipe.kind() == AggregatePatternKind.PROCESSING && removeOutput(stack.what(), options)) {
                continue;
            }
            if (options.ignoreOutputComponents()
                    && stack.what() instanceof AEItemKey itemKey
                    && itemKey.hasComponents()) {
                stack = new GenericStack(AEItemKey.of(itemKey.getItem()), stack.amount());
            }
            if (probabilistic) {
                probabilisticOutputMask |= 1 << outputs.size();
            }
            outputs.add(stack);
        }
        return new AggregateRecipe(
                recipe.patternId(), recipe.recipeId(), recipe.kind(),
                recipe.inputs(), recipe.inputSlots(), outputs,
                probabilisticOutputMask, recipe.processingTicks());
    }

    /**
     * Resolves processing-slot alternatives at expansion time so tags follow the current datapack.
     * A custom input view is only needed for alternatives or unit splitting; exact legacy inputs keep
     * delegating to AE2 so their normal container-item semantics remain untouched.
     */
    private static List<AggregateInputSlot> configuredProcessingInputs(
            AggregateRecipe recipe,
            AggregatePatternOptions options,
            net.minecraft.server.level.ServerLevel level) {
        if (recipe.kind() != AggregatePatternKind.PROCESSING) {
            return List.of();
        }

        boolean filtersInputs = filtersProcessingInputs(options);
        List<AggregateInputSlot> slots = filtersInputs
                ? recipe.inputSlots().stream()
                        .filter(slot -> !removeInput(slot, options, level))
                        .toList()
                : recipe.inputSlots();

        if (options.splitSameItems()) {
            List<AggregateInputSlot> result = new ArrayList<>();
            for (AggregateInputSlot slot : slots) {
                result.addAll(slot.splitUnits(level));
                if (result.size() > MAX_SPLIT_ITEM_INPUTS) {
                    throw new IllegalArgumentException(
                            "split item input count exceeds safety limit " + MAX_SPLIT_ITEM_INPUTS);
                }
            }
            return swapFirstAndLastIfRequested(List.copyOf(result), options);
        }

        if (!filtersInputs
                && !options.swapFirstAndLastInputs()
                && slots.stream().noneMatch(AggregateInputSlot::hasAlternatives)) {
            return List.of();
        }
        List<AggregateInputSlot> resolved = slots.stream()
                .map(slot -> new AggregateInputSlot(slot.resolve(level), Optional.empty()))
                .toList();
        return swapFirstAndLastIfRequested(resolved, options);
    }

    private static List<AggregateInputSlot> swapFirstAndLastIfRequested(
            List<AggregateInputSlot> inputs, AggregatePatternOptions options) {
        if (!options.swapFirstAndLastInputs() || inputs.size() < 2) {
            return inputs;
        }
        List<AggregateInputSlot> reordered = new ArrayList<>(inputs);
        AggregateInputSlot first = reordered.getFirst();
        int lastIndex = reordered.size() - 1;
        reordered.set(0, reordered.get(lastIndex));
        reordered.set(lastIndex, first);
        return List.copyOf(reordered);
    }

    private static boolean filtersProcessingInputs(AggregatePatternOptions options) {
        return options.removeProcessingCatalysts()
                || options.removeInputFluids()
                || options.removeInputChemicals();
    }

    private static boolean removeInput(
            AggregateInputSlot slot, AggregatePatternOptions options, Level level) {
        return slot.resolve(level).stream().allMatch(stack -> {
            AEKey key = stack.what();
            return options.removeInputFluids() && key instanceof AEFluidKey
                    || options.removeInputChemicals() && isChemical(key)
                    || options.removeProcessingCatalysts()
                            && key instanceof AEItemKey itemKey
                            && itemKey.toStack().is(PROCESSING_CATALYSTS);
        });
    }

    private static boolean removeOutput(AEKey key, AggregatePatternOptions options) {
        return options.removeOutputFluids() && key instanceof AEFluidKey
                || options.removeOutputChemicals() && isChemical(key);
    }

    private static boolean isChemical(AEKey key) {
        return key.getType().getId().equals(CHEMICAL_KEY_TYPE);
    }

    /** Expands every item count n into exactly n independent unit inputs. */
    static List<GenericStack> splitItemInputs(List<GenericStack> inputs) {
        long expandedSize = 0;
        for (GenericStack input : inputs) {
            expandedSize += input.what() instanceof AEItemKey ? input.amount() : 1;
            if (expandedSize > MAX_SPLIT_ITEM_INPUTS) {
                throw new IllegalArgumentException(
                        "split item input count exceeds safety limit " + MAX_SPLIT_ITEM_INPUTS);
            }
        }

        List<GenericStack> expanded = new ArrayList<>((int) expandedSize);
        for (GenericStack input : inputs) {
            if (input.what() instanceof AEItemKey) {
                for (long index = 0; index < input.amount(); index++) {
                    expanded.add(new GenericStack(input.what(), 1));
                }
            } else {
                expanded.add(input);
            }
        }
        return List.copyOf(expanded);
    }

    private static String virtualPatternId(
            String prefix,
            AggregatePatternOptions options,
            List<AggregateInputSlot> configuredInputs) {
        String value = prefix + ":options=" + options.flags();
        if (!configuredInputs.isEmpty()) {
            // Makes refreshed providers observe datapack/tag membership changes without regenerating the item.
            value += ":inputs=" + Integer.toUnsignedString(configuredInputs.hashCode(), 16);
        }
        if (value.length() <= 160) {
            return value;
        }
        String suffix = Integer.toUnsignedString(value.hashCode(), 16);
        return value.substring(0, 159 - suffix.length()) + ":" + suffix;
    }

    private static ItemStack encode(
            AggregateRecipe recipe,
            net.minecraft.server.level.ServerLevel level,
            AggregatePatternOptions options) {
        return switch (recipe.kind()) {
            case PROCESSING -> PatternDetailsHelper.encodeProcessingPattern(recipe.inputs(), recipe.outputs());
            case CRAFTING -> encodeCrafting(recipe, level, options);
            case STONECUTTING -> encodeStonecutting(recipe, level, options);
            case SMITHING -> encodeSmithing(recipe, level, options);
        };
    }

    private static ItemStack encodeCrafting(
            AggregateRecipe aggregate,
            net.minecraft.server.level.ServerLevel level,
            AggregatePatternOptions options) {
        ItemStack[] storedGrid = storedCraftingGrid(aggregate.inputs());
        Optional<RecipeHolder<CraftingRecipe>> holder = craftingHolder(aggregate, storedGrid, level);
        if (holder.isEmpty()) {
            return ItemStack.EMPTY;
        }
        CraftingRecipe recipe = holder.orElseThrow().value();
        ItemStack[] grid = recipe.getIngredients().isEmpty() ? storedGrid : craftingGrid(recipe);
        if (Arrays.stream(grid).allMatch(ItemStack::isEmpty)) {
            return ItemStack.EMPTY;
        }
        ItemStack output = itemStack(aggregate.outputs().getFirst());
        if (output.isEmpty()) {
            output = recipe.getResultItem(level.registryAccess()).copy();
        }
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return PatternDetailsHelper.encodeCraftingPattern(
                holder.orElseThrow(), grid, output,
                options.allowItemSubstitutions(), options.allowFluidSubstitutions());
    }

    private static Optional<RecipeHolder<CraftingRecipe>> craftingHolder(
            AggregateRecipe aggregate,
            ItemStack[] storedGrid,
            net.minecraft.server.level.ServerLevel level) {
        Optional<RecipeHolder<?>> byId = level.getRecipeManager().byKey(aggregate.recipeId());
        if (byId.isPresent() && byId.orElseThrow().value() instanceof CraftingRecipe) {
            return Optional.of(castHolder(byId.orElseThrow()));
        }
        if (Arrays.stream(storedGrid).allMatch(ItemStack::isEmpty)) {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(
                RecipeType.CRAFTING,
                CraftingInput.of(3, 3, List.of(storedGrid)),
                level);
    }

    private static ItemStack encodeStonecutting(
            AggregateRecipe aggregate,
            net.minecraft.server.level.ServerLevel level,
            AggregatePatternOptions options) {
        Optional<RecipeHolder<?>> rawHolder = level.getRecipeManager().byKey(aggregate.recipeId());
        if (rawHolder.isEmpty() || !(rawHolder.orElseThrow().value() instanceof StonecutterRecipe)) {
            return ItemStack.EMPTY;
        }
        AEItemKey input = itemKey(aggregate.inputs().getFirst());
        AEItemKey output = itemKey(aggregate.outputs().getFirst());
        if (input == null || output == null) {
            return ItemStack.EMPTY;
        }
        return PatternDetailsHelper.encodeStonecuttingPattern(
                castHolder(rawHolder.orElseThrow()), input, output, options.allowItemSubstitutions());
    }

    private static ItemStack encodeSmithing(
            AggregateRecipe aggregate,
            net.minecraft.server.level.ServerLevel level,
            AggregatePatternOptions options) {
        Optional<RecipeHolder<?>> rawHolder = level.getRecipeManager().byKey(aggregate.recipeId());
        if (rawHolder.isEmpty() || !(rawHolder.orElseThrow().value() instanceof SmithingRecipe recipe)) {
            return ItemStack.EMPTY;
        }
        AEItemKey template = findItemKey(aggregate.inputs(), recipe::isTemplateIngredient);
        AEItemKey base = findItemKey(aggregate.inputs(), recipe::isBaseIngredient);
        AEItemKey addition = findItemKey(aggregate.inputs(), recipe::isAdditionIngredient);
        AEItemKey output = itemKey(aggregate.outputs().getFirst());
        if (template == null || base == null || addition == null || output == null) {
            return ItemStack.EMPTY;
        }
        return PatternDetailsHelper.encodeSmithingTablePattern(
                castHolder(rawHolder.orElseThrow()), template, base, addition, output,
                options.allowItemSubstitutions());
    }

    private static ItemStack[] craftingGrid(CraftingRecipe recipe) {
        ItemStack[] grid = new ItemStack[9];
        Arrays.fill(grid, ItemStack.EMPTY);
        List<Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof ShapedRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    int ingredientIndex = row * width + column;
                    if (ingredientIndex < ingredients.size()) {
                        grid[row * 3 + column] = chooseItem(ingredients.get(ingredientIndex));
                    }
                }
            }
        } else {
            for (int index = 0; index < Math.min(grid.length, ingredients.size()); index++) {
                grid[index] = chooseItem(ingredients.get(index));
            }
        }
        return grid;
    }

    private static ItemStack[] storedCraftingGrid(List<GenericStack> inputs) {
        ItemStack[] grid = new ItemStack[9];
        Arrays.fill(grid, ItemStack.EMPTY);
        for (int index = 0; index < Math.min(grid.length, inputs.size()); index++) {
            AEItemKey key = itemKey(inputs.get(index));
            if (key == null) {
                Arrays.fill(grid, ItemStack.EMPTY);
                return grid;
            }
            grid[index] = key.toStack(1);
        }
        return grid;
    }

    private static ItemStack chooseItem(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems())
                .filter(stack -> !stack.isEmpty())
                .min(Comparator.comparing(AggregatePatternExpander::itemIdentity))
                .map(stack -> stack.copyWithCount(1))
                .orElse(ItemStack.EMPTY);
    }

    private static String itemIdentity(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "*" + stack.getComponents();
    }

    private static AEItemKey findItemKey(List<GenericStack> stacks, Predicate<ItemStack> predicate) {
        for (GenericStack stack : stacks) {
            AEItemKey key = itemKey(stack);
            if (key != null && predicate.test(key.getReadOnlyStack())) {
                return key;
            }
        }
        return null;
    }

    private static AEItemKey itemKey(GenericStack stack) {
        return stack.what() instanceof AEItemKey itemKey ? itemKey : null;
    }

    private static ItemStack itemStack(GenericStack stack) {
        AEItemKey key = itemKey(stack);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        return key.toStack((int) Math.min(Integer.MAX_VALUE, stack.amount()));
    }

    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeHolder<T> castHolder(
            RecipeHolder<?> holder) {
        return (RecipeHolder<T>) holder;
    }
}
