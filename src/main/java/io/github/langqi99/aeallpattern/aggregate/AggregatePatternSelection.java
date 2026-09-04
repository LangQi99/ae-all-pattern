package io.github.langqi99.aeallpattern.aggregate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import io.github.langqi99.aeallpattern.network.FriendlyStreamCodec;

/**
 * Per-item publication selection for one aggregate pattern.
 *
 * <p>The component stores a compact ID list whose meaning depends on {@link #inverted()}:
 * when {@code inverted == false} the IDs are <em>disabled</em> patterns (default: everything
 * is published); when {@code inverted == true} the IDs are the only <em>enabled</em> patterns.
 * That way both extremes ("all selected" and "nothing selected") stay tiny: selecting
 * everything removes the component, deselecting everything is an inverted empty list.</p>
 */
public record AggregatePatternSelection(boolean inverted, List<String> ids) {
    public static final int MAX_IDS = Integer.MAX_VALUE;
    public static final int MAX_ID_LENGTH = 160;

    public static final AggregatePatternSelection ALL_ENABLED = new AggregatePatternSelection(false, List.of());
    public static final AggregatePatternSelection NONE_ENABLED = new AggregatePatternSelection(true, List.of());

    public static final Codec<AggregatePatternSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("inverted", false).forGetter(AggregatePatternSelection::inverted),
            Codec.STRING.listOf().optionalFieldOf("ids", List.of()).forGetter(AggregatePatternSelection::ids)
    ).apply(instance, AggregatePatternSelection::new));

    public static final FriendlyStreamCodec<AggregatePatternSelection> STREAM_CODEC =
            FriendlyStreamCodec.of(AggregatePatternSelection::encode, AggregatePatternSelection::decode);

    public AggregatePatternSelection {
        if (ids == null) {
            ids = List.of();
        }
        if (ids.size() > MAX_IDS) {
            throw new IllegalArgumentException("aggregate pattern selection exceeds " + MAX_IDS + " ids");
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>(ids.size());
        for (String id : ids) {
            if (id == null || id.isBlank() || id.length() > MAX_ID_LENGTH) {
                throw new IllegalArgumentException("invalid aggregate pattern selection id");
            }
            distinct.add(id);
        }
        ids = List.copyOf(distinct);
    }

    /** True when the aggregate should publish this child pattern. */
    public boolean isEnabled(String patternId) {
        return inverted == ids.contains(patternId);
    }

    /** True when every pattern is published; an absent component behaves the same way. */
    public boolean isAllEnabled() {
        return !inverted && ids.isEmpty();
    }

    /** True when nothing is published. */
    public boolean isNoneEnabled() {
        return inverted && ids.isEmpty();
    }

    /** Selection with the given pattern's publication state flipped. */
    public AggregatePatternSelection toggled(String patternId) {
        if (patternId == null || patternId.isBlank() || patternId.length() > MAX_ID_LENGTH) {
            return this;
        }
        List<String> updated = new ArrayList<>(ids);
        if (ids.contains(patternId)) {
            updated.remove(patternId);
        } else if (updated.size() < MAX_IDS) {
            updated.add(patternId);
        } else {
            return this;
        }
        return new AggregatePatternSelection(inverted, updated);
    }

    /** Selection with every supplied pattern set to the same publication state. */
    public AggregatePatternSelection withEnabled(Collection<String> patternIds, boolean enabled) {
        LinkedHashSet<String> updated = new LinkedHashSet<>(ids);
        for (String patternId : patternIds) {
            if (patternId == null || patternId.isBlank() || patternId.length() > MAX_ID_LENGTH) {
                continue;
            }
            if (enabled == inverted) {
                updated.add(patternId);
            } else {
                updated.remove(patternId);
            }
        }
        return new AggregatePatternSelection(inverted, List.copyOf(updated));
    }

    /**
     * Removes ids that no longer exist and stores whichever side of the current catalog is
     * smaller. The existing mode decides the state of newly discovered ids before compaction:
     * a disabled-id list enables new recipes, while an enabled-id list keeps them disabled.
     */
    public AggregatePatternSelection reconciled(Collection<String> currentPatternIds) {
        LinkedHashSet<String> current = new LinkedHashSet<>();
        for (String patternId : currentPatternIds) {
            if (patternId != null && !patternId.isBlank() && patternId.length() <= MAX_ID_LENGTH) {
                current.add(patternId);
            }
        }
        if (current.isEmpty()) {
            return inverted ? NONE_ENABLED : ALL_ENABLED;
        }

        List<String> enabled = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        for (String patternId : current) {
            (isEnabled(patternId) ? enabled : disabled).add(patternId);
        }
        if (enabled.size() < disabled.size()) {
            return new AggregatePatternSelection(true, enabled);
        }
        if (disabled.size() < enabled.size()) {
            return new AggregatePatternSelection(false, disabled);
        }
        // A tie has equal storage cost. Preserve the old mode so the default state of recipes
        // added by a later catalog refresh does not change merely because we compacted it.
        return inverted
                ? new AggregatePatternSelection(true, enabled)
                : new AggregatePatternSelection(false, disabled);
    }

    public AggregatePatternSelection toggled(String patternId, Collection<String> currentPatternIds) {
        return toggled(patternId).reconciled(currentPatternIds);
    }

    public AggregatePatternSelection withEnabled(
            Collection<String> patternIds,
            boolean enabled,
            Collection<String> currentPatternIds) {
        return withEnabled(patternIds, enabled).reconciled(currentPatternIds);
    }

    private static void encode(FriendlyByteBuf buffer, AggregatePatternSelection selection) {
        buffer.writeBoolean(selection.inverted);
        buffer.writeVarInt(selection.ids.size());
        for (String id : selection.ids) {
            buffer.writeUtf(id, MAX_ID_LENGTH);
        }
    }

    private static AggregatePatternSelection decode(FriendlyByteBuf buffer) {
        boolean inverted = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_IDS) {
            throw new IllegalArgumentException("invalid aggregate pattern selection count: " + count);
        }
        List<String> ids = new ArrayList<>(Math.min(count, 16384));
        for (int index = 0; index < count; index++) {
            ids.add(buffer.readUtf(MAX_ID_LENGTH));
        }
        return new AggregatePatternSelection(inverted, ids);
    }
}
