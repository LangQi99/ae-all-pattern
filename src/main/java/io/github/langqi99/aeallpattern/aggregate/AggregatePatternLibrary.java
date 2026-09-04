package io.github.langqi99.aeallpattern.aggregate;

import appeng.api.stacks.GenericStack;
import io.github.langqi99.aeallpattern.AeAllPattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * Server-owned, paged backing store for aggregate patterns. Items only retain a UUID.
 * A page is deliberately small so large packs never create a monolithic SavedData file.
 */
public final class AggregatePatternLibrary extends SavedData {
    public static final int PAGE_SIZE = 128;
    private static final String INDEX_NAME = AeAllPattern.MOD_ID + "_aggregate_index";

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static AggregatePatternLibrary get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                AggregatePatternLibrary::load, AggregatePatternLibrary::new, INDEX_NAME);
    }

    public AggregatePatternRef put(
            MinecraftServer server,
            ResourceLocation catalystId,
            String machineTranslationKey,
            List<AggregateRecipe> recipes) {
        if (recipes.isEmpty() || recipes.size() > AggregatePatternData.configuredRecipeLimit()) {
            throw new IllegalArgumentException("invalid aggregate library recipe count: " + recipes.size());
        }
        String hash = contentHash(recipes);
        Entry entry = entries.values().stream()
                .filter(candidate -> candidate.catalystId().equals(catalystId)
                        && candidate.batchCount() == 1)
                .findFirst().orElse(null);
        UUID id = entry == null ? UUID.randomUUID() : entry.libraryId();
        int pageCount = ceilDiv(recipes.size(), PAGE_SIZE);
        var storage = server.overworld().getDataStorage();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * PAGE_SIZE;
            int to = Math.min(recipes.size(), from + PAGE_SIZE);
            storage.set(pageName(id, pageIndex), new Page(recipes.subList(from, to)));
        }
        Entry updated = new Entry(id, catalystId, machineTranslationKey, hash, recipes.size(), pageCount,
                hash, recipes.size(), 0, 1, recipes.size());
        entries.put(id, updated);
        setDirty();
        return updated.toRef();
    }

    /** Replaces one existing single-item catalog while keeping every item reference valid. */
    public AggregatePatternRef replace(
            MinecraftServer server,
            UUID libraryId,
            ResourceLocation catalystId,
            String machineTranslationKey,
            List<AggregateRecipe> recipes) {
        Entry existing = entries.get(libraryId);
        if (existing == null || existing.batchCount() != 1
                || !existing.catalystId().equals(catalystId)) {
            throw new IllegalArgumentException("aggregate library entry cannot be refreshed: " + libraryId);
        }
        if (recipes.size() > AggregatePatternData.configuredRecipeLimit()) {
            throw new IllegalArgumentException("invalid aggregate library recipe count: " + recipes.size());
        }
        String hash = contentHash(recipes);
        int pageCount = ceilDiv(recipes.size(), PAGE_SIZE);
        var storage = server.overworld().getDataStorage();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * PAGE_SIZE;
            int to = Math.min(recipes.size(), from + PAGE_SIZE);
            storage.set(pageName(libraryId, pageIndex), new Page(recipes.subList(from, to)));
        }
        Entry updated = new Entry(
                libraryId, catalystId, machineTranslationKey, hash, recipes.size(), pageCount,
                hash, Math.max(1, recipes.size()), 0, 1, recipes.size());
        entries.put(libraryId, updated);
        setDirty();
        return updated.toRef();
    }

    public Optional<List<AggregateRecipe>> recipes(MinecraftServer server, UUID libraryId) {
        Entry entry = entries.get(libraryId);
        if (entry == null) {
            return Optional.empty();
        }
        List<AggregateRecipe> recipes = new ArrayList<>(entry.recipeCount());
        var storage = server.overworld().getDataStorage();
        for (int pageIndex = 0; pageIndex < entry.pageCount(); pageIndex++) {
            Page page = storage.get(Page::load, pageName(libraryId, pageIndex));
            if (page == null) {
                AeAllPattern.LOGGER.warn("Missing aggregate pattern page {} for {}", pageIndex, libraryId);
                return Optional.empty();
            }
            recipes.addAll(page.recipes());
        }
        if (recipes.size() != entry.recipeCount()) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(recipes));
    }

    public Optional<Entry> find(UUID libraryId) {
        return Optional.ofNullable(entries.get(libraryId));
    }

    public Collection<Entry> entries() {
        return List.copyOf(entries.values());
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        entries.values().stream().sorted(Comparator.comparing(Entry::libraryId)).forEach(entry -> {
            CompoundTag raw = new CompoundTag();
            raw.putUUID("LibraryId", entry.libraryId());
            raw.putString("CatalystId", entry.catalystId().toString());
            raw.putString("MachineTranslationKey", entry.machineTranslationKey());
            raw.putString("ContentHash", entry.contentHash());
            raw.putInt("RecipeCount", entry.recipeCount());
            raw.putInt("PageCount", entry.pageCount());
            raw.putString("SeriesHash", entry.seriesHash());
            raw.putInt("BatchSize", entry.batchSize());
            raw.putInt("BatchIndex", entry.batchIndex());
            raw.putInt("BatchCount", entry.batchCount());
            raw.putInt("TotalRecipeCount", entry.totalRecipeCount());
            list.add(raw);
        });
        tag.put("Entries", list);
        return tag;
    }

    private static AggregatePatternLibrary load(CompoundTag tag) {
        AggregatePatternLibrary library = new AggregatePatternLibrary();
        for (Tag rawTag : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            CompoundTag raw = (CompoundTag) rawTag;
            try {
                ResourceLocation catalyst = new ResourceLocation(raw.getString("CatalystId"));
                String contentHash = raw.getString("ContentHash");
                int recipeCount = raw.getInt("RecipeCount");
                boolean numbered = raw.contains("BatchCount", Tag.TAG_INT);
                Entry entry = new Entry(
                        raw.getUUID("LibraryId"), catalyst,
                        raw.getString("MachineTranslationKey"), contentHash,
                        recipeCount, raw.getInt("PageCount"),
                        numbered ? raw.getString("SeriesHash") : contentHash,
                        numbered ? raw.getInt("BatchSize") : Math.max(1, recipeCount),
                        numbered ? raw.getInt("BatchIndex") : 0,
                        numbered ? raw.getInt("BatchCount") : 1,
                        numbered ? raw.getInt("TotalRecipeCount") : recipeCount);
                library.entries.put(entry.libraryId(), entry);
            } catch (RuntimeException error) {
                AeAllPattern.LOGGER.warn("Skipping unreadable aggregate library entry", error);
            }
        }
        return library;
    }

    public static String contentHash(List<AggregateRecipe> recipes) {
        return hashPatternIds(recipes.stream().map(AggregateRecipe::patternId).sorted().toList());
    }

    private static String hashPatternIds(List<String> patternIds) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            patternIds.forEach(patternId -> {
                digest.update(patternId.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }

    private static String pageName(UUID id, int page) {
        return AeAllPattern.MOD_ID + "_aggregate_" + id.toString().replace("-", "") + "_" + page;
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    public record Entry(
            UUID libraryId,
            ResourceLocation catalystId,
            String machineTranslationKey,
            String contentHash,
            int recipeCount,
            int pageCount,
            String seriesHash,
            int batchSize,
            int batchIndex,
            int batchCount,
            int totalRecipeCount) {
        public Entry {
            if (recipeCount < 0 || pageCount != ceilDiv(recipeCount, PAGE_SIZE)) {
                throw new IllegalArgumentException("invalid aggregate library metadata");
            }
            if (recipeCount == 0) {
                if (seriesHash == null || seriesHash.length() != 64 || batchSize != 1
                        || batchIndex != 0 || batchCount != 1 || totalRecipeCount != 0) {
                    throw new IllegalArgumentException("invalid empty aggregate library metadata");
                }
            } else {
                validateBatch(seriesHash, batchSize, batchIndex, batchCount, totalRecipeCount, recipeCount);
            }
        }

        public AggregatePatternRef toRef() {
            return new AggregatePatternRef(libraryId, catalystId);
        }
    }

    private static void validateBatch(
            String seriesHash,
            int batchSize,
            int batchIndex,
            int batchCount,
            int totalRecipeCount,
            int recipeCount) {
        boolean legacyOversizedSingle = batchCount == 1 && batchIndex == 0
                && totalRecipeCount == recipeCount && batchSize == recipeCount;
        if (seriesHash == null || seriesHash.length() != 64
                || batchSize < 1
                || (batchSize > AggregatePatternData.MAX_RECIPES && !legacyOversizedSingle)
                || batchIndex < 0 || batchCount < 1 || batchIndex >= batchCount
                || totalRecipeCount < 1 || batchCount != ceilDiv(totalRecipeCount, batchSize)) {
            throw new IllegalArgumentException("invalid aggregate batch metadata");
        }
        long start = (long) batchIndex * batchSize;
        int expectedRecipeCount = (int) Math.min(batchSize, totalRecipeCount - start);
        if (expectedRecipeCount != recipeCount) {
            throw new IllegalArgumentException("invalid aggregate batch recipe count: " + recipeCount);
        }
    }

    private static final class Page extends SavedData {
        private final List<AggregateRecipe> recipes;

        private Page() {
            this.recipes = List.of();
        }

        private Page(List<AggregateRecipe> recipes) {
            this(recipes, true);
        }

        private Page(List<AggregateRecipe> recipes, boolean dirty) {
            this.recipes = List.copyOf(recipes);
            if (dirty) {
                setDirty();
            }
        }

        private List<AggregateRecipe> recipes() {
            return recipes;
        }

        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
            ListTag recipeTags = new ListTag();
            for (AggregateRecipe recipe : recipes) {
                CompoundTag raw = new CompoundTag();
                raw.putString("PatternId", recipe.patternId());
                raw.putString("RecipeId", recipe.recipeId().toString());
                raw.putString("Kind", recipe.kind().serializedName());
                raw.putInt("ProcessingTicks", recipe.processingTicks());
                raw.putInt("ProbabilisticOutputMask", recipe.probabilisticOutputMask());
                ListTag inputs = new ListTag();
                recipe.inputs().forEach(stack -> inputs.add(GenericStack.writeTag(stack)));
                raw.put("GenericInputs", inputs);
                ListTag inputSlots = new ListTag();
                for (AggregateInputSlot slot : recipe.inputSlots()) {
                    CompoundTag slotTag = new CompoundTag();
                    ListTag alternatives = new ListTag();
                    slot.alternatives().forEach(stack ->
                            alternatives.add(GenericStack.writeTag(stack)));
                    slotTag.put("Alternatives", alternatives);
                    slot.itemTag().ifPresent(tagId -> slotTag.putString("ItemTag", tagId.toString()));
                    inputSlots.add(slotTag);
                }
                raw.put("InputSlots", inputSlots);
                ListTag outputs = new ListTag();
                recipe.outputs().forEach(stack -> outputs.add(GenericStack.writeTag(stack)));
                raw.put("GenericOutputs", outputs);
                recipeTags.add(raw);
            }
            tag.put("Recipes", recipeTags);
            return tag;
        }

        private static Page load(CompoundTag tag) {
            List<AggregateRecipe> recipes = new ArrayList<>();
            for (Tag recipeTag : tag.getList("Recipes", Tag.TAG_COMPOUND)) {
                CompoundTag raw = (CompoundTag) recipeTag;
                try {
                    List<GenericStack> inputs = parseStacks(raw, "GenericInputs", "Inputs");
                    List<AggregateInputSlot> inputSlots = parseInputSlots(raw, inputs);
                    List<GenericStack> outputs = parseStacks(raw, "GenericOutputs", "Outputs");
                    recipes.add(new AggregateRecipe(
                            raw.getString("PatternId"),
                            new ResourceLocation(raw.getString("RecipeId")),
                            AggregatePatternKind.fromName(raw.getString("Kind")),
                            inputs, inputSlots, outputs,
                            raw.getInt("ProbabilisticOutputMask"),
                            raw.getInt("ProcessingTicks")));
                } catch (RuntimeException error) {
                    AeAllPattern.LOGGER.warn("Skipping unreadable aggregate recipe page entry", error);
                }
            }
            return new Page(recipes, false);
        }

        private static List<GenericStack> parseStacks(
                CompoundTag recipe, String genericName, String legacyName) {
            boolean generic = recipe.contains(genericName, Tag.TAG_LIST);
            ListTag tags = recipe.getList(generic ? genericName : legacyName, Tag.TAG_COMPOUND);
            List<GenericStack> stacks = new ArrayList<>(tags.size());
            for (Tag raw : tags) {
                GenericStack stack;
                if (generic) {
                    stack = GenericStack.readTag((CompoundTag) raw);
                } else {
                    ItemStack item = ItemStack.of((CompoundTag) raw);
                    stack = item.isEmpty() ? null : GenericStack.fromItemStack(item);
                }
                if (stack == null || stack.what() == null || stack.amount() <= 0) {
                    throw new IllegalArgumentException("empty stack in aggregate page");
                }
                stacks.add(stack);
            }
            return stacks;
        }

        private static List<AggregateInputSlot> parseInputSlots(
                CompoundTag recipe,
                List<GenericStack> legacyInputs) {
            if (!recipe.contains("InputSlots", Tag.TAG_LIST)) {
                return legacyInputs.stream().map(AggregateInputSlot::exact).toList();
            }
            List<AggregateInputSlot> slots = new ArrayList<>();
            for (Tag rawSlot : recipe.getList("InputSlots", Tag.TAG_COMPOUND)) {
                CompoundTag slotTag = (CompoundTag) rawSlot;
                List<GenericStack> alternatives = new ArrayList<>();
                for (Tag rawAlternative : slotTag.getList("Alternatives", Tag.TAG_COMPOUND)) {
                    GenericStack stack = GenericStack.readTag((CompoundTag) rawAlternative);
                    if (stack == null || stack.what() == null || stack.amount() <= 0) {
                        throw new IllegalArgumentException("empty alternative in aggregate input slot");
                    }
                    alternatives.add(stack);
                }
                Optional<ResourceLocation> itemTag = slotTag.contains("ItemTag", Tag.TAG_STRING)
                        ? Optional.of(new ResourceLocation(slotTag.getString("ItemTag")))
                        : Optional.empty();
                slots.add(AggregateInputSlot.fromSavedData(alternatives, itemTag));
            }
            return List.copyOf(slots);
        }
    }
}
