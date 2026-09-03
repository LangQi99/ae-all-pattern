package io.github.langqi99.aeallpattern.diagnostics;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import com.mojang.brigadier.CommandDispatcher;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternKind;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternLibrary;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternOptions;
import io.github.langqi99.aeallpattern.aggregate.AggregateRecipe;
import io.github.langqi99.aeallpattern.binding.BindingSavedData;
import io.github.langqi99.aeallpattern.linker.PatternLinkerBlockEntity;
import io.github.langqi99.aeallpattern.recipe.RecipeIndexService;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.registry.ModItems;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.Locale;
import net.minecraft.core.BlockPos;

public final class ModCommands {
    private ModCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("aeallpattern")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("seed-test-materials")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("linker", BlockPosArgument.blockPos())
                                .executes(context -> seedTestMaterials(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "linker")))))
                .then(Commands.literal("seed-showcase-patterns")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("config_container", BlockPosArgument.blockPos())
                                .then(Commands.argument("route_provider", BlockPosArgument.blockPos())
                                        .executes(context -> seedShowcasePatterns(
                                                context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(
                                                        context, "config_container"),
                                                BlockPosArgument.getLoadedBlockPos(
                                                        context, "route_provider"))))))
                .then(Commands.literal("seed-eco-showcase-pattern")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("pattern_bus", BlockPosArgument.blockPos())
                                .executes(context -> seedEcoShowcasePattern(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pattern_bus")))))
                .then(Commands.literal("verify-eco-showcase-pattern")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("pattern_bus", BlockPosArgument.blockPos())
                                .executes(context -> verifyEcoShowcasePattern(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pattern_bus")))))
                .then(Commands.literal("perf")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> perf(context.getSource()))));
    }

    private static int status(CommandSourceStack source) {
        var records = BindingSavedData.get(source.getServer()).all();
        long visible = source.getPlayer() == null || source.hasPermission(2)
                ? records.size()
                : records.stream().filter(record -> record.ownerId().equals(source.getPlayer().getUUID())).count();
        source.sendSuccess(() -> Component.literal(
                "AE All Pattern: bindings=" + visible + ", recipeGeneration=" + RecipeIndexService.generation()), false);
        return (int) visible;
    }

    private static int perf(CommandSourceStack source) {
        PerformanceMetrics.Snapshot metrics = PerformanceMetrics.snapshot();
        double rebuildMillis = metrics.catalogRebuildNanos() / 1_000_000.0;
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "catalog rebuilds=%d time=%.2fms accepted=%d filtered=%d | provider refreshes=%d diff=%d | pushes accepted=%d rejected=%d | machine input=%d recovered=%d",
                metrics.catalogRebuilds(), rebuildMillis, metrics.recipesAccepted(), metrics.recipesFiltered(),
                metrics.providerRefreshes(), metrics.providerDiffSize(),
                metrics.pushAccepted(), metrics.pushRejected(),
                metrics.machineInputInserted(), metrics.machineOutputRecovered())), false);
        return 1;
    }

    private static int seedTestMaterials(CommandSourceStack source, BlockPos linkerPos) {
        if (!(source.getLevel().getBlockEntity(linkerPos) instanceof PatternLinkerBlockEntity linker)) {
            source.sendFailure(Component.literal("No All Pattern Linker at " + linkerPos.toShortString()));
            return 0;
        }
        if (!linker.getMainNode().isOnline()) {
            source.sendFailure(Component.literal("All Pattern Linker is not online"));
            return 0;
        }

        int inserted = 0;
        for (SeedStack seed : TEST_MATERIALS) {
            var item = BuiltInRegistries.ITEM.getOptional(seed.id());
            if (item.isPresent()) {
                inserted += linker.insertIntoNetwork(
                        new ItemStack(item.orElseThrow(), seed.count()), Actionable.MODULATE);
            }
        }
        var grid = linker.getMainNode().getGrid();
        if (grid != null) {
            grid.getStorageService().invalidateCache();
        }
        int result = inserted;
        source.sendSuccess(() -> Component.literal("Seeded " + result + " test items into the ME network"), false);
        return inserted;
    }

    /** Installs deterministic patterns used only by the reproducible video-showcase world. */
    private static int seedShowcasePatterns(
            CommandSourceStack source,
            net.minecraft.core.BlockPos configContainerPos,
            net.minecraft.core.BlockPos routeProviderPos) {
        if (!(source.getLevel().getBlockEntity(configContainerPos) instanceof Container container)) {
            source.sendFailure(Component.literal("No item container at " + configContainerPos.toShortString()));
            return 0;
        }
        if (!(source.getLevel().getBlockEntity(routeProviderPos) instanceof PatternProviderBlockEntity provider)) {
            source.sendFailure(Component.literal("No AE pattern provider at " + routeProviderPos.toShortString()));
            return 0;
        }

        ItemStack namedEmerald = new ItemStack(Items.EMERALD);
        namedEmerald.set(DataComponents.CUSTOM_NAME, Component.literal("带组件的演示产物"));
        List<AggregateRecipe> optionRecipes = List.of(
                processing("showcase_config_split", Items.DIAMOND, 3, namedEmerald, 1, 0, 40),
                new AggregateRecipe(
                        "showcase_config_input_order",
                        id("showcase_config_input_order"),
                        AggregatePatternKind.PROCESSING,
                        List.of(
                                GenericStack.fromItemStack(new ItemStack(Items.REDSTONE)),
                                GenericStack.fromItemStack(new ItemStack(Items.QUARTZ)),
                                GenericStack.fromItemStack(new ItemStack(Items.DIAMOND))),
                        List.of(GenericStack.fromItemStack(new ItemStack(Items.ENDER_EYE))),
                        40),
                processing("showcase_config_probability_main", Items.IRON_INGOT, 1,
                        new ItemStack(Items.DIAMOND), 1, 1, 80),
                new AggregateRecipe(
                        "showcase_config_probability_byproduct",
                        id("showcase_config_probability_byproduct"),
                        AggregatePatternKind.PROCESSING,
                        List.of(GenericStack.fromItemStack(new ItemStack(Items.RAW_GOLD))),
                        List.of(),
                        List.of(
                                GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT)),
                                GenericStack.fromItemStack(new ItemStack(Items.DIAMOND))),
                        2,
                        60));
        var configRef = AggregatePatternLibrary.get(source.getServer()).put(
                source.getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "pattern_linker"),
                "block.aeallpattern.pattern_linker",
                optionRecipes);
        ItemStack configurable = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        configurable.set(ModDataComponents.AGGREGATE_PATTERN.get(), configRef);
        configurable.set(ModDataComponents.AGGREGATE_PATTERN_OPTIONS.get(), AggregatePatternOptions.DEFAULT);
        container.setItem(0, configurable);
        container.setChanged();

        List<AggregateRecipe> routeRecipes = List.of(
                processing("showcase_route_short", Items.COBBLESTONE, 1,
                        new ItemStack(Items.AMETHYST_SHARD), 1, 0, 20),
                processing("showcase_route_high_yield", Items.DIAMOND, 4,
                        new ItemStack(Items.AMETHYST_SHARD), 8, 0, 200),
                processing("showcase_route_fast", Items.QUARTZ, 2,
                        new ItemStack(Items.AMETHYST_SHARD), 3, 0, 1),
                processing("showcase_route_intermediate", Items.REDSTONE, 1,
                        new ItemStack(Items.ECHO_SHARD), 1, 0, 5),
                processing("showcase_route_long", Items.ECHO_SHARD, 1,
                        new ItemStack(Items.AMETHYST_SHARD), 4, 0, 5));
        var routeRef = AggregatePatternLibrary.get(source.getServer()).put(
                source.getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "tianshu_pattern_selector"),
                "block.aeallpattern.tianshu_pattern_selector",
                routeRecipes);
        ItemStack routes = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        routes.set(ModDataComponents.AGGREGATE_PATTERN.get(), routeRef);

        var patternInventory = provider.getLogic().getPatternInv();
        for (int slot = 0; slot < patternInventory.size(); slot++) {
            patternInventory.setItemDirect(slot, ItemStack.EMPTY);
        }
        patternInventory.setItemDirect(0, routes);
        provider.getLogic().updatePatterns();

        boolean routePublished = provider.getMainNode().getGrid() != null
                && provider.getMainNode().getGrid().getCraftingService()
                        .isCraftable(AEItemKey.of(Items.AMETHYST_SHARD));
        source.sendSuccess(() -> Component.literal(
                "Installed configurable aggregate pattern and five-route showcase pattern"
                        + " (published=" + routePublished + ")"), false);
        return routeRecipes.size();
    }

    /** Inserts a crafting-only aggregate into any AE pattern container, including Neo ECO pattern buses. */
    private static int seedEcoShowcasePattern(
            CommandSourceStack source,
            net.minecraft.core.BlockPos patternBusPos) {
        if (!(source.getLevel().getBlockEntity(patternBusPos) instanceof PatternContainer patternContainer)) {
            source.sendFailure(Component.literal("No AE-compatible pattern container at "
                    + patternBusPos.toShortString()));
            return 0;
        }

        List<AggregateRecipe> craftingRecipes = List.of(
                crafting("eco_oak_planks", "oak_planks", Items.OAK_LOG, 1, Items.OAK_PLANKS, 4),
                crafting("eco_crafting_table", "crafting_table", Items.OAK_PLANKS, 4, Items.CRAFTING_TABLE, 1),
                crafting("eco_stick", "stick", Items.OAK_PLANKS, 2, Items.STICK, 4),
                crafting("eco_chest", "chest", Items.OAK_PLANKS, 8, Items.CHEST, 1));
        var ref = AggregatePatternLibrary.get(source.getServer()).put(
                source.getServer(),
                ResourceLocation.fromNamespaceAndPath("neoecoae", "crafting_pattern_bus"),
                "block.neoecoae.crafting_pattern_bus",
                craftingRecipes);
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN_OPTIONS.get(), AggregatePatternOptions.DEFAULT);

        ItemStack remainder = patternContainer.getTerminalPatternInventory().insertItem(0, aggregate, false);
        if (!remainder.isEmpty()) {
            source.sendFailure(Component.literal("Neo ECO pattern bus rejected the aggregate pattern"));
            AeAllPattern.LOGGER.error("Neo ECO showcase pattern bus at {} rejected the aggregate pattern",
                    patternBusPos.toShortString());
            return 0;
        }
        AeAllPattern.LOGGER.info("Installed crafting aggregate into Neo ECO pattern bus at {} (children={})",
                patternBusPos.toShortString(), craftingRecipes.size());
        source.sendSuccess(() -> Component.literal(
                "Installed crafting aggregate into Neo ECO pattern bus (children="
                        + craftingRecipes.size() + ")"), false);
        return craftingRecipes.size();
    }

    /** Verifies that the ECO host has published every child of the showcase aggregate to AE. */
    private static int verifyEcoShowcasePattern(
            CommandSourceStack source,
            net.minecraft.core.BlockPos patternBusPos) {
        if (!(source.getLevel().getBlockEntity(patternBusPos) instanceof PatternContainer patternContainer)) {
            source.sendFailure(Component.literal("No AE-compatible pattern container at "
                    + patternBusPos.toShortString()));
            return 0;
        }
        var grid = patternContainer.getGrid();
        if (grid == null) {
            source.sendFailure(Component.literal("Neo ECO pattern bus is not connected to an AE grid"));
            AeAllPattern.LOGGER.error("Neo ECO showcase pattern bus at {} is not connected to an AE grid",
                    patternBusPos.toShortString());
            return 0;
        }

        List<net.minecraft.world.item.Item> expectedOutputs = List.of(
                Items.OAK_PLANKS, Items.CRAFTING_TABLE, Items.STICK, Items.CHEST);
        List<String> missing = expectedOutputs.stream()
                .filter(item -> !grid.getCraftingService().isCraftable(AEItemKey.of(item)))
                .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
                .toList();
        if (!missing.isEmpty()) {
            source.sendFailure(Component.literal("Neo ECO did not publish aggregate children: " + missing));
            AeAllPattern.LOGGER.error("Neo ECO aggregate publication verification failed at {}: missing {}",
                    patternBusPos.toShortString(), missing);
            return 0;
        }

        AeAllPattern.LOGGER.info(
                "Verified Neo ECO aggregate publication at {}: oak_planks, crafting_table, stick, chest are craftable",
                patternBusPos.toShortString());
        source.sendSuccess(() -> Component.literal(
                "Verified Neo ECO aggregate: all four child patterns are craftable"), false);
        return expectedOutputs.size();
    }

    private static AggregateRecipe processing(
            String name,
            net.minecraft.world.item.Item input,
            int inputCount,
            ItemStack output,
            int outputCount,
            int probabilisticMask,
            int processingTicks) {
        ItemStack countedOutput = output.copyWithCount(outputCount);
        return new AggregateRecipe(
                name,
                id(name),
                AggregatePatternKind.PROCESSING,
                List.of(GenericStack.fromItemStack(new ItemStack(input, inputCount))),
                List.of(),
                List.of(GenericStack.fromItemStack(countedOutput)),
                probabilisticMask,
                processingTicks);
    }

    private static AggregateRecipe crafting(
            String name,
            String recipePath,
            net.minecraft.world.item.Item input,
            int inputCount,
            net.minecraft.world.item.Item output,
            int outputCount) {
        return new AggregateRecipe(
                name,
                ResourceLocation.withDefaultNamespace(recipePath),
                AggregatePatternKind.CRAFTING,
                List.of(GenericStack.fromItemStack(new ItemStack(input, inputCount))),
                List.of(GenericStack.fromItemStack(new ItemStack(output, outputCount))),
                1);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aeallpattern", path);
    }

    private static final List<SeedStack> TEST_MATERIALS = List.of(
            seed("minecraft:raw_iron", 16),
            seed("minecraft:raw_gold", 16),
            seed("minecraft:raw_copper", 16),
            seed("minecraft:cobblestone", 16),
            seed("minecraft:beef", 16),
            seed("minecraft:potato", 16),
            seed("minecraft:redstone", 16),
            seed("minecraft:quartz", 16),
            seed("minecraft:diamond", 8),
            seed("minecraft:oak_log", 16),
            seed("minecraft:stone_bricks", 16),
            seed("mekanism:raw_osmium", 16),
            seed("mekanism:raw_tin", 16),
            seed("mekanism:raw_lead", 16),
            seed("mysticalagriculture:prosperity_seed_base", 16),
            seed("mysticalagriculture:inferium_essence", 64),
            seed("mysticalagriculture:prudentium_essence", 32),
            seed("mysticalagriculture:tertium_essence", 32));

    private static SeedStack seed(String id, int count) {
        return new SeedStack(ResourceLocation.parse(id), count);
    }

    private record SeedStack(ResourceLocation id, int count) {
    }
}
