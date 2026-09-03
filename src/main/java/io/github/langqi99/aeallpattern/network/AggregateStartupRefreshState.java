package io.github.langqi99.aeallpattern.network;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;

/**
 * Server-session-only record of catalogs already supplied by a recipe-viewer client.
 *
 * <p>Nothing is written to the save: a new server instance deliberately starts with an empty
 * set, making every persisted catalog eligible for one refresh. Catalogs created or refreshed
 * during this server run are marked complete so later player logins do not rescan them.</p>
 */
final class AggregateStartupRefreshState {
    private static final Map<MinecraftServer, Set<UUID>> REFRESHED = new WeakHashMap<>();

    private AggregateStartupRefreshState() {
    }

    static synchronized boolean isRequired(MinecraftServer server, UUID libraryId) {
        return !REFRESHED.getOrDefault(server, Set.of()).contains(libraryId);
    }

    static synchronized void markComplete(MinecraftServer server, UUID libraryId) {
        REFRESHED.computeIfAbsent(server, ignored -> new HashSet<>()).add(libraryId);
    }
}
