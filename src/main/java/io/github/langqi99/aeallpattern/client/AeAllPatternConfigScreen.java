package io.github.langqi99.aeallpattern.client;

import io.github.langqi99.aeallpattern.config.AeAllPatternCommonConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.client.ConfigScreenHandler;

public final class AeAllPatternConfigScreen {
    private AeAllPatternConfigScreen() {
    }

    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(AeAllPatternConfigScreen::create));
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.aeallpattern.title"));
        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory linker = builder.getOrCreateCategory(
                Component.translatable("config.aeallpattern.pattern_linker"));

        linker.addEntry(entries.startIntField(
                        Component.translatable("config.aeallpattern.max_binding_distance"),
                        AeAllPatternCommonConfig.LINKER_MAX_BINDING_DISTANCE.get())
                .setDefaultValue(0)
                .setMin(0)
                .setMax(30_000_000)
                .setTooltip(Component.translatable("config.aeallpattern.max_binding_distance.tooltip"))
                .setSaveConsumer(AeAllPatternCommonConfig.LINKER_MAX_BINDING_DISTANCE::set)
                .build());
        linker.addEntry(entries.startBooleanToggle(
                        Component.translatable("config.aeallpattern.allow_cross_dimension"),
                        AeAllPatternCommonConfig.LINKER_ALLOW_CROSS_DIMENSION.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.aeallpattern.allow_cross_dimension.tooltip"))
                .setSaveConsumer(AeAllPatternCommonConfig.LINKER_ALLOW_CROSS_DIMENSION::set)
                .build());

        ConfigCategory selection = builder.getOrCreateCategory(Component.translatable("config.aeallpattern.pattern_selection"));
        selection.addEntry(entries.startIntField(Component.translatable("config.aeallpattern.selection_display_limit"), AeAllPatternCommonConfig.SELECTION_DISPLAY_LIMIT.get())
                .setDefaultValue(1024).setMin(1).setMax(16384)
                .setTooltip(Component.translatable("config.aeallpattern.selection_display_limit.tooltip"))
                .setSaveConsumer(AeAllPatternCommonConfig.SELECTION_DISPLAY_LIMIT::set).build());

        ConfigCategory aggregate = builder.getOrCreateCategory(
                Component.translatable("config.aeallpattern.aggregate_pattern"));
        aggregate.addEntry(entries.startIntField(
                        Component.translatable("config.aeallpattern.aggregate_recipe_limit"),
                        AeAllPatternCommonConfig.AGGREGATE_RECIPE_LIMIT.get())
                .setDefaultValue(1_048_576)
                .setMin(1)
                .setMax(1_048_576)
                .setTooltip(Component.translatable("config.aeallpattern.aggregate_recipe_limit.tooltip"))
                .setSaveConsumer(AeAllPatternCommonConfig.AGGREGATE_RECIPE_LIMIT::set)
                .build());
        aggregate.addEntry(entries.startIntField(
                        Component.translatable("config.aeallpattern.tag_expansion_limit"),
                        AeAllPatternCommonConfig.TAG_EXPANSION_LIMIT.get())
                .setDefaultValue(1024)
                .setMin(1)
                .setMax(Integer.MAX_VALUE)
                .setTooltip(Component.translatable("config.aeallpattern.tag_expansion_limit.tooltip"))
                .setSaveConsumer(AeAllPatternCommonConfig.TAG_EXPANSION_LIMIT::set)
                .build());
        builder.setSavingRunnable(AeAllPatternCommonConfig.SPEC::save);
        return builder.build();
    }
}
