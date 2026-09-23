package io.github.langqi99.aeallpattern.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import io.github.langqi99.aeallpattern.binding.BindingPatternKey;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternExpander;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternOptions;
import io.github.langqi99.aeallpattern.aggregate.AggregateRecipe;
import io.github.langqi99.aeallpattern.binding.BindingRecord;
import io.github.langqi99.aeallpattern.binding.BindingSavedData;
import io.github.langqi99.aeallpattern.linker.IncomingBuffer;
import io.github.langqi99.aeallpattern.linker.PatternLinkerBlockEntity;
import io.github.langqi99.aeallpattern.machine.MachineAdapterRegistry;
import io.github.langqi99.aeallpattern.recipe.RecipeIndexService;
import io.github.langqi99.aeallpattern.recipe.RecipeSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import io.github.langqi99.aeallpattern.binding.BlockEntityFingerprint;
import io.github.langqi99.aeallpattern.diagnostics.PerformanceMetrics;

@SuppressWarnings("deprecation")
public final class VirtualCraftingProvider implements ICraftingProvider {
    private final PatternLinkerBlockEntity linker;
    private final IncomingBuffer buffer;
    private volatile List<IPatternDetails> patterns = List.of();
    private Map<VirtualPatternDetails, PatternRoute> routes = Map.of();
    private long catalogGeneration;
    private boolean lastAvailable;
    private RefreshJob refreshJob;

    public VirtualCraftingProvider(PatternLinkerBlockEntity linker, IncomingBuffer buffer) {
        this.linker = linker;
        this.buffer = buffer;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return linker.getMainNode().isActive() ? patterns : List.of();
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputHolders) {
        if (!(details instanceof VirtualPatternDetails virtual) || !linker.getMainNode().isActive()) {
            PerformanceMetrics.pushRejected();
            return false;
        }
        PatternRoute route = routes.get(virtual);
        if (route == null || !buffer.canAccept(route.binding.bindingId(),
                route.recipe.fingerprint().stableKey(), linker.getOperationOptions().allowsParallelQueue())) {
            PerformanceMetrics.pushRejected();
            return false;
        }
        List<ItemStack> inputs = suppliedInputs(virtual, inputHolders);
        if (inputs.isEmpty()) {
            PerformanceMetrics.pushRejected();
            return false;
        }

        if (!routeStillValid(route)) {
            refresh();
            PerformanceMetrics.pushRejected();
            return false;
        }
        if (linker.getOperationOptions().blocking()) {
            var targetLevel = ((ServerLevel) linker.getLevel()).getServer().getLevel(route.binding.target().dimension());
            var adapter = MachineAdapterRegistry.byId(ResourceLocation.tryParse(route.binding.adapterId()));
            if (targetLevel == null || adapter.isEmpty()
                    || adapter.get().isInputBlocked(targetLevel, route.binding)) {
                PerformanceMetrics.pushRejected();
                return false;
            }
        }
        buffer.enqueue(
                route.binding,
                route.recipe.fingerprint().stableKey(),
                route.recipe,
                inputs,
                route.recipe.output(),
                route.recipe.processingTicks(), linker.getOperationOptions().allowsParallelQueue());
        linker.saveChanges();
        PerformanceMetrics.pushAccepted();
        return true;
    }

    @Override
    public boolean isBusy() {
        if (routes.isEmpty()) {
            return true;
        }
        return routes.values().stream().allMatch(route -> !buffer.canAccept(route.binding.bindingId(),
                route.recipe.fingerprint().stableKey(), linker.getOperationOptions().allowsParallelQueue()));
    }

    public long catalogGeneration() {
        return catalogGeneration;
    }

