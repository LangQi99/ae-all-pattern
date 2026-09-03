package io.github.langqi99.aeallpattern.tianshu;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.CraftingRoutePolicy;
import io.github.langqi99.aeallpattern.client.RoutingPolicyEditor;
import io.github.langqi99.aeallpattern.client.RoutingQualificationButton;
import io.github.langqi99.aeallpattern.client.RoutingTooltipArea;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.List;

/** Native AE-styled editor for the Tianshu network defaults. */
public final class TianshuRoutingScreen extends AEBaseScreen<TianshuRoutingMenu> {
    private final AETextField priorityField;
    private boolean changingPriority;
    private RoutingPolicyEditor routingEditor;
    private RoutingQualificationButton byproductOrders;
    private RoutingQualificationButton amplifyingCycles;

    public TianshuRoutingScreen(
            TianshuRoutingMenu menu,
            Inventory inventory,
            Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        setTextContent("dialog_title", Component.translatable("block.aeallpattern.tianshu_pattern_selector"));
        setTextHidden("priority_insertion_hint", true);
        setTextHidden("priority_extraction_hint", true);

        priorityField = widgets.addTextField("priorityInput");
        priorityField.setMaxLength(3);
        priorityField.setValue(Integer.toString(menu.getPolicy().aggregatePriority()));
        priorityField.setResponder(this::priorityChanged);
        priorityField.setTooltipMessage(List.of(
                Component.translatable("gui.aeallpattern.routing.aggregate_priority"),
                Component.translatable("gui.aeallpattern.routing.priority_semantics")));
    }

    @Override
    protected void init() {
        super.init();
        priorityField.move(new Point(leftPos + 119, topPos + 20));
        priorityField.resize(43, 12);
        addRenderableWidget(new RoutingTooltipArea(
                leftPos + 8,
                topPos + 36,
                160,
                14,
                () -> List.of(
                        Component.translatable("gui.aeallpattern.routing.feasible"),
                        Component.translatable("gui.aeallpattern.routing.feasible_details"),
                        Component.translatable("gui.aeallpattern.routing.feasible_locked"))));
        byproductOrders = addRenderableWidget(new RoutingQualificationButton(
                leftPos + 8,
                topPos + 52,
                160,
                () -> menu.getPolicy().allowByproductOrders(),
                enabled -> menu.updatePolicy(menu.getPolicy().withByproductOrders(enabled))));
        amplifyingCycles = addRenderableWidget(new RoutingQualificationButton(
                leftPos + 8,
                topPos + 69,
                160,
                "gui.aeallpattern.routing.amplifying_cycles",
                () -> menu.getPolicy().allowAmplifyingCycles(),
                enabled -> menu.updatePolicy(menu.getPolicy().withAmplifyingCycles(enabled))));
        routingEditor = addRenderableWidget(new RoutingPolicyEditor(
                leftPos + 8,
                topPos + 86,
                160,
                menu::getPolicy,
                menu::updatePolicy));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && routingEditor != null && routingEditor.beginHandleDrag(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && routingEditor != null && routingEditor.dragHandle(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && routingEditor != null && routingEditor.endHandleDrag()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void priorityChanged(String text) {
        if (changingPriority || text == null || !text.matches("-?\\d{1,2}")) {
            return;
        }
        try {
            int value = Integer.parseInt(text);
            if (value >= CraftingRoutePolicy.MIN_PRIORITY && value <= CraftingRoutePolicy.MAX_PRIORITY
                    && value != menu.getPolicy().aggregatePriority()) {
                menu.updatePolicy(menu.getPolicy().withAggregatePriority(value));
            }
        } catch (NumberFormatException ignored) {
            // Keep the partially typed value until it becomes valid.
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (!priorityField.isFocused()) {
            String expected = Integer.toString(menu.getPolicy().aggregatePriority());
            if (!expected.equals(priorityField.getValue())) {
                changingPriority = true;
                priorityField.setValue(expected);
                changingPriority = false;
            }
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        graphics.drawString(
                font,
                Component.translatable("gui.aeallpattern.routing.aggregate_priority"),
                8,
                22,
                0xFF404052,
                false);
        Icon.PRIORITY.getBlitter().dest(99, 18).blit(graphics);

        graphics.fill(8, 36, 168, 50, 0xFFC7C7D2);
        graphics.renderOutline(8, 36, 160, 14, 0xFF777789);
        graphics.drawString(
                font,
                Component.translatable("gui.aeallpattern.routing.feasible_locked_short"),
                12,
                39,
                0xFF303044,
                false);
    }

    public RoutingQualificationButton getByproductOrders() {
        return byproductOrders;
    }

    public RoutingQualificationButton getAmplifyingCycles() {
        return amplifyingCycles;
    }
}
