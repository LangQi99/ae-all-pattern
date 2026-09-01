package io.github.langqi99.aeallpattern.client;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEBlocks;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternData;
import io.github.langqi99.aeallpattern.aggregate.AggregateInputSlot;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternKind;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternLibrary;
import io.github.langqi99.aeallpattern.aggregate.AggregateRecipe;
import io.github.langqi99.aeallpattern.compat.jei.AeAllPatternJeiPlugin;
import io.github.langqi99.aeallpattern.network.GenerateAggregatePayload;
import io.github.langqi99.aeallpattern.recipe.RecipeFingerprint;
import io.github.langqi99.aeallpattern.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters;

/** Converts any JEI catalyst's visible recipes into concrete AE generic-stack patterns. */
public final class ClientJeiAggregateScanner {
    /** Machine tier prefixes, stripped before matching a machine id against its recipe category. */
    private static final List<String> FACTORY_TIERS = List.of(
            "basic_", "advanced_", "elite_", "ultimate_",
            "absolute_", "supreme_", "cosmic_", "infinite_",
            "dense_", "quantum_", "overclocked_", "multiversal_", "creative_");
    /** Housing suffixes that say how the machine is shaped, not what it does. */
    private static final List<String> MACHINE_HOUSINGS = List.of(
            "_factory", "_machine", "_chamber");
    /** Below this many shared leading characters a prefix match is treated as coincidence. */
    private static final int MIN_SHARED_PREFIX = 4;
    /**
     * Building one JEI recipe layout drawable is expensive; with 900-2000 recipes a single-frame
     * scan freezes the client. Scans therefore run across ticks with a tiny budget per tick.
     */
    private static final long SCAN_BUDGET_NANOS = 2_500_000L;
    /**
     * Upload pages must stay below the protocol packet limit (~32KiB). Recipe complexity is
     * unbounded (alternatives, fluids, components), so pages are split by an estimated byte
     * budget rather than a fixed recipe count; 20KiB keeps a generous margin.
     */
    private static final int MAX_UPLOAD_PAGE_BYTES = 20_000;
    /** How often the action bar reports scan progress while a large scan runs. */
    private static final int PROGRESS_INTERVAL = 400;
    private static long lastScanTick = Long.MIN_VALUE;
    private static BlockPos lastScanPos = BlockPos.ZERO;
    private static ScanJob activeJob;

    private ClientJeiAggregateScanner() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        var level = player.level();
        if (!level.isClientSide()
                || event.getHand() != InteractionHand.MAIN_HAND
                || !player.isShiftKeyDown()
                || !player.getItemInHand(event.getHand()).is(ModItems.ALL_PATTERN_GENERATOR.get())) {
            return;
        }
        if (level.getGameTime() == lastScanTick && event.getPos().equals(lastScanPos)) {
            return;
        }
        lastScanTick = level.getGameTime();
        lastScanPos = event.getPos().immutable();