    public void refresh() {
        if (!(linker.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!AggregatePatternExpander.isSynchronous()) {
            // Live path: rebuild routes across ticks so a linker bound to a multi-thousand
            // recipe machine never stalls the server thread. GameTest keeps the synchronous
            // contract via refreshSync().
            RefreshJob job = refreshJob;
            if (job == null || job.generation != RecipeIndexService.generation()
                    || !job.options.equals(linker.getPatternOptions())) {
                refreshJob = new RefreshJob(linker, serverLevel);
            }
            return;
        }
        refreshSync();
    }

    /** Advances the cross-tick rebuild, if any; called from the linker's server tick. */
    public void tickRefresh() {
        RefreshJob job = refreshJob;
        if (job == null) {
            return;
        }
        if (job.generation != RecipeIndexService.generation()) {
            refreshJob = null;
            return;
        }
        if (job.advance()) {
            refreshJob = null;
            commit(job.rebuilt());
        }
    }

    private void refreshSync() {
        if (!(linker.getLevel() instanceof ServerLevel linkerLevel)) {
            return;
        }
        Map<VirtualPatternDetails, PatternRoute> rebuilt = new LinkedHashMap<>();
        var anchor = linker.getGlobalPos();
        AggregatePatternOptions options = linker.getPatternOptions();
        List<BindingRecord> bindings = BindingSavedData.get(linkerLevel.getServer()).byAnchor(anchor);
        for (BindingRecord binding : bindings) {
            ServerLevel targetLevel = linkerLevel.getServer().getLevel(binding.target().dimension());
            if (targetLevel == null || !targetLevel.hasChunkAt(binding.target().pos())) {
                continue;
            }
            BlockEntity target = targetLevel.getBlockEntity(binding.target().pos());
            if (target == null || !binding.targetFingerprint().equals(BlockEntityFingerprint.of(target))) {
                continue;
            }
            ResourceLocation adapterId = ResourceLocation.tryParse(binding.adapterId());
            if (adapterId == null) {
                continue;
            }
            var adapter = MachineAdapterRegistry.byId(adapterId)
                    .filter(candidate -> candidate.schemaVersion() == binding.adapterSchema())
                    .filter(candidate -> candidate.supports(targetLevel, target));
            if (adapter.isEmpty()) {
                continue;
            }
            var catalog = RecipeIndexService.catalog(targetLevel, target, adapter.get());
            for (RecipeSnapshot recipe : catalog.recipes()) {
                BindingPatternKey patternKey = new BindingPatternKey(binding.bindingId(), recipe.fingerprint());
                AggregateRecipe aggregateRecipe = AggregateRecipe.from(recipe);
                IPatternDetails processed = AggregatePatternExpander.expandRecipe(
                        aggregateRecipe,
                        options,
                        linkerLevel,
                        "linker:" + binding.bindingId() + ":" + recipe.fingerprint().stableKey());
                if (processed == null) {
                    continue;
                }
                VirtualPatternDetails details = new VirtualPatternDetails(
                        patternKey, processed);
                rebuilt.put(details, new PatternRoute(binding, recipe));
            }
        }
        commit(rebuilt);
    }

    private void commit(Map<VirtualPatternDetails, PatternRoute> rebuilt) {
        boolean patternsChanged = !routes.keySet().equals(rebuilt.keySet());
        Set<BindingPatternKey> oldKeys = routes.keySet().stream().map(VirtualPatternDetails::key).collect(java.util.stream.Collectors.toSet());
        Set<BindingPatternKey> newKeys = rebuilt.keySet().stream().map(VirtualPatternDetails::key).collect(java.util.stream.Collectors.toSet());
        routes = Map.copyOf(rebuilt);
        patterns = List.copyOf(new ArrayList<>(rebuilt.keySet()));
        catalogGeneration = RecipeIndexService.generation();
        int diffSize = com.google.common.collect.Sets.symmetricDifference(oldKeys, newKeys).size();
        PerformanceMetrics.providerRefreshed(diffSize);
        boolean available = linker.getMainNode().isActive();
        if ((patternsChanged || available != lastAvailable) && linker.getMainNode().isReady()) {
            ICraftingProvider.requestUpdate(linker.getMainNode());
        }
        lastAvailable = available;
    }

    public void tickAvailability() {
        boolean available = linker.getMainNode().isActive();
        if (available != lastAvailable && linker.getMainNode().isReady()) {
            lastAvailable = available;
            ICraftingProvider.requestUpdate(linker.getMainNode());
        }
    }

    private boolean routeStillValid(PatternRoute route) {
        if (!(linker.getLevel() instanceof ServerLevel linkerLevel)) {
            return false;
        }
        if (BindingSavedData.get(linkerLevel.getServer()).find(route.binding.bindingId())
                .filter(route.binding::equals)
                .isEmpty()) {
            return false;
        }
        ServerLevel targetLevel = linkerLevel.getServer().getLevel(route.binding.target().dimension());
        if (targetLevel == null || !targetLevel.hasChunkAt(route.binding.target().pos())) {
            return false;
        }
        BlockEntity target = targetLevel.getBlockEntity(route.binding.target().pos());
        return target != null && route.binding.targetFingerprint().equals(BlockEntityFingerprint.of(target));
    }

    private List<ItemStack> suppliedInputs(VirtualPatternDetails details, KeyCounter[] holders) {
        IPatternDetails.IInput[] expected = details.getInputs();
        if (holders.length != expected.length) {
            return List.of();
        }
        List<ItemStack> result = new ArrayList<>(holders.length);
        for (int index = 0; index < holders.length; index++) {
            AEItemKey key = null;
            long amount = 0;
            for (var entry : holders[index]) {
                if (entry.getLongValue() <= 0) {
                    continue;
                }
                if (!(entry.getKey() instanceof AEItemKey itemKey) || key != null) {
                    return List.of();
                }
                key = itemKey;
                amount = entry.getLongValue();
            }
            if (key == null || amount < 1 || amount > Integer.MAX_VALUE
                    || !expected[index].isValid(key, linker.getLevel())) {
                return List.of();
            }
            long required = -1;
            for (var possible : expected[index].getPossibleInputs()) {
                if (possible.what().equals(key)
                        || (expected[index] instanceof io.github.langqi99.aeallpattern.aggregate.IgnoreInputNbtInput
                            && possible.what() instanceof AEItemKey template && template.getItem() == key.getItem())) {
                    required = possible.amount() * expected[index].getMultiplier();
                    break;
                }
            }
            if (amount != required) {
                return List.of();
            }
            result.add(key.toStack((int) amount));
        }
        return List.copyOf(result);
    }

    private record PatternRoute(BindingRecord binding, RecipeSnapshot recipe) {
    }

    /**
     * Cross-tick rebuild of the route table. The linker keeps publishing the previous route
     * table while this runs, and the completed table is swapped in atomically so AE2 never
     * observes a half-built provider.
     */
    private static final class RefreshJob {
        private static final long BUDGET_NANOS = 2_000_000L;
        private static final int MAX_STEPS_PER_TICK = 128;

        private final long generation;
        private final ServerLevel linkerLevel;
        private final AggregatePatternOptions options;
        private final List<BindingRecord> bindings;
        private final Map<VirtualPatternDetails, PatternRoute> rebuilt = new LinkedHashMap<>();
        private int bindingCursor;
        private boolean bindingLoaded;
        private List<RecipeSnapshot> currentCatalog = List.of();
        private int recipeCursor;

        private RefreshJob(PatternLinkerBlockEntity linker, ServerLevel linkerLevel) {
            this.generation = RecipeIndexService.generation();
            this.linkerLevel = linkerLevel;
            GlobalPos anchor = linker.getGlobalPos();
            this.options = linker.getPatternOptions();
            this.bindings = BindingSavedData.get(linkerLevel.getServer()).byAnchor(anchor);
        }

        private Map<VirtualPatternDetails, PatternRoute> rebuilt() {
            return rebuilt;
        }

        /** Returns true once every binding has been expanded. */
        private boolean advance() {
            long deadline = System.nanoTime() + BUDGET_NANOS;
            while (bindingCursor < bindings.size()) {
                BindingRecord binding = bindings.get(bindingCursor);
                if (!bindingLoaded && !loadBinding(binding)) {
                    bindingCursor++;
                    continue;
                }
                int steps = 0;
                while (recipeCursor < currentCatalog.size()) {
                    RecipeSnapshot recipe = currentCatalog.get(recipeCursor++);
                    steps++;
                    BindingPatternKey patternKey =
                            new BindingPatternKey(binding.bindingId(), recipe.fingerprint());
                    AggregateRecipe aggregateRecipe = AggregateRecipe.from(recipe);
                    IPatternDetails processed = AggregatePatternExpander.expandRecipe(
                            aggregateRecipe,
                            options,
                            linkerLevel,
                            "linker:" + binding.bindingId() + ":" + recipe.fingerprint().stableKey());
                    if (processed != null) {
                        rebuilt.put(
                                new VirtualPatternDetails(patternKey, processed),
                                new PatternRoute(binding, recipe));
                    }
                    if (steps >= MAX_STEPS_PER_TICK || System.nanoTime() >= deadline) {
                        if (recipeCursor >= currentCatalog.size()) {
                            finishBinding();
                        }
                        return false;
                    }
                }
                finishBinding();
            }
            return true;
        }

        private boolean loadBinding(BindingRecord binding) {
            ServerLevel targetLevel = linkerLevel.getServer().getLevel(binding.target().dimension());
            if (targetLevel == null || !targetLevel.hasChunkAt(binding.target().pos())) {
                return false;
            }
            BlockEntity target = targetLevel.getBlockEntity(binding.target().pos());
            if (target == null || !binding.targetFingerprint().equals(BlockEntityFingerprint.of(target))) {
                return false;
            }
            ResourceLocation adapterId = ResourceLocation.tryParse(binding.adapterId());
            if (adapterId == null) {
                return false;
            }
            var adapter = MachineAdapterRegistry.byId(adapterId)
                    .filter(candidate -> candidate.schemaVersion() == binding.adapterSchema())
                    .filter(candidate -> candidate.supports(targetLevel, target));
            if (adapter.isEmpty()) {
                return false;
            }
            currentCatalog = RecipeIndexService.catalog(targetLevel, target, adapter.get()).recipes();
            recipeCursor = 0;
            bindingLoaded = true;
            return true;
        }

        private void finishBinding() {
            bindingCursor++;
            bindingLoaded = false;
            currentCatalog = List.of();
            recipeCursor = 0;
        }
    }
}
