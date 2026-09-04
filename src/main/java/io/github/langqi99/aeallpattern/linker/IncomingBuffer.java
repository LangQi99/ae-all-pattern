package io.github.langqi99.aeallpattern.linker;

import appeng.api.config.Actionable;
import io.github.langqi99.aeallpattern.binding.BindingRecord;
import io.github.langqi99.aeallpattern.binding.BindingSavedData;
import io.github.langqi99.aeallpattern.binding.BlockEntityFingerprint;
import io.github.langqi99.aeallpattern.diagnostics.PerformanceMetrics;
import io.github.langqi99.aeallpattern.machine.MachineAdapterRegistry;
import io.github.langqi99.aeallpattern.recipe.RecipeFingerprint;
import io.github.langqi99.aeallpattern.recipe.RecipeSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Persistent ownership boundary between AE and an external machine. */
@SuppressWarnings("deprecation")
public final class IncomingBuffer {
    private static final int MAX_QUEUED_CRAFTS = 64;
    private static final int MAX_RECOVERED_STACKS = 64;
    private static final String QUEUE_TAG = "IncomingQueue";
    private static final String PENDING_TAG = "PendingCrafts";
    private static final String RECOVERED_TAG = "RecoveredOutputs";

    private final List<BufferedInput> queue = new ArrayList<>();
    private final List<PendingCraft> pending = new ArrayList<>();
    private final List<RecoveredOutput> recoveredOutputs = new ArrayList<>();
    /** O(1) outstanding-work counter per binding, kept in sync with queue and pending. */
    private final java.util.Map<UUID, Integer> workCount = new java.util.HashMap<>();

    public boolean canAccept(UUID bindingId) {
        return queue.size() < MAX_QUEUED_CRAFTS && !hasWork(bindingId);
    }

    public boolean hasWork(UUID bindingId) {
        return workCount.getOrDefault(bindingId, 0) > 0;
    }

    private void incrementWork(UUID bindingId) {
        workCount.merge(bindingId, 1, Integer::sum);
    }

    private void decrementWork(UUID bindingId) {
        Integer count = workCount.get(bindingId);
        if (count == null || count <= 1) {
            workCount.remove(bindingId);
        } else {
            workCount.put(bindingId, count - 1);
        }
    }

    public void enqueue(BindingRecord binding, String patternKey, ItemStack input, ItemStack output, int processingTicks) {
        RecipeFingerprint fingerprint = new RecipeFingerprint(
                binding.adapterId(), patternKey, input.toString(), output.toString(), binding.adapterSchema());
        enqueue(binding, patternKey,
                new RecipeSnapshot(new ResourceLocation("aeallpattern:legacy_buffer"), input, output, fingerprint, processingTicks),
                List.of(input), output, processingTicks);
    }

    public void enqueue(
            BindingRecord binding,
            String patternKey,
            RecipeSnapshot recipe,
            List<ItemStack> inputs,
            ItemStack output,
            int processingTicks) {
        if (!canAccept(binding.bindingId())) {
            throw new IllegalStateException("buffer cannot accept another craft for " + binding.bindingId());
        }
        queue.add(new BufferedInput(
                binding.bindingId(), patternKey, recipe.recipeId(),
                inputs.stream().map(ItemStack::copy).toList(), output.copy(), Math.max(1, processingTicks)));
        incrementWork(binding.bindingId());
    }

    public boolean tick(ServerLevel linkerLevel, PatternLinkerBlockEntity linker) {
        boolean changed = flushRecoveredOutputs(linker);
        changed |= drainBoundMachineOutput(linkerLevel, linker);
        changed |= releaseFinishedCrafts(linkerLevel);
        if (queue.isEmpty()) {
            return changed;
        }

        for (int index = 0; index < queue.size(); index++) {
            BufferedInput buffered = queue.get(index);
            Optional<BindingRecord> binding =
                    BindingSavedData.get(linkerLevel.getServer()).find(buffered.bindingId);
            if (binding.isEmpty()) {
                continue;
            }

            BindingRecord record = binding.get();
            ServerLevel targetLevel = linkerLevel.getServer().getLevel(record.target().dimension());
            if (targetLevel == null || !targetLevel.hasChunkAt(record.target().pos())) {
                continue;
            }
            var target = targetLevel.getBlockEntity(record.target().pos());
            ResourceLocation adapterId = ResourceLocation.tryParse(record.adapterId());
            if (target == null || adapterId == null
                    || !record.targetFingerprint().equals(BlockEntityFingerprint.of(target))) {
                continue;
            }
            var adapter = MachineAdapterRegistry.byId(adapterId)
                    .filter(candidate -> candidate.supports(targetLevel, target));
            RecipeSnapshot recipe = new RecipeSnapshot(
                    buffered.recipeId, buffered.inputs, buffered.output,
                    new RecipeFingerprint(record.adapterId(), buffered.patternKey,
                            buffered.inputs.toString(), buffered.output.toString(), record.adapterSchema()),
                    buffered.processingTicks);
            if (adapter.isEmpty() || !adapter.get().insertRecipe(targetLevel, record, recipe, buffered.inputs)) {
                continue;
            }

            queue.remove(index);
            pending.add(new PendingCraft(
                    record.bindingId(),
                    buffered.patternKey,
                    buffered.output,
                    targetLevel.getGameTime() + buffered.processingTicks + 40L));
            // Work counter stays unchanged: the queue entry moved into pending.
            PerformanceMetrics.machineInputInserted(buffered.inputs.stream().mapToInt(ItemStack::getCount).sum());
            return true;
        }
        return changed;
    }

