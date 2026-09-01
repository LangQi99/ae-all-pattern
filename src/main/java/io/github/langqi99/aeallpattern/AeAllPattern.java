package io.github.langqi99.aeallpattern;

import com.mojang.logging.LogUtils;
import io.github.langqi99.aeallpattern.registry.ModBlockEntities;
import io.github.langqi99.aeallpattern.registry.ModBlocks;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.registry.ModItems;
import io.github.langqi99.aeallpattern.registry.ModMenus;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternDecoder;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternExpander;
import io.github.langqi99.aeallpattern.aggregate.AggregateProviderRefreshService;
import io.github.langqi99.aeallpattern.recipe.RecipeIndexService;
import io.github.langqi99.aeallpattern.network.BindingNetwork;
import io.github.langqi99.aeallpattern.network.BindingSyncService;
import io.github.langqi99.aeallpattern.network.AggregateMetadataSyncService;
import io.github.langqi99.aeallpattern.client.ClientEvents;
import io.github.langqi99.aeallpattern.config.AeAllPatternCommonConfig;
import io.github.langqi99.aeallpattern.diagnostics.ModCommands;
import io.github.langqi99.aeallpattern.machine.MachineAdapterRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(AeAllPattern.MOD_ID)
public final class AeAllPattern {
    public static final String MOD_ID = "aeallpattern";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AeAllPattern(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, AeAllPatternCommonConfig.SPEC);
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModDataComponents.register(modBus);
        ModItems.register(modBus);
        ModMenus.register(modBus);
        AggregatePatternDecoder.register();
        MachineAdapterRegistry.initialize();
        modBus.addListener(ModBlockEntities::registerCapabilities);
        modBus.addListener(BindingNetwork::register);
        NeoForge.EVENT_BUS.addListener(RecipeIndexService::addReloadListener);
        NeoForge.EVENT_BUS.addListener(AggregateProviderRefreshService::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(BindingSyncService::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(BindingSyncService::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(AggregateMetadataSyncService::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(AggregateMetadataSyncService::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(ModCommands::register);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            AggregateProviderRefreshService.tickServer(event.getServer());
            AggregatePatternExpander.tickServer(event.getServer());
        });
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(ClientEvents::registerScreens);
            modBus.addListener(ClientEvents::registerConfigScreen);
            ClientEvents.register();
        }
        LOGGER.info("AE All Pattern initialized");
    }
}
