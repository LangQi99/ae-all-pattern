package io.github.langqi99.aeallpattern.client;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ITooltip;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.CraftingRoutePolicy;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/** Compact AE-styled, drag-sortable lexicographic route policy editor. */
public final class RoutingPolicyEditor extends AbstractWidget implements ITooltip {
    private static final int ROW_HEIGHT = 17;

    private final Supplier<CraftingRoutePolicy> policy;
    private final Consumer<CraftingRoutePolicy> change;
    private final float[] animatedRowY = new float[CraftingRoutePolicy.CRITERION_COUNT];
    private int dragFrom = -1;
    private int dragTo = -1;
    private int hoveredRow = -1;
    private double dragMouseY;
    private double dragGrabOffset;
    private long lastRenderNanos;

    public RoutingPolicyEditor(
            int x,
            int y,
            int width,
            Supplier<CraftingRoutePolicy> policy,
            Consumer<CraftingRoutePolicy> change) {
        super(x, y, width, ROW_HEIGHT * CraftingRoutePolicy.CRITERION_COUNT,
                Component.translatable("gui.aeallpattern.routing.order"));
        this.policy = policy;
        this.change = change;
        for (int row = 0; row < animatedRowY.length; row++) {
            animatedRowY[row] = row * ROW_HEIGHT;
        }
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoveredRow = dragFrom < 0 ? rowAt(mouseX, mouseY) : -1;
        CraftingRoutePolicy current = policy.get();
        float blend = animationBlend();

        for (int row = 0; row < CraftingRoutePolicy.CRITERION_COUNT; row++) {
            if (row == dragFrom) {
                continue;
            }
            float target = previewRow(row) * ROW_HEIGHT;
            animatedRowY[row] = approach(animatedRowY[row], target, blend);
            renderRow(
                    graphics,
                    current,
                    row,
                    getY() + Math.round(animatedRowY[row]),
                    row == hoveredRow,
                    false);
        }

        if (dragFrom >= 0) {
            int slotY = getY() + dragTo * ROW_HEIGHT;
            graphics.fill(getX() + 1, slotY, getX() + width - 1, slotY + ROW_HEIGHT - 1, 0x553C174F);
            graphics.renderOutline(getX(), slotY, width, ROW_HEIGHT - 1, 0xFFA85BE0);

            float target = (float) io.github.langqi99.aeallpattern.util.CompatMath.clamp(
                    dragMouseY - getY() - dragGrabOffset,
                    0,
                    (CraftingRoutePolicy.CRITERION_COUNT - 1) * ROW_HEIGHT);
            animatedRowY[dragFrom] = target;
            int floatingY = getY() + Math.round(target);
            graphics.fill(
                    getX() + 3,
                    floatingY + 3,
                    getX() + width + 3,
                    floatingY + ROW_HEIGHT + 2,
                    0x66000000);
            renderRow(graphics, current, dragFrom, floatingY, false, true);
        }
    }

    private void renderRow(
            GuiGraphics graphics,
            CraftingRoutePolicy current,
            int policyRow,
            int rowY,
            boolean hovered,
            boolean dragging) {
        int criterion = current.criterionAt(policyRow);
        boolean enabled = enabled(current, criterion);
        int background = dragging
                ? 0xFFE1D2EF
                : hovered ? 0xFFE7E7EE : enabled ? 0xFFC7C7D2 : 0xFFB8B8C3;
        graphics.fill(getX(), rowY, getX() + width, rowY + ROW_HEIGHT - 1, background);
        graphics.renderOutline(
                getX(), rowY, width, ROW_HEIGHT - 1,
                dragging ? 0xFFA85BE0 : 0xFF777789);

        drawHandle(graphics, getX() + 4, rowY + 5, dragging ? 0xFFA85BE0 : 0xFFF4F4F7);
        Icon icon = icon(current, criterion);
        var blitter = icon.getBlitter().dest(getX() + 16, rowY);
        if (!enabled) {
            blitter.opacity(0.45F);
        }
        blitter.blit(graphics);

        int textColor = enabled ? 0xFF303044 : 0xFF777784;
        graphics.drawString(
                Minecraft.getInstance().font,
                criterionName(criterion),
                getX() + 35,
                rowY + 4,
                textColor,
                false);
        Component value = criterionValue(current, criterion);
        graphics.drawString(
                Minecraft.getInstance().font,
                value,
                getX() + width - 5 - Minecraft.getInstance().font.width(value),
                rowY + 4,
                textColor,
                false);
    }

    private int previewRow(int row) {
        if (dragFrom < 0 || dragFrom == dragTo) {
            return row;
        }
        if (dragFrom < dragTo && row > dragFrom && row <= dragTo) {
            return row - 1;
        }
        if (dragFrom > dragTo && row >= dragTo && row < dragFrom) {
            return row + 1;
        }
        return row;
    }

