package io.github.langqi99.aeallpattern.client;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import io.github.langqi99.aeallpattern.AeAllPattern;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Native AE toolbar button for the temporary route-policy popup. */
public final class RoutingOptionButton extends IconButton {
    private static final ResourceLocation ROUTE_ICON =
            new ResourceLocation(AeAllPattern.MOD_ID, "textures/gui/tianshu_route.png");

    private final Supplier<Icon> icon;
    private final Supplier<List<Component>> tooltip;
    private final Runnable rightClick;

    public RoutingOptionButton(
            Supplier<Icon> icon,
            OnPress onPress,
            Runnable rightClick,
            Supplier<List<Component>> tooltip) {
        super(onPress);
        this.icon = icon;
        this.tooltip = tooltip;
        this.rightClick = rightClick;
    }

    @Override
    protected Icon getIcon() {
        return icon.get();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int hoverOffset = isHovered() ? 1 : 0;
        Icon background = isHovered() || isFocused()
                ? Icon.TAB_BUTTON_BACKGROUND_FOCUS
                : Icon.TOOLBAR_BUTTON_BACKGROUND;
        background.getBlitter()
                .dest(getX() - 1, getY() + hoverOffset, 18, 20)
                .blit(graphics);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 3.0F);
        if (!active) {
            graphics.setColor(1.0F, 1.0F, 1.0F, 0.45F);
        }
        graphics.blit(ROUTE_ICON, getX(), getY() + 1 + hoverOffset, 0.0F, 0.0F, 16, 16, 16, 16);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && active && visible && isMouseOver(mouseX, mouseY) && rightClick != null) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            rightClick.run();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return tooltip.get();
    }

}
