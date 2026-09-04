package io.github.langqi99.aeallpattern.recipe;

import io.github.langqi99.aeallpattern.aggregate.AggregatePatternExpander;
import io.github.langqi99.aeallpattern.machine.MachineAdapter;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraft.server.level.ServerLevel;
import io.github.langqi99.aeallpattern.diagnostics.PerformanceMetrics;
import org.jetbrains.annotations.NotNull;

/** Generation-aware shared recipe cache. It never retains a level or block entity. */
public final class RecipeIndexService {
    private static final AtomicLong GENERATION = new AtomicLong(1);
    private static final Map<RecipeManager, ManagerCache> CACHE = new WeakHashMap<>();

    private RecipeIndexService() {
    }

    public static long generation() {
        return GENERATION.get();
    }

    public static synchronized RecipeCatalog catalog(
            ServerLevel level, BlockEntity target, MachineAdapter adapter) {
        long generation = GENERATION.get();
        ManagerCache managerCache = CACHE.computeIfAbsent(level.getRecipeManager(), ignored -> new ManagerCache());
        if (managerCache.generation != generation) {
            managerCache.generation = generation;
            managerCache.catalogs.clear();
        }
        String cacheKey = adapter.id() + "|" + target.getType();
        return managerCache.catalogs.computeIfAbsent(cacheKey, ignored -> {
            long start = System.nanoTime();
            RecipeCatalog catalog = adapter.discoverRecipes(level, target, generation);
            PerformanceMetrics.catalogRebuilt(
                    System.nanoTime() - start, catalog.recipes().size(), catalog.filteredCount());
            return catalog;
        });
    }

    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new PreparableReloadListener() {
            @Override
            public @NotNull CompletableFuture<Void> reload(
                    @NotNull PreparationBarrier barrier,
                    @NotNull ResourceManager resources,
                    @NotNull ProfilerFiller preparationProfiler,
                    @NotNull ProfilerFiller reloadProfiler,
                    @NotNull Executor backgroundExecutor,
                    @NotNull Executor gameExecutor) {
                return barrier.wait(Boolean.FALSE).thenRunAsync(RecipeIndexService::invalidate, gameExecutor);
            }
        });
    }

    public static synchronized void invalidate() {
        GENERATION.incrementAndGet();
        CACHE.clear();
        AggregatePatternExpander.clearCaches();
    }

    private static final class ManagerCache {
        private long generation;
        private final Map<String, RecipeCatalog> catalogs = new HashMap<>();
    }
}
