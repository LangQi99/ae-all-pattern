package io.github.langqi99.aeallpattern.client;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftConfirmMenu;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.registry.ModBlocks;
import io.github.langqi99.aeallpattern.tianshu.CraftConfirmRoutingMenu;
import io.github.langqi99.aeallpattern.tianshu.TianshuPatternSelectorBlockEntity;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Opt-in release-JAR UI regression check, run only in a copied test world. */
final class ProductionScreenSmokeTest {
    private static CraftConfirmScreen screen;
    private static int ticks;
    private static BlockPos routerPos;
    private static CompletableFuture<?> serverAction;
    private static boolean opening;

    private ProductionScreenSmokeTest() {}

    static boolean tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return false;
        }
        try {
            var server = minecraft.getSingleplayerServer();
            require(server != null, "Routing smoke test requires an isolated integrated-server world");
            if (serverAction != null) {
                if (!serverAction.isDone()) return false;
                serverAction.join();
            }
            if (screen == null) {
                if (serverAction == null) {
                    var playerId = minecraft.player.getUUID();
                    serverAction = server.submit(() -> {
                        var player = server.getPlayerList().getPlayer(playerId);
                        require(player != null, "Integrated-server player is missing");
                        var level = player.serverLevel();
                        var energyPos = new BlockPos(player.getBlockX(), 100, player.getBlockZ());
                        routerPos = energyPos.east();
                        var terminalPos = energyPos.north();
                        for (int x = -2; x <= 2; x++) {
                            for (int z = -4; z <= 2; z++) {
                                level.setBlockAndUpdate(energyPos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                            }
                        }
                        level.setBlockAndUpdate(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        level.setBlockAndUpdate(routerPos, ModBlocks.TIANSHU_PATTERN_SELECTOR.get().defaultBlockState());
                        PartHelper.setPart(level, terminalPos, null, player,
                                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
                        var terminal = PartHelper.setPart(level, terminalPos, Direction.NORTH, player,
                                AEParts.CRAFTING_TERMINAL.asItem());
                        require(terminal != null, "Could not place the test crafting terminal");
                        player.teleportTo(terminalPos.getX() + 0.5, 101, terminalPos.getZ() - 1.5);
                        return terminal;
                    });
                    return false;
                }
                if (!opening && ++ticks >= 40) {
                    var terminal = (appeng.parts.reporting.CraftingTerminalPart) serverAction.join();
                    var playerId = minecraft.player.getUUID();
                    opening = true;
                    serverAction = server.submit(() -> {
                        var player = server.getPlayerList().getPlayer(playerId);
                        var router = (TianshuPatternSelectorBlockEntity) player.serverLevel().getBlockEntity(routerPos);
                        require(router != null && router.isRouterOnline(), "Test router did not come online");
                        require(MenuOpener.open(CraftConfirmMenu.TYPE, player, MenuLocators.forPart(terminal)),
                                "Could not open the real AE confirmation menu");
                        var menu = (CraftConfirmMenu) player.containerMenu;
                        require(((CraftConfirmRoutingMenu) menu).aeallpattern$isRoutingAvailable(),
                                "Router unavailable before planning on the server");
                        require(menu.planJob(AEItemKey.of(Items.EMERALD), 1,
                                CalculationStrategy.REPORT_MISSING_ITEMS), "Could not start the test order");
                        return null;
                    });
                }
                if (opening && minecraft.screen instanceof CraftConfirmScreen confirmation) {
                    screen = confirmation;
                    ticks = 0;
                }
                return false;
            }
            // Allow actual frames to run render/updateBeforeRender and create the widgets.
            if (++ticks == 20) {
                require(((CraftConfirmRoutingMenu) screen.getMenu()).aeallpattern$isRoutingAvailable(),
                        "Real server GUI synchronization left the client routing button unavailable");
                var button = CraftConfirmScreen.class.getDeclaredField("aeallpattern$routeButton");
                button.setAccessible(true);
                require(((RoutingOptionButton) button.get(screen)).active, "Online routing button is disabled");
                Screenshot.grab(minecraft.gameDirectory, "aap-routing-online.png", minecraft.getMainRenderTarget(),
                        message -> AeAllPattern.LOGGER.info("Routing screenshot: {}", message.getString()));
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
                var playerId = minecraft.player.getUUID();
                serverAction = server.submit(() -> {
                    server.getPlayerList().getPlayer(playerId).serverLevel()
                            .setBlockAndUpdate(routerPos, Blocks.AIR.defaultBlockState());
                    return null;
                });
            }
            if (ticks == 80) {
                require(!((CraftConfirmRoutingMenu) screen.getMenu()).aeallpattern$isRoutingAvailable(),
                        "Disconnecting the router did not disable routing on the client");
                var playerId = minecraft.player.getUUID();
                serverAction = server.submit(() -> {
                    server.getPlayerList().getPlayer(playerId).serverLevel().setBlockAndUpdate(
                            routerPos, ModBlocks.TIANSHU_PATTERN_SELECTOR.get().defaultBlockState());
                    return null;
                });
            }
            if (ticks == 120) {
                require(((CraftConfirmRoutingMenu) screen.getMenu()).aeallpattern$isRoutingAvailable(),
                        "Reconnecting the router did not restore routing without replanning");
                AeAllPattern.LOGGER.info("ROUTING_GUI_SYNC_SMOKE_TEST_PASSED");
                AeAllPattern.LOGGER.info("PRODUCTION_SCREEN_SMOKE_TEST_PASSED");
            }
            return ticks >= 120;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Routing screen Mixin was not applied", e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
