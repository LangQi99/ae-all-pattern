package io.github.langqi99.aeallpattern.client;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/** One keyboard-accessible choice in the pattern search scope selector. */
public final class AggregateSearchModeButton extends AbstractButton {
    private final BooleanSupplier selected;
    private final Runnable select;

    public AggregateSearchModeButton(
            int x, int y, int width, Component label, Component details,
            BooleanSupplier selected, Runnable select) {
        super(x, y, width, 18, label);
        this.selected = selected;
        this.select = select;
        setTooltip(Tooltip.create(details));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean value = selected.getAsBoolean();
        int background = value ? 0xFFC7DFE2 : isHoveredOrFocused() ? 0xFFE7E7EE : 0xFFB8B8C3;
        int outline = value ? 0xFF2F929D : 0xFF777789;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
        graphics.renderOutline(getX(), getY(), width, height, outline);
        var font = Minecraft.getInstance().font;
        String label = font.plainSubstrByWidth(getMessage().getString(), width - 8);
        graphics.drawString(font, label, getX() + (width - font.width(label)) / 2,
                getY() + 5, 0xFF303044, false);
    }

    @Override
    public void onPress() {
        select.run();
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.@NotNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
