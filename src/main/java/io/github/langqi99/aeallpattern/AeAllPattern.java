package io.github.langqi99.aeallpattern;

import com.mojang.logging.LogUtils;
import io.github.langqi99.aeallpattern.registry.ModBlockEntities;
import io.github.langqi99.aeallpattern.registry.ModBlocks;
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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.event.TickEvent;

@Mod(AeAllPattern.MOD_ID)
public final class AeAllPattern {
    public static final String MOD_ID = "aeallpattern";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AeAllPattern() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AeAllPatternCommonConfig.SPEC);
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModItems.register(modBus);
        ModMenus.register(modBus);
        AggregatePatternDecoder.register();
        MachineAdapterRegistry.initialize();
        BindingNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(RecipeIndexService::addReloadListener);
        MinecraftForge.EVENT_BUS.addListener(AggregateProviderRefreshService::onDatapackSync);
        MinecraftForge.EVENT_BUS.addListener(BindingSyncService::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(BindingSyncService::onPlayerChangedDimension);
        MinecraftForge.EVENT_BUS.addListener(AggregateMetadataSyncService::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(AggregateMetadataSyncService::onPlayerChangedDimension);
        MinecraftForge.EVENT_BUS.addListener(ModCommands::register);
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                AggregateProviderRefreshService.tickServer(event.getServer());
                AggregatePatternExpander.tickServer(event.getServer());
            }
        });
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(ClientEvents::registerScreens);
            modBus.addListener(ClientEvents::registerConfigScreen);
            ClientEvents.register();
        }
        LOGGER.info("AE All Pattern initialized");
    }
}
