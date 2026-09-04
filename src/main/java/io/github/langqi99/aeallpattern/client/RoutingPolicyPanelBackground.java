package io.github.langqi99.aeallpattern.client;

import appeng.client.gui.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/** Non-interactive AE-style backing used by the temporary order policy popover. */
public final class RoutingPolicyPanelBackground extends AbstractWidget {
    public RoutingPolicyPanelBackground(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        active = false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFFE1E1E8);
        graphics.renderOutline(getX(), getY(), width, height, 0xFF55556A);
        graphics.drawString(
                Minecraft.getInstance().font,
                Component.translatable("gui.aeallpattern.routing.aggregate_priority_short"),
                getX() + 6,
                getY() + 6,
                0xFF303044,
                false);
        Icon.SORT_BY_AMOUNT.getBlitter().dest(getX() + width - 64, getY() + 2).blit(graphics);

        graphics.fill(getX() + 4, getY() + 20, getX() + width - 4, getY() + 34, 0xFFC7C7D2);
        graphics.renderOutline(getX() + 4, getY() + 20, width - 8, 14, 0xFF777789);
        graphics.drawString(
                Minecraft.getInstance().font,
                Component.translatable("gui.aeallpattern.routing.feasible_locked_short"),
                getX() + 8,
                getY() + 23,
                0xFF303044,
                false);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
    }
}
