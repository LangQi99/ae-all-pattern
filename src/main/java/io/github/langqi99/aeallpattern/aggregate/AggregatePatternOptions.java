package io.github.langqi99.aeallpattern.aggregate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Per-item encoding preferences for one aggregate pattern. */
public record AggregatePatternOptions(
        boolean splitSameItems,
        boolean ignoreOutputComponents,
        boolean skipProbabilisticMainOutput,
        boolean ignoreProbabilisticByproducts,
        boolean removeProcessingCatalysts,
        boolean allowItemSubstitutions,
        boolean allowFluidSubstitutions,
        boolean removeInputFluids,
        boolean removeOutputFluids,
        boolean removeInputChemicals,
        boolean removeOutputChemicals,
        boolean swapFirstAndLastInputs,
        boolean skipDurabilityConsumingRecipes) {
    public static final AggregatePatternOptions DEFAULT =
            new AggregatePatternOptions(
                    false, true, true, true, false, true, true,
                    false, false, false, false, false, true);

    /** Compatibility constructor; newly introduced probability safeguards default to enabled. */
    public AggregatePatternOptions(boolean splitSameItems, boolean ignoreOutputComponents) {
        this(splitSameItems, ignoreOutputComponents, true, true, false, false, true,
                false, false, false, false, false, true);
    }

    /** Compatibility constructor for patterns saved before catalyst filtering was added. */
    public AggregatePatternOptions(
            boolean splitSameItems,
            boolean ignoreOutputComponents,
            boolean skipProbabilisticMainOutput,
            boolean ignoreProbabilisticByproducts) {
        this(splitSameItems, ignoreOutputComponents, skipProbabilisticMainOutput,
                ignoreProbabilisticByproducts, false, false, true,
                false, false, false, false, false, true);
    }

    /** Compatibility constructor for patterns saved before AE2 substitution controls were added. */
    public AggregatePatternOptions(
            boolean splitSameItems,
            boolean ignoreOutputComponents,
            boolean skipProbabilisticMainOutput,
            boolean ignoreProbabilisticByproducts,
            boolean removeProcessingCatalysts) {
        this(splitSameItems, ignoreOutputComponents, skipProbabilisticMainOutput,
                ignoreProbabilisticByproducts, removeProcessingCatalysts, false, true,
                false, false, false, false, false, true);
    }

    /** Compatibility constructor for patterns saved before fluid and chemical filters were added. */
    public AggregatePatternOptions(
            boolean splitSameItems,
            boolean ignoreOutputComponents,
            boolean skipProbabilisticMainOutput,
            boolean ignoreProbabilisticByproducts,
            boolean removeProcessingCatalysts,
            boolean allowItemSubstitutions,
            boolean allowFluidSubstitutions) {
        this(splitSameItems, ignoreOutputComponents, skipProbabilisticMainOutput,
                ignoreProbabilisticByproducts, removeProcessingCatalysts,
                allowItemSubstitutions, allowFluidSubstitutions,
                false, false, false, false, false, true);
    }

    /** Compatibility constructor for patterns saved before durability-recipe filtering was added. */
    public AggregatePatternOptions(
            boolean splitSameItems,
            boolean ignoreOutputComponents,
            boolean skipProbabilisticMainOutput,
            boolean ignoreProbabilisticByproducts,
            boolean removeProcessingCatalysts,
            boolean allowItemSubstitutions,
            boolean allowFluidSubstitutions,
            boolean removeInputFluids,
            boolean removeOutputFluids,
            boolean removeInputChemicals,
            boolean removeOutputChemicals,
            boolean swapFirstAndLastInputs) {
        this(splitSameItems, ignoreOutputComponents, skipProbabilisticMainOutput,
                ignoreProbabilisticByproducts, removeProcessingCatalysts,
                allowItemSubstitutions, allowFluidSubstitutions,
                removeInputFluids, removeOutputFluids, removeInputChemicals,
                removeOutputChemicals, swapFirstAndLastInputs, true);
    }

    public static final Codec<AggregatePatternOptions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("split_same_items", false)
                    .forGetter(AggregatePatternOptions::splitSameItems),
            Codec.BOOL.optionalFieldOf("ignore_output_components", true)
                    .forGetter(AggregatePatternOptions::ignoreOutputComponents),
            Codec.BOOL.optionalFieldOf("skip_probabilistic_main_output", true)
                    .forGetter(AggregatePatternOptions::skipProbabilisticMainOutput),
            Codec.BOOL.optionalFieldOf("ignore_probabilistic_byproducts", true)
                    .forGetter(AggregatePatternOptions::ignoreProbabilisticByproducts),
            Codec.BOOL.optionalFieldOf("remove_processing_catalysts", false)
                    .forGetter(AggregatePatternOptions::removeProcessingCatalysts),
            Codec.BOOL.optionalFieldOf("allow_item_substitutions", true)
                    .forGetter(AggregatePatternOptions::allowItemSubstitutions),
            Codec.BOOL.optionalFieldOf("allow_fluid_substitutions", true)
                    .forGetter(AggregatePatternOptions::allowFluidSubstitutions),
            Codec.BOOL.optionalFieldOf("remove_input_fluids", false)
                    .forGetter(AggregatePatternOptions::removeInputFluids),
            Codec.BOOL.optionalFieldOf("remove_output_fluids", false)
                    .forGetter(AggregatePatternOptions::removeOutputFluids),
            Codec.BOOL.optionalFieldOf("remove_input_chemicals", false)
                    .forGetter(AggregatePatternOptions::removeInputChemicals),
            Codec.BOOL.optionalFieldOf("remove_output_chemicals", false)
                    .forGetter(AggregatePatternOptions::removeOutputChemicals),
            Codec.BOOL.optionalFieldOf("swap_first_and_last_inputs", false)
                    .forGetter(AggregatePatternOptions::swapFirstAndLastInputs),
            Codec.BOOL.optionalFieldOf("skip_durability_consuming_recipes", true)
                    .forGetter(AggregatePatternOptions::skipDurabilityConsumingRecipes)
    ).apply(instance, AggregatePatternOptions::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AggregatePatternOptions> STREAM_CODEC = StreamCodec.of(
            (buffer, options) -> buffer.writeVarInt(options.flags()),
            buffer -> fromFlags(buffer.readVarInt()));

    public int flags() {
        return (splitSameItems ? 1 : 0)
                | (ignoreOutputComponents ? 2 : 0)
                | (skipProbabilisticMainOutput ? 4 : 0)
                | (ignoreProbabilisticByproducts ? 8 : 0)
                | (removeProcessingCatalysts ? 16 : 0)
                | (allowItemSubstitutions ? 32 : 0)
                | (allowFluidSubstitutions ? 64 : 0)
                | (removeInputFluids ? 128 : 0)
                | (removeOutputFluids ? 256 : 0)
                | (removeInputChemicals ? 512 : 0)
                | (removeOutputChemicals ? 1024 : 0)
                | (swapFirstAndLastInputs ? 2048 : 0)
                | (skipDurabilityConsumingRecipes ? 4096 : 0);
    }

    public static AggregatePatternOptions fromFlags(int flags) {
        return new AggregatePatternOptions(
                (flags & 1) != 0,
                (flags & 2) != 0,
                (flags & 4) != 0,
                (flags & 8) != 0,
                (flags & 16) != 0,
                (flags & 32) != 0,
                (flags & 64) != 0,
                (flags & 128) != 0,
                (flags & 256) != 0,
                (flags & 512) != 0,
                (flags & 1024) != 0,
                (flags & 2048) != 0,
                (flags & 4096) != 0);
    }
}
