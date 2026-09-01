package io.github.langqi99.aeallpattern.aggregate;

import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.langqi99.aeallpattern.recipe.RecipeSnapshot;
import java.util.List;
import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** One concrete native AE2 recipe stored inside an aggregate pattern. */
public record AggregateRecipe(
        String patternId,
        ResourceLocation recipeId,
        AggregatePatternKind kind,
        List<GenericStack> inputs,
        List<AggregateInputSlot> inputSlots,
        List<GenericStack> outputs,
        int probabilisticOutputMask,
        int processingTicks) {
    public static final int MAX_INPUTS = 81;
    public static final int MAX_OUTPUTS = 27;
    public static final int MAX_TOTAL_INPUT_ALTERNATIVES = Integer.MAX_VALUE;
    private static final Codec<List<GenericStack>> INPUTS_CODEC = GenericStack.CODEC.listOf()
            .validate(inputs -> validateStacks(inputs, MAX_INPUTS, "inputs"));
    private static final Codec<List<GenericStack>> OUTPUTS_CODEC = GenericStack.CODEC.listOf()
            .validate(outputs -> validateStacks(outputs, MAX_OUTPUTS, "outputs"));
    private static final Codec<List<AggregateInputSlot>> INPUT_SLOTS_CODEC = AggregateInputSlot.CODEC.listOf()
            .validate(AggregateRecipe::validateInputSlots);

    public static final Codec<AggregateRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("pattern_id").forGetter(AggregateRecipe::patternId),
            ResourceLocation.CODEC.fieldOf("recipe_id").forGetter(AggregateRecipe::recipeId),
            AggregatePatternKind.CODEC.optionalFieldOf("kind", AggregatePatternKind.PROCESSING)
                    .forGetter(AggregateRecipe::kind),
            INPUTS_CODEC.fieldOf("inputs").forGetter(AggregateRecipe::inputs),
            INPUT_SLOTS_CODEC.optionalFieldOf("input_slots", List.of()).forGetter(AggregateRecipe::inputSlots),
            OUTPUTS_CODEC.fieldOf("outputs").forGetter(AggregateRecipe::outputs),
            Codec.INT.optionalFieldOf("probabilistic_output_mask", 0)
                    .forGetter(AggregateRecipe::probabilisticOutputMask),
            Codec.INT.optionalFieldOf("processing_ticks", 1).forGetter(AggregateRecipe::processingTicks)
    ).apply(instance, AggregateRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AggregateRecipe> STREAM_CODEC = StreamCodec.of(
            AggregateRecipe::encode, AggregateRecipe::decode);

    public AggregateRecipe {
        if (patternId == null || patternId.isBlank() || patternId.length() > 160) {
            throw new IllegalArgumentException("invalid aggregate pattern id");
        }
        inputs = copyAndValidate(inputs, MAX_INPUTS, "inputs");
        inputSlots = inputSlots == null || inputSlots.isEmpty()
                ? inputs.stream().map(AggregateInputSlot::exact).toList()
                : copyAndValidateSlots(inputSlots);
        inputs = inputSlots.stream().map(AggregateInputSlot::primary).toList();
        outputs = copyAndValidate(outputs, MAX_OUTPUTS, "outputs");
        probabilisticOutputMask &= (1 << outputs.size()) - 1;
        processingTicks = Math.max(1, processingTicks);
    }

    /** Compatibility constructor for callers that do not provide probability metadata. */
    public AggregateRecipe(
            String patternId,
            ResourceLocation recipeId,
            AggregatePatternKind kind,
            List<GenericStack> inputs,
            List<AggregateInputSlot> inputSlots,
            List<GenericStack> outputs,
            int processingTicks) {
        this(patternId, recipeId, kind, inputs, inputSlots, outputs, 0, processingTicks);
    }

    /** Compatibility constructor for existing processing-only callers and saved data. */
    public AggregateRecipe(
            String patternId,
            ResourceLocation recipeId,
            AggregatePatternKind kind,
            List<GenericStack> inputs,
            List<GenericStack> outputs,
            int processingTicks) {
        this(patternId, recipeId, kind, inputs, List.of(), outputs, 0, processingTicks);
    }

    /** Compatibility constructor for existing processing-only callers and saved data. */
    public AggregateRecipe(
            String patternId,
            ResourceLocation recipeId,
            List<GenericStack> inputs,
            List<GenericStack> outputs,
            int processingTicks) {
        this(patternId, recipeId, AggregatePatternKind.PROCESSING, inputs, List.of(), outputs, 0, processingTicks);
    }

    public static AggregateRecipe from(RecipeSnapshot snapshot) {
        List<AggregateInputSlot> slots = snapshot.inputAlternatives().stream()
                .map(alternatives -> new AggregateInputSlot(
                        alternatives.stream().map(GenericStack::fromItemStack).toList(),
                        java.util.Optional.empty()))
                .toList();
        return new AggregateRecipe(
                snapshot.fingerprint().stableKey(),
                snapshot.recipeId(),
                AggregatePatternKind.PROCESSING,
                slots.stream().map(AggregateInputSlot::primary).toList(),
                slots,
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(snapshot.output()))),
                0,
                snapshot.processingTicks());
    }

    public boolean isProbabilisticOutput(int index) {
        return index >= 0 && index < outputs.size() && (probabilisticOutputMask & (1 << index)) != 0;
    }

    private static DataResult<List<GenericStack>> validateStacks(
            List<GenericStack> stacks, int maximum, String name) {
        if (stacks.isEmpty() || stacks.size() > maximum) {
            return DataResult.error(() -> "aggregate recipe must have 1-" + maximum + " " + name);
        }
        if (stacks.stream().anyMatch(stack -> stack == null || stack.what() == null || stack.amount() <= 0)) {
            return DataResult.error(() -> "aggregate recipe " + name + " must be non-empty");
        }
        return DataResult.success(stacks);
    }

    private static DataResult<List<AggregateInputSlot>> validateInputSlots(List<AggregateInputSlot> slots) {
        try {
            return DataResult.success(copyAndValidateSlots(slots));
        } catch (IllegalArgumentException error) {
            return DataResult.error(error::getMessage);
        }
    }

    private static List<AggregateInputSlot> copyAndValidateSlots(List<AggregateInputSlot> slots) {
        if (slots == null || slots.isEmpty() || slots.size() > MAX_INPUTS) {
            throw new IllegalArgumentException("aggregate recipe must have 1-" + MAX_INPUTS + " input slots");
        }
        List<AggregateInputSlot> result = List.copyOf(slots);
        if (result.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("aggregate recipe input slots must be non-empty");
        }
        long totalAlternatives = result.stream().mapToLong(slot -> slot.alternatives().size()).sum();
        if (totalAlternatives > MAX_TOTAL_INPUT_ALTERNATIVES) {
            throw new IllegalArgumentException("aggregate recipe has too many explicit input alternatives");
        }
        return result;
    }

    private static List<GenericStack> copyAndValidate(
            List<GenericStack> stacks, int maximum, String name) {
        if (stacks == null || stacks.isEmpty() || stacks.size() > maximum) {
            throw new IllegalArgumentException("aggregate recipe must have 1-" + maximum + " " + name);
        }
        List<GenericStack> result = List.copyOf(stacks);
        if (result.stream().anyMatch(stack -> stack == null || stack.what() == null || stack.amount() <= 0)) {
            throw new IllegalArgumentException("aggregate recipe " + name + " must be non-empty");
        }
        return result;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, AggregateRecipe recipe) {
        buffer.writeUtf(recipe.patternId(), 160);
        buffer.writeResourceLocation(recipe.recipeId());
        buffer.writeEnum(recipe.kind());
        buffer.writeVarInt(recipe.inputSlots.size());
        recipe.inputSlots.forEach(slot -> AggregateInputSlot.STREAM_CODEC.encode(buffer, slot));
        buffer.writeVarInt(recipe.outputs.size());
        recipe.outputs.forEach(stack -> GenericStack.STREAM_CODEC.encode(buffer, stack));
        buffer.writeVarInt(recipe.probabilisticOutputMask);
        buffer.writeVarInt(recipe.processingTicks());
    }

    private static AggregateRecipe decode(RegistryFriendlyByteBuf buffer) {
        String patternId = buffer.readUtf(160);
        ResourceLocation recipeId = buffer.readResourceLocation();
        AggregatePatternKind kind = buffer.readEnum(AggregatePatternKind.class);
        int inputCount = checkedCount(buffer.readVarInt(), MAX_INPUTS, "input");
        List<AggregateInputSlot> inputSlots = java.util.stream.IntStream.range(0, inputCount)
                .mapToObj(index -> AggregateInputSlot.STREAM_CODEC.decode(buffer)).toList();
        int outputCount = checkedCount(buffer.readVarInt(), MAX_OUTPUTS, "output");
        List<GenericStack> outputs = java.util.stream.IntStream.range(0, outputCount)
                .mapToObj(index -> GenericStack.STREAM_CODEC.decode(buffer)).toList();
        int probabilisticOutputMask = buffer.readVarInt();
        return new AggregateRecipe(
                patternId, recipeId, kind,
                inputSlots.stream().map(AggregateInputSlot::primary).toList(),
                inputSlots, outputs, probabilisticOutputMask, buffer.readVarInt());
    }

    private static int checkedCount(int count, int maximum, String name) {
        if (count < 1 || count > maximum) {
            throw new IllegalArgumentException("invalid aggregate " + name + " count: " + count);
        }
        return count;
    }

    /**
     * Upper-bound estimate of this recipe's on-wire size (registry-friendly buffer), used to
     * split client upload pages below the protocol packet limit regardless of recipe
     * complexity. Deliberately conservative: it must never under-estimate.
     */
    public int encodedSizeEstimate() {
        int size = utfSize(patternId);
        size += resourceLocationSize(recipeId);
        size += 1; // kind enum
        size += varIntSize(inputSlots.size());
        for (AggregateInputSlot slot : inputSlots) {
            size += varIntSize(slot.alternatives().size());
            for (GenericStack stack : slot.alternatives()) {
                size += genericStackSize(stack);
            }
            size += 1; // item tag presence
            if (slot.itemTag().isPresent()) {
                size += resourceLocationSize(slot.itemTag().orElseThrow());
            }
        }
        size += varIntSize(outputs.size());
        for (GenericStack stack : outputs) {
            size += genericStackSize(stack);
        }
        size += varIntSize(probabilisticOutputMask);
        size += varIntSize(processingTicks);
        return size;
    }

    private static int utfSize(String text) {
        return varIntSize(text.length()) + text.length();
    }

    private static int resourceLocationSize(ResourceLocation id) {
        return utfSize(id.getNamespace()) + utfSize(id.getPath());
    }

    private static int varIntSize(long value) {
        int size = 1;
        while (value >= 0x80) {
            value >>>= 7;
            size++;
        }
        return size;
    }

    private static int genericStackSize(GenericStack stack) {
        appeng.api.stacks.AEKey key = stack.what();
        // key type id + key id + a generous pad for AEItemKey components / NBT
        // (unbounded in size) + amount varLong + count int.
        return resourceLocationSize(key.getType().getId()) + resourceLocationSize(key.getId())
                + 192 + varIntSize(stack.amount());
    }
}