    private float animationBlend() {
        long now = System.nanoTime();
        if (lastRenderNanos == 0L) {
            lastRenderNanos = now;
            return 1.0F;
        }
        float elapsed = Math.min(0.05F, (now - lastRenderNanos) / 1_000_000_000.0F);
        lastRenderNanos = now;
        return 1.0F - (float) Math.exp(-18.0F * elapsed);
    }

    private static float approach(float current, float target, float blend) {
        return Math.abs(target - current) < 0.05F ? target : current + (target - current) * blend;
    }

    private static void drawHandle(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 7, y + 1, color);
        graphics.fill(x, y + 3, x + 7, y + 4, color);
        graphics.fill(x, y + 6, x + 7, y + 7, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = rowAt(mouseX, mouseY);
        if (row < 0) {
            return false;
        }
        CraftingRoutePolicy current = policy.get();
        if (button == 0) {
            if (mouseX < getX() + 15) {
                // The owning screen captures handle drags before the container
                // can turn them into ordinary slot/widget clicks.
                return false;
            }
            change.accept(cycle(current, current.criterionAt(row), false));
            return true;
        }
        if (button == 1) {
            int criterion = current.criterionAt(row);
            change.accept(cycle(current, criterion, true));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(
            double mouseX, double mouseY, int button, double dragX, double dragY) {
        return button == 0 && dragHandle(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && endHandleDrag()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Called by the owning screen before normal container click dispatch. */
    public boolean beginHandleDrag(double mouseX, double mouseY) {
        int row = rowAt(mouseX, mouseY);
        if (row < 0 || mouseX >= getX() + 15) {
            return false;
        }
        dragFrom = row;
        dragTo = row;
        dragMouseY = mouseY;
        dragGrabOffset = mouseY - (getY() + row * ROW_HEIGHT);
        return true;
    }

    /** Keeps the drag even when the cursor leaves this widget. */
    public boolean dragHandle(double mouseX, double mouseY) {
        if (dragFrom < 0) {
            return false;
        }
        dragMouseY = mouseY;
        dragTo = io.github.langqi99.aeallpattern.util.CompatMath.clamp(
                (int) ((mouseY - getY()) / ROW_HEIGHT), 0, CraftingRoutePolicy.CRITERION_COUNT - 1);
        return true;
    }

    /** Commits exactly once on release, avoiding replans during movement. */
    public boolean endHandleDrag() {
        if (dragFrom < 0) {
            return false;
        }
        int from = dragFrom;
        int to = dragTo;
        dragFrom = -1;
        dragTo = -1;
        remapAnimationAfterMove(from, to);
        if (from != to) {
            change.accept(policy.get().moveCriterion(from, to));
        }
        return true;
    }

    private void remapAnimationAfterMove(int from, int to) {
        float[] before = animatedRowY.clone();
        if (from < to) {
            if (to - from >= 0) System.arraycopy(before, from + 1, animatedRowY, from, to - from);
            animatedRowY[to] = before[from];
        } else if (from > to) {
            if (from - to >= 0) System.arraycopy(before, to, animatedRowY, to + 1, from - to);
            animatedRowY[to] = before[from];
        }
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!visible || mouseX < getX() || mouseX >= getX() + width
                || mouseY < getY() || mouseY >= getY() + height) {
            return -1;
        }
        return (int) ((mouseY - getY()) / ROW_HEIGHT);
    }

    private static CraftingRoutePolicy cycle(CraftingRoutePolicy policy, int criterion, boolean reverse) {
        return switch (criterion) {
            case CraftingRoutePolicy.CRITERION_PATH ->
                    policy.withPathPreference(cycleDirection(policy.pathPreference(), reverse));
            case CraftingRoutePolicy.CRITERION_STOCK_SURPLUS ->
                    policy.withStockSurplusPreference(
                            cycleDirection(policy.stockSurplusPreference(), reverse));
            case CraftingRoutePolicy.CRITERION_HIGH_YIELD ->
                    policy.withYieldPreference(cycleDirection(policy.yieldPreference(), reverse));
            case CraftingRoutePolicy.CRITERION_FAST -> policy.withFast(!policy.preferFast());
            default -> policy;
        };
    }

    /** Cycles all three visible states so neither direction is hidden behind a specific mouse button. */
    private static int cycleDirection(int current, boolean reverse) {
        return reverse
                ? (current <= -1 ? 1 : current - 1)
                : (current >= 1 ? -1 : current + 1);
    }

    private static boolean enabled(CraftingRoutePolicy policy, int criterion) {
        return switch (criterion) {
            case CraftingRoutePolicy.CRITERION_PATH -> policy.pathPreference() != 0;
            case CraftingRoutePolicy.CRITERION_STOCK_SURPLUS -> policy.stockSurplusPreference() != 0;
            case CraftingRoutePolicy.CRITERION_HIGH_YIELD -> policy.yieldPreference() != 0;
            case CraftingRoutePolicy.CRITERION_FAST -> policy.preferFast();
            default -> false;
        };
    }

    private static Icon icon(CraftingRoutePolicy policy, int criterion) {
        return switch (criterion) {
            case CraftingRoutePolicy.CRITERION_PATH -> policy.pathPreference() < 0
                    ? Icon.ARROW_LEFT
                    : policy.pathPreference() > 0 ? Icon.ARROW_RIGHT : Icon.SCHEDULING_ROUND_ROBIN;
            case CraftingRoutePolicy.CRITERION_STOCK_SURPLUS -> policy.stockSurplusPreference() > 0
                    ? Icon.FULLNESS_FULL
                    : policy.stockSurplusPreference() < 0 ? Icon.FULLNESS_EMPTY : Icon.SCHEDULING_ROUND_ROBIN;
            case CraftingRoutePolicy.CRITERION_HIGH_YIELD -> policy.yieldPreference() > 0
                    ? Icon.ARROW_UP
                    : policy.yieldPreference() < 0 ? Icon.ARROW_DOWN : Icon.SCHEDULING_ROUND_ROBIN;
            case CraftingRoutePolicy.CRITERION_FAST -> policy.preferFast() ? Icon.WRENCH : Icon.WRENCH_DISABLED;
            default -> Icon.SCHEDULING_ROUND_ROBIN;
        };
    }

    private static Component criterionName(int criterion) {
        return Component.translatable(switch (criterion) {
            case CraftingRoutePolicy.CRITERION_PATH -> "gui.aeallpattern.routing.path";
            case CraftingRoutePolicy.CRITERION_STOCK_SURPLUS -> "gui.aeallpattern.routing.surplus";
            case CraftingRoutePolicy.CRITERION_HIGH_YIELD -> "gui.aeallpattern.routing.yield";
            case CraftingRoutePolicy.CRITERION_FAST -> "gui.aeallpattern.routing.waiting";
            default -> "gui.aeallpattern.routing.disabled";
        });
    }

    private static Component criterionValue(CraftingRoutePolicy policy, int criterion) {
        if (!enabled(policy, criterion)) {
            return Component.translatable("gui.aeallpattern.routing.disabled");
        }
        return Component.translatable(switch (criterion) {
            case CraftingRoutePolicy.CRITERION_PATH -> policy.pathPreference() < 0
                    ? "gui.aeallpattern.routing.path_short"
                    : "gui.aeallpattern.routing.path_long";
            case CraftingRoutePolicy.CRITERION_STOCK_SURPLUS -> policy.stockSurplusPreference() > 0
                    ? "gui.aeallpattern.routing.more"
                    : "gui.aeallpattern.routing.less";
            case CraftingRoutePolicy.CRITERION_HIGH_YIELD -> policy.yieldPreference() > 0
                    ? "gui.aeallpattern.routing.more"
                    : "gui.aeallpattern.routing.less";
            case CraftingRoutePolicy.CRITERION_FAST -> "gui.aeallpattern.routing.waiting_value";
            default -> "gui.aeallpattern.routing.disabled";
        });
    }

    @Override
    public List<Component> getTooltipMessage() {
        if (hoveredRow < 0) {
            return List.of();
        }
        int criterion = policy.get().criterionAt(hoveredRow);
        return List.of(
                criterionName(criterion),
                criterionDescription(policy.get(), criterion),
                Component.translatable("gui.aeallpattern.routing.order_rule"),
                Component.translatable("gui.aeallpattern.routing.drag_hint"),
                Component.translatable("gui.aeallpattern.routing.toggle_hint"));
    }

    private static Component criterionDescription(CraftingRoutePolicy policy, int criterion) {
        return Component.translatable(switch (criterion) {
            case CraftingRoutePolicy.CRITERION_PATH -> policy.pathPreference() < 0
                    ? "gui.aeallpattern.routing.path_details_short"
                    : policy.pathPreference() > 0
                            ? "gui.aeallpattern.routing.path_details_long"
                            : "gui.aeallpattern.routing.path_details_off";
            case CraftingRoutePolicy.CRITERION_STOCK_SURPLUS ->
                    policy.stockSurplusPreference() < 0
                            ? "gui.aeallpattern.routing.surplus_details_less"
                            : policy.stockSurplusPreference() > 0
                                    ? "gui.aeallpattern.routing.surplus_details_more"
                                    : "gui.aeallpattern.routing.surplus_details_off";
            case CraftingRoutePolicy.CRITERION_HIGH_YIELD ->
                    policy.yieldPreference() < 0
                            ? "gui.aeallpattern.routing.yield_details_less"
                            : policy.yieldPreference() > 0
                                    ? "gui.aeallpattern.routing.yield_details_more"
                                    : "gui.aeallpattern.routing.yield_details_off";
            case CraftingRoutePolicy.CRITERION_FAST ->
                    "gui.aeallpattern.routing.waiting_details";
            default -> "gui.aeallpattern.routing.disabled";
        });
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), width, height);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return visible && hoveredRow >= 0;
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.@NotNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
