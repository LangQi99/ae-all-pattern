package io.github.langqi99.aeallpattern.aggregate;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.langqi99.aeallpattern.config.AeAllPatternCommonConfig;

import java.util.*;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;

/** One logical recipe input whose alternatives are OR choices, never separate recipes. */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record AggregateInputSlot(
        List<GenericStack> alternatives,
        Optional<ResourceLocation> itemTag) {
    public static final int MAX_ALTERNATIVES = Integer.MAX_VALUE;

    public static int configuredAlternativeLimit() {
        return AeAllPatternCommonConfig.TAG_EXPANSION_LIMIT.getAsInt();
    }

    private static final Codec<List<GenericStack>> ALTERNATIVES_CODEC = GenericStack.CODEC.listOf()
            .validate(AggregateInputSlot::validateAlternatives);
    public static final Codec<AggregateInputSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ALTERNATIVES_CODEC.fieldOf("alternatives").forGetter(AggregateInputSlot::alternatives),
            ResourceLocation.CODEC.optionalFieldOf("item_tag").forGetter(AggregateInputSlot::itemTag)
    ).apply(instance, AggregateInputSlot::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AggregateInputSlot> STREAM_CODEC = StreamCodec.of(
            AggregateInputSlot::encode,
            AggregateInputSlot::decode);

    public AggregateInputSlot {
        alternatives = copyAndValidate(alternatives);
        if (itemTag.isPresent() && !(alternatives.getFirst().what() instanceof AEItemKey)) {
            throw new IllegalArgumentException("only item inputs can reference an item tag");
        }
    }

    public static AggregateInputSlot exact(GenericStack stack) {
        return new AggregateInputSlot(List.of(stack), Optional.empty());
    }

    public static AggregateInputSlot fromSavedData(
            List<GenericStack> alternatives, Optional<ResourceLocation> itemTag) {
        int limit = configuredAlternativeLimit();
        if (alternatives.size() > limit) {
            alternatives = alternatives.subList(0, limit);
        }
        return new AggregateInputSlot(alternatives, itemTag);
    }

    public GenericStack primary() {
        return alternatives.getFirst();
    }

    /** Resolves a saved tag against the current datapack; explicit candidates are the safe fallback. */
    public List<GenericStack> resolve(Level level) {
        if (itemTag.isEmpty()) {
            return alternatives;
        }
        var registry = level.registryAccess().registryOrThrow(Registries.ITEM);
        var tag = registry.getTag(TagKey.create(Registries.ITEM, itemTag.orElseThrow()));
        if (tag.isEmpty()) {
            return alternatives;
        }
        long amount = primary().amount();
        LinkedHashMap<Object, GenericStack> resolved = new LinkedHashMap<>();
        tag.orElseThrow().stream()
                .map(Holder::value)
                .sorted(Comparator.comparing(item -> Objects.requireNonNull(registry.getKey(item)).toString()))
                .limit(configuredAlternativeLimit())
                .forEach(item -> {
                    GenericStack stack = new GenericStack(AEItemKey.of(item), amount);
                    resolved.putIfAbsent(stack.what(), stack);
                });
        return resolved.isEmpty() ? alternatives : List.copyOf(resolved.values());
    }

    public boolean hasAlternatives() {
        return itemTag.isPresent() || alternatives.size() > 1;
    }

    /** Splits n units into n slots, each retaining the complete candidate set. */
    public List<AggregateInputSlot> splitUnits(Level level) {
        List<GenericStack> resolved = resolve(level);
        if (resolved.stream().anyMatch(stack -> !(stack.what() instanceof AEItemKey))) {
            return List.of(new AggregateInputSlot(resolved, Optional.empty()));
        }
        long amount = resolved.getFirst().amount();
        if (amount <= 1 || resolved.stream().anyMatch(stack -> stack.amount() != amount)) {
            return List.of(new AggregateInputSlot(resolved, Optional.empty()));
        }
        if (amount > AggregatePatternExpander.MAX_SPLIT_ITEM_INPUTS) {
            throw new IllegalArgumentException("split input exceeds safety limit: " + amount);
        }
        List<GenericStack> units = resolved.stream()
                .map(stack -> new GenericStack(stack.what(), 1))
                .toList();
        AggregateInputSlot unit = new AggregateInputSlot(units, Optional.empty());
        List<AggregateInputSlot> result = new ArrayList<>((int) amount);
        for (int index = 0; index < amount; index++) {
            result.add(unit);
        }
        return List.copyOf(result);
    }

    private static DataResult<List<GenericStack>> validateAlternatives(List<GenericStack> alternatives) {
        try {
            return DataResult.success(copyAndValidate(alternatives));
        } catch (IllegalArgumentException error) {
            return DataResult.error(error::getMessage);
        }
    }

    private static List<GenericStack> copyAndValidate(List<GenericStack> alternatives) {
        if (alternatives == null || alternatives.isEmpty() || alternatives.size() > MAX_ALTERNATIVES) {
            throw new IllegalArgumentException("input slot must contain 1-" + MAX_ALTERNATIVES + " alternatives");
        }
        List<GenericStack> result = List.copyOf(alternatives);
        if (result.stream().anyMatch(stack -> stack == null || stack.what() == null || stack.amount() <= 0)) {
            throw new IllegalArgumentException("input alternatives must be non-empty");
        }
        return result;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, AggregateInputSlot slot) {
        buffer.writeVarInt(slot.alternatives.size());
        slot.alternatives.forEach(stack -> GenericStack.STREAM_CODEC.encode(buffer, stack));
        buffer.writeBoolean(slot.itemTag.isPresent());
        slot.itemTag.ifPresent(buffer::writeResourceLocation);
    }

    private static AggregateInputSlot decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 1 || count > MAX_ALTERNATIVES) {
            throw new IllegalArgumentException("invalid input alternative count: " + count);
        }
        List<GenericStack> alternatives = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            alternatives.add(GenericStack.STREAM_CODEC.decode(buffer));
        }
        Optional<ResourceLocation> tag = buffer.readBoolean()
                ? Optional.of(buffer.readResourceLocation())
                : Optional.empty();
        return new AggregateInputSlot(alternatives, tag);
    }
}