    public List<ItemStack> recoverableDrops() {
        List<ItemStack> drops = new ArrayList<>();
        queue.forEach(input -> input.inputs.forEach(stack -> drops.add(stack.copy())));
        recoveredOutputs.forEach(output -> drops.add(output.stack.copy()));
        return List.copyOf(drops);
    }

    public List<ItemStack> removeBinding(UUID bindingId) {
        List<ItemStack> recovered = queue.stream()
                .filter(input -> input.bindingId.equals(bindingId))
                .flatMap(input -> input.inputs.stream().map(ItemStack::copy))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        recoveredOutputs.stream()
                .filter(output -> output.bindingId.equals(bindingId))
                .map(output -> output.stack.copy())
                .forEach(recovered::add);
        queue.removeIf(input -> input.bindingId.equals(bindingId));
        pending.removeIf(craft -> craft.bindingId.equals(bindingId));
        recoveredOutputs.removeIf(output -> output.bindingId.equals(bindingId));
        workCount.remove(bindingId);
        return List.copyOf(recovered);
    }

    public void clear() {
        queue.clear();
        pending.clear();
        recoveredOutputs.clear();
        workCount.clear();
    }

    public void save(CompoundTag parent) {
        ListTag queueTag = new ListTag();
        for (BufferedInput input : queue) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("BindingId", input.bindingId);
            tag.putString("PatternKey", input.patternKey);
            tag.putString("RecipeId", input.recipeId.toString());
            ListTag inputsTag = new ListTag();
            input.inputs.forEach(stack -> inputsTag.add(stack.save(new CompoundTag())));
            tag.put("Inputs", inputsTag);
            tag.put("Output", input.output.save(new CompoundTag()));
            tag.putInt("ProcessingTicks", input.processingTicks);
            queueTag.add(tag);
        }
        parent.put(QUEUE_TAG, queueTag);