        var runtime = AeAllPatternJeiPlugin.runtime();
        if (runtime.isEmpty()) {
            show("message.aeallpattern.generator.jei_not_ready");
            return;
        }
        startScan(runtime.orElseThrow(), ClientRecipeMachineResolver.resolvePosition(level, event.getPos()));
    }

    /** Client tick pump: advances the active scan job within the per-tick budget. */
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        ScanJob job = activeJob;
        if (job == null) {
            return;
        }
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level == null) {
            activeJob = null;
            return;
        }
        if (job.step()) {
            activeJob = null;
            job.finish();
        }
    }

    /** Cheap preparation: resolve the catalyst and its native JEI category, then queue the job. */
    private static void startScan(IJeiRuntime runtime, BlockPos pos) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        ItemStack catalyst = ClientRecipeMachineResolver.recipeViewerCatalyst(minecraft.level, pos);
        if (catalyst.isEmpty()) {
            show("message.aeallpattern.generator.no_jei_recipes");
            return;
        }

        var focusFactory = runtime.getJeiHelpers().getFocusFactory();
        IFocus<ItemStack> catalystFocus = focusFactory.createFocus(
                RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK, catalyst);
        List<IRecipeCategory<?>> categories;
        if (catalyst.is(Blocks.CRAFTING_TABLE.asItem()) || AEBlocks.MOLECULAR_ASSEMBLER.is(catalyst)) {
            // JEI integrations do not consistently register the molecular assembler as a
            // vanilla crafting catalyst. It executes the same native AE crafting patterns.
            categories = List.of(runtime.getRecipeManager().getRecipeCategory(RecipeTypes.CRAFTING));
        } else {
            categories = runtime.getRecipeManager()
                    .createRecipeCategoryLookup()
                    .limitFocus(List.of(catalystFocus))
                    .get()
                    .toList();
        }
        if (categories.isEmpty()) {
            show("message.aeallpattern.generator.no_jei_recipes");
            return;
        }

        // A compatibility machine may register the clicked block as a catalyst for
        // its own category. A native JEI category is owned by the same namespace as
        // the clicked machine, so never fall back to an unrelated category.
        IRecipeCategory<?> category = findNativeCategory(categories, catalyst);
        if (category == null || !allowsCategory(
                BuiltInRegistries.ITEM.getKey(catalyst.getItem()), category.getRecipeType().getUid())) {
            show("message.aeallpattern.generator.no_jei_recipes");
            return;
        }
        AggregatePatternKind kind = patternKind(category.getRecipeType().getUid());
        var machineBlock = minecraft.level.getBlockState(pos).getBlock();
        if (kind == AggregatePatternKind.CRAFTING || kind == AggregatePatternKind.STONECUTTING) {
            // Vanilla recipes are plain data; parsing them through JEI layout drawables is
            // hundreds of times slower for no extra fidelity. Scan them on a worker thread so
            // a 18k-recipe crafting category completes in seconds without touching the render
            // thread, then hand the result back for the same paged upload.
            startVanillaScan(
                    kind, pos,
                    machineBlock.getDescriptionId(),
                    BuiltInRegistries.BLOCK.getKey(machineBlock));
            show("message.aeallpattern.generator.scan_started");
            return;
        }

        var manager = runtime.getRecipeManager();
        List<?> categoryRecipes = manager.createRecipeLookup(category.getRecipeType()).get().toList();
        if (categoryRecipes.isEmpty()) {
            show("message.aeallpattern.generator.no_item_recipes");
            return;
        }

        activeJob = new ScanJob(
                runtime, category, focusFactory.getEmptyFocusGroup(),
                kind,
                category.getRecipeType().getUid(),
                categoryRecipes,
                pos,
                machineBlock.getDescriptionId(),
                BuiltInRegistries.BLOCK.getKey(machineBlock));
        show("message.aeallpattern.generator.scan_started");
    }

    /** Worker-thread scan of vanilla crafting/stonecutting recipes. */
    private static void startVanillaScan(
            AggregatePatternKind kind, BlockPos pos, String machineKey, ResourceLocation catalystId) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        var manager = level.getRecipeManager();
        var registryAccess = level.registryAccess();
        ResourceLocation categoryId = kind == AggregatePatternKind.CRAFTING
                ? RecipeTypes.CRAFTING.getUid()
                : RecipeTypes.STONECUTTING.getUid();
        Thread worker = new Thread(() -> {
            int recipeLimit = AggregatePatternData.configuredRecipeLimit();
            List<AggregateRecipe> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            int index = 0;
            boolean truncated = false;
            int[] lastNotify = {0};
            try {
                if (kind == AggregatePatternKind.CRAFTING) {
                    List<RecipeHolder<CraftingRecipe>> recipes =
                            manager.getAllRecipesFor(RecipeType.CRAFTING);
                    for (RecipeHolder<CraftingRecipe> holder : recipes) {
                        if (result.size() >= recipeLimit) {
                            truncated = true;
                            break;
                        }
                        scanVanillaCrafting(holder, registryAccess, categoryId, result, seen);
                        index++;
                        if (index - lastNotify[0] >= PROGRESS_INTERVAL * 5) {
                            lastNotify[0] = index;
                            notifyProgress(index, recipes.size());
                        }
                    }
                } else {
                    List<RecipeHolder<StonecutterRecipe>> recipes =
                            manager.getAllRecipesFor(RecipeType.STONECUTTING);
                    for (RecipeHolder<StonecutterRecipe> holder : recipes) {
                        if (result.size() >= recipeLimit) {
                            truncated = true;
                            break;
                        }
                        scanVanillaStonecutting(holder, registryAccess, categoryId, result, seen);
                        index++;
                        if (index - lastNotify[0] >= PROGRESS_INTERVAL * 5) {
                            lastNotify[0] = index;
                            notifyProgress(index, recipes.size());
                        }
                    }
                }
            } catch (RuntimeException error) {
                io.github.langqi99.aeallpattern.AeAllPattern.LOGGER.debug(
                        "vanilla recipe scan aborted after {} recipes", index, error);
            }
            final boolean wasTruncated = truncated;
            final List<AggregateRecipe> frozen = List.copyOf(result);
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (wasTruncated) {
                    show("message.aeallpattern.generator.truncated",
                            String.valueOf(recipeLimit), String.valueOf(frozen.size()));
                }
                upload(frozen, pos, machineKey, catalystId);
            });
        }, "aeallpattern-vanilla-scan");
        worker.setDaemon(true);
        worker.start();
    }

    private static void notifyProgress(int current, int total) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> show(
                "message.aeallpattern.generator.scan_progress",
                String.valueOf(current), String.valueOf(total)));
    }

    private static void scanVanillaCrafting(
            RecipeHolder<CraftingRecipe> holder,
            net.minecraft.core.HolderLookup.Provider registryAccess,
            ResourceLocation categoryId,
            List<AggregateRecipe> destination,
            Set<String> seen) {
        try {
            CraftingRecipe recipe = holder.value();
            List<AggregateInputSlot> inputSlots = new ArrayList<>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                List<GenericStack> candidates = ingredientCandidates(ingredient);
                if (candidates.isEmpty()) {
                    return;
                }
                inputSlots.add(new AggregateInputSlot(candidates, Optional.empty()));
            }
            ItemStack output = recipe.getResultItem(registryAccess);
            if (inputSlots.isEmpty() || inputSlots.size() > AggregateRecipe.MAX_INPUTS || output.isEmpty()) {
                return;
            }
            GenericStack out = GenericStack.fromItemStack(output);
            if (out == null || out.what() == null || out.amount() <= 0) {
                return;
            }
            String normalizedInputs = inputSlots.stream().map(ClientJeiAggregateScanner::normalizeSlot).sorted()
                    .reduce("", (left, right) -> left + "|" + right);
            String normalizedOutputs = normalize(out);
            String recipeIdentity = holder.id().toString();
            RecipeFingerprint fingerprint = new RecipeFingerprint(
                    "jei:" + AggregatePatternKind.CRAFTING.serializedName() + ":" + categoryId,
                    recipeIdentity, normalizedInputs, normalizedOutputs, 1);
            String patternId = fingerprint.stableKey();
            if (seen.add(patternId)) {
                destination.add(new AggregateRecipe(
                        patternId, holder.id(), AggregatePatternKind.CRAFTING,
                        inputSlots.stream().map(AggregateInputSlot::primary).toList(),
                        inputSlots, List.of(out), 0, 200));
            }
        } catch (RuntimeException error) {
            io.github.langqi99.aeallpattern.AeAllPattern.LOGGER.debug(
                    "vanilla crafting scan skipped {}", holder.id(), error);
        }
    }

    private static void scanVanillaStonecutting(
            RecipeHolder<StonecutterRecipe> holder,
            net.minecraft.core.HolderLookup.Provider registryAccess,
            ResourceLocation categoryId,
            List<AggregateRecipe> destination,
            Set<String> seen) {
        try {
            StonecutterRecipe recipe = holder.value();
            var ingredients = recipe.getIngredients();
            Ingredient ingredient = ingredients.isEmpty() ? Ingredient.EMPTY : ingredients.getFirst();
            List<GenericStack> candidates = ingredientCandidates(ingredient);
            if (candidates.isEmpty()) {
                return;
            }
            ItemStack output = recipe.getResultItem(registryAccess);
            if (output.isEmpty()) {
                return;
            }
            GenericStack out = GenericStack.fromItemStack(output);
            if (out == null || out.what() == null || out.amount() <= 0) {
                return;
            }
            AggregateInputSlot input = new AggregateInputSlot(candidates, Optional.empty());
            String normalizedInputs = normalizeSlot(input);
            String normalizedOutputs = normalize(out);
            String recipeIdentity = holder.id().toString();
            RecipeFingerprint fingerprint = new RecipeFingerprint(
                    "jei:" + AggregatePatternKind.STONECUTTING.serializedName() + ":" + categoryId,
                    recipeIdentity, normalizedInputs, normalizedOutputs, 1);
            String patternId = fingerprint.stableKey();
            if (seen.add(patternId)) {
                destination.add(new AggregateRecipe(
                        patternId, holder.id(), AggregatePatternKind.STONECUTTING,
                        List.of(input.primary()),
                        List.of(input), List.of(out), 0, 200));
            }
        } catch (RuntimeException error) {
            io.github.langqi99.aeallpattern.AeAllPattern.LOGGER.debug(
                    "vanilla stonecutting scan skipped {}", holder.id(), error);
        }
    }

    /** Expands an ingredient into de-duplicated stacks, capped at the slot alternative limit. */
    private static List<GenericStack> ingredientCandidates(Ingredient ingredient) {
        LinkedHashMap<String, GenericStack> unique = new LinkedHashMap<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            GenericStack generic = GenericStack.fromItemStack(stack);
            if (generic == null || generic.what() == null || generic.amount() <= 0) {
                continue;
            }
            unique.putIfAbsent(normalize(generic), generic);
            if (unique.size() >= AggregateInputSlot.configuredAlternativeLimit()) {
                break;
            }
        }
        return List.copyOf(unique.values());
    }

    /** Shared paged upload for both the JEI job and the vanilla worker path. */
    private static void upload(
            List<AggregateRecipe> recipes, BlockPos pos, String machineKey, ResourceLocation catalystId) {
        if (recipes.isEmpty()) {
            show("message.aeallpattern.generator.no_item_recipes");
            return;
        }
        UUID uploadId = UUID.randomUUID();
        // Split by estimated bytes so a page never exceeds the protocol packet limit,
        // and never by a fixed recipe count alone.
        List<List<AggregateRecipe>> pages = createPages(recipes);
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            PacketDistributor.sendToServer(new GenerateAggregatePayload(
                    uploadId, pos, catalystId, machineKey, pageIndex, pages.size(),
                    recipes.size(), pages.get(pageIndex)));
        }
    }

    private static @NotNull List<List<AggregateRecipe>> createPages(List<AggregateRecipe> recipes) {
        List<List<AggregateRecipe>> pages = new ArrayList<>();
        List<AggregateRecipe> current = new ArrayList<>();
        int currentBytes = 0;
        for (AggregateRecipe recipe : recipes) {
            int bytes = recipe.encodedSizeEstimate();
            if (!current.isEmpty()
                    && (currentBytes + bytes > MAX_UPLOAD_PAGE_BYTES
                            || current.size() >= AggregatePatternLibrary.PAGE_SIZE)) {
                pages.add(current);
                current = new ArrayList<>();
                currentBytes = 0;
            }
            current.add(recipe);
            currentBytes += bytes;
        }
        if (!current.isEmpty()) {
            pages.add(current);
        }
        return pages;
    }

    /** Resumable scan of one JEI category, one recipe per loop iteration. */
    @SuppressWarnings("rawtypes")
    private static final class ScanJob {
        private final IJeiRuntime runtime;
        private final IRecipeCategory category;
        private final IFocusGroup emptyFocus;
        private final AggregatePatternKind kind;
        private final ResourceLocation categoryId;
        private final List<?> categoryRecipes;
        private final BlockPos pos;
        private final String machineKey;
        private final ResourceLocation catalystId;
        private final int recipeLimit = AggregatePatternData.configuredRecipeLimit();
        private final List<AggregateRecipe> destination = new ArrayList<>();
        private final Set<String> seen = new HashSet<>();
        private int index;
        private int lastProgressShown;

        @SuppressWarnings("rawtypes")
        private ScanJob(
                IJeiRuntime runtime,
                IRecipeCategory category,
                IFocusGroup emptyFocus,
                AggregatePatternKind kind,
                ResourceLocation categoryId,
                List<?> categoryRecipes,
                BlockPos pos,
                String machineKey,
                ResourceLocation catalystId) {
            this.runtime = runtime;
            this.category = category;
            this.emptyFocus = emptyFocus;
            this.kind = kind;
            this.categoryId = categoryId;
            this.categoryRecipes = categoryRecipes;
            this.pos = pos;
            this.machineKey = machineKey;
            this.catalystId = catalystId;
        }

        /** Processes recipes until the time budget is spent; true when the scan is complete. */
        boolean step() {
            long deadline = System.nanoTime() + ClientJeiAggregateScanner.SCAN_BUDGET_NANOS;
            while (index < categoryRecipes.size() && destination.size() < recipeLimit) {
                try {
                    scanOne(categoryRecipes.get(index), index);
                } catch (RuntimeException error) {
                    // One malformed recipe must never stall the whole scan.
                    io.github.langqi99.aeallpattern.AeAllPattern.LOGGER.debug(
                            "JEI scan skipped recipe at index {}", index, error);
                }
                index++;
                if (System.nanoTime() >= deadline) {
                    break;
                }
            }
            if (index - lastProgressShown >= PROGRESS_INTERVAL) {
                lastProgressShown = index;
                show("message.aeallpattern.generator.scan_progress",
                        String.valueOf(Math.min(index, categoryRecipes.size())),
                        String.valueOf(categoryRecipes.size()));
            }
            return index >= categoryRecipes.size() || destination.size() >= recipeLimit;
        }

        private void finish() {
            if (destination.size() >= recipeLimit) {
                show("message.aeallpattern.generator.truncated",
                        String.valueOf(recipeLimit), String.valueOf(destination.size()));
            }
            upload(destination, pos, machineKey, catalystId);
        }

        @SuppressWarnings({"unchecked"})
        private void scanOne(Object recipe, int position) {
            IRecipeManager manager = runtime.getRecipeManager();
            var drawable = manager.createRecipeLayoutDrawable(category, recipe, emptyFocus);
            if (drawable.isEmpty()) {
                return;
            }
            mezz.jei.api.gui.IRecipeLayoutDrawable<?> layout =
                    (mezz.jei.api.gui.IRecipeLayoutDrawable<?>) drawable.orElseThrow();
            var slots = layout.getRecipeSlotsView();
            List<AggregateInputSlot> inputSlots = new ArrayList<>();
            boolean valid = true;
            List<IRecipeSlotView> inputViews = slots.getSlotViews(RecipeIngredientRole.INPUT);
            int alternativesPerSlot = Math.min(
                    AggregateInputSlot.configuredAlternativeLimit(),
                    AggregateRecipe.MAX_TOTAL_INPUT_ALTERNATIVES / Math.max(1, inputViews.size()));
            for (IRecipeSlotView slot : inputViews) {
                Optional<AggregateInputSlot> input = chooseInputSlot(slot, alternativesPerSlot);
                if (input.isPresent()) {
                    inputSlots.add(input.orElseThrow());
                } else if (!slot.isEmpty()) {
                    valid = false;
                    return;
                }
            }
            List<ScannedOutput> scannedOutputs = slots.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                    .map(slot -> scanOutput(slot, kind))
                    .flatMap(Optional::stream)
                    .limit(AggregateRecipe.MAX_OUTPUTS)
                    .toList();
            List<GenericStack> outputs = scannedOutputs.stream().map(ScannedOutput::stack).toList();
            if (inputSlots.isEmpty() || outputs.isEmpty() || inputSlots.size() > AggregateRecipe.MAX_INPUTS) {
                return;
            }

            String normalizedInputs = inputSlots.stream().map(ClientJeiAggregateScanner::normalizeSlot).sorted()
                    .reduce("", (left, right) -> left + "|" + right);
            String normalizedOutputs = outputs.stream().map(ClientJeiAggregateScanner::normalize).sorted()
                    .reduce("", (left, right) -> left + "|" + right);
            ResourceLocation originalId = category.getRegistryName(recipe);
            if (kind != AggregatePatternKind.PROCESSING && originalId == null) {
                return;
            }
            String recipeIdentity = originalId == null ? categoryId + "#" + position : originalId.toString();
            RecipeFingerprint fingerprint = new RecipeFingerprint(
                    "jei:" + kind.serializedName() + ":" + categoryId,
                    recipeIdentity, normalizedInputs, normalizedOutputs, 1);
            String patternId = fingerprint.stableKey();
            if (seen.add(patternId)) {
                destination.add(new AggregateRecipe(
                        patternId,
                        originalId == null
                                ? ResourceLocation.fromNamespaceAndPath("aeallpattern", "jei/" + patternId.substring(0, 32))
                                : originalId,
                        kind,
                        inputSlots.stream().map(AggregateInputSlot::primary).toList(),
                        inputSlots,
                        outputs,
                        probabilisticOutputMask(scannedOutputs),
                        200));
            }
        }
    }

    private static AggregatePatternKind patternKind(ResourceLocation categoryId) {
        if (categoryId.equals(RecipeTypes.CRAFTING.getUid())) {
            return AggregatePatternKind.CRAFTING;
        }
        if (categoryId.equals(RecipeTypes.STONECUTTING.getUid())) {
            return AggregatePatternKind.STONECUTTING;
        }
        if (categoryId.equals(RecipeTypes.SMITHING.getUid())) {
            return AggregatePatternKind.SMITHING;
        }
        return AggregatePatternKind.PROCESSING;
    }

    private static IRecipeCategory<?> findNativeCategory(
            List<IRecipeCategory<?>> categories, ItemStack catalyst) {
        ResourceLocation catalystId = BuiltInRegistries.ITEM.getKey(catalyst.getItem());
        List<ResourceLocation> ids = categories.stream()
                .map(category -> category.getRecipeType().getUid())
                .toList();
        ResourceLocation picked = pickCategoryId(ids, catalystId);
        if (picked == null) {
            return null;
        }
        return categories.stream()
                .filter(category -> category.getRecipeType().getUid().equals(picked))
                .findFirst()
                .orElse(null);
    }

    /**
     * Picks the category the machine actually runs.
     *
     * <p>Choosing by namespace alone and taking the first hit is wrong for machines that show up
     * as a catalyst for several categories: Mekanism's infusing factory is a catalyst for both
     * {@code mekanism:metallurgic_infusing} (item + infusion -> item, the recipes players want)
     * and {@code mekanism:infusion_conversion} (item -> infusion type, how the machine is
     * refilled). Namespace matching picked the conversion category and every generated pattern
     * was nonsense ("1 redstone dust -> 10 redstone infusion").</p>
     *
     * <p>Machine blocks name the operation they perform, so the path of the machine id carries
     * the keyword of its own category: {@code advanced_infusing_factory} -> {@code infusing},
     * which only {@code mekanism:metallurgic_infusing} contains.</p>
     */
    public static ResourceLocation pickCategoryId(
            List<ResourceLocation> categoryIds, ResourceLocation catalystId) {
        if (categoryIds.isEmpty()) {
            return null;
        }
        String keyword = machineKeyword(catalystId.getPath());
        List<ResourceLocation> sameNamespace = categoryIds.stream()
                .filter(id -> id.getNamespace().equals(catalystId.getNamespace()))
                .toList();
        if (!keyword.isEmpty()) {
            // 1. Same namespace first: the mod's own category wins over anything borrowed.
            ResourceLocation own = matchByKeyword(sameNamespace, keyword);
            if (own != null) {
                return own;
            }
            // 2. Addon machines reuse the base mod's recipe category, so the namespaces differ:
            //    mekmm:*_oxidizing_factory runs mekanism:oxidizing.
            ResourceLocation borrowed = matchByKeyword(categoryIds, keyword);
            if (borrowed != null) {
                return borrowed;
            }
        }
        return sameNamespace.isEmpty() ? categoryIds.getFirst() : sameNamespace.getFirst();
    }

    /** Contains match first, then longest shared prefix for names that do not line up exactly. */
    private static ResourceLocation matchByKeyword(
            List<ResourceLocation> categoryIds, String keyword) {
        var exact = categoryIds.stream()
                .filter(id -> id.getPath().contains(keyword))
                .findFirst();
        return exact.orElseGet(() -> categoryIds.stream()
                .filter(id -> commonPrefixLength(id.getPath(), keyword) >= MIN_SHARED_PREFIX)
                .max(Comparator.comparingInt(id -> commonPrefixLength(id.getPath(), keyword)))
                .orElse(null));
    }

    /** Strips tier prefixes and housing suffixes: {@code advanced_infusing_factory} -> {@code infusing}. */
    static String machineKeyword(String path) {
        String keyword = path;
        for (String tier : FACTORY_TIERS) {
            if (keyword.startsWith(tier)) {
                keyword = keyword.substring(tier.length());
                break;
            }
        }
        for (String housing : MACHINE_HOUSINGS) {
            if (keyword.endsWith(housing) && keyword.length() > housing.length()) {
                keyword = keyword.substring(0, keyword.length() - housing.length());
                break;
            }
        }
        // Mekanism's standalone machine is named chemical_oxidizer while its
        // JEI processing category is oxidizing (factories already use the latter).
        if (keyword.equals("chemical_oxidizer") || keyword.equals("oxidizer")) {
            return "oxidizing";
        }
        return switch (keyword) {
            case "combiner" -> "combining";
            case "osmium_compressor" -> "compressing";
            case "chemical_injection", "injecting" -> "injecting";
            case "precision_sawmill" -> "sawing";
            case "energized_smelter" -> "smelting";
            case "isotopic_centrifuge" -> "centrifuging";
            case "chemical_crystallizer" -> "crystallizing";
            case "chemical_dissolution", "dissolving" -> "dissolution";
            case "cnc_lathe" -> "lathing";
            case "liquifying", "nutritional_liquifier" -> "nutritional_liquification";
            case "pigment_extractor" -> "pigment_extracting";
            case "planting_station" -> "planting";
            case "pressurised_reacting", "pressurized_reaction" -> "reaction";
            case "cnc_rolling_mill" -> "rolling_mill";
            case "cnc_stamper" -> "stamping";
            case "chemical_washer" -> "washing";
            default -> keyword;
        };
    }

    private static int commonPrefixLength(String left, String right) {
        int limit = Math.min(left.length(), right.length());
        int index = 0;
        while (index < limit && left.charAt(index) == right.charAt(index)) {
            index++;
        }
        return index;
    }

    private static Optional<GenericStack> chooseStack(IRecipeSlotView slot) {
        return slot.getAllIngredients()
                .map(ClientJeiAggregateScanner::toGenericStack)
                .flatMap(Optional::stream)
                .filter(stack -> stack.what() != null && stack.amount() > 0)
                .min(Comparator.comparing(ClientJeiAggregateScanner::normalize));
    }

    private static Optional<ScannedOutput> scanOutput(IRecipeSlotView slot, AggregatePatternKind kind) {
        return chooseStack(slot).map(stack -> new ScannedOutput(
                stack, kind == AggregatePatternKind.PROCESSING && isProbabilistic(slot)));
    }

    private static int probabilisticOutputMask(List<ScannedOutput> outputs) {
        int mask = 0;
        for (int index = 0; index < outputs.size(); index++) {
            if (outputs.get(index).probabilistic()) {
                mask |= 1 << index;
            }
        }
        return mask;
    }

    /**
     * JEI has no dedicated probability field, but recipe integrations expose chance information
     * through the output slot name or its tooltip callback. Inspect those semantic labels while
     * deliberately skipping the first tooltip line (the ingredient name) to avoid treating an item
     * whose own name contains "chance" as a probabilistic output.
     */
    @SuppressWarnings("removal")
    private static boolean isProbabilistic(IRecipeSlotView slot) {
        if (slot.getSlotName().map(ClientJeiAggregateScanner::containsProbabilityMarker).orElse(false)) {
            return true;
        }
        if (!(slot instanceof IRecipeSlotDrawable drawable)) {
            return false;
        }
        try {
            List<Component> tooltip = drawable.getTooltip();
            for (int index = 1; index < tooltip.size(); index++) {
                Component line = tooltip.get(index);
                if (containsProbabilityMarker(line.getString())
                        || containsProbabilityMarker(line.getContents().toString())) {
                    return true;
                }
            }
        } catch (RuntimeException error) {
            AeAllPattern.LOGGER.debug(
                    "JEI output tooltip rejected probability inspection", error);
        }
        return false;
    }

    private static boolean containsProbabilityMarker(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("chance")
                || normalized.contains("probab")
                || normalized.contains("random output")
                || normalized.contains("概率")
                || normalized.contains("几率")
                || normalized.contains("機率")
                || normalized.contains("確率")
                || normalized.contains("확률");
    }

    private static Optional<AggregateInputSlot> chooseInputSlot(
            IRecipeSlotView slot, int alternativeLimit) {
        LinkedHashMap<String, GenericStack> unique = new LinkedHashMap<>();
        slot.getAllIngredients()
                .map(ClientJeiAggregateScanner::toGenericStack)
                .flatMap(Optional::stream)
                .filter(stack -> stack.what() != null && stack.amount() > 0)
                .sorted(Comparator.comparing(ClientJeiAggregateScanner::normalize))
                .limit(AggregateInputSlot.configuredAlternativeLimit())
                .forEach(stack -> unique.putIfAbsent(normalize(stack), stack));
        if (unique.isEmpty()) {
            return Optional.empty();
        }
        List<GenericStack> candidates = List.copyOf(unique.values());
        Optional<ResourceLocation> itemTag = exactItemTag(candidates);
        if (itemTag.isPresent()) {
            // The tag will be resolved from the server's current datapack. Keep
            // only one concrete fallback so large tags never inflate packets.
            return Optional.of(new AggregateInputSlot(
                    List.of(candidates.getFirst()), itemTag));
        }
        return Optional.of(new AggregateInputSlot(
                candidates.stream().limit(alternativeLimit).toList(),
                Optional.empty()));
    }

    public static boolean allowsCategory(ResourceLocation catalystId, ResourceLocation categoryId) {
        if (!categoryId.getNamespace().equals("mekanism")) {
            return true;
        }
        return switch (categoryId.getPath()) {
            case "infusion_conversion", "pigment_extracting" -> false;
            case "oxidizing" -> machineKeyword(catalystId.getPath()).equals("oxidizing");
            default -> true;
        };
    }

    /** Tag lookups scan the whole registry; cache by the exact item set. */
    private static final Map<Set<net.minecraft.world.item.Item>, Optional<ResourceLocation>> ITEM_TAG_CACHE =
            new java.util.LinkedHashMap<>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<Set<net.minecraft.world.item.Item>, Optional<ResourceLocation>> eldest) {
                    return size() > 256;
                }
            };

    private static Optional<ResourceLocation> exactItemTag(List<GenericStack> candidates) {
        if (candidates.isEmpty()
                || candidates.stream().anyMatch(stack -> !(stack.what() instanceof AEItemKey))
                || candidates.stream().mapToLong(GenericStack::amount).distinct().count() != 1) {
            return Optional.empty();
        }
        Set<net.minecraft.world.item.Item> candidateItems = candidates.stream()
                .map(GenericStack::what)
                .map(AEItemKey.class::cast)
                .map(AEItemKey::getItem)
                .collect(java.util.stream.Collectors.toSet());
        Optional<ResourceLocation> cached = ITEM_TAG_CACHE.get(candidateItems);
        if (cached != null) {
            return cached;
        }
        Optional<ResourceLocation> result = BuiltInRegistries.ITEM.getTagNames()
                .filter(tag -> BuiltInRegistries.ITEM.getTag(tag)
                        .map(named -> named.size() == candidateItems.size()
                                && named.stream().allMatch(holder -> candidateItems.contains(holder.value())))
                        .orElse(false))
                .map(net.minecraft.tags.TagKey::location).min(Comparator.comparingInt((ResourceLocation id) -> id.toString().length())
                        .thenComparing(ResourceLocation::toString));
        ITEM_TAG_CACHE.put(candidateItems, result);
        return result;
    }

    private static Optional<GenericStack> toGenericStack(ITypedIngredient<?> typed) {
        Object ingredient = typed.getIngredient();
        if (ingredient instanceof ItemStack item && !item.isEmpty()) {
            return Optional.ofNullable(GenericStack.fromItemStack(item.copy()));
        }
        if (ingredient instanceof FluidStack fluid && !fluid.isEmpty()) {
            return Optional.ofNullable(GenericStack.fromFluidStack(fluid.copy()));
        }
        return convertRegistered(typed);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Optional<GenericStack> convertRegistered(ITypedIngredient typed) {
        if (!ModList.get().isLoaded("ae2jeiintegration")) {
            return Optional.empty();
        }
        try {
            IngredientConverter converter = IngredientConverters.getConverter(typed.getType());
            return converter == null
                    ? Optional.empty()
                    : Optional.ofNullable(converter.getStackFromIngredient(typed.getIngredient()));
        } catch (RuntimeException error) {
            io.github.langqi99.aeallpattern.AeAllPattern.LOGGER.debug(
                    "AE JEI converter rejected ingredient type {}", typed.getType(), error);
            return Optional.empty();
        }
    }

    private static String normalize(GenericStack stack) {
        return stack.what().getType().getId() + "*" + stack.what().getId()
                + "*" + stack.amount() + "*" + stack.what();
    }

    private static String normalizeSlot(AggregateInputSlot slot) {
        String tag = slot.itemTag().map(ResourceLocation::toString).orElse("-");
        return tag + slot.alternatives().stream()
                .map(ClientJeiAggregateScanner::normalize)
                .sorted()
                .reduce("", (left, right) -> left + "+" + right);
    }

    private static void show(String key, Object... args) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable(key, args), true);
        }
    }

    private record ScannedOutput(GenericStack stack, boolean probabilistic) {
    }

}
