package io.github.langqi99.aeallpattern.client;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternConfigMenu;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternRef;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternSelectionMenu;
import io.github.langqi99.aeallpattern.network.AggregateSearchPayload;
import io.github.langqi99.aeallpattern.network.AggregateSearchResultPayload;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * Unified editor for one aggregate pattern item. The settings tab edits encoding options and
 * the patterns tab toggles published child patterns without changing their original order.
 */
public final class AggregatePatternSelectionScreen extends AbstractContainerScreen<AggregatePatternSelectionMenu> {
    private static final int DEFAULT_COLUMNS = 11;
    private static final int MIN_COLUMNS = 8;
    private static final int MAX_COLUMNS = 12;
    private static final int MIN_VISIBLE_ROWS = 4;
    private static final int MAX_VISIBLE_ROWS = 6;
    private static final float SCREEN_WIDTH_RATIO = 0.55F;
    private static final float SCREEN_HEIGHT_RATIO = 0.80F;
    // Grid starts 8 px from the left; after its final cell we keep the scrollbar
    // and an AE-like 9 px right inset instead of the old fixed-layout dead space.
    private static final int NON_GRID_WIDTH = 24;
    private static final int SLOT_SIZE = 26;
    private static final int SLOT_PITCH = 28;
    private static final int GRID_LEFT = 8;
    private static final int SIDE_TAB_WIDTH = 22;
    private static final int SIDE_TAB_HEIGHT = 24;
    private static final int SEARCH_TOP = 30;
    private static final int GRID_TOP = 54;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_GAP = 2;
    private static final int RIGHT_PADDING = 12;
    private static final int BOTTOM_AREA = 32;
    private static final float ICON_SCALE = 0.75F;
    private static final int INPUT_ICON_XY = 2;
    private static final int OUTPUT_ICON_XY = 12;
    private static final int SEARCH_BOX_HEIGHT = 16;
    private static final int ALL_BUTTON_WIDTH = 66;
    private static final int ALL_BUTTON_HEIGHT = 18;
    private static final int PAGE_BUTTON_SIZE = 18;
    private static final int PAGE_LABEL_WIDTH = 48;
    private static final int PAGE_CONTROL_GAP = 2;

    private static final int SELECTED_FILL = 0xFFC7DFE2;
    private static final int SELECTED_FILL_HOVER = 0xFFD9EEF0;
    private static final int SELECTED_OUTLINE = 0xFF2F929D;
    private static final int UNSELECTED_FILL = 0xFFADB1C2;
    private static final int UNSELECTED_FILL_HOVER = 0xFFC2C5D2;
    private static final int UNSELECTED_OUTLINE = 0xFF77798B;
    private static final int PANEL_BG = 0xFFC9CAD4;
    private static final int PANEL_BORDER = 0xFF454559;
    private static final int PANEL_INNER = 0xFFF0F0F5;
    private static final int SCROLLBAR_TRACK = 0xFFA7A9B8;
    private static final int SCROLLBAR_THUMB = 0xFF686A7D;
    private static final int SEARCH_FILL = 0xFFAEB3C7;
    private static final int SEARCH_OUTLINE = 0xFF686A7D;

    private Button allButton;
    private Button previousPageButton;
    private Button nextPageButton;
    private EditBox searchBox;
    private final List<AggregateConfigOptionButton> optionButtons = new ArrayList<>();
    private boolean settingsPage = true;
    private boolean searchDirty;
    private long lastSearchAt;
    private UUID pendingRequestId;
    private boolean searchPending;
    private final Map<Integer, List<AggregatePatternSelectionMenu.Entry>> pendingPages = new HashMap<>();
    private final Map<Integer, List<Boolean>> pendingEnabledStates = new HashMap<>();
    private int currentResultPage;
    private int resultPageCount = 1;
    private boolean initialPageRequested;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private int columns = DEFAULT_COLUMNS;
    private int visibleRows = MIN_VISIBLE_ROWS;

