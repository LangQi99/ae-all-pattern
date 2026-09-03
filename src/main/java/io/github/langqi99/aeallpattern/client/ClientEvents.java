package io.github.langqi99.aeallpattern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.network.BindingRenderEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import io.github.langqi99.aeallpattern.registry.ModMenus;
import io.github.langqi99.aeallpattern.tianshu.TianshuRoutingScreen;
import appeng.init.client.InitScreens;

public final class ClientEvents {
    private static final double MAX_RENDER_DISTANCE_SQUARED = 96.0 * 96.0;
    private static int smokeTestTicks;

    private ClientEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientEvents::renderBindings);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(ClientJeiAggregateScanner::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(ClientJeiAggregateScanner::onClientTick);
        NeoForge.EVENT_BUS.addListener(AggregateStartupRefreshService::onClientTick);
        if (Boolean.getBoolean("aeallpattern.clientSmokeTest")) {
            NeoForge.EVENT_BUS.addListener(ClientEvents::runClientSmokeTest);
        }
    }

    private static void runClientSmokeTest(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null || ++smokeTestTicks < 20) {
            return;
        }
        for (String value : System.getProperty("aeallpattern.expectedTestMods", "").split(",")) {
            String modId = value.trim();
            if (!modId.isEmpty() && !ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("Client smoke test expected mod '" + modId + "' but it was not loaded");
            }
        }
        AeAllPattern.LOGGER.info("CLIENT_SMOKE_TEST_PASSED");
        minecraft.stop();
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.AGGREGATE_PATTERN_CONFIG.get(), AggregatePatternConfigScreen::new);
        event.register(ModMenus.AGGREGATE_PATTERN_SELECTION.get(), AggregatePatternSelectionScreen::new);
        InitScreens.register(
                event,
                ModMenus.TIANSHU_ROUTING.get(),
                TianshuRoutingScreen::new,
                "/screens/priority.json");
    }

    public static void registerConfigScreen(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("cloth_config")) {
            AeAllPatternConfigScreen.register();
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBindingState.clear();
        io.github.langqi99.aeallpattern.aggregate.AggregateMetadataView.replace(java.util.List.of());
        AggregateStartupRefreshService.reset();
    }

    private static void renderBindings(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        for (BindingRenderEntry binding : ClientBindingState.bindings()) {
            if (!binding.dimension().equals(minecraft.level.dimension())
                    || minecraft.player.distanceToSqr(binding.pos().getCenter()) > MAX_RENDER_DISTANCE_SQUARED) {
                continue;
            }
            AABB bounds = blockBounds(minecraft, binding);
            float pulse = 0.72F + 0.18F * (float) Math.sin((minecraft.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 0.12F);
            renderFrame(poses, buffers, bounds.inflate(0.035), pulse);
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(poses, lines, bounds.inflate(0.004), 0.68F, 0.25F, 1.0F, pulse);
        }
        poses.popPose();
        buffers.endBatch(RenderType.debugFilledBox());
        buffers.endBatch(RenderType.lines());
    }

    private static void renderFrame(
            PoseStack poses, MultiBufferSource buffers, AABB bounds, float pulse) {
        // A dark AE-style casing around a smaller energized purple core makes
        // every edge read as a real cuboid frame instead of a debug outline.
        renderFrameLayer(poses, buffers, bounds, 0.080, 0.16F, 0.14F, 0.24F, 0.82F);
        renderFrameLayer(poses, buffers, bounds, 0.038, 0.66F, 0.28F, 0.96F, 0.58F + pulse * 0.22F);
    }

    private static void renderFrameLayer(
            PoseStack poses,
            MultiBufferSource buffers,
            AABB bounds,
            double thickness,
            float red,
            float green,
            float blue,
            float alpha) {
        double half = thickness * 0.5;
        double[] xs = {bounds.minX, bounds.maxX};
        double[] ys = {bounds.minY, bounds.maxY};
        double[] zs = {bounds.minZ, bounds.maxZ};

        for (double y : ys) {
            for (double z : zs) {
                renderRod(poses, buffers,
                        bounds.minX - half, y - half, z - half,
                        bounds.maxX + half, y + half, z + half,
                        red, green, blue, alpha);
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                renderRod(poses, buffers,
                        x - half, bounds.minY - half, z - half,
                        x + half, bounds.maxY + half, z + half,
                        red, green, blue, alpha);
            }
        }
        for (double x : xs) {
            for (double y : ys) {
                renderRod(poses, buffers,
                        x - half, y - half, bounds.minZ - half,
                        x + half, y + half, bounds.maxZ + half,
                        red, green, blue, alpha);
            }
        }
    }

    private static void renderRod(
            PoseStack poses,
            MultiBufferSource buffers,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            float red,
            float green,
            float blue,
            float alpha) {
        DebugRenderer.renderFilledBox(
                poses, buffers, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
    }

    private static AABB blockBounds(Minecraft minecraft, BindingRenderEntry binding) {
        var level = minecraft.level;
        if (level == null) {
            return new AABB(binding.pos());
        }
        var state = level.getBlockState(binding.pos());
        var shape = state.getShape(level, binding.pos());
        return (shape.isEmpty() ? new AABB(binding.pos()) : shape.bounds().move(binding.pos()));
    }
}
