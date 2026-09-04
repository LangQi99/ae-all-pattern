package io.github.langqi99.aeallpattern.client;

import io.github.langqi99.aeallpattern.aggregate.AggregatePatternConfigMenu;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternRef;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Compact AE-colored editor for one held aggregate pattern. */
public final class AggregatePatternConfigScreen extends AbstractContainerScreen<AggregatePatternConfigMenu> {
    public AggregatePatternConfigScreen(
            AggregatePatternConfigMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title);
        imageWidth = 354;
        imageHeight = 186;
        titleLabelX = 34;
        titleLabelY = 10;
    }

    @Override
    protected void init() {
        super.init();
        addOption(12, 38, 164,
                "gui.aeallpattern.aggregate_config.split_same_items",
                "gui.aeallpattern.aggregate_config.split_same_items.tooltip",
                () -> menu.getOptions().splitSameItems(),
                AggregatePatternConfigMenu.TOGGLE_SPLIT_SAME_ITEMS);
        addOption(178, 38, 164,
                "gui.aeallpattern.aggregate_config.ignore_output_nbt",
                "gui.aeallpattern.aggregate_config.ignore_output_nbt.tooltip",
                () -> menu.getOptions().ignoreOutputComponents(),
                AggregatePatternConfigMenu.TOGGLE_IGNORE_OUTPUT_COMPONENTS);
        addOption(12, 56, 164,
                "gui.aeallpattern.aggregate_config.skip_probabilistic_main",
                "gui.aeallpattern.aggregate_config.skip_probabilistic_main.tooltip",
                () -> menu.getOptions().skipProbabilisticMainOutput(),
                AggregatePatternConfigMenu.TOGGLE_SKIP_PROBABILISTIC_MAIN_OUTPUT);
        addOption(178, 56, 164,
                "gui.aeallpattern.aggregate_config.ignore_probabilistic_byproducts",
                "gui.aeallpattern.aggregate_config.ignore_probabilistic_byproducts.tooltip",
                () -> menu.getOptions().ignoreProbabilisticByproducts(),
                AggregatePatternConfigMenu.TOGGLE_IGNORE_PROBABILISTIC_BYPRODUCTS);
        addOption(12, 74, 164,
                "gui.aeallpattern.aggregate_config.allow_item_substitutions",
                "gui.aeallpattern.aggregate_config.allow_item_substitutions.tooltip",
                () -> menu.getOptions().allowItemSubstitutions(),
                AggregatePatternConfigMenu.TOGGLE_ALLOW_ITEM_SUBSTITUTIONS);
        addOption(178, 74, 164,
                "gui.aeallpattern.aggregate_config.allow_fluid_substitutions",
                "gui.aeallpattern.aggregate_config.allow_fluid_substitutions.tooltip",
                () -> menu.getOptions().allowFluidSubstitutions(),
                AggregatePatternConfigMenu.TOGGLE_ALLOW_FLUID_SUBSTITUTIONS);
        addOption(12, 92, 164,
                "gui.aeallpattern.aggregate_config.remove_input_fluids",
                "gui.aeallpattern.aggregate_config.remove_input_fluids.tooltip",
                () -> menu.getOptions().removeInputFluids(),
                AggregatePatternConfigMenu.TOGGLE_REMOVE_INPUT_FLUIDS);
        addOption(178, 92, 164,
                "gui.aeallpattern.aggregate_config.remove_output_fluids",
                "gui.aeallpattern.aggregate_config.remove_output_fluids.tooltip",
                () -> menu.getOptions().removeOutputFluids(),
                AggregatePatternConfigMenu.TOGGLE_REMOVE_OUTPUT_FLUIDS);
        addOption(12, 110, 164,
                "gui.aeallpattern.aggregate_config.remove_input_chemicals",
                "gui.aeallpattern.aggregate_config.remove_input_chemicals.tooltip",
                () -> menu.getOptions().removeInputChemicals(),
                AggregatePatternConfigMenu.TOGGLE_REMOVE_INPUT_CHEMICALS);
        addOption(178, 110, 164,
                "gui.aeallpattern.aggregate_config.remove_output_chemicals",
                "gui.aeallpattern.aggregate_config.remove_output_chemicals.tooltip",
                () -> menu.getOptions().removeOutputChemicals(),
                AggregatePatternConfigMenu.TOGGLE_REMOVE_OUTPUT_CHEMICALS);
        addOption(12, 128, 164,
                "gui.aeallpattern.aggregate_config.remove_processing_catalysts",
                "gui.aeallpattern.aggregate_config.remove_processing_catalysts.tooltip",
                () -> menu.getOptions().removeProcessingCatalysts(),
                AggregatePatternConfigMenu.TOGGLE_REMOVE_PROCESSING_CATALYSTS);
        addOption(178, 128, 164,
                "gui.aeallpattern.aggregate_config.swap_first_and_last_inputs",
                "gui.aeallpattern.aggregate_config.swap_first_and_last_inputs.tooltip",
                () -> menu.getOptions().swapFirstAndLastInputs(),
                AggregatePatternConfigMenu.TOGGLE_SWAP_FIRST_AND_LAST_INPUTS);
        addOption(12, 146, 164,
                "gui.aeallpattern.aggregate_config.skip_durability_consuming_recipes",
                "gui.aeallpattern.aggregate_config.skip_durability_consuming_recipes.tooltip",
                () -> menu.getOptions().skipDurabilityConsumingRecipes(),
                AggregatePatternConfigMenu.TOGGLE_SKIP_DURABILITY_CONSUMING_RECIPES);
    }

    private void addOption(
            int x,
            int y,
            int width,
            String labelKey,
            String tooltipKey,
            java.util.function.BooleanSupplier enabled,
            int toggleId) {
        addRenderableWidget(new AggregateConfigOptionButton(
                leftPos + x,
                topPos + y,
                width,
                Component.translatable(labelKey),
                Component.translatable(tooltipKey),
                enabled,
                () -> toggle(toggleId)));
    }

    private void toggle(int id) {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        menu.clickMenuButton(minecraft.player, id);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFD8D8E2);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF4B4B61);
        graphics.renderOutline(leftPos + 3, topPos + 3, imageWidth - 6, imageHeight - 6, 0xFFF2F2F7);
        graphics.fill(leftPos + 8, topPos + 31, leftPos + imageWidth - 8, topPos + 32, 0xFF777789);

        ItemStack machine = machineStack();
        if (!machine.isEmpty()) {
            graphics.renderItem(machine, leftPos + 10, topPos + 7);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFF3A3A50, false);
        graphics.drawString(
                font,
                Component.translatable(menu.isLinkerConfiguration()
                        ? "gui.aeallpattern.linker_config.hint"
                        : "gui.aeallpattern.aggregate_config.hint"),
                12,
                170,
                0xFF67677A,
                false);
    }

    private ItemStack machineStack() {
        if (menu.isLinkerConfiguration()) {
            return new ItemStack(ModItems.PATTERN_LINKER.get());
        }
        AggregatePatternRef ref = ModDataComponents.getAggregatePattern(menu.stack());
        if (ref == null) {
            return ItemStack.EMPTY;
        }
        var block = BuiltInRegistries.BLOCK.get(ref.catalystId());
        return block.asItem().getDefaultInstance();
    }
}
