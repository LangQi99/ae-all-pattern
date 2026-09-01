package io.github.langqi99.aeallpattern.aggregate;

import io.github.langqi99.aeallpattern.recipe.RecipeIndexService;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** Refreshes loaded AE2 pattern providers after server recipes finish reloading. */
public final class AggregateProviderRefreshService {
    private static final Map<MinecraftServer, List<TrackedRefresh>> PROVIDERS =
            new WeakHashMap<>();
    private static final Map<MinecraftServer, Long> PENDING = new WeakHashMap<>();
    private static final Map<MinecraftServer, Long> REFRESHED = new WeakHashMap<>();

    private AggregateProviderRefreshService() {
    }

    private record TrackedRefresh(WeakReference<Object> owner, Consumer<Object> refresh) {
    }

    public static synchronized void track(MinecraftServer server, Object owner, Consumer<Object> refresh) {
        List<TrackedRefresh> providers =
                PROVIDERS.computeIfAbsent(server, ignored -> new ArrayList<>());
        providers.removeIf(tracked -> tracked.owner().get() == null);
        if (providers.stream().noneMatch(tracked -> tracked.owner().get() == owner)) {
            providers.add(new TrackedRefresh(new WeakReference<>(owner), refresh));
        }
    }

    public static synchronized void onDatapackSync(OnDatapackSyncEvent event) {
        MinecraftServer server = event.getPlayerList().getServer();
        long generation = RecipeIndexService.generation();
        if (!REFRESHED.getOrDefault(server, 0L).equals(generation)) {
            PENDING.put(server, generation);
        }
    }

    /** Called one tick after datapack sync, when RecipeManager is stable again. */
    public static void tickServer(MinecraftServer server) {
        List<TrackedRefresh> refresh;
        synchronized (AggregateProviderRefreshService.class) {
            Long generation = PENDING.remove(server);
            if (generation == null || generation != RecipeIndexService.generation()) {
                return;
            }
            REFRESHED.put(server, generation);
            List<TrackedRefresh> providers = PROVIDERS.get(server);
            if (providers == null) {
                return;
            }
            providers.removeIf(tracked -> tracked.owner().get() == null);
            refresh = List.copyOf(providers);
        }
        for (TrackedRefresh tracked : refresh) {
            Object owner = tracked.owner().get();
            if (owner != null) {
                tracked.refresh().accept(owner);
            }
        }
    }
}
