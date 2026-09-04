package io.github.langqi99.aeallpattern.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class AeAllPatternCommonConfig {
    public static final int MIN_AGGREGATE_RECIPE_LIMIT = 1;
    public static final int MAX_AGGREGATE_RECIPE_LIMIT = 1_048_576;
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue LINKER_MAX_BINDING_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue LINKER_ALLOW_CROSS_DIMENSION;
    public static final ForgeConfigSpec.IntValue SELECTION_DISPLAY_LIMIT;
    public static final ForgeConfigSpec.IntValue AGGREGATE_RECIPE_LIMIT;
    public static final ForgeConfigSpec.IntValue TAG_EXPANSION_LIMIT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("patternLinker");
        LINKER_MAX_BINDING_DISTANCE = builder
                .comment("Maximum same-dimension distance in blocks between a linker and a bound machine. 0 is unlimited.")
                .defineInRange("maxBindingDistance", 0, 0, 30_000_000);
        LINKER_ALLOW_CROSS_DIMENSION = builder
                .comment("Whether a linker may bind machines in another dimension. Cross-dimension bindings ignore distance.")
                .define("allowCrossDimension", true);
        builder.pop();
        builder.push("patternSelection");
        SELECTION_DISPLAY_LIMIT = builder.defineInRange("displayLimit", 1024, 1, 16384);
        builder.pop();
        builder.push("aggregatePattern");
        AGGREGATE_RECIPE_LIMIT = builder
                .comment("Maximum number of recipes stored in a newly generated aggregate pattern.")
                // This key intentionally differs from the old per-part limit. Existing 0.2.1
                // configs must receive the new single-item default instead of retaining 16384.
                .defineInRange(
                        "singlePatternRecipeLimit",
                        MAX_AGGREGATE_RECIPE_LIMIT,
                        MIN_AGGREGATE_RECIPE_LIMIT,
                        MAX_AGGREGATE_RECIPE_LIMIT);
        TAG_EXPANSION_LIMIT = builder
                .comment("Maximum number of item alternatives expanded from one recipe ingredient tag.")
                .defineInRange("tagExpansionLimit", 1024, 1, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private AeAllPatternCommonConfig() {
    }

    public static double maxBindingDistanceSquared() {
        double distance = LINKER_MAX_BINDING_DISTANCE.get();
        return distance == 0 ? Double.POSITIVE_INFINITY : distance * distance;
    }

    public static boolean isAggregateRecipeLimitValid(int value) {
        return value >= MIN_AGGREGATE_RECIPE_LIMIT && value <= MAX_AGGREGATE_RECIPE_LIMIT;
    }
}
