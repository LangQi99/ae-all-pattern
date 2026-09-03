package io.github.langqi99.aeallpattern.aggregate;

import appeng.api.stacks.GenericStack;
import io.github.langqi99.aeallpattern.config.AeAllPatternCommonConfig;
import io.github.langqi99.aeallpattern.network.AggregateSearchResultPayload;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.registry.ModItems;
import io.github.langqi99.aeallpattern.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * Server-authoritative pattern picker for one held aggregate pattern. The client keeps an
 * optimistic copy of the selection so clicks feel instant, exactly like the option menu.
 */
public final class AggregatePatternSelectionMenu extends AbstractContainerMenu {
    /** Negative clickMenuButton ids are reserved for bulk actions and option toggles. */
    public static final int SELECT_ALL = -1;
    public static final int DESELECT_ALL = -2;
    private static final int OPTION_BUTTON_BASE = -100;

    /** The default logical page is 1024; transport splits it into smaller safe packets. */
    public static final int DEFAULT_UI_PAGE_SIZE = 1024;
    public static final int MAX_SYNCED_ENTRIES = 16384;

    private static final int MAX_STACKS_PER_LIST = 81;

    private final Inventory inventory;
    @Nullable
    private final InteractionHand hand;
    private List<Entry> entries;
    private List<Boolean> entryEnabledStates;
    private AggregatePatternSelection selection;
    private boolean filteredView;
    private String currentSearchText = "";
    private int totalEntryCount;
    private int selectedEntryCount;
    private int optionFlags;

    /** Client-side summary of one child pattern inside the aggregate. */
    public record Entry(String patternId, List<GenericStack> inputs, List<GenericStack> outputs) {
        public Entry {
            patternId = patternId == null ? "" : patternId;
            inputs = List.copyOf(inputs == null ? List.of() : inputs);
            outputs = List.copyOf(outputs == null ? List.of() : outputs);
        }
    }

