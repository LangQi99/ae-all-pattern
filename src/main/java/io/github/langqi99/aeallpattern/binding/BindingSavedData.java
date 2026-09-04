package io.github.langqi99.aeallpattern.binding;

import io.github.langqi99.aeallpattern.AeAllPattern;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/** Server-wide binding store, physically saved in the overworld data directory. */
public final class BindingSavedData extends SavedData {
    private static final String DATA_NAME = AeAllPattern.MOD_ID + "_bindings";
    private static final String BINDINGS_TAG = "Bindings";
    private static final String UNMIGRATED_TAG = "UnmigratedBindings";

    private final Map<UUID, BindingRecord> bindings = new LinkedHashMap<>();
    private final List<CompoundTag> unmigrated = new ArrayList<>();

    public static BindingSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                BindingSavedData::load, BindingSavedData::new, DATA_NAME);
    }

    public Collection<BindingRecord> all() {
        return List.copyOf(bindings.values());
    }

    /** Bindings anchored at the given global position, in insertion order. */
    public List<BindingRecord> byAnchor(GlobalPos anchor) {
        return bindings.values().stream()
                .filter(record -> record.anchor().equals(anchor))
                .toList();
    }

    public Optional<BindingRecord> find(UUID bindingId) {
        return Optional.ofNullable(bindings.get(bindingId));
    }

    public Optional<BindingRecord> findByTarget(GlobalPos target) {
        return bindings.values().stream().filter(record -> record.target().equals(target)).findFirst();
    }

    public void put(BindingRecord record) {
        bindings.put(record.bindingId(), record);
        setDirty();
    }

    public boolean remove(UUID bindingId) {
        if (bindings.remove(bindingId) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public int removeByAnchor(GlobalPos anchor) {
        int before = bindings.size();
        bindings.values().removeIf(record -> record.anchor().equals(anchor));
        int removed = before - bindings.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag bindingTags = new ListTag();
        bindings.values().stream()
                .sorted((left, right) -> left.bindingId().compareTo(right.bindingId()))
                .map(BindingRecord::toTag)
                .forEach(bindingTags::add);
        tag.put(BINDINGS_TAG, bindingTags);

        ListTag unmigratedTags = new ListTag();
        unmigrated.stream().map(CompoundTag::copy).forEach(unmigratedTags::add);
        tag.put(UNMIGRATED_TAG, unmigratedTags);
        return tag;
    }

    private static BindingSavedData load(CompoundTag tag) {
        BindingSavedData data = new BindingSavedData();
        ListTag bindings = tag.getList(BINDINGS_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : bindings) {
            CompoundTag bindingTag = (CompoundTag) raw;
            try {
                BindingRecord record = BindingRecord.fromTag(bindingTag);
                data.bindings.put(record.bindingId(), record);
            } catch (RuntimeException error) {
                AeAllPattern.LOGGER.warn("Preserving unreadable binding for a future migration", error);
                data.unmigrated.add(bindingTag.copy());
            }
        }
        ListTag unmigrated = tag.getList(UNMIGRATED_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : unmigrated) {
            data.unmigrated.add(((CompoundTag) raw).copy());
        }
        return data;
    }
}