        ListTag pendingTag = new ListTag();
        for (PendingCraft craft : pending) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("BindingId", craft.bindingId);
            tag.putString("PatternKey", craft.patternKey);
            tag.put("Output", craft.output.save(new CompoundTag()));
            tag.putLong("ReleaseAt", craft.releaseAt);
            pendingTag.add(tag);
        }
        parent.put(PENDING_TAG, pendingTag);

        ListTag recoveredTag = new ListTag();
        for (RecoveredOutput output : recoveredOutputs) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("BindingId", output.bindingId);
            tag.put("Stack", output.stack.save(new CompoundTag()));
            recoveredTag.add(tag);
        }
        parent.put(RECOVERED_TAG, recoveredTag);
    }

    public void load(CompoundTag parent) {
        clear();
        ListTag queueTag = parent.getList(QUEUE_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : queueTag) {
            CompoundTag tag = (CompoundTag) raw;
            List<ItemStack> inputs = new ArrayList<>();
            for (Tag inputTag : tag.getList("Inputs", Tag.TAG_COMPOUND)) {
                ItemStack input = parseStack(inputTag);
                if (!input.isEmpty()) {
                    inputs.add(input);
                }
            }
            if (inputs.isEmpty()) {
                ItemStack legacyInput = parseStack(tag.get("Input"));
                if (!legacyInput.isEmpty()) {
                    inputs.add(legacyInput);
                }
            }
            ItemStack output = parseStack(tag.get("Output"));
            ResourceLocation recipeId = ResourceLocation.tryParse(tag.getString("RecipeId"));
            if (recipeId == null) {
                recipeId = new ResourceLocation("aeallpattern:legacy_buffer");
            }
            if (!inputs.isEmpty() && !output.isEmpty() && tag.hasUUID("BindingId")) {
                UUID bindingId = tag.getUUID("BindingId");
                queue.add(new BufferedInput(
                        bindingId,
                        tag.getString("PatternKey"),
                        recipeId,
                        List.copyOf(inputs),
                        output,
                        Math.max(1, tag.getInt("ProcessingTicks"))));
                incrementWork(bindingId);
            }
        }
        ListTag pendingTag = parent.getList(PENDING_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : pendingTag) {
            CompoundTag tag = (CompoundTag) raw;
            ItemStack output = parseStack(tag.get("Output"));
            if (!output.isEmpty() && tag.hasUUID("BindingId")) {
                UUID bindingId = tag.getUUID("BindingId");
                pending.add(new PendingCraft(
                        bindingId,
                        tag.getString("PatternKey"),
                        output,
                        tag.getLong("ReleaseAt")));
                incrementWork(bindingId);
                ItemStack legacyRecovered = parseStack(tag.get("RecoveredOutput"));
                if (!legacyRecovered.isEmpty()) {
                    recoveredOutputs.add(new RecoveredOutput(bindingId, legacyRecovered));
                }
            }
        }
        ListTag recoveredTag = parent.getList(RECOVERED_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : recoveredTag) {
            CompoundTag tag = (CompoundTag) raw;
            ItemStack stack = parseStack(tag.get("Stack"));
            if (!stack.isEmpty() && tag.hasUUID("BindingId")) {
                recoveredOutputs.add(new RecoveredOutput(tag.getUUID("BindingId"), stack));
            }
        }
    }

    private static ItemStack parseStack(Tag tag) {
        if (!(tag instanceof CompoundTag compound)) {
            return ItemStack.EMPTY;
        }
        try {
            return ItemStack.of(compound);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private boolean drainBoundMachineOutput(
            ServerLevel linkerLevel, PatternLinkerBlockEntity linker) {
        if (recoveredOutputs.size() >= MAX_RECOVERED_STACKS) {
            return false;
        }
        BindingSavedData savedData = BindingSavedData.get(linkerLevel.getServer());
        for (BindingRecord record : savedData.byAnchor(
                net.minecraft.core.GlobalPos.of(linkerLevel.dimension(), linker.getBlockPos()))) {
            ServerLevel targetLevel = linkerLevel.getServer().getLevel(record.target().dimension());
            if (targetLevel == null || !targetLevel.hasChunkAt(record.target().pos())) {
                continue;
            }
            var target = targetLevel.getBlockEntity(record.target().pos());
            ResourceLocation adapterId = ResourceLocation.tryParse(record.adapterId());
            if (target == null || adapterId == null
                    || !record.targetFingerprint().equals(BlockEntityFingerprint.of(target))) {
                continue;
            }
            var adapter = MachineAdapterRegistry.byId(adapterId)
                    .filter(candidate -> candidate.supports(targetLevel, target));
            if (adapter.isEmpty()) {
                continue;
            }
            ItemStack available = adapter.get().extractAnyOutput(targetLevel, record, true);
            if (available.isEmpty()
                    || linker.insertIntoNetwork(available, Actionable.SIMULATE) != available.getCount()) {
                continue;
            }
            ItemStack extracted = adapter.get().extractAnyOutput(targetLevel, record, false);
            if (extracted.isEmpty()) {
                continue;
            }
            ItemStack remainder = insertIntoNetwork(linker, extracted);
            int recovered = extracted.getCount() - remainder.getCount();
            if (recovered > 0) {
                PerformanceMetrics.machineOutputRecovered(recovered);
            }
            if (!remainder.isEmpty()) {
                recoveredOutputs.add(new RecoveredOutput(record.bindingId(), remainder));
            }
            return true;
        }
        return false;
    }

    private boolean flushRecoveredOutputs(PatternLinkerBlockEntity linker) {
        for (int index = 0; index < recoveredOutputs.size(); index++) {
            RecoveredOutput output = recoveredOutputs.get(index);
            ItemStack remainder = insertIntoNetwork(linker, output.stack);
            int recovered = output.stack.getCount() - remainder.getCount();
            if (recovered <= 0) {
                continue;
            }
            PerformanceMetrics.machineOutputRecovered(recovered);
            if (remainder.isEmpty()) {
                recoveredOutputs.remove(index);
            } else {
                recoveredOutputs.set(index, new RecoveredOutput(output.bindingId, remainder));
            }
            return true;
        }
        return false;
    }

    private boolean releaseFinishedCrafts(ServerLevel linkerLevel) {
        BindingSavedData savedData = BindingSavedData.get(linkerLevel.getServer());
        int before = pending.size();
        pending.removeIf(craft -> {
            boolean release = savedData.find(craft.bindingId)
                    .map(binding -> linkerLevel.getServer().getLevel(binding.target().dimension()))
                    .map(level -> level.getGameTime() >= craft.releaseAt)
                    .orElse(true);
            if (release) {
                decrementWork(craft.bindingId);
            }
            return release;
        });
        return before != pending.size();
    }

    private static ItemStack insertIntoNetwork(PatternLinkerBlockEntity linker, ItemStack stack) {
        int inserted = linker.insertIntoNetwork(stack, Actionable.MODULATE);
        if (inserted >= stack.getCount()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(inserted);
        return remainder;
    }

    private record BufferedInput(
            UUID bindingId,
            String patternKey,
            ResourceLocation recipeId,
            List<ItemStack> inputs,
            ItemStack output,
            int processingTicks) {
        private BufferedInput {
            inputs = inputs.stream().map(ItemStack::copy).toList();
        }
    }

    private record PendingCraft(
            UUID bindingId,
            String patternKey,
            ItemStack output,
            long releaseAt) {
    }

    private record RecoveredOutput(UUID bindingId, ItemStack stack) {
        private RecoveredOutput {
            stack = stack.copy();
        }
    }
}
