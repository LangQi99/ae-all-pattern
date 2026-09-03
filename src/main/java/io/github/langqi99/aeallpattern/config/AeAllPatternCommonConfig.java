package io.github.langqi99.aeallpattern.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AeAllPatternCommonConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue LINKER_MAX_BINDING_DISTANCE;
    public static final ModConfigSpec.BooleanValue LINKER_ALLOW_CROSS_DIMENSION;
    public static final ModConfigSpec.IntValue SELECTION_DISPLAY_LIMIT;
    public static final ModConfigSpec.IntValue AGGREGATE_RECIPE_LIMIT;
    public static final ModConfigSpec.IntValue TAG_EXPANSION_LIMIT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
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
                .defineInRange("singlePatternRecipeLimit", 1_048_576, 1, 1_048_576);
        TAG_EXPANSION_LIMIT = builder
                .comment("Maximum number of item alternatives expanded from one recipe ingredient tag.")
                .defineInRange("tagExpansionLimit", 1024, 1, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private AeAllPatternCommonConfig() {
    }

    public static double maxBindingDistanceSquared() {
        double distance = LINKER_MAX_BINDING_DISTANCE.getAsInt();
        return distance == 0 ? Double.POSITIVE_INFINITY : distance * distance;
    }
}