    public AggregatePatternSelectionMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, data.readEnum(InteractionHand.class), readEntries(data),
                AggregatePatternSelection.STREAM_CODEC.decode(data));
    }

    public AggregatePatternSelectionMenu(
            int id,
            Inventory inventory,
            @Nullable InteractionHand hand,
            List<Entry> entries,
            AggregatePatternSelection selection) {
        super(ModMenus.AGGREGATE_PATTERN_SELECTION.get(), id);
        this.inventory = inventory;
        this.hand = hand;
        this.entries = List.copyOf(entries);
        this.selection = selection == null ? AggregatePatternSelection.ALL_ENABLED : selection;
        this.entryEnabledStates = this.entries.stream()
                .map(entry -> this.selection.isEnabled(entry.patternId()))
                .toList();
        this.totalEntryCount = this.entries.size();
        this.selectedEntryCount = (int) this.entryEnabledStates.stream().filter(Boolean::booleanValue).count();
        optionFlags = currentOptions().flags();
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return inventory.player.level().isClientSide() ? optionFlags : currentOptions().flags();
            }

            @Override
            public void set(int value) {
                optionFlags = value & 8191;
            }
        });
    }

    public static int optionButtonId(int optionIndex) {
        return OPTION_BUTTON_BASE - optionIndex;
    }

    public AggregatePatternOptions getOptions() {
        return AggregatePatternOptions.fromFlags(optionFlags);
    }

    public static List<Entry> entriesFromRecipes(List<AggregateRecipe> recipes) {
        return entriesFromRecipes(recipes, 0);
    }

    public static List<Entry> entriesFromRecipes(List<AggregateRecipe> recipes, int pageIndex) {
        int pageSize = uiPageSize();
        int from = (int) Math.min(recipes.size(), (long) Math.max(0, pageIndex) * pageSize);
        int to = Math.min(recipes.size(), from + pageSize);
        List<Entry> entries = new ArrayList<>(to - from);
        for (AggregateRecipe recipe : recipes.subList(from, to)) {
            entries.add(new Entry(
                    recipe.patternId(),
                    recipe.inputs().stream().limit(MAX_STACKS_PER_LIST).toList(),
                    recipe.outputs().stream().limit(MAX_STACKS_PER_LIST).toList()));
        }
        return List.copyOf(entries);
    }

    public static int pageCount(int entryCount) {
        int pageSize = uiPageSize();
        return Math.max(1, (Math.max(0, entryCount) + pageSize - 1) / pageSize);
    }

    public static int uiPageSize() {
        return Math.min(MAX_SYNCED_ENTRIES, AeAllPatternCommonConfig.SELECTION_DISPLAY_LIMIT.getAsInt());
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Client-side replacement of the visible entries after a search result arrives. */
    public void updateEntries(
            List<Entry> entries,
            List<Boolean> enabledStates,
            boolean filteredView,
            int totalEntryCount,
            int selectedEntryCount) {
        this.entries = List.copyOf(entries);
        this.entryEnabledStates = List.copyOf(enabledStates);
        this.filteredView = filteredView;
        this.totalEntryCount = Math.max(0, totalEntryCount);
        this.selectedEntryCount = Math.clamp(selectedEntryCount, 0, this.totalEntryCount);
    }

    /**
     * Server-side search across the aggregate's complete recipe list, not just the synced
     * subset. Replaces the local entry table and streams the filtered result back in bounded
     * pages so the client picker can search every stored pattern.
     */
    public void applySearch(
            ServerPlayer player,
            String searchText,
            boolean searchOutputs,
            int requestedPageIndex,
            UUID requestId) {
        ItemStack stack = stack();
        if (!isSelectable(stack) || player.level().isClientSide()) {
            return;
        }
        AggregatePatternRef ref = stack.get(ModDataComponents.AGGREGATE_PATTERN.get());
        if (ref == null) {
            return;
        }
        List<AggregateRecipe> recipes = AggregatePatternLibrary.get(player.server)
                .recipes(player.server, ref.libraryId())
                .orElseGet(List::of);
        // Search runs over the complete recipe list with no cap: the user wants to search
        // every stored pattern, not just the initially synced subset.
        // The management screen follows AE terminal search: one field matches both sides of
        // the pattern. Keep the packet flag for protocol compatibility with 0.2.1 clients.
        List<AggregateRecipe> filteredRecipes = AggregatePatternSearch.filterRecipesAny(
                recipes, searchText, Integer.MAX_VALUE);
        int resultPageCount = pageCount(filteredRecipes.size());
        int resultPageIndex = Math.clamp(requestedPageIndex, 0, resultPageCount - 1);
        List<Entry> filtered = entriesFromRecipes(filteredRecipes, resultPageIndex);
        List<Boolean> enabledStates = filtered.stream()
                .map(entry -> selection.isEnabled(entry.patternId()))
                .toList();
        this.entries = List.copyOf(filtered);
        this.entryEnabledStates = enabledStates;
        this.filteredView = !searchText.isBlank();
        this.currentSearchText = searchText;
        this.totalEntryCount = filteredRecipes.size();
        this.selectedEntryCount = (int) filteredRecipes.stream()
                .filter(recipe -> selection.isEnabled(recipe.patternId()))
                .count();
        int chunkCount = Math.max(1, (filtered.size() + AggregateSearchResultPayload.MAX_ENTRIES_PER_PAGE - 1)
                / AggregateSearchResultPayload.MAX_ENTRIES_PER_PAGE);
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int from = chunkIndex * AggregateSearchResultPayload.MAX_ENTRIES_PER_PAGE;
            int to = Math.min(filtered.size(), from + AggregateSearchResultPayload.MAX_ENTRIES_PER_PAGE);
            PacketDistributor.sendToPlayer(player, new AggregateSearchResultPayload(
                    requestId,
                    chunkIndex,
                    chunkCount,
                    resultPageIndex,
                    resultPageCount,
                    totalEntryCount,
                    selectedEntryCount,
                    filtered.subList(from, to),
                    enabledStates.subList(from, to)));
        }
    }

    public int totalRecipeCount() {
        return totalEntryCount;
    }

    public boolean isEnabled(int index) {
        return index >= 0 && index < entryEnabledStates.size() && entryEnabledStates.get(index);
    }

    public boolean isAllSelected() {
        return selectedEntryCount == totalEntryCount;
    }

    public long selectedCount() {
        return selectedEntryCount;
    }

    public ItemStack stack() {
        return hand == null ? ItemStack.EMPTY : inventory.player.getItemInHand(hand);
    }

    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        int optionIndex = OPTION_BUTTON_BASE - id;
        if (optionIndex >= AggregatePatternConfigMenu.TOGGLE_SPLIT_SAME_ITEMS
                && optionIndex <= AggregatePatternConfigMenu.TOGGLE_SKIP_DURABILITY_CONSUMING_RECIPES) {
            return toggleOption(player, optionIndex);
        }

        AggregatePatternSelection updated;
        if (id == SELECT_ALL) {
            updated = filteredView
                    ? selection.withEnabled(bulkPatternIds(player), true)
                    : AggregatePatternSelection.ALL_ENABLED;
        } else if (id == DESELECT_ALL) {
            updated = filteredView
                    ? selection.withEnabled(bulkPatternIds(player), false)
                    : AggregatePatternSelection.NONE_ENABLED;
        } else if (id >= 0 && id < entries.size()) {
            updated = selection.toggled(entries.get(id).patternId());
        } else {
            return false;
        }

        if (id == SELECT_ALL || id == DESELECT_ALL) {
            boolean enabled = id == SELECT_ALL;
            entryEnabledStates = entries.stream().map(ignored -> enabled).toList();
            selectedEntryCount = enabled ? totalEntryCount : 0;
        } else {
            List<Boolean> updatedStates = new ArrayList<>(entryEnabledStates);
            boolean enabled = !updatedStates.get(id);
            updatedStates.set(id, enabled);
            entryEnabledStates = List.copyOf(updatedStates);
            selectedEntryCount = Math.clamp(selectedEntryCount + (enabled ? 1 : -1), 0, totalEntryCount);
        }

        if (player.level().isClientSide()) {
            selection = updated;
            return true;
        }

        ItemStack stack = stack();
        if (!isSelectable(stack)) {
            return false;
        }
        AggregatePatternRef ref = stack.get(ModDataComponents.AGGREGATE_PATTERN.get());
        if (ref != null && player instanceof ServerPlayer serverPlayer) {
            List<String> currentPatternIds = AggregatePatternLibrary.get(serverPlayer.server)
                    .recipes(serverPlayer.server, ref.libraryId())
                    .orElseGet(List::of).stream()
                    .map(AggregateRecipe::patternId)
                    .toList();
            updated = updated.reconciled(currentPatternIds);
        }
        if (updated.isAllEnabled()) {
            stack.remove(ModDataComponents.AGGREGATE_PATTERN_SELECTION.get());
        } else {
            stack.set(ModDataComponents.AGGREGATE_PATTERN_SELECTION.get(), updated);
        }
        player.getInventory().setChanged();
        selection = updated;
        broadcastChanges();
        return true;
    }

    private List<String> bulkPatternIds(Player player) {
        if (!filteredView || player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return entries.stream().map(Entry::patternId).toList();
        }
        AggregatePatternRef ref = stack().get(ModDataComponents.AGGREGATE_PATTERN.get());
        if (ref == null) {
            return List.of();
        }
        List<AggregateRecipe> recipes = AggregatePatternLibrary.get(serverPlayer.server)
                .recipes(serverPlayer.server, ref.libraryId())
                .orElseGet(List::of);
        return AggregatePatternSearch.filterRecipesAny(recipes, currentSearchText, Integer.MAX_VALUE)
                .stream()
                .map(AggregateRecipe::patternId)
                .toList();
    }

    private boolean toggleOption(Player player, int optionIndex) {
        ItemStack stack = stack();
        if (!isSelectable(stack)) {
            return false;
        }
        int mask = 1 << optionIndex;
        if (player.level().isClientSide()) {
            optionFlags ^= mask;
            return true;
        }
        optionFlags = options(stack).flags() ^ mask;
        stack.set(ModDataComponents.AGGREGATE_PATTERN_OPTIONS.get(), AggregatePatternOptions.fromFlags(optionFlags));
        player.getInventory().setChanged();
        broadcastChanges();
        return true;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return hand != null && isSelectable(player.getItemInHand(hand));
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    private static boolean isSelectable(ItemStack stack) {
        return stack.is(ModItems.AGGREGATE_PATTERN.get())
                && stack.has(ModDataComponents.AGGREGATE_PATTERN.get());
    }

    private static AggregatePatternOptions options(ItemStack stack) {
        AggregatePatternOptions options = stack.get(ModDataComponents.AGGREGATE_PATTERN_OPTIONS.get());
        return options == null ? AggregatePatternOptions.DEFAULT : options;
    }

    private AggregatePatternOptions currentOptions() {
        return options(stack());
    }

    private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_SYNCED_ENTRIES) {
            throw new IllegalArgumentException("invalid aggregate selection entry count: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String patternId = buffer.readUtf(AggregatePatternSelection.MAX_ID_LENGTH);
            entries.add(new Entry(patternId, readStacks(buffer), readStacks(buffer)));
        }
        return entries;
    }

    private static List<GenericStack> readStacks(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_STACKS_PER_LIST) {
            throw new IllegalArgumentException("invalid aggregate selection stack count: " + count);
        }
        List<GenericStack> stacks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            stacks.add(GenericStack.STREAM_CODEC.decode(buffer));
        }
        return stacks;
    }
}
