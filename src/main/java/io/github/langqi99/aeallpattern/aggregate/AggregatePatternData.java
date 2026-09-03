package io.github.langqi99.aeallpattern.aggregate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.langqi99.aeallpattern.config.AeAllPatternCommonConfig;
import io.github.langqi99.aeallpattern.machine.MachineAdapter;
import io.github.langqi99.aeallpattern.recipe.RecipeCatalog;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import java.util.ArrayList;

/** Temporary capture model used before recipes are written into paged server storage. */
public record AggregatePatternData(
        int schemaVersion,
        ResourceLocation adapterId,
        String machineTranslationKey,
        List<AggregateRecipe> recipes) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Absolute number of recipes stored by one physical aggregate-pattern item. */
    public static final int MAX_RECIPES = 16384;

    private static final Codec<List<AggregateRecipe>> RECIPES_CODEC = AggregateRecipe.CODEC.listOf()
            .validate(recipes -> recipes.isEmpty() || recipes.size() > MAX_RECIPES
                    ? DataResult.error(() -> "aggregate recipe count must be between 1 and " + MAX_RECIPES)
                    : DataResult.success(recipes));

    public static final Codec<AggregatePatternData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", CURRENT_SCHEMA_VERSION)
                    .forGetter(AggregatePatternData::schemaVersion),
            ResourceLocation.CODEC.fieldOf("adapter_id").forGetter(AggregatePatternData::adapterId),
            Codec.STRING.fieldOf("machine_translation_key").forGetter(AggregatePatternData::machineTranslationKey),
            RECIPES_CODEC.fieldOf("recipes").forGetter(AggregatePatternData::recipes)
    ).apply(instance, AggregatePatternData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AggregatePatternData> STREAM_CODEC = StreamCodec.of(
            AggregatePatternData::encode,
            AggregatePatternData::decode);

    public AggregatePatternData {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported aggregate pattern schema: " + schemaVersion);
        }
        if (machineTranslationKey == null || machineTranslationKey.isBlank() || machineTranslationKey.length() > 256) {
            throw new IllegalArgumentException("invalid machine translation key");
        }
        recipes = List.copyOf(recipes);
        if (recipes.isEmpty() || recipes.size() > MAX_RECIPES) {
            throw new IllegalArgumentException("invalid aggregate recipe count: " + recipes.size());
        }
    }

    public static AggregatePatternData capture(
            BlockEntity target, MachineAdapter adapter, RecipeCatalog catalog) {
        return new AggregatePatternData(
                CURRENT_SCHEMA_VERSION,
                adapter.id(),
                target.getBlockState().getBlock().getDescriptionId(),
                catalog.recipes().stream()
                        .limit(configuredRecipeLimit())
                        .map(AggregateRecipe::from)
                        .toList());
    }

    public static AggregatePatternData captureJei(
            String machineTranslationKey, List<AggregateRecipe> recipes) {
        return new AggregatePatternData(
                CURRENT_SCHEMA_VERSION,
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "jei"),
                machineTranslationKey,
                recipes.stream().limit(configuredRecipeLimit()).toList());
    }

    public static int configuredRecipeLimit() {
        return Math.min(MAX_RECIPES, AeAllPatternCommonConfig.AGGREGATE_RECIPE_LIMIT.getAsInt());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, AggregatePatternData data) {
        buffer.writeVarInt(data.schemaVersion());
        buffer.writeResourceLocation(data.adapterId());
        buffer.writeUtf(data.machineTranslationKey(), 256);
        buffer.writeVarInt(data.recipes().size());
        for (AggregateRecipe recipe : data.recipes()) {
            AggregateRecipe.STREAM_CODEC.encode(buffer, recipe);
        }
    }

    private static AggregatePatternData decode(RegistryFriendlyByteBuf buffer) {
        int schema = buffer.readVarInt();
        ResourceLocation adapterId = buffer.readResourceLocation();
        String machineKey = buffer.readUtf(256);
        int count = checkedCount(buffer.readVarInt(), 1, MAX_RECIPES, "recipe");
        List<AggregateRecipe> recipes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            recipes.add(AggregateRecipe.STREAM_CODEC.decode(buffer));
        }
        return new AggregatePatternData(schema, adapterId, machineKey, recipes);
    }

    private static int checkedCount(int count, int minimum, int maximum, String kind) {
        if (count < minimum || count > maximum) {
            throw new IllegalArgumentException("invalid aggregate " + kind + " count: " + count);
        }
        return count;
    }
}
