package io.github.langqi99.aeallpattern.network;

import io.github.langqi99.aeallpattern.aggregate.AggregateMetadataView;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternLibrary;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Objects;

public final class AggregateMetadataSyncService {
    private AggregateMetadataSyncService() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player);
        }
    }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player);
        }
    }

    public static void send(ServerPlayer player) {
        var entries = AggregatePatternLibrary.get(Objects.requireNonNull(player.getServer())).entries().stream()
                .map(entry -> new AggregateMetadataView.Entry(
                        entry.libraryId(), entry.catalystId(), entry.machineTranslationKey(),
                        entry.contentHash(), entry.recipeCount(), entry.seriesHash(), entry.batchSize(),
                        entry.batchIndex(), entry.batchCount(), entry.totalRecipeCount(),
                        entry.batchCount() == 1
                                && AggregateStartupRefreshState.isRequired(player.getServer(), entry.libraryId())))
                .toList();
        PacketDistributor.sendToPlayer(player, new AggregateMetadataPayload(entries));
    }

    public static void sendToOnlinePlayers(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(AggregateMetadataSyncService::send);
    }
}
