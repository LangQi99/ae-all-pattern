package io.github.langqi99.aeallpattern.client;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.StyleManager;
import appeng.menu.me.crafting.CraftConfirmMenu;
import io.github.langqi99.aeallpattern.AeAllPattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Opt-in release-JAR UI regression check, run only in a copied test world. */
final class ProductionScreenSmokeTest {
    private static CraftConfirmScreen screen;
    private static int ticks;

    private ProductionScreenSmokeTest() {}

    static boolean tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return false;
        }
        try {
            if (screen == null) {
                var inventory = minecraft.player.getInventory();
                var menu = new CraftConfirmMenu(0, inventory, null);
                // Simulate the server-synced availability flag without requiring a full AE network.
                CraftConfirmMenu.class.getField("aeallpattern$routingAvailable").setBoolean(menu, true);
                screen = new CraftConfirmScreen(menu, inventory, Component.literal("Production UI test"),
                        StyleManager.loadStyleDoc("/screens/craft_confirm.json"));
                minecraft.setScreen(screen);
                return false;
            }
            // Allow actual frames to run render/updateBeforeRender and create the widgets.
            if (++ticks == 20) {
                var button = CraftConfirmScreen.class.getDeclaredField("aeallpattern$routeButton");
                button.setAccessible(true);
                ((RoutingOptionButton) button.get(screen)).onPress();
            }
            if (ticks == 40) {
                var expanded = CraftConfirmScreen.class.getDeclaredField("aeallpattern$expanded");
                expanded.setAccessible(true);
                if (!expanded.getBoolean(screen)) {
                    throw new IllegalStateException("Routing popup was not open");
                }
                screen.mouseClicked(0, 0, 0);
                if (expanded.getBoolean(screen)) {
                    throw new IllegalStateException("Outside click did not close the routing popup");
                }
                screen.mouseDragged(0, 0, 0, 1, 1);
                screen.mouseReleased(0, 0, 0);
                expanded.setBoolean(screen, true);
                if (!screen.keyPressed(256, 0, 0) || expanded.getBoolean(screen)) {
                    throw new IllegalStateException("Escape did not close the routing popup");
                }
                AeAllPattern.LOGGER.info("PRODUCTION_SCREEN_SMOKE_TEST_PASSED");
            }
            return ticks >= 40;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Routing screen Mixin was not applied", e);
        }
    }
}
