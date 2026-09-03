package io.github.langqi99.aeallpattern.client;

import appeng.client.gui.Icon;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/** Compact AE-style boolean row used by the aggregate-pattern configuration screen. */
public final class AggregateConfigOptionButton extends AbstractWidget {
    private final Component label;
    private final BooleanSupplier enabled;
    private final Runnable toggle;

    public AggregateConfigOptionButton(
            int x,
            int y,
            int width,
            Component label,
            Component details,
            BooleanSupplier enabled,
            Runnable toggle) {
        super(x, y, width, 17, label);
        this.label = label;
        this.enabled = enabled;
        this.toggle = toggle;
        setTooltip(Tooltip.create(details));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean value = enabled.getAsBoolean();
        int background = isHovered() ? 0xFFE7E7EE : value ? 0xFFC7C7D2 : 0xFFB8B8C3;
        graphics.fill(getX(), getY(), getX() + width, getY() + height - 1, background);
        graphics.renderOutline(getX(), getY(), width, height - 1, 0xFF777789);

        (value ? Icon.S_SUBSTITUTION_ENABLED : Icon.S_SUBSTITUTION_DISABLED)
                .getBlitter().dest(getX() + 1, getY()).blit(graphics);

        var font = Minecraft.getInstance().font;
        int color = value ? 0xFF303044 : 0xFF666674;
        Component state = Component.translatable(value
                ? "gui.aeallpattern.aggregate_config.enabled"
                : "gui.aeallpattern.aggregate_config.disabled");
        int labelWidth = Math.max(0, width - 30 - font.width(state));
        String fullLabel = label.getString();
        String visibleLabel = font.width(fullLabel) <= labelWidth
                ? fullLabel
                : font.plainSubstrByWidth(fullLabel, Math.max(0, labelWidth - font.width("…"))) + "…";
        graphics.drawString(font, visibleLabel, getX() + 20, getY() + 4, color, false);
        graphics.drawString(font, state, getX() + width - 5 - font.width(state), getY() + 4, color, false);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onClick(double mouseX, double mouseY) {
        toggle.run();
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.@NotNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
