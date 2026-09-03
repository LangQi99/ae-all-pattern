package io.github.langqi99.aeallpattern.network;

import io.github.langqi99.aeallpattern.client.ClientBindingState;
import io.github.langqi99.aeallpattern.aggregate.AggregateMetadataView;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class BindingNetwork {
    private BindingNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(
                BindingSyncPayload.TYPE,
                BindingSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientBindingState.replace(payload.entries())));
        registrar.playToClient(
                AggregateMetadataPayload.TYPE,
                AggregateMetadataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        AggregateMetadataView.replace(payload.entries())));
        registrar.playToServer(
                GenerateAggregatePayload.TYPE,
                GenerateAggregatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        AggregateGenerationService.handle(payload, player);
                    }
                }));
        registrar.playToServer(
                AggregateSearchPayload.TYPE,
                AggregateSearchPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player
                            && player.containerMenu
                                    instanceof io.github.langqi99.aeallpattern.aggregate.AggregatePatternSelectionMenu menu) {
                        menu.applySearch(
                                player,
                                payload.searchText(),
                                payload.searchOutputs(),
                                payload.resultPageIndex(),
                                payload.requestId());
                    }
                }));
        registrar.playToClient(
                AggregateSearchResultPayload.TYPE,
                AggregateSearchResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    var minecraft = net.minecraft.client.Minecraft.getInstance();
                    if (minecraft.screen
                                    instanceof io.github.langqi99.aeallpattern.client.AggregatePatternSelectionScreen screen
                            && minecraft.player != null
                            && minecraft.player.containerMenu
                                    instanceof io.github.langqi99.aeallpattern.aggregate.AggregatePatternSelectionMenu menu) {
                        screen.receiveSearchResult(payload, menu);
                    }
                }));
    }
}