    public AggregatePatternSelectionScreen(
            AggregatePatternSelectionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 354;
        imageHeight = GRID_TOP + visibleRows * SLOT_PITCH - 1 + BOTTOM_AREA;
        titleLabelX = 30;
        titleLabelY = 10;
    }

    @Override
    protected void init() {
        String retainedSearch = searchBox == null ? "" : searchBox.getValue();
        updateAdaptiveDimensions();
        super.init();
        optionButtons.clear();
        scrollOffset = 0;
        int boxX = leftPos + 14;
        searchBox = addRenderableWidget(new EditBox(
                font,
                boxX,
                topPos + SEARCH_TOP,
                imageWidth - 28,
                SEARCH_BOX_HEIGHT,
                Component.translatable("gui.aeallpattern.aggregate_selection.search_hint")));
        searchBox.setMaxLength(64);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFF303044);
        searchBox.setHint(Component.translatable("gui.aeallpattern.aggregate_selection.search_hint"));
        searchBox.setValue(retainedSearch);
        searchBox.setResponder(text -> {
            searchDirty = true;
            currentResultPage = 0;
            scrollOffset = 0;
            clampScroll();
        });
        int buttonY = topPos + imageHeight - 24;
        allButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.aeallpattern.aggregate_selection.select_all"),
                        button -> onAllButtonClick())
                .bounds(leftPos + 8, buttonY, ALL_BUTTON_WIDTH, ALL_BUTTON_HEIGHT)
                .build());
        int pageX = leftPos + pageControlsStartX();
        previousPageButton = addRenderableWidget(Button.builder(
                        Component.literal("‹"), button -> requestResultPage(currentResultPage - 1))
                .bounds(pageX, buttonY, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE)
                .build());
        nextPageButton = addRenderableWidget(Button.builder(
                        Component.literal("›"), button -> requestResultPage(currentResultPage + 1))
                .bounds(pageX + PAGE_BUTTON_SIZE + PAGE_CONTROL_GAP + PAGE_LABEL_WIDTH + PAGE_CONTROL_GAP,
                        buttonY,
                        PAGE_BUTTON_SIZE,
                        PAGE_BUTTON_SIZE)
                .build());
        addConfigOptions();
        updatePageVisibility();
        updateAllButton();
        updatePageButtons();
        clampScroll();
        if (!initialPageRequested) {
            initialPageRequested = true;
            requestResultPage(0);
        }
    }

    private void addConfigOptions() {
        int optionGap = 2;
        int optionWidth = (imageWidth - 24 - optionGap) / 2;
        int rightX = 12 + optionWidth + optionGap;
        addOption(12, 35, optionWidth,
                "gui.aeallpattern.aggregate_config.split_same_items",
                "gui.aeallpattern.aggregate_config.split_same_items.tooltip",
                () -> menu.getOptions().splitSameItems(),
                AggregatePatternConfigMenu.TOGGLE_SPLIT_SAME_ITEMS);
        addOption(rightX, 35, optionWidth,
                "gui.aeallpattern.aggregate_config.ignore_output_nbt",
                "gui.aeallpattern.aggregate_config.ignore_output_nbt.tooltip",
                () -> menu.getOptions().ignoreOutputComponents(),
                AggregatePatternConfigMenu.TOGGLE_IGNORE_OUTPUT_COMPONENTS);
        addOption(12, 53, optionWidth,
                "gui.aeallpattern.aggregate_config.skip_probabilistic_main",
                "gui.aeallpattern.aggregate_config.skip_probabilistic_main.tooltip",
                () -> menu.getOptions().skipProbabilisticMainOutput(),
                AggregatePatternConfigMenu.TOGGLE_SKIP_PROBABILISTIC_MAIN_OUTPUT);
        addOption(rightX, 53, optionWidth,
                "gui.aeallpattern.aggregate_config.ignore_probabilistic_byproducts",
                "gui.aeallpattern.aggregate_config.ignore_probabilistic_byproducts.tooltip",
                () -> menu.getOptions().ignoreProbabilisticByproducts(),
                AggregatePatternConfigMenu.TOGGLE_IGNORE_PROBABILISTIC_BYPRODUCTS);
        addOption(12, 71, optionWidth,
                "gui.aeallpattern.aggregate_config.allow_item_substitutions",
                "gui.aeallpattern.aggregate_config.allow_item_substitutions.tooltip",
                () -> menu.getOptions().allowItemSubstitutions(),
                AggregatePatternConfigMenu.TOGGLE_ALLOW_ITEM_SUBSTITUTIONS);
        addOption(rightX, 71, optionWidth,
                "gui.aeallpattern.aggregate_config.allow_fluid_substitutions",
                "gui.aeallpattern.aggregate_config.allow_fluid_substitutions.tooltip",
                () -> menu.getOptions().allowFluidSubstitutions(),
                AggregatePatternConfigMenu.TOGGLE_ALLOW_FLUID_SUBSTITUTIONS);
        addOption(12, 89, optionWidth,
                "gui.aeallpattern.aggregate_config.remove_input_fluids",
                "gui.aeallpattern.aggregate_config.remove_input_fluids.tooltip",
                () -> menu.getOptions().removeInputFluids(),
                AggregatePatternConfigMenu.TOGGLE_REMOVE_INPUT_FLUIDS);
        addOption(rightX, 89, optionWidth,
                "gui.aeallpattern.aggregate_config.remove_output_fluids",
                "gui.aeallpattern.aggregate_config.remove_output_fluids.tooltip",
                () -> menu.getOptions().removeOutputFluids(),
                AggregatePatternConfigMenu.TOGGLE_REMOVE_OUTPUT_FLUIDS);
        addOption(12, 107, optionWidth,
                "gui.aeallpattern.aggregate_config.remove_input_chemicals",
                "gui.aeallpattern.aggregate_config.remove_input_chemicals.tooltip",
                () -> menu.getOptions().removeInputChemicals(),
                AggregatePatternConfigMenu.TOGGLE_REMOVE_INPUT_CHEMICALS);
        addOption(rightX, 107, optionWidth,
                "gui.aeallpattern.aggregate_config.remove_output_chemicals",
                "gui.aeallpattern.aggregate_config.remove_output_chemicals.tooltip",
                () -> menu.getOptions().removeOutputChemicals(),
                AggregatePatternConfigMenu.TOGGLE_REMOVE_OUTPUT_CHEMICALS);
        addOption(12, 125, optionWidth,
                "gui.aeallpattern.aggregate_config.remove_processing_catalysts",
                "gui.aeallpattern.aggregate_config.remove_processing_catalysts.tooltip",
                () -> menu.getOptions().removeProcessingCatalysts(),
                AggregatePatternConfigMenu.TOGGLE_REMOVE_PROCESSING_CATALYSTS);
        addOption(rightX, 125, optionWidth,
                "gui.aeallpattern.aggregate_config.swap_first_and_last_inputs",
                "gui.aeallpattern.aggregate_config.swap_first_and_last_inputs.tooltip",
                () -> menu.getOptions().swapFirstAndLastInputs(),
                AggregatePatternConfigMenu.TOGGLE_SWAP_FIRST_AND_LAST_INPUTS);
        addOption(12, 143, optionWidth,
                "gui.aeallpattern.aggregate_config.skip_durability_consuming_recipes",
                "gui.aeallpattern.aggregate_config.skip_durability_consuming_recipes.tooltip",
                () -> menu.getOptions().skipDurabilityConsumingRecipes(),
                AggregatePatternConfigMenu.TOGGLE_SKIP_DURABILITY_CONSUMING_RECIPES);
    }

    /** Uses a bounded share of the screen instead of growing to nearly full-screen. */
    private void updateAdaptiveDimensions() {
        int targetWidth = Math.round(width * SCREEN_WIDTH_RATIO);
        columns = Math.clamp((targetWidth - NON_GRID_WIDTH) / SLOT_PITCH, MIN_COLUMNS, MAX_COLUMNS);
        int targetHeight = Math.round(height * SCREEN_HEIGHT_RATIO);
        visibleRows = Math.clamp(
                (targetHeight - GRID_TOP - BOTTOM_AREA + 1) / SLOT_PITCH,
                MIN_VISIBLE_ROWS,
                MAX_VISIBLE_ROWS);
        imageWidth = NON_GRID_WIDTH + columns * SLOT_PITCH;
        imageHeight = GRID_TOP + visibleRows * SLOT_PITCH - 1 + BOTTOM_AREA;
    }

    private void addOption(
            int x,
            int y,
            int width,
            String labelKey,
            String tooltipKey,
            java.util.function.BooleanSupplier enabled,
            int toggleIndex) {
        optionButtons.add(addRenderableWidget(new AggregateConfigOptionButton(
                leftPos + x,
                topPos + y,
                width,
                Component.translatable(labelKey),
                Component.translatable(tooltipKey),
                enabled,
                () -> toggleOption(toggleIndex))));
    }

    private void toggleOption(int optionIndex) {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        int id = AggregatePatternSelectionMenu.optionButtonId(optionIndex);
        menu.clickMenuButton(minecraft.player, id);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void setSettingsPage(boolean settingsPage) {
        if (this.settingsPage == settingsPage) {
            return;
        }
        this.settingsPage = settingsPage;
        draggingScrollbar = false;
        updatePageVisibility();
    }

    private void updatePageVisibility() {
        if (searchBox != null) {
            searchBox.visible = !settingsPage;
            if (settingsPage) {
                searchBox.setFocused(false);
            }
        }
        if (allButton != null) {
            allButton.visible = !settingsPage;
        }
        if (previousPageButton != null) {
            previousPageButton.visible = !settingsPage && resultPageCount > 1;
        }
        if (nextPageButton != null) {
            nextPageButton.visible = !settingsPage && resultPageCount > 1;
        }
        optionButtons.forEach(button -> button.visible = settingsPage);
    }

    private void onAllButtonClick() {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        int id = menu.isAllSelected()
                ? AggregatePatternSelectionMenu.DESELECT_ALL
                : AggregatePatternSelectionMenu.SELECT_ALL;
        menu.clickMenuButton(minecraft.player, id);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        clampScroll();
    }

    private void onSlotClick(int entryIndex) {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        menu.clickMenuButton(minecraft.player, entryIndex);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, entryIndex);
        clampScroll();
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, menu.entries().size() - visibleSlots());
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);
    }

    private int visibleSlots() {
        return columns * visibleRows;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (!settingsPage && scrollY != 0) {
            scrollOffset -= (int) Math.signum(scrollY);
            clampScroll();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inMainTab(mouseX, mouseY, 0)) {
                setSettingsPage(true);
                return true;
            }
            if (inMainTab(mouseX, mouseY, 1)) {
                setSettingsPage(false);
                return true;
            }
        }
        if (!settingsPage && button == 0 && inScrollbarArea(mouseX, mouseY)) {
            draggingScrollbar = true;
            scrollToMouse(mouseY);
            return true;
        }
        if (!settingsPage && button == 0) {
            int slotIndex = slotIndexAt(mouseX, mouseY);
            if (slotIndex >= 0) {
                int entryIndex = visibleOrderIndex(slotIndex);
                if (entryIndex >= 0) {
                    onSlotClick(entryIndex);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean inMainTab(double mouseX, double mouseY, int index) {
        int x = leftPos - SIDE_TAB_WIDTH;
        int y = topPos + 7 + index * SIDE_TAB_HEIGHT;
        return mouseX >= x && mouseX < x + SIDE_TAB_WIDTH
                && mouseY >= y && mouseY < y + SIDE_TAB_HEIGHT;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Debounce typing, then let the server filter the complete recipe list.
        if (!settingsPage && searchDirty && searchBox != null
                && System.currentTimeMillis() - lastSearchAt > 250) {
            searchDirty = false;
            lastSearchAt = System.currentTimeMillis();
            requestResultPage(0);
        }
    }

    private void requestResultPage(int pageIndex) {
        if (searchBox == null) {
            return;
        }
        pendingRequestId = UUID.randomUUID();
        searchPending = true;
        pendingPages.clear();
        pendingEnabledStates.clear();
        updatePageButtons();
        PacketDistributor.sendToServer(new AggregateSearchPayload(
                pendingRequestId, searchBox.getValue(), true, Math.max(0, pageIndex)));
    }

    /** Called on the render thread when a search result page arrives. */
    public void receiveSearchResult(
            AggregateSearchResultPayload payload, AggregatePatternSelectionMenu menu) {
        if (!payload.requestId().equals(pendingRequestId)) {
            return; // stale response from an earlier query
        }
        pendingPages.put(payload.chunkIndex(), payload.entries());
        pendingEnabledStates.put(payload.chunkIndex(), payload.enabledStates());
        if (pendingPages.size() < payload.chunkCount()) {
            return;
        }
        List<AggregatePatternSelectionMenu.Entry> flat = new ArrayList<>(payload.chunkCount() * 64);
        List<Boolean> enabledStates = new ArrayList<>(payload.chunkCount() * 64);
        for (int index = 0; index < payload.chunkCount(); index++) {
            flat.addAll(pendingPages.get(index));
            enabledStates.addAll(pendingEnabledStates.get(index));
        }
        pendingPages.clear();
        pendingEnabledStates.clear();
        currentResultPage = payload.resultPageIndex();
        resultPageCount = payload.resultPageCount();
        menu.updateEntries(
                flat,
                enabledStates,
                searchBox != null && !searchBox.getValue().isBlank(),
                payload.totalResults(),
                payload.selectedResults());
        searchPending = false;
        scrollOffset = 0;
        updatePageButtons();
        clampScroll();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            if (keyCode == 256) { // ESC drops focus instead of closing while typing
                searchBox.setFocused(false);
                return true;
            }
            searchBox.keyPressed(keyCode, scanCode, modifiers);
            // Printable keys are inserted later through charTyped, so EditBox returns false
            // here. Still consume them to keep inventory-key bindings (normally E) from
            // reaching AbstractContainerScreen and closing the picker while typing.
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollToMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean inScrollbarArea(double mouseX, double mouseY) {
        if (settingsPage || menu.entries().size() <= visibleSlots()) {
            return false;
        }
        int trackX = leftPos + GRID_LEFT + columns * SLOT_PITCH - 1 + SCROLLBAR_GAP;
        int trackY = topPos + GRID_TOP;
        return mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
                && mouseY >= trackY && mouseY < trackY + visibleRows * SLOT_PITCH - 1;
    }

    /** Maps a mouse Y position to a scroll offset so the thumb follows the cursor while dragging. */
    private void scrollToMouse(double mouseY) {
        int total = menu.entries().size();
        if (total <= visibleSlots()) {
            return;
        }
        int trackY = topPos + GRID_TOP;
        int trackHeight = visibleRows * SLOT_PITCH - 1;
        int thumbHeight = Math.max(12, trackHeight * visibleSlots() / total);
        int maxScroll = total - visibleSlots();
        double relative = (mouseY - trackY - thumbHeight / 2.0) / Math.max(1.0, trackHeight - thumbHeight);
        scrollOffset = (int) Math.round(Math.clamp(relative, 0.0, 1.0) * maxScroll);
        clampScroll();
    }

    private int slotIndexAt(double mouseX, double mouseY) {
        int gridRight = leftPos + GRID_LEFT + columns * SLOT_PITCH - 1;
        int gridBottom = topPos + GRID_TOP + visibleRows * SLOT_PITCH - 1;
        if (mouseX < leftPos + GRID_LEFT || mouseX > gridRight
                || mouseY < topPos + GRID_TOP || mouseY > gridBottom) {
            return -1;
        }
        int col = (int) ((mouseX - leftPos - GRID_LEFT) / SLOT_PITCH);
        int row = (int) ((mouseY - topPos - GRID_TOP) / SLOT_PITCH);
        if (col < 0 || col >= columns || row < 0 || row >= visibleRows) {
            return -1;
        }
        int cellX = leftPos + GRID_LEFT + col * SLOT_PITCH;
        int cellY = topPos + GRID_TOP + row * SLOT_PITCH;
        if (mouseX > cellX + SLOT_SIZE || mouseY > cellY + SLOT_SIZE) {
            return -1;
        }
        return row * columns + col;
    }

    private int visibleOrderIndex(int slotIndex) {
        int target = scrollOffset + slotIndex;
        return target < menu.entries().size() ? target : -1;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderMainTabs(graphics, mouseX, mouseY);
        if (!settingsPage) {
            renderGrid(graphics, mouseX, mouseY);
            renderScrollbar(graphics, menu.entries().size());
            updateAllButton();
            updatePageButtons();
        }
        renderHoveredTooltip(graphics, mouseX, mouseY);
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        List<AggregatePatternSelectionMenu.Entry> entries = menu.entries();

        if (entries.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.aeallpattern.aggregate_selection.empty"),
                    leftPos + imageWidth / 2,
                    topPos + GRID_TOP + 50,
                    0xFF67677A);
            return;
        }
        int hoveredSlot = slotIndexAt(mouseX, mouseY);
        for (int slotIndex = 0; slotIndex < visibleSlots(); slotIndex++) {
            int entryIndex = scrollOffset + slotIndex;
            if (entryIndex >= entries.size()) {
                break;
            }
            int col = slotIndex % columns;
            int row = slotIndex / columns;
            int cellX = leftPos + GRID_LEFT + col * SLOT_PITCH;
            int cellY = topPos + GRID_TOP + row * SLOT_PITCH;
            AggregatePatternSelectionMenu.Entry entry = entries.get(entryIndex);
            boolean enabled = menu.isEnabled(entryIndex);
            boolean hovered = slotIndex == hoveredSlot;

            int fill = enabled
                    ? (hovered ? SELECTED_FILL_HOVER : SELECTED_FILL)
                    : (hovered ? UNSELECTED_FILL_HOVER : UNSELECTED_FILL);
            int outline = enabled ? SELECTED_OUTLINE : UNSELECTED_OUTLINE;

            graphics.fill(cellX, cellY, cellX + SLOT_SIZE, cellY + SLOT_SIZE, fill);
            graphics.renderOutline(cellX, cellY, SLOT_SIZE, SLOT_SIZE, outline);

            float alpha = enabled ? 1.0F : 0.45F;
            renderHalfIcon(graphics, primaryInput(entry), cellX + INPUT_ICON_XY, cellY + INPUT_ICON_XY, alpha);
            renderHalfIcon(graphics, primaryOutput(entry), cellX + OUTPUT_ICON_XY, cellY + OUTPUT_ICON_XY, alpha);

            // Diagonal separator: top-left half shows the input, bottom-right half the output.
            for (int step = 0; step < SLOT_SIZE; step++) {
                int px = cellX + step;
                int py = cellY + SLOT_SIZE - 1 - step;
                graphics.fill(px, py, px + 1, py + 1, outline);
            }
        }
    }

    /** Renders an item icon at 75% size so one input and one output share a single slot. */
    private static void renderHalfIcon(GuiGraphics graphics, ItemStack icon, int x, int y, float alpha) {
        if (icon.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(ICON_SCALE, ICON_SCALE, 1.0F);
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.renderItem(icon, 0, 0);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
    }

    private void renderScrollbar(GuiGraphics graphics, int total) {
        if (total <= visibleSlots()) {
            return;
        }
        int trackX = leftPos + GRID_LEFT + columns * SLOT_PITCH - 1 + SCROLLBAR_GAP;
        int trackY = topPos + GRID_TOP;
        int trackHeight = visibleRows * SLOT_PITCH - 1;
        graphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackHeight, SCROLLBAR_TRACK);
        int thumbHeight = Math.max(12, trackHeight * visibleSlots() / total);
        int maxScroll = total - visibleSlots();
        int thumbY = trackY + (trackHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll);
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, SCROLLBAR_THUMB);
    }

    private void renderMainTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        renderMainTab(graphics, 0, Icon.COG, settingsPage, mouseX, mouseY);
        renderMainTab(graphics, 1, Icon.PATTERN_ACCESS_SHOW, !settingsPage, mouseX, mouseY);
    }

    private void renderMainTab(
            GuiGraphics graphics, int index, Icon icon, boolean active, int mouseX, int mouseY) {
        int x = leftPos - SIDE_TAB_WIDTH;
        int y = topPos + 7 + index * SIDE_TAB_HEIGHT;
        Icon background = active
                ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS
                : inMainTab(mouseX, mouseY, index)
                        ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                        : Icon.TOOLBAR_BUTTON_BACKGROUND;
        background.getBlitter().dest(x, y, SIDE_TAB_WIDTH, SIDE_TAB_HEIGHT).blit(graphics);
        icon.getBlitter().dest(x + 2, y + 3, 18, 18).blit(graphics);
    }

    private void updateAllButton() {
        if (allButton != null) {
            allButton.setMessage(Component.translatable(menu.isAllSelected()
                    ? "gui.aeallpattern.aggregate_selection.deselect_all"
                    : "gui.aeallpattern.aggregate_selection.select_all"));
            allButton.active = !searchDirty && !searchPending && menu.totalRecipeCount() > 0;
        }
    }

    private int pageControlsStartX() {
        int controlsWidth = PAGE_BUTTON_SIZE * 2 + PAGE_LABEL_WIDTH + PAGE_CONTROL_GAP * 2;
        return imageWidth - 8 - controlsWidth;
    }

    private void updatePageButtons() {
        boolean visible = !settingsPage && resultPageCount > 1;
        if (previousPageButton != null) {
            previousPageButton.visible = visible;
            previousPageButton.active = !searchDirty && !searchPending && currentResultPage > 0;
        }
        if (nextPageButton != null) {
            nextPageButton.visible = visible;
            nextPageButton.active = !searchDirty
                    && !searchPending
                    && currentResultPage + 1 < resultPageCount;
        }
    }

    private void renderHoveredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (inMainTab(mouseX, mouseY, 0)) {
            graphics.renderTooltip(
                    font,
                    Component.translatable("gui.aeallpattern.aggregate_management.settings_tab"),
                    mouseX,
                    mouseY);
            return;
        }
        if (inMainTab(mouseX, mouseY, 1)) {
            graphics.renderTooltip(
                    font,
                    Component.translatable("gui.aeallpattern.aggregate_management.patterns_tab"),
                    mouseX,
                    mouseY);
            return;
        }
        // The settings page owns its widget tooltips. Pattern cells only exist on the
        // selection page, even though their coordinates overlap the settings controls.
        if (settingsPage) {
            return;
        }
        int slotIndex = slotIndexAt(mouseX, mouseY);
        if (slotIndex < 0) {
            return;
        }
        int entryIndex = visibleOrderIndex(slotIndex);
        if (entryIndex < 0) {
            return;
        }
        AggregatePatternSelectionMenu.Entry entry = menu.entries().get(entryIndex);
        List<Component> lines = new ArrayList<>();
        lines.add(entry.outputs().isEmpty()
                ? Component.literal(entry.patternId())
                : entry.outputs().getFirst().what().getDisplayName());
        lines.add(Component.translatable("gui.aeallpattern.aggregate_selection.inputs")
                .withStyle(ChatFormatting.GRAY));
        entry.inputs().stream().limit(9).forEach(stack ->
                lines.add(Component.literal("  " + describe(stack)).withStyle(ChatFormatting.DARK_GRAY)));
        if (entry.inputs().size() > 9) {
            lines.add(Component.literal("  +" + (entry.inputs().size() - 9))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable("gui.aeallpattern.aggregate_selection.outputs")
                .withStyle(ChatFormatting.GRAY));
        entry.outputs().stream().limit(5).forEach(stack ->
                lines.add(Component.literal("  " + describe(stack)).withStyle(ChatFormatting.DARK_GRAY)));
        if (entry.outputs().size() > 5) {
            lines.add(Component.literal("  +" + (entry.outputs().size() - 5))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable(menu.isEnabled(entryIndex)
                        ? "gui.aeallpattern.aggregate_selection.selected"
                        : "gui.aeallpattern.aggregate_selection.unselected")
                .withStyle(menu.isEnabled(entryIndex) ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private static String describe(GenericStack stack) {
        AEKey key = stack.what();
        String name = key == null ? "?" : key.getDisplayName().getString();
        return name + " x" + stack.amount();
    }

    private static ItemStack primaryOutput(AggregatePatternSelectionMenu.Entry entry) {
        if (entry.outputs().isEmpty()) {
            return ItemStack.EMPTY;
        }
        AEKey key = entry.outputs().getFirst().what();
        return key instanceof AEItemKey itemKey ? itemKey.toStack() : ItemStack.EMPTY;
    }

    private static ItemStack primaryInput(AggregatePatternSelectionMenu.Entry entry) {
        if (entry.inputs().isEmpty()) {
            return ItemStack.EMPTY;
        }
        AEKey key = entry.inputs().getFirst().what();
        return key instanceof AEItemKey itemKey ? itemKey.toStack() : ItemStack.EMPTY;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_BG);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, PANEL_BORDER);
        graphics.renderOutline(leftPos + 3, topPos + 3, imageWidth - 6, imageHeight - 6, PANEL_INNER);
        if (!settingsPage) {
            int searchX = leftPos + 10;
            int searchY = topPos + SEARCH_TOP - 2;
            graphics.fill(searchX, searchY, searchX + imageWidth - 20,
                    searchY + SEARCH_BOX_HEIGHT + 4, SEARCH_FILL);
            graphics.renderOutline(searchX, searchY, imageWidth - 20,
                    SEARCH_BOX_HEIGHT + 4, searchBox != null && searchBox.isFocused()
                            ? SELECTED_OUTLINE : SEARCH_OUTLINE);
        }

        ItemStack machine = machineStack();
        if (!machine.isEmpty()) {
            graphics.renderItem(machine, leftPos + 10, topPos + 7);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFF3A3A50, false);

        if (settingsPage) {
            graphics.drawString(
                    font,
                    Component.translatable("gui.aeallpattern.aggregate_config.hint"),
                    12,
                    imageHeight - 18,
                    0xFF67677A,
                    false);
            return;
        }

        String count = Component.translatable(
                        "gui.aeallpattern.aggregate_selection.selected_count",
                        menu.selectedCount(), menu.totalRecipeCount())
                .getString();
        int countX = allButton != null
                ? (allButton.getX() - leftPos + allButton.getWidth() + 8)
                : 82;
        boolean showPagination = resultPageCount > 1;
        int countRight = showPagination ? pageControlsStartX() - 4 : imageWidth - 8;
        int countWidth = Math.max(0, countRight - countX);
        String visibleCount = font.plainSubstrByWidth(count, countWidth);
        graphics.drawString(font, visibleCount, countX, imageHeight - 18, 0xFF67677A, false);

        if (showPagination) {
            String page = (currentResultPage + 1) + " / " + resultPageCount;
            int pageLabelX = pageControlsStartX() + PAGE_BUTTON_SIZE + PAGE_CONTROL_GAP;
            graphics.drawCenteredString(
                    font,
                    page,
                    pageLabelX + PAGE_LABEL_WIDTH / 2,
                    imageHeight - 18,
                    0xFF4B4B61);
        }
    }

    private ItemStack machineStack() {
        AggregatePatternRef ref = menu.stack().get(ModDataComponents.AGGREGATE_PATTERN.get());
        if (ref == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.BLOCK.get(ref.catalystId()).asItem().getDefaultInstance();
    }
}
