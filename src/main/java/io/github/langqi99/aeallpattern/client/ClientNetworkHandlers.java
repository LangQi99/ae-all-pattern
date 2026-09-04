package io.github.langqi99.aeallpattern.client;

import io.github.langqi99.aeallpattern.aggregate.AggregateMetadataView;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternSelectionMenu;
import io.github.langqi99.aeallpattern.network.AggregateMetadataPayload;
import io.github.langqi99.aeallpattern.network.AggregateSearchResultPayload;
import io.github.langqi99.aeallpattern.network.BindingSyncPayload;
import net.minecraft.client.Minecraft;

/** Client-only payload endpoints kept out of the dedicated-server network bootstrap. */
public final class ClientNetworkHandlers {
    private ClientNetworkHandlers() {
    }

    public static void handle(BindingSyncPayload payload) {
        ClientBindingState.replace(payload.entries());
    }

    public static void handle(AggregateMetadataPayload payload) {
        AggregateMetadataView.replace(payload.entries());
    }

    public static void handle(AggregateSearchResultPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AggregatePatternSelectionScreen screen
                && minecraft.player != null
                && minecraft.player.containerMenu instanceof AggregatePatternSelectionMenu menu) {
            screen.receiveSearchResult(payload, menu);
        }
    }
}
