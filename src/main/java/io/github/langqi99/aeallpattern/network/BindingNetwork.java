package io.github.langqi99.aeallpattern.network;

import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternSelectionMenu;
import io.github.langqi99.aeallpattern.client.ClientNetworkHandlers;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Forge 1.20.1 SimpleChannel transport for the same bounded payloads as main. */
public final class BindingNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AeAllPattern.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);
    private static int nextId;

    private BindingNetwork() {
    }

    public static void register() {
        register(BindingSyncPayload.class, BindingSyncPayload.STREAM_CODEC,
                NetworkDirection.PLAY_TO_CLIENT, (payload, context) -> client(() ->
                        ClientNetworkHandlers.handle(payload)));
        register(AggregateMetadataPayload.class, AggregateMetadataPayload.STREAM_CODEC,
                NetworkDirection.PLAY_TO_CLIENT, (payload, context) -> client(() ->
                        ClientNetworkHandlers.handle(payload)));
        register(GenerateAggregatePayload.class, GenerateAggregatePayload.STREAM_CODEC,
                NetworkDirection.PLAY_TO_SERVER, (payload, context) -> {
                    ServerPlayer player = context.getSender();
                    if (player != null) {
                        AggregateGenerationService.handle(payload, player);
                    }
                });
        register(AggregateSearchPayload.class, AggregateSearchPayload.STREAM_CODEC,
                NetworkDirection.PLAY_TO_SERVER, (payload, context) -> {
                    ServerPlayer player = context.getSender();
                    if (player != null && player.containerMenu instanceof AggregatePatternSelectionMenu menu) {
                        menu.applySearch(player, payload.searchText(), payload.searchOutputs(),
                                payload.resultPageIndex(), payload.requestId());
                    }
                });
        register(AggregateSearchResultPayload.class, AggregateSearchResultPayload.STREAM_CODEC,
                NetworkDirection.PLAY_TO_CLIENT, (payload, context) -> client(() ->
                        ClientNetworkHandlers.handle(payload)));
    }

    private static <T> void register(
            Class<T> type,
            FriendlyStreamCodec<T> codec,
            NetworkDirection direction,
            java.util.function.BiConsumer<T, NetworkEvent.Context> handler) {
        CHANNEL.registerMessage(
                nextId++, type,
                (payload, buffer) -> codec.encode(buffer, payload),
                codec::decode,
                (payload, contextSupplier) -> {
                    NetworkEvent.Context context = contextSupplier.get();
                    context.enqueueWork(() -> handler.accept(payload, context));
                    context.setPacketHandled(true);
                },
                Optional.of(direction));
    }

    private static void client(Runnable action) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> action.run());
    }

    public static void sendToPlayer(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
    }
}
