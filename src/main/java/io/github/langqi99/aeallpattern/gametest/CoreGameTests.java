package io.github.langqi99.aeallpattern.gametest;

import appeng.api.networking.GridFlags;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.ids.AEComponents;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.EncodedCraftingPattern;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternData;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternConfigMenu;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternDecoder;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternEditPolicy;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternExpander;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternMarkerDetails;
import io.github.langqi99.aeallpattern.aggregate.AggregateInputSlot;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternKind;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternLibrary;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternOptions;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternRef;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternSelection;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternSelectionMenu;
import io.github.langqi99.aeallpattern.aggregate.AggregateRecipe;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.binding.BindingSavedData;
import io.github.langqi99.aeallpattern.binding.BindingRecord;
import io.github.langqi99.aeallpattern.linker.IncomingBuffer;
import io.github.langqi99.aeallpattern.linker.PatternLinkerBlockEntity;
import io.github.langqi99.aeallpattern.machine.MachineAdapterRegistry;
import io.github.langqi99.aeallpattern.recipe.RecipeIndexService;
import io.github.langqi99.aeallpattern.recipe.RecipeFingerprint;
import io.github.langqi99.aeallpattern.recipe.RecipeSnapshot;
import io.github.langqi99.aeallpattern.registry.ModBlocks;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.registry.ModItems;
import io.github.langqi99.aeallpattern.tianshu.TianshuPatternSelectorBlock;
import io.github.langqi99.aeallpattern.tianshu.TianshuPatternSelectorBlockEntity;
import io.github.langqi99.aeallpattern.tianshu.TianshuRoutingPolicies;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.ByproductPlanWarnings;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.CraftingRoutePolicy;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.CraftingRoutePolicyContext;

import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("DataFlowIssue")
@GameTestHolder(AeAllPattern.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreGameTests {
    static {
        // GameTests assert full publication immediately after updatePatterns, so the scheduled
        // expansion path must behave synchronously here. The scheduled-modes test toggles it.
        AggregatePatternExpander.setSynchronous(true);
    }

    private CoreGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void ciDependencyProfileIsActuallyLoaded(GameTestHelper helper) {
        for (String modId : configuredModIds("aeallpattern.expectedTestMods")) {
            helper.assertTrue(ModList.get().isLoaded(modId),
                    "CI profile expected mod '" + modId + "' but it was not loaded");
        }
        for (String modId : configuredModIds("aeallpattern.forbiddenTestMods")) {
            helper.assertFalse(ModList.get().isLoaded(modId),
                    "CI profile forbids mod '" + modId + "' but it was loaded");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void loadedCompatibilityMixinTargetsTransformCleanly(GameTestHelper helper) {
        Map<String, List<String>> targets = Map.ofEntries(
                Map.entry("advanced_ae", List.of(
                        "net.pedroksl.advanced_ae.gui.AdvPatternEncoderMenu",
                        "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic")),
                Map.entry("ae2cs", List.of(
                        "io.github.lounode.ae2cs.common.me.logic.IntegratedInterfaceLogic",
                        "io.github.lounode.ae2cs.common.me.logic.MirrorPatternProviderLogic")),
                Map.entry("neoecoae", List.of(
                        "cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity")),
                Map.entry("extendedae", List.of(
                        "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern",
                        "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern$Filter")),
                Map.entry("extendedae_plus", List.of(
                        "com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity$Filter",
                        "com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCluster")),
                Map.entry("ae2lt", List.of(
                        "com.moakiee.ae2lt.blockentity.MatrixPatternStorageBlockEntity",
                        "com.moakiee.ae2lt.blockentity.PigmeePatternProviderBlockEntity",
                        "com.moakiee.ae2lt.logic.OverloadedProviderPatternCatalog")),
                Map.entry("ae2ltpp", List.of(
                        "com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderLogic")),
                Map.entry("packagedauto", List.of(
                        "thelm.packagedauto.integration.appeng.blockentity.AEPackagingProviderBlockEntity",
                        "thelm.packagedauto.inventory.PackagingProviderItemHandler")),
                Map.entry("useless_mod", List.of(
                        "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnaceAeManager")));

        ClassLoader loader = CoreGameTests.class.getClassLoader();
        for (Map.Entry<String, List<String>> entry : targets.entrySet()) {
            if (!ModList.get().isLoaded(entry.getKey())) {
                continue;
            }
            for (String className : entry.getValue()) {
                try {
                    Class.forName(className, false, loader);
                } catch (ClassNotFoundException | LinkageError error) {
                    helper.fail("Compatibility target failed to load for " + entry.getKey()
                            + ": " + className + " (" + error + ")");
                    return;
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void linkerCreatesChannelNode(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.PATTERN_LINKER.get());
        helper.runAfterDelay(2, () -> {
            PatternLinkerBlockEntity linker = Objects.requireNonNull(helper.getBlockEntity(pos),
                    "linker block entity was not created");
            helper.assertTrue(linker.getMainNode().getNode() != null, "managed grid node was not created");
            helper.assertTrue(linker.getMainNode().getNode().hasFlag(GridFlags.REQUIRE_CHANNEL),
                    "linker must consume a channel");
            helper.assertValueEqual(linker.getMainNode().getNode().getIdlePowerUsage(), 2.0,
                    "unexpected idle power usage");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void linkerPatternOptionsPersistAndMenuToggles(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.PATTERN_LINKER.get());
        PatternLinkerBlockEntity linker = Objects.requireNonNull(helper.getBlockEntity(pos));
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setPos(linker.getBlockPos().getCenter());
        linker.setOwner(player);

        AggregatePatternConfigMenu menu = new AggregatePatternConfigMenu(
                1, player.getInventory(), linker.getBlockPos());
        helper.assertTrue(menu.stillValid(player), "owner could not configure a nearby linker");
        helper.assertTrue(menu.clickMenuButton(
                        player, AggregatePatternConfigMenu.TOGGLE_REMOVE_INPUT_FLUIDS),
                "linker option button was rejected");
        helper.assertTrue(linker.getPatternOptions().removeInputFluids(),
                "linker did not store the configured aggregate option");

        var saved = linker.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity restored = BlockEntity.loadStatic(
                linker.getBlockPos(), helper.getBlockState(pos), saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored instanceof PatternLinkerBlockEntity restoredLinker
                        && restoredLinker.getPatternOptions().removeInputFluids(),
                "linker pattern options did not survive block entity persistence");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void tianshuRouterPublishesRoutingWithoutCraftingCpu(GameTestHelper helper) {
        BlockPos selectorPos = new BlockPos(1, 1, 1);
        BlockPos energyPos = new BlockPos(1, 1, 2);
        helper.setBlock(selectorPos, ModBlocks.TIANSHU_PATTERN_SELECTOR.get());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block());

        helper.runAfterDelay(10, () -> {
            TianshuPatternSelectorBlockEntity selector = Objects.requireNonNull(helper.getBlockEntity(selectorPos),
                    "Tianshu router block entity was not created");
            helper.assertTrue(selector.isRouterOnline(), "powered Tianshu router did not come online");
            helper.assertTrue(
                    Objects.requireNonNull(selector.getGrid()).getCraftingService().getCpus().isEmpty(),
                    "Tianshu router must not register as an AE crafting CPU");
            helper.assertTrue(
                    TianshuRoutingPolicies.isAvailable(Objects.requireNonNull(selector.getGrid())),
                    "online Tianshu router was not discoverable by route planning");
            helper.assertTrue(
                    helper.getBlockState(selectorPos).getValue(TianshuPatternSelectorBlock.ACTIVE),
                    "online Tianshu router did not switch to its active model");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void tianshuRouterSupportsVerticalFacing(GameTestHelper helper) {
        var base = ModBlocks.TIANSHU_PATTERN_SELECTOR.get().defaultBlockState();
        helper.assertTrue(base.setValue(TianshuPatternSelectorBlock.FACING, Direction.UP)
                        .getValue(TianshuPatternSelectorBlock.FACING) == Direction.UP,
                "Tianshu router rejected upward facing");
        helper.assertTrue(base.setValue(TianshuPatternSelectorBlock.FACING, Direction.DOWN)
                        .getValue(TianshuPatternSelectorBlock.FACING) == Direction.DOWN,
                "Tianshu router rejected downward facing");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void byproductOrdersRequireQualificationAndPublishWarning(GameTestHelper helper) {
        BlockPos routerPos = new BlockPos(1, 1, 1);
        BlockPos energyPos = new BlockPos(1, 1, 2);
        helper.setBlock(routerPos, ModBlocks.TIANSHU_PATTERN_SELECTOR.get());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block());

        helper.runAfterDelay(10, () -> {
            TianshuPatternSelectorBlockEntity router = Objects.requireNonNull(helper.getBlockEntity(routerPos));
            helper.assertTrue(router.isRouterOnline(),
                    "powered Tianshu router did not come online");
            ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                    List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.COBBLESTONE)))),
                    List.of(
                            Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.DIAMOND))),
                            Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.EMERALD)))));
            IPatternDetails details = Objects.requireNonNull(
                    PatternDetailsHelper.decodePattern(encoded, helper.getLevel()),
                    "test processing pattern could not be decoded");

            var service = getService(details, router);
            helper.assertFalse(service.isCraftable(AEItemKey.of(Items.EMERALD)),
                    "secondary output was exposed while the qualification switch was disabled");

            ICraftingSimulationRequester requester = new ICraftingSimulationRequester() {
                @Override
                public IActionSource getActionSource() {
                    return IActionSource.ofMachine(router);
                }

                @Override
                public IGridNode getGridNode() {
                    return router.getMainNode().getNode();
                }
            };
            Future<ICraftingPlan> blockedFuture = beginCalculation(
                    service, helper, requester, CraftingRoutePolicy.DEFAULT);
            awaitPlan(helper, blockedFuture, blocked -> {
                helper.assertTrue(blocked.patternTimes().isEmpty(),
                        "disabled byproduct qualification still scheduled the whole recipe");
                helper.assertTrue(ByproductPlanWarnings.get(blocked).isEmpty(),
                        "disabled byproduct qualification unexpectedly published a warning");

                Future<ICraftingPlan> allowedFuture = beginCalculation(
                        service, helper, requester,
                        CraftingRoutePolicy.DEFAULT.withByproductOrders(true));
                awaitPlan(helper, allowedFuture, allowed -> {
                    helper.assertValueEqual(allowed.patternTimes().getOrDefault(details, 0L), 1L,
                            "enabled byproduct qualification did not schedule the source recipe once");
                    List<GenericStack> warning = ByproductPlanWarnings.get(allowed);
                    helper.assertValueEqual(warning.size(), 1,
                            "secondary-output route did not publish one extra-output warning");
                    helper.assertTrue(warning.getFirst().what() instanceof AEItemKey key
                                    && key.is(Items.DIAMOND) && warning.getFirst().amount() == 1,
                            "secondary-output warning did not report the recipe's primary output");

                    router.setRoutingPolicy(CraftingRoutePolicy.DEFAULT.withByproductOrders(true));
                    helper.assertTrue(service.isCraftable(AEItemKey.of(Items.EMERALD)),
                            "router default did not expose the secondary output in the crafting terminal");
                    helper.succeed();
                }, 0);
            }, 0);
        });
    }

    private static @NotNull ICraftingService getService(IPatternDetails details, TianshuPatternSelectorBlockEntity router) {
        ICraftingProvider provider = new ICraftingProvider() {
            @Override
            public List<IPatternDetails> getAvailablePatterns() {
                return List.of(details);
            }

            @Override
            public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputHolders) {
                return false;
            }

            @Override
            public boolean isBusy() {
                return false;
            }

            @Override
            public Set<appeng.api.stacks.AEKey> getEmitableItems() {
                return Set.of(AEItemKey.of(Items.COBBLESTONE));
            }
        };
        var service = Objects.requireNonNull(router.getGrid()).getCraftingService();
        service.addGlobalCraftingProvider(provider);
        return service;
    }

    private static Future<ICraftingPlan> beginCalculation(
            ICraftingService service,
            GameTestHelper helper,
            ICraftingSimulationRequester requester,
            CraftingRoutePolicy policy) {
        return CraftingRoutePolicyContext.withPolicy(policy, () -> service.beginCraftingCalculation(
                        helper.getLevel(), requester, AEItemKey.of(Items.EMERALD), 1,
                        CalculationStrategy.REPORT_MISSING_ITEMS));
    }

    private static void awaitPlan(
            GameTestHelper helper,
            Future<ICraftingPlan> future,
            Consumer<ICraftingPlan> success,
            int attempts) {
        if (future.isDone()) {
            try {
                success.accept(future.get());
            } catch (Exception error) {
                helper.fail("byproduct route calculation failed: " + error);
            }
            return;
        }
        if (attempts >= 30) {
            helper.fail("byproduct route calculation timed out");
            return;
        }
        helper.runAfterDelay(2, () -> awaitPlan(helper, future, success, attempts + 1));
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void binderKeepsAnchorForContinuousBindings(GameTestHelper helper) {
        BlockPos linkerPos = new BlockPos(1, 1, 1);
        BlockPos replacementLinkerPos = new BlockPos(2, 1, 2);
        BlockPos energyPos = new BlockPos(1, 1, 2);
        BlockPos firstTarget = new BlockPos(3, 1, 1);
        BlockPos secondTarget = new BlockPos(4, 1, 1);
        helper.setBlock(linkerPos, ModBlocks.PATTERN_LINKER.get());
        helper.setBlock(replacementLinkerPos, ModBlocks.PATTERN_LINKER.get());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(firstTarget, Blocks.FURNACE);
        helper.setBlock(secondTarget, Blocks.FURNACE);

        helper.runAfterDelay(10, () -> {
            PatternLinkerBlockEntity linker = Objects.requireNonNull(helper.getBlockEntity(linkerPos));
            helper.assertTrue(linker.getMainNode().isOnline(),
                    "powered linker did not come online");

            var player = helper.makeMockPlayer(GameType.CREATIVE);
            player.setPos(helper.absolutePos(linkerPos).getCenter());
            ItemStack binder = new ItemStack(ModItems.PATTERN_BINDER.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, binder);

            player.setShiftKeyDown(true);
            ModItems.PATTERN_BINDER.get().useOn(context(helper, player, linkerPos));
            helper.assertTrue(binder.has(ModDataComponents.ANCHOR_SELECTION.get()),
                    "sneak-right-clicking a linker with the binder did not store an anchor");

            ModItems.PATTERN_BINDER.get().useOn(context(helper, player, replacementLinkerPos));
            helper.assertValueEqual(
                    Objects.requireNonNull(binder.get(ModDataComponents.ANCHOR_SELECTION.get())).anchor().pos(),
                    helper.absolutePos(replacementLinkerPos),
                    "sneak-right-clicking another linker did not replace the selected anchor");

            ModItems.PATTERN_BINDER.get().useOn(context(helper, player, firstTarget));
            helper.assertTrue(binder.has(ModDataComponents.ANCHOR_SELECTION.get()),
                    "first binding cleared the selected linker");
            ModItems.PATTERN_BINDER.get().useOn(context(helper, player, secondTarget));
            helper.assertTrue(binder.has(ModDataComponents.ANCHOR_SELECTION.get()),
                    "second binding cleared the selected linker");

            BindingSavedData data = BindingSavedData.get(helper.getLevel().getServer());
            var first = data.findByTarget(GlobalPos.of(
                    helper.getLevel().dimension(), helper.absolutePos(firstTarget)));
            var second = data.findByTarget(GlobalPos.of(
                    helper.getLevel().dimension(), helper.absolutePos(secondTarget)));
            helper.assertTrue(first.isPresent() && second.isPresent(),
                    "continuous binding did not create both records");
            data.remove(first.orElseThrow().bindingId());
            data.remove(second.orElseThrow().bindingId());
            helper.succeed();
        });
    }

    private static UseOnContext context(
            GameTestHelper helper, Player player, BlockPos relativePos) {
        return new UseOnContext(
                helper.getLevel(),
                player,
                InteractionHand.MAIN_HAND,
                player.getMainHandItem(),
                hitResult(helper, relativePos));
    }

    private static BlockHitResult hitResult(GameTestHelper helper, BlockPos relativePos) {
        BlockPos absolutePos = helper.absolutePos(relativePos);
        return new BlockHitResult(Vec3.atCenterOf(absolutePos), Direction.UP, absolutePos, false);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void furnaceCatalogIsDiscoverable(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, Blocks.FURNACE);
        BlockEntity furnace = Objects.requireNonNull(helper.getBlockEntity(pos));
        var adapter = MachineAdapterRegistry.find(helper.getLevel(), furnace);
        helper.assertTrue(adapter.isPresent(), "vanilla furnace adapter was not found");
        var catalog = RecipeIndexService.catalog(helper.getLevel(), furnace, adapter.orElseThrow());
        helper.assertFalse(catalog.recipes().isEmpty(), "smelting catalog is empty");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void generatorCreatesPersistentAggregatePattern(GameTestHelper helper) {
        BlockPos furnacePos = new BlockPos(1, 1, 1);
        helper.setBlock(furnacePos, Blocks.FURNACE);
        FurnaceBlockEntity furnace = Objects.requireNonNull(helper.getBlockEntity(furnacePos));
        var adapter = MachineAdapterRegistry.find(helper.getLevel(), furnace).orElseThrow();
        int expectedCount = RecipeIndexService.catalog(helper.getLevel(), furnace, adapter).recipes().size();

        AggregatePatternData captured = AggregatePatternData.capture(
                furnace, adapter, RecipeIndexService.catalog(helper.getLevel(), furnace, adapter));
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(), BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE),
                captured.machineTranslationKey(), captured.recipes());
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        AggregatePatternRef stored = aggregate.get(ModDataComponents.AGGREGATE_PATTERN.get());
        helper.assertTrue(stored != null, "aggregate item did not retain its lightweight reference");
        if (stored != null) {
            helper.assertValueEqual(
                    AggregatePatternLibrary.get(helper.getLevel().getServer())
                            .recipes(helper.getLevel().getServer(), stored.libraryId()).orElseThrow().size(),
                    expectedCount, "server library did not retain the complete machine catalog");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void patternProviderExpandsOneAggregateIntoAllRecipes(GameTestHelper helper) {
        BlockPos providerPos = new BlockPos(1, 1, 1);
        BlockPos energyPos = new BlockPos(1, 1, 2);
        BlockPos furnacePos = new BlockPos(3, 1, 1);
        helper.setBlock(providerPos, AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(furnacePos, Blocks.FURNACE);

        helper.runAfterDelay(10, () -> {
            PatternProviderBlockEntity provider = Objects.requireNonNull(helper.getBlockEntity(providerPos));
            FurnaceBlockEntity furnace = Objects.requireNonNull(helper.getBlockEntity(furnacePos));
            var adapter = MachineAdapterRegistry.find(helper.getLevel(), furnace).orElseThrow();
            var catalog = RecipeIndexService.catalog(helper.getLevel(), furnace, adapter);
            ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
            AggregatePatternData captured = AggregatePatternData.capture(furnace, adapter, catalog);
            var ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                    helper.getLevel().getServer(), BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE),
                    captured.machineTranslationKey(), captured.recipes());
            aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

            helper.assertTrue(provider.getLogic().getPatternInv().isItemValid(0, aggregate),
                    "AE2 rejected the aggregate item as an encoded pattern");
            provider.getLogic().getPatternInv().setItemDirect(0, aggregate);
            provider.getLogic().updatePatterns();
            helper.assertValueEqual(provider.getLogic().getAvailablePatterns().size(), catalog.recipes().size(),
                    "one aggregate item did not publish every child pattern");
            var firstOutput = catalog.recipes().getFirst().output();
            helper.assertTrue(Objects.requireNonNull(provider.getMainNode().getGrid()).getCraftingService()
                            .isCraftable(AEItemKey.of(firstOutput)),
                    "AE network crafting service did not receive the expanded aggregate pattern");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void aggregatePatternPublishesFluidOutput(GameTestHelper helper) {
        BlockPos providerPos = new BlockPos(1, 1, 1);
        BlockPos energyPos = new BlockPos(1, 1, 2);
        helper.setBlock(providerPos, AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block());

        helper.runAfterDelay(10, () -> {
            PatternProviderBlockEntity provider = Objects.requireNonNull(helper.getBlockEntity(providerPos));
            AggregateRecipe fluidRecipe = new AggregateRecipe(
                    "fluid-output-test",
                    ResourceLocation.fromNamespaceAndPath("aeallpattern", "fluid_output_test"),
                    List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.ICE)))),
                    List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000)),
                    1);
            AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                    helper.getLevel().getServer(),
                    ResourceLocation.fromNamespaceAndPath("aeallpattern", "fluid_test_machine"),
                    "block.aeallpattern.fluid_test_machine", List.of(fluidRecipe));
            ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
            aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

            provider.getLogic().getPatternInv().setItemDirect(0, aggregate);
            provider.getLogic().updatePatterns();
            helper.assertTrue(Objects.requireNonNull(provider.getMainNode().getGrid()).getCraftingService()
                            .isCraftable(AEFluidKey.of(Fluids.WATER)),
                    "AE network did not publish aggregate fluid output");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void aggregatePreservesNativeAePatternKinds(GameTestHelper helper) {
        List<AggregateRecipe> recipes = List.of(
                new AggregateRecipe(
                        "native-crafting-test",
                        ResourceLocation.withDefaultNamespace("oak_planks"),
                        AggregatePatternKind.CRAFTING,
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.OAK_LOG)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.OAK_PLANKS, 4)))),
                        1),
                new AggregateRecipe(
                        "native-stonecutting-test",
                        ResourceLocation.withDefaultNamespace("andesite_slab_from_andesite_stonecutting"),
                        AggregatePatternKind.STONECUTTING,
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.ANDESITE)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.ANDESITE_SLAB, 2)))),
                        1),
                new AggregateRecipe(
                        "native-smithing-test",
                        ResourceLocation.withDefaultNamespace("netherite_sword_smithing"),
                        AggregatePatternKind.SMITHING,
                        List.of(
                                Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE))),
                                Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.DIAMOND_SWORD))),
                                Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.NETHERITE_INGOT)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.NETHERITE_SWORD)))),
                        1),
                new AggregateRecipe(
                        "native-processing-test",
                        ResourceLocation.fromNamespaceAndPath("aeallpattern", "native_processing_test"),
                        AggregatePatternKind.PROCESSING,
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.RAW_IRON)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT)))),
                        1),
                new AggregateRecipe(
                        "dynamic-crafting-test",
                        ResourceLocation.fromNamespaceAndPath(
                                "minecraft", "jei.shulker.color.block.minecraft.white_shulker_box"),
                        AggregatePatternKind.CRAFTING,
                        List.of(
                                Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.SHULKER_BOX))),
                                Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.WHITE_DYE)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.WHITE_SHULKER_BOX)))),
                        1));
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "native_pattern_test_machine"),
                "block.aeallpattern.native_pattern_test_machine", recipes);
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        List<IPatternDetails> expanded = AggregatePatternExpander.expand(aggregate, helper.getLevel());
        helper.assertValueEqual(expanded.size(), 5,
                "aggregate did not expand all native and dynamic AE pattern kinds");
        helper.assertTrue(expanded.get(0) instanceof IMolecularAssemblerSupportedPattern,
                "crafting aggregate child lost molecular assembler support");
        helper.assertTrue(expanded.get(1) instanceof IMolecularAssemblerSupportedPattern,
                "stonecutting aggregate child lost native crafting support");
        helper.assertTrue(expanded.get(2) instanceof IMolecularAssemblerSupportedPattern,
                "smithing aggregate child lost native crafting support");
        helper.assertFalse(expanded.get(3) instanceof IMolecularAssemblerSupportedPattern,
                "processing aggregate child was incorrectly exposed to molecular assemblers");
        helper.assertTrue(expanded.get(4) instanceof IMolecularAssemblerSupportedPattern,
                "dynamic JEI crafting child lost molecular assembler support");

        List<String> delegateTypes = expanded.stream()
                .map(IPatternDetails::getDefinition)
                .map(definition -> PatternDetailsHelper.decodePattern(definition, helper.getLevel()))
                .map(details -> details == null ? "null" : details.getClass().getSimpleName())
                .toList();
        helper.assertValueEqual(delegateTypes.get(0), "AECraftingPattern",
                "workbench recipe was not encoded as an AE crafting pattern");
        helper.assertValueEqual(delegateTypes.get(1), "AEStonecuttingPattern",
                "stonecutter recipe was not encoded as an AE stonecutting pattern");
        helper.assertValueEqual(delegateTypes.get(2), "AESmithingTablePattern",
                "smithing recipe was not encoded as an AE smithing pattern");
        helper.assertValueEqual(delegateTypes.get(3), "AEProcessingPattern",
                "machine recipe was not kept as an AE processing pattern");
        helper.assertValueEqual(delegateTypes.get(4), "AECraftingPattern",
                "dynamic JEI recipe was not resolved to an AE crafting pattern");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aggregateOptionsSplitEveryItemAndIgnoreOutputComponents(GameTestHelper helper) {
        ItemStack namedOutput = new ItemStack(Items.EMERALD);
        namedOutput.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Configured output"));
        AggregateRecipe recipe = new AggregateRecipe(
                "configured-processing-test",
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "configured_processing_test"),
                AggregatePatternKind.PROCESSING,
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.DIAMOND, 3)))),
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(namedOutput))),
                1);
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "configured_test_machine"),
                "block.aeallpattern.configured_test_machine", List.of(recipe));
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);
        aggregate.set(
                ModDataComponents.AGGREGATE_PATTERN_OPTIONS.get(),
                new AggregatePatternOptions(true, true));

        List<IPatternDetails> expanded = AggregatePatternExpander.expand(aggregate, helper.getLevel());
        helper.assertValueEqual(expanded.size(), 1, "configured aggregate recipe was not published");
        IPatternDetails configured = expanded.getFirst();
        helper.assertValueEqual(configured.getInputs().length, 3,
                "three input items were not split into three independent inputs");
        for (IPatternDetails.IInput input : configured.getInputs()) {
            helper.assertValueEqual(input.getMultiplier(), 1L, "split input multiplier was not one");
            helper.assertValueEqual(input.getPossibleInputs()[0].amount(), 1L, "split input amount was not one");
        }
        helper.assertTrue(configured.getOutputs().getFirst().what() instanceof AEItemKey outputKey
                        && outputKey.is(Items.EMERALD)
                        && !outputKey.hasComponents(),
                "ignore-output-NBT did not strip the custom output components");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aggregateDefaultSkipsProbabilisticMainOutput(GameTestHelper helper) {
        AggregateRecipe recipe = new AggregateRecipe(
                "probabilistic-main-output-test",
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "probabilistic_main_output_test"),
                AggregatePatternKind.PROCESSING,
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT)))),
                List.of(),
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.DIAMOND)))),
                1,
                1);
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "probabilistic_main_machine"),
                "block.aeallpattern.probabilistic_main_machine", List.of(recipe));
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        helper.assertValueEqual(
                AggregatePatternExpander.expand(aggregate, helper.getLevel()).size(),
                0,
                "a chance-based main output was encoded despite the default safeguard");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aggregateDefaultRemovesProbabilisticByproduct(GameTestHelper helper) {
        AggregateRecipe recipe = new AggregateRecipe(
                "probabilistic-byproduct-test",
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "probabilistic_byproduct_test"),
                AggregatePatternKind.PROCESSING,
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT)))),
                List.of(),
                List.of(
                        Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT))),
                        Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.DIAMOND)))),
                2,
                1);
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "probabilistic_byproduct_machine"),
                "block.aeallpattern.probabilistic_byproduct_machine", List.of(recipe));
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        List<IPatternDetails> expanded = AggregatePatternExpander.expand(aggregate, helper.getLevel());
        helper.assertValueEqual(expanded.size(), 1, "deterministic main output recipe was removed");
        helper.assertValueEqual(expanded.getFirst().getOutputs().size(), 1,
                "chance-based byproduct remained exposed as a pattern output");
        helper.assertTrue(expanded.getFirst().getOutputs().getFirst().what() instanceof AEItemKey key
                        && key.is(Items.GOLD_INGOT),
                "deterministic main output changed while filtering the byproduct");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aggregateCraftingUsesItemSubstitution(GameTestHelper helper) {
        AggregateRecipe recipe = new AggregateRecipe(
                "native-chest-tag-test",
                ResourceLocation.withDefaultNamespace("chest"),
                AggregatePatternKind.CRAFTING,
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.OAK_PLANKS, 8)))),
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.CHEST)))),
                1);
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "native_chest_tag_test"),
                "block.aeallpattern.native_chest_tag_test", List.of(recipe));
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        List<IPatternDetails> expanded = AggregatePatternExpander.expand(aggregate, helper.getLevel());
        helper.assertValueEqual(expanded.size(), 1, "native chest aggregate recipe was not published");
        EncodedCraftingPattern defaults = expanded.getFirst().getDefinition().toStack()
                .get(AEComponents.ENCODED_CRAFTING_PATTERN);
        helper.assertTrue(defaults != null && !defaults.canSubstitute() && defaults.canSubstituteFluids(),
                "default AE2 substitution flags were not item-off/fluid-on");

        aggregate.set(ModDataComponents.AGGREGATE_PATTERN_OPTIONS.get(),
                new AggregatePatternOptions(false, false, true, true, false, true, false));
        expanded = AggregatePatternExpander.expand(aggregate, helper.getLevel());
        helper.assertTrue(expanded.getFirst() instanceof IMolecularAssemblerSupportedPattern,
                "native chest recipe was not kept as a molecular assembler pattern");
        EncodedCraftingPattern toggled = expanded.getFirst().getDefinition().toStack()
                .get(AEComponents.ENCODED_CRAFTING_PATTERN);
        helper.assertTrue(toggled != null && toggled.canSubstitute() && !toggled.canSubstituteFluids(),
                "configured AE2 substitution flags were not item-on/fluid-off");
        helper.assertTrue(Arrays.stream(expanded.getFirst().getInputs()).anyMatch(input ->
                        input.isValid(AEItemKey.of(Items.BIRCH_PLANKS), helper.getLevel())),
                "AE item substitution did not accept another plank type");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void assemblerMatrixAcceptsAndExpandsAggregatePatterns(GameTestHelper helper) {
        if (!ModList.get().isLoaded("extendedae")) {
            helper.succeed();
            return;
        }
        try {
            AggregateRecipe recipe = new AggregateRecipe(
                    "assembler-matrix-chest",
                    ResourceLocation.withDefaultNamespace("chest"),
                    AggregatePatternKind.CRAFTING,
                    List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.OAK_PLANKS, 8)))),
                    List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.CHEST)))),
                    1);
            AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                    helper.getLevel().getServer(),
                    ResourceLocation.fromNamespaceAndPath("aeallpattern", "assembler_matrix_test"),
                    "block.aeallpattern.assembler_matrix_test", List.of(recipe));
            ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
            aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

            Class<?> filterClass = Class.forName(
                    "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern$Filter");
            Object filter = filterClass.getConstructor(java.util.function.Supplier.class)
                    .newInstance((java.util.function.Supplier<net.minecraft.world.level.Level>) helper::getLevel);
            boolean accepted = (boolean) filterClass.getMethod(
                            "allowInsert", appeng.api.inventories.InternalInventory.class,
                            int.class, ItemStack.class)
                    .invoke(filter, appeng.api.inventories.InternalInventory.empty(), 0, aggregate);
            helper.assertTrue(accepted, "assembler-matrix GUI rejected an aggregate pattern");

            Class<?> singletons = Class.forName("com.glodblock.github.extendedae.common.EAESingletons");
            net.minecraft.world.level.block.Block patternBlock =
                    (net.minecraft.world.level.block.Block) singletons.getField("ASSEMBLER_MATRIX_PATTERN").get(null);
            Class<?> tileClass = Class.forName(
                    "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern");
            Object tile = tileClass.getConstructor(BlockPos.class, net.minecraft.world.level.block.state.BlockState.class)
                    .newInstance(helper.absolutePos(new BlockPos(0, 1, 0)), patternBlock.defaultBlockState());
            ((net.minecraft.world.level.block.entity.BlockEntity) tile).setLevel(helper.getLevel());
            appeng.api.inventories.InternalInventory inventory =
                    (appeng.api.inventories.InternalInventory) tileClass.getMethod("getPatternInventory").invoke(tile);
            inventory.setItemDirect(0, aggregate);
            tileClass.getMethod("updatePatterns").invoke(tile);
            List<?> patterns = (List<?>) tileClass.getMethod("getAvailablePatterns").invoke(tile);
            helper.assertValueEqual(patterns.size(), 1,
                    "assembler matrix did not expand the aggregate child");
            helper.assertFalse(patterns.getFirst() instanceof AggregatePatternMarkerDetails,
                    "assembler matrix published only the aggregate marker");

            if (ModList.get().isLoaded("extendedae_plus")) {
                Class<?> plusFilterClass = Class.forName(
                        "com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity$Filter");
                Object plusFilter = plusFilterClass.getConstructor(java.util.function.Supplier.class)
                        .newInstance((java.util.function.Supplier<net.minecraft.world.level.Level>) helper::getLevel);
                boolean plusAccepted = (boolean) plusFilterClass.getMethod(
                                "allowInsert", appeng.api.inventories.InternalInventory.class,
                                int.class, ItemStack.class)
                        .invoke(plusFilter, appeng.api.inventories.InternalInventory.empty(), 0, aggregate);
                helper.assertTrue(plusAccepted, "super assembler-matrix GUI rejected an aggregate pattern");
            }
            helper.succeed();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Assembler-matrix aggregate compatibility failed", error);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aggregateProcessingSplitKeepsTagCandidates(GameTestHelper helper) {
        GenericStack oakPlanks = Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.OAK_PLANKS, 3)));
        AggregateInputSlot plankSlot = new AggregateInputSlot(
                List.of(oakPlanks), Optional.of(ItemTags.PLANKS.location()));
        AggregateRecipe recipe = new AggregateRecipe(
                "processing-plank-tag-test",
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "processing_plank_tag_test"),
                AggregatePatternKind.PROCESSING,
                List.of(oakPlanks),
                List.of(plankSlot),
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.CHEST)))),
                1);
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "processing_plank_tag_test"),
                "block.aeallpattern.processing_plank_tag_test", List.of(recipe));
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);
        aggregate.set(
                ModDataComponents.AGGREGATE_PATTERN_OPTIONS.get(),
                new AggregatePatternOptions(true, false));

        List<IPatternDetails> expanded = AggregatePatternExpander.expand(aggregate, helper.getLevel());
        helper.assertValueEqual(expanded.size(), 1, "tagged processing recipe was not published");
        helper.assertValueEqual(expanded.getFirst().getInputs().length, 3,
                "three tagged planks were not split into three independent candidate slots");
        for (IPatternDetails.IInput input : expanded.getFirst().getInputs()) {
            helper.assertValueEqual(input.getMultiplier(), 1L, "split candidate multiplier was not one");
            helper.assertTrue(input.getPossibleInputs().length > 1,
                    "split input lost its full plank candidate set");
            helper.assertTrue(input.isValid(AEItemKey.of(Items.OAK_PLANKS), helper.getLevel())
                            && input.isValid(AEItemKey.of(Items.BIRCH_PLANKS), helper.getLevel()),
                    "split input cannot independently mix different plank types");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aggregateProcessingTagPushUsesPatternAmount(GameTestHelper helper) {
        GenericStack oakPlanks = Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.OAK_PLANKS, 3)));
        AggregateInputSlot plankSlot = new AggregateInputSlot(
                List.of(oakPlanks), Optional.of(ItemTags.PLANKS.location()));
        AggregateRecipe recipe = new AggregateRecipe(
                "processing-plank-push-test",
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "processing_plank_push_test"),
                AggregatePatternKind.PROCESSING,
                List.of(oakPlanks),
                List.of(plankSlot),
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.CHEST)))),
                1);
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "processing_plank_push_test"),
                "block.aeallpattern.processing_plank_push_test", List.of(recipe));
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        IPatternDetails details = AggregatePatternExpander.expand(aggregate, helper.getLevel()).getFirst();
        IPatternDetails.IInput input = details.getInputs()[0];
        helper.assertValueEqual(input.getMultiplier(), 3L, "tagged input amount was not retained as multiplier");
        helper.assertValueEqual(input.getPossibleInputs()[0].amount(), 1L,
                "tagged input candidate was not normalized to one unit");

        KeyCounter holders = new KeyCounter();
        holders.add(AEItemKey.of(Items.OAK_PLANKS), 3);
        List<GenericStack> pushed = new ArrayList<>();
        details.pushInputsToExternalInventory(new KeyCounter[] { holders },
                (key, amount) -> pushed.add(new GenericStack(key, amount)));
        helper.assertValueEqual(pushed.size(), 1, "tagged input was not pushed to the external inventory");
        helper.assertValueEqual(pushed.getFirst().amount(), 3L, "tagged input push amount was incorrect");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aggregateProcessingCanSwapFirstAndLastInputs(GameTestHelper helper) {
        GenericStack iron = GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        GenericStack gold = GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT));
        GenericStack diamond = GenericStack.fromItemStack(new ItemStack(Items.DIAMOND));
        AggregateRecipe recipe = new AggregateRecipe(
                "processing-input-order-test",
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "processing_input_order_test"),
                AggregatePatternKind.PROCESSING,
                List.of(iron, gold, diamond),
                List.of(
                        AggregateInputSlot.exact(iron),
                        AggregateInputSlot.exact(gold),
                        AggregateInputSlot.exact(diamond)),
                List.of(GenericStack.fromItemStack(new ItemStack(Items.EMERALD))),
                1);
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "processing_input_order_test"),
                "block.aeallpattern.processing_input_order_test", List.of(recipe));
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN_OPTIONS.get(),
                new AggregatePatternOptions(
                        false, false, true, true, false, false, true,
                        false, false, false, false, true));

        List<IPatternDetails> expanded = AggregatePatternExpander.expand(aggregate, helper.getLevel());
        helper.assertValueEqual(expanded.size(), 1, "input-order recipe was not published");
        IPatternDetails.IInput[] inputs = expanded.getFirst().getInputs();
        helper.assertValueEqual(inputs.length, 3, "input-order recipe lost an input");
        helper.assertTrue(inputs[0].isValid(AEItemKey.of(Items.DIAMOND), helper.getLevel()),
                "last input was not moved to the first slot");
        helper.assertTrue(inputs[1].isValid(AEItemKey.of(Items.GOLD_INGOT), helper.getLevel()),
                "middle input changed while swapping the ends");
        helper.assertTrue(inputs[2].isValid(AEItemKey.of(Items.IRON_INGOT), helper.getLevel()),
                "first input was not moved to the last slot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aggregateDurabilityRecipesAreSkippedByDefault(GameTestHelper helper) {
        ItemStack intactPickaxe = new ItemStack(Items.IRON_PICKAXE);
        ItemStack damagedPickaxe = intactPickaxe.copy();
        damagedPickaxe.setDamageValue(1);
        GenericStack pickaxe = Objects.requireNonNull(GenericStack.fromItemStack(intactPickaxe));
        GenericStack damaged = Objects.requireNonNull(GenericStack.fromItemStack(damagedPickaxe));
        GenericStack diamond = Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.DIAMOND)));
        GenericStack waterBucket = Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.WATER_BUCKET)));
        GenericStack emptyBucket = Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.BUCKET)));

        AggregateRecipe durabilityRecipe = new AggregateRecipe(
                "durability-filter-test",
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "durability_filter_test"),
                AggregatePatternKind.PROCESSING,
                List.of(pickaxe),
                List.of(AggregateInputSlot.exact(pickaxe)),
                List.of(diamond, damaged),
                1);
        AggregateRecipe containerRecipe = new AggregateRecipe(
                "container-filter-test",
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "container_filter_test"),
                AggregatePatternKind.PROCESSING,
                List.of(waterBucket),
                List.of(AggregateInputSlot.exact(waterBucket)),
                List.of(diamond, emptyBucket),
                1);
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "durability_filter_test"),
                "block.aeallpattern.durability_filter_test",
                List.of(durabilityRecipe, containerRecipe));
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        List<IPatternDetails> filtered = AggregatePatternExpander.expand(aggregate, helper.getLevel());
        helper.assertValueEqual(filtered.size(), 1,
                "default durability filter did not keep only the non-durability container recipe");

        aggregate.set(ModDataComponents.AGGREGATE_PATTERN_OPTIONS.get(),
                new AggregatePatternOptions(
                        false, false, true, true, false, false, true,
                        false, false, false, false, false, false));
        helper.assertValueEqual(AggregatePatternExpander.expand(aggregate, helper.getLevel()).size(), 2,
                "disabling the durability filter did not restore the recipe");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aggregateProcessingCatalystsCanBeRemoved(GameTestHelper helper) {
        GenericStack press = Objects.requireNonNull(GenericStack.fromItemStack(AEItems.CALCULATION_PROCESSOR_PRESS.stack()));
        GenericStack iron = Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT)));
        AggregateRecipe mixedInputs = new AggregateRecipe(
                "processing-catalyst-test",
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "processing_catalyst_test"),
                AggregatePatternKind.PROCESSING,
                List.of(press, iron),
                List.of(AggregateInputSlot.exact(press), AggregateInputSlot.exact(iron)),
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT)))),
                1);
        AggregateRecipe catalystOnly = new AggregateRecipe(
                "processing-catalyst-only-test",
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "processing_catalyst_only_test"),
                AggregatePatternKind.PROCESSING,
                List.of(press),
                List.of(AggregateInputSlot.exact(press)),
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.DIAMOND)))),
                1);
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "processing_catalyst_test"),
                "block.aeallpattern.processing_catalyst_test", List.of(mixedInputs, catalystOnly));
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN_OPTIONS.get(),
                new AggregatePatternOptions(false, false, true, true, true));

        List<IPatternDetails> expanded = AggregatePatternExpander.expand(aggregate, helper.getLevel());
        helper.assertValueEqual(expanded.size(), 1,
                "a catalyst-only recipe was published or the mixed recipe was removed");
        helper.assertValueEqual(expanded.getFirst().getInputs().length, 1,
                "the reusable processing catalyst was not removed");
        helper.assertTrue(expanded.getFirst().getInputs()[0].isValid(AEItemKey.of(Items.IRON_INGOT), helper.getLevel()),
                "catalyst filtering changed the remaining material input");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aggregateDecoderIgnoresEmptyMolecularAssemblerSlot(GameTestHelper helper) {
        new AggregatePatternDecoder().decodePattern((AEItemKey) null, helper.getLevel());
        helper.assertTrue(
                true,
                "aggregate decoder did not ignore an empty AE pattern key");
        helper.assertTrue(
                PatternDetailsHelper.decodePattern(ItemStack.EMPTY, helper.getLevel()) == null,
                "AE pattern decoding did not safely ignore an empty molecular assembler slot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void appliedMekanisticsConvertsJeiChemicalToAeKey(GameTestHelper helper) {
        if (!ModList.get().isLoaded("appmek") || !ModList.get().isLoaded("ae2jeiintegration")) {
            helper.succeed();
            return;
        }
        try {
            // AppMek registers this converter from its JEI plugin constructor.
            Class.forName("me.ramidzkh.mekae2.integration.jei.AMJeiPlugin")
                    .getConstructor().newInstance();
            Class<?> convertersClass = Class.forName(
                    "tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters");
            List<?> converters = (List<?>) convertersClass.getMethod("getConverters").invoke(null);
            Object converter = converters.stream()
                    .filter(candidate -> candidate.getClass().getName().equals(
                            "me.ramidzkh.mekae2.integration.jei.ChemicalIngredientConverter"))
                    .findFirst().orElseThrow();

            Class<?> mekanismApi = Class.forName("mekanism.api.MekanismAPI");
            net.minecraft.core.Registry<?> chemicals =
                    (net.minecraft.core.Registry<?>) mekanismApi.getField("CHEMICAL_REGISTRY").get(null);
            Object oxygen = chemicals.get(ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"));
            Class<?> chemicalClass = Class.forName("mekanism.api.chemical.Chemical");
            Class<?> chemicalStackClass = Class.forName("mekanism.api.chemical.ChemicalStack");
            Object oxygenStack = chemicalStackClass
                    .getConstructor(chemicalClass, long.class).newInstance(oxygen, 1_000L);
            Class<?> converterApi = Class.forName(
                    "tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter");
            GenericStack converted = (GenericStack) converterApi
                    .getMethod("getStackFromIngredient", Object.class).invoke(converter, oxygenStack);

            helper.assertTrue(converted != null, "AppMek chemical converter returned no AE stack");
            if (converted != null) {
                helper.assertValueEqual(converted.what().getType().getId().toString(), "appmek:chemical",
                        "Mekanism chemical was not converted to AppMek's AE key type");
                helper.assertFalse(io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.TianshuFastPlanningPolicy
                                .supportsOutput(converted.what()),
                        "Tianshu fast planner claimed a chemical output instead of leaving it to AE2");
            }
            helper.assertTrue(io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.TianshuFastPlanningPolicy
                            .supportsOutput(AEItemKey.of(Items.IRON_INGOT)),
                    "Tianshu fast planner rejected an ordinary item output");
            if (converted != null) {
                helper.assertValueEqual(converted.amount(), 1_000L,
                        "Mekanism chemical amount changed during JEI conversion");
            }
            ItemStack encoded = appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                    List.of(converted),
                    List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000L)));
            var decoded = appeng.api.crafting.PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
            helper.assertTrue(decoded != null,
                    "AE2 rejected a processing pattern containing Chemical and fluid keys");
            helper.assertTrue(java.util.Arrays.stream(decoded.getInputs()).anyMatch(input ->
                            java.util.Arrays.stream(input.getPossibleInputs())
                            .anyMatch(candidate -> candidate.what().equals(converted.what())
                                    && candidate.amount() * input.getMultiplier() == converted.amount())),
                    "AE2 processing pattern lost its Chemical input");

            GenericStack water = new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000L);
            GenericStack iron = Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT)));
            GenericStack gold = Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT)));
            AggregateRecipe filteredRecipe = new AggregateRecipe(
                    "fluid-chemical-filter-test",
                    ResourceLocation.fromNamespaceAndPath("aeallpattern", "fluid_chemical_filter_test"),
                    AggregatePatternKind.PROCESSING,
                    List.of(converted, water, iron),
                    List.of(
                            AggregateInputSlot.exact(converted),
                            AggregateInputSlot.exact(water),
                            AggregateInputSlot.exact(iron)),
                    List.of(converted, water, gold),
                    1);
            AggregateRecipe noInputs = new AggregateRecipe(
                    "fluid-filter-empty-input-test",
                    ResourceLocation.fromNamespaceAndPath("aeallpattern", "fluid_filter_empty_input_test"),
                    AggregatePatternKind.PROCESSING,
                    List.of(water),
                    List.of(AggregateInputSlot.exact(water)),
                    List.of(gold),
                    1);
            AggregateRecipe noOutputs = new AggregateRecipe(
                    "chemical-filter-empty-output-test",
                    ResourceLocation.fromNamespaceAndPath("aeallpattern", "chemical_filter_empty_output_test"),
                    AggregatePatternKind.PROCESSING,
                    List.of(iron),
                    List.of(AggregateInputSlot.exact(iron)),
                    List.of(converted),
                    1);
            AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                    helper.getLevel().getServer(),
                    ResourceLocation.fromNamespaceAndPath("aeallpattern", "fluid_chemical_filter_test"),
                    "block.aeallpattern.fluid_chemical_filter_test",
                    List.of(filteredRecipe, noInputs, noOutputs));
            ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
            aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);
            aggregate.set(ModDataComponents.AGGREGATE_PATTERN_OPTIONS.get(),
                    new AggregatePatternOptions(
                            false, false, true, true, false, false, true,
                            true, true, true, true, false));

            List<IPatternDetails> expanded = AggregatePatternExpander.expand(aggregate, helper.getLevel());
            helper.assertValueEqual(expanded.size(), 1,
                    "a recipe left without inputs or outputs was published");
            helper.assertValueEqual(expanded.getFirst().getInputs().length, 1,
                    "fluid or chemical processing input was not removed");
            helper.assertTrue(expanded.getFirst().getInputs()[0]
                            .isValid(AEItemKey.of(Items.IRON_INGOT), helper.getLevel()),
                    "fluid and chemical input filtering changed the remaining item");
            helper.assertValueEqual(expanded.getFirst().getOutputs().size(), 1,
                    "fluid or chemical processing output was not removed");
            helper.assertTrue(expanded.getFirst().getOutputs().getFirst().what() instanceof AEItemKey key
                            && key.is(Items.GOLD_INGOT),
                    "fluid and chemical output filtering changed the remaining item");
            helper.succeed();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Applied Mekanistics JEI chemical conversion failed", error);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aggregateLibraryPagesLargeCatalog(GameTestHelper helper) {
        List<AggregateRecipe> recipes = new ArrayList<>();
        for (int index = 0; index < AggregatePatternLibrary.PAGE_SIZE * 2 + 1; index++) {
            String id = String.format("%064x", index + 1);
            recipes.add(new AggregateRecipe(
                    id,
                    ResourceLocation.fromNamespaceAndPath("aeallpattern", "paging_test/" + index),
                    List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.COBBLESTONE)))),
                    List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.STONE)))),
                    1));
        }
        var library = AggregatePatternLibrary.get(helper.getLevel().getServer());
        AggregatePatternRef ref = library.put(
                helper.getLevel().getServer(), BuiltInRegistries.BLOCK.getKey(Blocks.BLAST_FURNACE),
                Blocks.BLAST_FURNACE.getDescriptionId(), recipes);
        var entry = library.find(ref.libraryId()).orElseThrow();
        helper.assertValueEqual(entry.pageCount(), 3, "large catalog was not split into three pages");
        helper.assertValueEqual(
                library.recipes(helper.getLevel().getServer(), ref.libraryId()).orElseThrow().size(),
                recipes.size(), "paged catalog did not reconstruct all recipes");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aggregateLibraryStoresNumberedBatchesIndependently(GameTestHelper helper) {
        List<AggregateRecipe> recipes = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            String id = String.format("%064x", index + 1);
            recipes.add(new AggregateRecipe(
                    id,
                    ResourceLocation.fromNamespaceAndPath("aeallpattern", "batch_test/" + index),
                    List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.COBBLESTONE)))),
                    List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.STONE)))),
                    1));
        }
        var library = AggregatePatternLibrary.get(helper.getLevel().getServer());
        var catalyst = BuiltInRegistries.BLOCK.getKey(Blocks.SMOKER);
        String randomSeries = UUID.randomUUID().toString().replace("-", "");
        String seriesHash = randomSeries + randomSeries;
        int batchSize = 2;
        int batchCount = 3;

        AggregatePatternRef first = library.putBatch(
                helper.getLevel().getServer(), catalyst, Blocks.SMOKER.getDescriptionId(),
                recipes.subList(0, 2), seriesHash, batchSize, 0, batchCount, recipes.size());
        helper.assertValueEqual(
                library.nextMissingBatch(catalyst, seriesHash, batchSize, batchCount), 1,
                "numbered aggregate did not continue with its second part");
        AggregatePatternRef second = library.putBatch(
                helper.getLevel().getServer(), catalyst, Blocks.SMOKER.getDescriptionId(),
                recipes.subList(2, 4), seriesHash, batchSize, 1, batchCount, recipes.size());
        AggregatePatternRef third = library.putBatch(
                helper.getLevel().getServer(), catalyst, Blocks.SMOKER.getDescriptionId(),
                recipes.subList(4, 5), seriesHash, batchSize, 2, batchCount, recipes.size());

        helper.assertFalse(first.libraryId().equals(second.libraryId()),
                "second aggregate part replaced the first part");
        helper.assertFalse(second.libraryId().equals(third.libraryId()),
                "last aggregate part replaced an earlier part");
        helper.assertValueEqual(
                library.nextMissingBatch(catalyst, seriesHash, batchSize, batchCount), batchCount,
                "completed aggregate series still reported a missing part");
        var lastEntry = library.find(third.libraryId()).orElseThrow();
        helper.assertValueEqual(lastEntry.batchIndex(), 2, "last aggregate part has the wrong number");
        helper.assertValueEqual(lastEntry.batchCount(), 3, "aggregate part count was not retained");
        helper.assertValueEqual(lastEntry.totalRecipeCount(), 5, "aggregate total recipe count was not retained");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void furnaceTransferIgnoresWrongClickedFace(GameTestHelper helper) {
        BlockPos relativePos = new BlockPos(0, 1, 0);
        helper.setBlock(relativePos, Blocks.FURNACE);
        FurnaceBlockEntity furnace = Objects.requireNonNull(helper.getBlockEntity(relativePos));
        var adapter = MachineAdapterRegistry.find(helper.getLevel(), furnace).orElseThrow();
        BindingRecord binding = new BindingRecord(
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 1, 0))),
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relativePos)),
                Direction.NORTH,
                "anchor",
                "target",
                adapter.id().toString(),
                adapter.schemaVersion(),
                helper.getLevel().getGameTime(),
                helper.getLevel().getGameTime());

        helper.assertTrue(adapter.insert(helper.getLevel(), binding, new ItemStack(Items.RAW_IRON)),
                "front-face binding did not fall back to the furnace input");
        helper.assertTrue(furnace.getItem(0).is(Items.RAW_IRON), "input was not placed in slot 0");
        helper.assertTrue(furnace.getItem(1).isEmpty(), "input was incorrectly placed in the fuel slot");

        furnace.setItem(2, new ItemStack(Items.IRON_INGOT));
        ItemStack simulated = adapter.extractAnyOutput(helper.getLevel(), binding, true);
        helper.assertTrue(simulated.is(Items.IRON_INGOT), "output extraction simulation failed");
        helper.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT), "simulation removed the output");
        ItemStack extracted = adapter.extractAnyOutput(helper.getLevel(), binding, false);
        helper.assertTrue(extracted.is(Items.IRON_INGOT), "output was not extracted");
        helper.assertTrue(furnace.getItem(2).isEmpty(), "committed extraction left the output behind");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void incomingBufferPersistsOwnedInput(GameTestHelper helper) {
        UUID bindingId = UUID.randomUUID();
        BindingRecord binding = new BindingRecord(
                1,
                bindingId,
                UUID.randomUUID(),
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 1, 0))),
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 1, 0))),
                Direction.UP,
                "anchor",
                "target",
                "minecraft:furnace",
                1,
                helper.getLevel().getGameTime(),
                helper.getLevel().getGameTime());
        IncomingBuffer original = new IncomingBuffer();
        original.enqueue(binding, "pattern", new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT), 200);
        var tag = new net.minecraft.nbt.CompoundTag();
        original.save(tag, helper.getLevel().registryAccess());

        IncomingBuffer restored = new IncomingBuffer();
        restored.load(tag, helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.recoverableDrops().size(), 1, "buffered input count changed");
        helper.assertTrue(restored.recoverableDrops().getFirst().is(Items.RAW_IRON),
                "buffered input item changed");
        helper.assertValueEqual(restored.removeBinding(bindingId).size(), 1,
                "unbind did not recover buffered input");
        helper.assertTrue(restored.recoverableDrops().isEmpty(),
                "unbind left an owned input in the queue");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void incomingBufferPersistsAllRecipeInputs(GameTestHelper helper) {
        UUID bindingId = UUID.randomUUID();
        BindingRecord binding = new BindingRecord(
                1,
                bindingId,
                UUID.randomUUID(),
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 1, 0))),
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 1, 0))),
                Direction.UP,
                "anchor",
                "target",
                "aeallpattern:test_multi",
                1,
                helper.getLevel().getGameTime(),
                helper.getLevel().getGameTime());
        List<ItemStack> inputs = List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.REDSTONE, 2));
        ResourceLocation recipeId = ResourceLocation.parse("aeallpattern:test_multi");
        RecipeSnapshot recipe = new RecipeSnapshot(
                recipeId,
                inputs,
                new ItemStack(Items.COMPASS),
                new RecipeFingerprint("aeallpattern:test_multi", recipeId.toString(), "inputs", "output", 1),
                20);
        IncomingBuffer original = new IncomingBuffer();
        original.enqueue(binding, "pattern", recipe, inputs, recipe.output(), recipe.processingTicks());
        var tag = new net.minecraft.nbt.CompoundTag();
        original.save(tag, helper.getLevel().registryAccess());

        IncomingBuffer restored = new IncomingBuffer();
        restored.load(tag, helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.recoverableDrops().size(), 2, "multi-input queue lost a stack");
        List<ItemStack> recovered = restored.removeBinding(bindingId);
        helper.assertValueEqual(recovered.size(), 2, "unbind did not recover every recipe input");
        helper.assertTrue(recovered.stream().anyMatch(stack -> stack.is(Items.REDSTONE) && stack.getCount() == 2),
                "multi-input count changed during persistence");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void packagedCrafterAdapterLoadsConditionally(GameTestHelper helper) {
        var crafter = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.fromNamespaceAndPath("packagedexcrafting", "ender_crafter"));
        if (crafter.isEmpty()) {
            helper.succeed();
            return;
        }
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, crafter.get());
        BlockEntity machine = Objects.requireNonNull(helper.getBlockEntity(pos));
        var adapter = MachineAdapterRegistry.find(helper.getLevel(), machine);
        helper.assertTrue(adapter.isPresent(), "PackagedExCrafting adapter was not found");
        helper.assertValueEqual(adapter.orElseThrow().id().toString(), "aeallpattern:packaged_crafting",
                "wrong packaged crafting adapter selected");
        var catalog = RecipeIndexService.catalog(helper.getLevel(), machine, adapter.orElseThrow());
        helper.assertFalse(catalog.recipes().isEmpty(), "PackagedExCrafting catalog is empty");
        RecipeSnapshot recipe = catalog.recipes().getFirst();
        BindingRecord binding = bindingFor(helper, pos, adapter.orElseThrow());
        helper.assertTrue(adapter.orElseThrow().insertRecipe(
                        helper.getLevel(), binding, recipe, recipe.inputs()),
                "PackagedExCrafting machine rejected its discovered recipe");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void mePackagingProviderAcceptsAggregatePatternsConditionally(GameTestHelper helper) {
        var crafter = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.fromNamespaceAndPath("packagedexcrafting", "ender_crafter"));
        var providerBlock = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.fromNamespaceAndPath("packagedauto", "packaging_provider"));
        if (crafter.isEmpty() || providerBlock.isEmpty()) {
            helper.succeed();
            return;
        }
        BlockPos crafterPos = new BlockPos(0, 1, 0);
        helper.setBlock(crafterPos, crafter.get());
        BlockEntity machine = Objects.requireNonNull(helper.getBlockEntity(crafterPos));
        var adapter = MachineAdapterRegistry.find(helper.getLevel(), machine).orElseThrow();
        RecipeSnapshot recipe = RecipeIndexService.catalog(helper.getLevel(), machine, adapter).recipes().getFirst();
        ResourceLocation viewerRecipeId = ResourceLocation.fromNamespaceAndPath(
                "toomanyrecipeviewers", "/" + recipe.recipeId().getNamespace() + "/" + recipe.recipeId().getPath());
        RecipeSnapshot viewerRecipe = RecipeSnapshot.withAlternatives(
                viewerRecipeId,
                recipe.inputAlternatives(),
                recipe.output(),
                recipe.fingerprint(),
                recipe.processingTicks());
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("extendedcrafting", "ender_crafter"),
                "block.extendedcrafting.ender_crafter",
                List.of(AggregateRecipe.from(viewerRecipe)));
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        BlockPos providerPos = new BlockPos(1, 1, 0);
        BlockPos energyPos = new BlockPos(1, 1, 1);
        helper.setBlock(providerPos, providerBlock.get());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.runAfterDelay(10, () -> {
            BlockEntity provider = Objects.requireNonNull(helper.getBlockEntity(providerPos));
            try {
                Object handler = provider.getClass().getMethod("getItemHandler").invoke(provider);
                boolean valid = (boolean) handler.getClass()
                        .getMethod("isItemValid", int.class, ItemStack.class).invoke(handler, 0, aggregate);
                helper.assertTrue(valid, "ME packaging provider rejected an aggregate pattern");
                handler.getClass().getMethod("setStackInSlot", int.class, ItemStack.class)
                        .invoke(handler, 0, aggregate);
                List<?> recipeList = (List<?>) provider.getClass().getField("recipeList").get(provider);
                helper.assertValueEqual(recipeList.size(), 1,
                        "ME packaging provider did not expand the aggregate package recipe");
                IManagedGridNode node = (IManagedGridNode) provider.getClass().getMethod("getMainNode").invoke(provider);
                IPatternDetails unpackaging = assertAggregatePackageWorkflow(
                        helper, provider, node, recipe.output());
                boolean accepted = (boolean) provider.getClass()
                        .getMethod("pushPattern", IPatternDetails.class, KeyCounter[].class)
                        .invoke(provider, unpackaging, new KeyCounter[0]);
                helper.assertTrue(accepted, "ME packaging provider did not send the unpackaged recipe to its target");
                helper.assertTrue((boolean) machine.getClass().getMethod("isBusy").invoke(machine),
                        "target packaged crafter did not accept the unpackaged recipe");

                AggregatePatternRef genericRef = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                        helper.getLevel().getServer(),
                        ResourceLocation.fromNamespaceAndPath("minecraft", "furnace"),
                        "block.minecraft.furnace",
                        List.of(AggregateRecipe.from(recipe)));
                ItemStack genericAggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
                genericAggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), genericRef);
                handler.getClass().getMethod("setStackInSlot", int.class, ItemStack.class)
                        .invoke(handler, 0, genericAggregate);
                helper.assertValueEqual(recipeList.size(), 1,
                        "ME packaging provider did not create a generic processing package recipe");
                helper.assertValueEqual(recipeList.getFirst().getClass().getName(),
                        "thelm.packagedauto.recipe.ProcessingPackageRecipeInfo",
                        "ME packaging provider used the wrong generic package recipe type");
                assertAggregatePackageWorkflow(helper, provider, node, recipe.output());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to inspect the optional ME packaging provider", error);
            }
            helper.succeed();
        });
    }

    private static IPatternDetails assertAggregatePackageWorkflow(
            GameTestHelper helper, BlockEntity provider, IManagedGridNode node, ItemStack output)
            throws ReflectiveOperationException {
        List<?> available = (List<?>) provider.getClass().getMethod("getAvailablePatterns").invoke(provider);
        List<IPatternDetails> packaging = available.stream()
                .filter(pattern -> pattern.getClass().getName().endsWith("PackageCraftingPatternDetails"))
                .map(IPatternDetails.class::cast)
                .toList();
        List<IPatternDetails> unpackaging = available.stream()
                .filter(pattern -> pattern.getClass().getName().endsWith("RecipeCraftingPatternDetails"))
                .map(IPatternDetails.class::cast)
                .toList();
        helper.assertTrue(available.stream().noneMatch(
                        pattern -> pattern.getClass().getName().endsWith("DirectCraftingPatternDetails")),
                "aggregate recipe still exposed PackagedAuto's direct shortcut");
        helper.assertFalse(packaging.isEmpty(), "aggregate recipe did not expose package crafting patterns");
        helper.assertValueEqual(unpackaging.size(), 1,
                "aggregate recipe did not expose exactly one unpackaging pattern");

        Set<Object> packageOutputs = packaging.stream()
                .flatMap(pattern -> pattern.getOutputs().stream())
                .map(GenericStack::what)
                .collect(java.util.stream.Collectors.toSet());
        for (IPatternDetails.IInput input : unpackaging.getFirst().getInputs()) {
            helper.assertTrue(Arrays.stream(input.getPossibleInputs())
                            .map(GenericStack::what)
                            .anyMatch(packageOutputs::contains),
                    "unpackaging recipe input is not produced by a package crafting pattern");
        }
        helper.assertTrue(Objects.requireNonNull(node.getGrid()).getCraftingService()
                        .isCraftable(AEItemKey.of(output)),
                "AE network did not receive the aggregate package workflow's final output");
        return unpackaging.getFirst();
    }

    @GameTest(template = "empty")
    public static void legacyAggregateInputAlternativesAreMigrated(GameTestHelper helper) {
        List<GenericStack> alternatives = new ArrayList<>();
        int limit = AggregateInputSlot.configuredAlternativeLimit();
        for (int index = 0; index <= limit; index++) {
            alternatives.add(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.STONE))));
        }
        AggregateInputSlot migrated = AggregateInputSlot.fromSavedData(alternatives, Optional.empty());
        helper.assertValueEqual(migrated.alternatives().size(), limit,
                "legacy aggregate alternatives were not truncated to the current limit");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void packagedAvaritiaAdapterExecutesConditionally(GameTestHelper helper) {
        var crafter = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.fromNamespaceAndPath("packagedavaritia", "extreme_crafter"));
        if (crafter.isEmpty()) {
            helper.succeed();
            return;
        }
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, crafter.get());
        BlockEntity machine = Objects.requireNonNull(helper.getBlockEntity(pos));
        var adapter = MachineAdapterRegistry.find(helper.getLevel(), machine);
        helper.assertTrue(adapter.isPresent(), "PackagedAvaritia adapter was not found");
        var catalog = RecipeIndexService.catalog(helper.getLevel(), machine, adapter.orElseThrow());
        helper.assertFalse(catalog.recipes().isEmpty(), "PackagedAvaritia catalog is empty");
        RecipeSnapshot recipe = catalog.recipes().getFirst();
        helper.assertTrue(adapter.orElseThrow().insertRecipe(
                        helper.getLevel(), bindingFor(helper, pos, adapter.orElseThrow()), recipe, recipe.inputs()),
                "PackagedAvaritia machine rejected its discovered recipe");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void mekmmItemMachineAdapterLoadsConditionally(GameTestHelper helper) {
        var lathe = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.fromNamespaceAndPath("mekmm", "cnc_lathe"));
        if (lathe.isEmpty()) {
            helper.succeed();
            return;
        }
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, lathe.get());
        BlockEntity machine = Objects.requireNonNull(helper.getBlockEntity(pos));
        var adapter = MachineAdapterRegistry.find(helper.getLevel(), machine);
        helper.assertTrue(adapter.isPresent(), "MekMM lathing adapter was not found");
        helper.assertValueEqual(adapter.orElseThrow().id().toString(), "mekanism:lathing",
                "wrong MekMM adapter selected");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void mekanismAdapterLoadsConditionally(GameTestHelper helper) {
        var smelter = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.fromNamespaceAndPath("mekanism", "energized_smelter"));
        if (smelter.isEmpty()) {
            helper.succeed();
            return;
        }
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, smelter.get());
        BlockEntity machine = Objects.requireNonNull(helper.getBlockEntity(pos));
        var adapter = MachineAdapterRegistry.find(helper.getLevel(), machine);
        helper.assertTrue(adapter.isPresent(), "Mekanism smelting adapter was not found");
        helper.assertValueEqual(adapter.orElseThrow().id().toString(), "mekanism:smelting",
                "wrong Mekanism adapter selected");
        var catalog = RecipeIndexService.catalog(helper.getLevel(), machine, adapter.orElseThrow());
        helper.assertFalse(catalog.recipes().isEmpty(), "Mekanism smelting catalog is empty");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void aggregateSelectionFiltersExpandedPatterns(GameTestHelper helper) {
        List<AggregateRecipe> recipes = List.of(
                new AggregateRecipe(
                        "selection-iron",
                        ResourceLocation.fromNamespaceAndPath("aeallpattern", "selection_iron"),
                        AggregatePatternKind.PROCESSING,
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.RAW_IRON)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT)))),
                        1),
                new AggregateRecipe(
                        "selection-gold",
                        ResourceLocation.fromNamespaceAndPath("aeallpattern", "selection_gold"),
                        AggregatePatternKind.PROCESSING,
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.RAW_GOLD)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT)))),
                        1));
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "selection_test_machine"),
                "block.aeallpattern.selection_test_machine", recipes);
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        helper.assertValueEqual(AggregatePatternExpander.expand(aggregate, helper.getLevel()).size(), 2,
                "aggregate without a selection must publish every child pattern");

        aggregate.set(ModDataComponents.AGGREGATE_PATTERN_SELECTION.get(),
                AggregatePatternSelection.ALL_ENABLED.toggled("selection-iron"));
        helper.assertValueEqual(AggregatePatternExpander.expand(aggregate, helper.getLevel()).size(), 1,
                "deselected child pattern was still published");
        helper.assertValueEqual(
                AggregatePatternExpander.expand(aggregate, helper.getLevel()).getFirst().getOutputs().size(), 1,
                "unexpected pattern survived selection filtering");

        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, aggregate);
        var menu = new AggregatePatternSelectionMenu(
                1,
                player.getInventory(),
                InteractionHand.MAIN_HAND,
                AggregatePatternSelectionMenu.entriesFromRecipes(recipes),
                aggregate.getOrDefault(ModDataComponents.AGGREGATE_PATTERN_SELECTION.get(),
                        AggregatePatternSelection.ALL_ENABLED));
        helper.assertTrue(menu.stillValid(player), "owner could not open the selection menu");
        helper.assertTrue(menu.selectedCount() == 1, "selection menu counted the wrong enabled patterns");

        // Toggling the remaining enabled pattern must disable it on the held stack.
        helper.assertTrue(menu.clickMenuButton(player, 1), "menu rejected a pattern toggle");
        var stored = aggregate.get(ModDataComponents.AGGREGATE_PATTERN_SELECTION.get());
        helper.assertTrue(stored != null && !stored.isEnabled("selection-gold"),
                "toggled pattern stayed enabled on the held item");

        // Bulk actions must reach both extremes compactly.
        helper.assertTrue(menu.clickMenuButton(player, AggregatePatternSelectionMenu.DESELECT_ALL),
                "menu rejected deselect-all");
        helper.assertTrue(aggregate.get(ModDataComponents.AGGREGATE_PATTERN_SELECTION.get()).isNoneEnabled(),
                "deselect-all did not disable every pattern");
        helper.assertTrue(AggregatePatternExpander.expand(aggregate, helper.getLevel()).isEmpty(),
                "fully deselected aggregate still published patterns");

        // The provider fallback decodes the stack through the aggregate decoder; a fully
        // deselected aggregate must decode to a placeholder marker (slot-valid, never published).
        var decoded = PatternDetailsHelper.decodePattern(aggregate.copy(), helper.getLevel());
        helper.assertTrue(decoded instanceof AggregatePatternMarkerDetails marker && marker.isPlaceholder(),
                "fully deselected aggregate did not decode to a placeholder marker");

        helper.assertTrue(menu.clickMenuButton(player, AggregatePatternSelectionMenu.SELECT_ALL),
                "menu rejected select-all");
        helper.assertFalse(aggregate.has(ModDataComponents.AGGREGATE_PATTERN_SELECTION.get()),
                "select-all did not remove the selection component");
        helper.assertValueEqual(AggregatePatternExpander.expand(aggregate, helper.getLevel()).size(), 2,
                "select-all did not restore every child pattern");

        // The server-wide expansion cache must reuse the immutable expanded list.
        helper.assertTrue(
                AggregatePatternExpander.expand(aggregate, helper.getLevel())
                        == AggregatePatternExpander.expand(aggregate, helper.getLevel()),
                "expansion cache did not reuse the expanded pattern list");

        // With children restored the marker must no longer be a placeholder.
        var restored = PatternDetailsHelper.decodePattern(aggregate.copy(), helper.getLevel());
        helper.assertTrue(
                restored instanceof AggregatePatternMarkerDetails marker && !marker.isPlaceholder(),
                "restored aggregate decoded to a placeholder marker");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void scheduledExpansionCompletesAcrossTicks(GameTestHelper helper) {
        List<AggregateRecipe> recipes = List.of(
                new AggregateRecipe(
                        "scheduled-alpha",
                        ResourceLocation.fromNamespaceAndPath("aeallpattern", "scheduled_alpha"),
                        AggregatePatternKind.PROCESSING,
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.RAW_IRON)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT)))),
                        1),
                new AggregateRecipe(
                        "scheduled-beta",
                        ResourceLocation.fromNamespaceAndPath("aeallpattern", "scheduled_beta"),
                        AggregatePatternKind.PROCESSING,
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.RAW_GOLD)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT)))),
                        1));
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "scheduled_test_machine"),
                "block.aeallpattern.scheduled_test_machine", recipes);
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        AggregatePatternExpander.setSynchronous(false);
        try {
            boolean[] completed = {false};
            List<IPatternDetails> first =
                    AggregatePatternExpander.expandScheduled(aggregate, helper.getLevel(), () -> completed[0] = true);
            helper.assertTrue(first.isEmpty(),
                    "cold scheduled expansion must not block the calling tick");

            // Manual pump: the server tick handler advances the job within its budget.
            int guard = 0;
            while (!completed[0] && guard++ < 400) {
                AggregatePatternExpander.tickServer(helper.getLevel().getServer());
            }
            helper.assertTrue(completed[0], "scheduled expansion never completed");

            List<IPatternDetails> done =
                    AggregatePatternExpander.expandScheduled(aggregate, helper.getLevel(), () -> {});
            helper.assertValueEqual(done.size(), 2,
                    "completed scheduled expansion did not publish every child pattern");
            helper.assertValueEqual(
                    AggregatePatternExpander.expand(aggregate, helper.getLevel()).size(), 2,
                    "scheduled and synchronous expansions disagree");
        } finally {
            AggregatePatternExpander.setSynchronous(true);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void scheduledExpansionRetriesAfterRecipeReload(GameTestHelper helper) {
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "reload_retry_machine"),
                "block.aeallpattern.reload_retry_machine",
                List.of(new AggregateRecipe(
                        "reload-retry",
                        ResourceLocation.fromNamespaceAndPath("aeallpattern", "reload_retry"),
                        AggregatePatternKind.PROCESSING,
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.RAW_IRON)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT)))),
                        1)));
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        AggregatePatternExpander.setSynchronous(false);
        try {
            boolean[] retryRequested = {false};
            AggregatePatternExpander.expandScheduled(
                    aggregate, helper.getLevel(), () -> retryRequested[0] = true);
            RecipeIndexService.invalidate();
            AggregatePatternExpander.tickServer(helper.getLevel().getServer());
            helper.assertTrue(retryRequested[0],
                    "recipe reload discarded the provider retry callback");
        } finally {
            AggregatePatternExpander.setSynchronous(true);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void worldLoadQueuesEveryColdAggregate(GameTestHelper helper) {
        AggregatePatternExpander.setSynchronous(false);
        try {
            int count = 12;
            boolean[] completed = new boolean[count];
            for (int index = 0; index < count; index++) {
                AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                        helper.getLevel().getServer(),
                        ResourceLocation.fromNamespaceAndPath("aeallpattern", "queue_machine_" + index),
                        "block.aeallpattern.queue_machine_" + index,
                        List.of(new AggregateRecipe(
                                "queue-" + index,
                                ResourceLocation.fromNamespaceAndPath("aeallpattern", "queue_" + index),
                                AggregatePatternKind.PROCESSING,
                                List.of(Objects.requireNonNull(GenericStack.fromItemStack(
                                        new ItemStack(Items.RAW_IRON)))),
                                List.of(Objects.requireNonNull(GenericStack.fromItemStack(
                                        new ItemStack(Items.IRON_INGOT)))),
                                1)));
                ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
                aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);
                int job = index;
                AggregatePatternExpander.expandScheduled(
                        aggregate, helper.getLevel(), () -> completed[job] = true);
            }

            int guard = 0;
            while (java.util.stream.IntStream.range(0, completed.length)
                    .anyMatch(index -> !completed[index]) && guard++ < 120) {
                AggregatePatternExpander.tickServer(helper.getLevel().getServer());
            }
            helper.assertTrue(java.util.stream.IntStream.range(0, completed.length)
                            .allMatch(index -> completed[index]),
                    "world-load expansion queue discarded cold aggregate providers");
        } finally {
            AggregatePatternExpander.setSynchronous(true);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void sharedAggregateRefreshesEveryProvider(GameTestHelper helper) {
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "shared_queue_machine"),
                "block.aeallpattern.shared_queue_machine",
                List.of(new AggregateRecipe(
                        "shared-queue",
                        ResourceLocation.fromNamespaceAndPath("aeallpattern", "shared_queue"),
                        AggregatePatternKind.PROCESSING,
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(
                                new ItemStack(Items.RAW_IRON)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(
                                new ItemStack(Items.IRON_INGOT)))),
                        1)));
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        AggregatePatternExpander.setSynchronous(false);
        try {
            int providerCount = 32;
            boolean[] refreshed = new boolean[providerCount];
            for (int provider = 0; provider < providerCount; provider++) {
                int index = provider;
                AggregatePatternExpander.expandScheduled(
                        aggregate, helper.getLevel(), () -> refreshed[index] = true);
            }
            int guard = 0;
            while (java.util.stream.IntStream.range(0, refreshed.length)
                    .anyMatch(index -> !refreshed[index]) && guard++ < 120) {
                AggregatePatternExpander.tickServer(helper.getLevel().getServer());
            }
            helper.assertTrue(java.util.stream.IntStream.range(0, refreshed.length)
                            .allMatch(index -> refreshed[index]),
                    "shared aggregate expansion discarded provider refresh callbacks");
        } finally {
            AggregatePatternExpander.setSynchronous(true);
        }
        helper.succeed();
    }

    /**
     * Guards the addon provider mixins against an unloadable helper class.
     *
     * <p>Every class under the mixin package declared in {@code aeallpattern.mixins.json} is owned
     * by that config and throws {@code IllegalClassLoadError} as soon as a transformed target class
     * references it at runtime. That is exactly how the 0.1.16 pigmee provider crashed on load, and
     * it cannot be caught by ordinary tests because a fresh dev world never contains the block.
     * The test exercises the host refresh directly and only runs when the addon is present.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void addonProviderMixinsStayLoadable(GameTestHelper helper) {
        var block = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.fromNamespaceAndPath("ae2lt", "pigmee_pattern_provider"));
        if (block.isEmpty()) {
            // Addon missing in this environment; there is no target class to exercise.
            helper.succeed();
            return;
        }
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, block.get().defaultBlockState());
        helper.runAfterDelay(5, () -> {
            var entity = Objects.requireNonNull(helper.getBlockEntity(pos));
            helper.assertTrue(true, "pigmee pattern provider block entity was not created");
            try {
                java.lang.reflect.Method updatePatterns = entity.getClass().getDeclaredMethod("updatePatterns");
                updatePatterns.setAccessible(true);
                updatePatterns.invoke(entity);
            } catch (ReflectiveOperationException error) {
                helper.fail("addon provider refresh was not reachable: " + error);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void infusingAggregateIsEditableByPatternEditor(GameTestHelper helper) {
        List<AggregateRecipe> recipes = List.of(
                new AggregateRecipe(
                        "infusion-gold",
                        ResourceLocation.fromNamespaceAndPath("aeallpattern", "infusion_gold"),
                        AggregatePatternKind.PROCESSING,
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.REDSTONE)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT)))),
                        1),
                new AggregateRecipe(
                        "infusion-diamond",
                        ResourceLocation.fromNamespaceAndPath("aeallpattern", "infusion_diamond"),
                        AggregatePatternKind.PROCESSING,
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.LAPIS_LAZULI)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.DIAMOND)))),
                        1));
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("mekanism", "advanced_infusing_factory"),
                "block.mekanism.advanced_infusing_factory", recipes);
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        helper.assertTrue(AggregatePatternEditPolicy.isEditorEditable(aggregate, helper.getLevel()),
                "infusing-factory aggregate was not classified as editor-editable");
        IPatternDetails editable = AggregatePatternEditPolicy.decodeForEditor(aggregate, helper.getLevel());
        helper.assertTrue(editable instanceof appeng.crafting.pattern.AEProcessingPattern,
                "editable aggregate did not unwrap down to an AE2 processing pattern");
        helper.assertTrue(editable.getOutputs().getFirst().what() instanceof AEItemKey key
                        && key.is(Items.GOLD_INGOT),
                "editor did not see the first selected child of the aggregate");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nonInfusingAggregateStaysEditorLocked(GameTestHelper helper) {
        AggregateRecipe recipe = new AggregateRecipe(
                "furnace-iron",
                ResourceLocation.fromNamespaceAndPath("aeallpattern", "furnace_iron"),
                AggregatePatternKind.PROCESSING,
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.RAW_IRON)))),
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT)))),
                1);
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.withDefaultNamespace("furnace"),
                "block.minecraft.furnace", List.of(recipe));
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        helper.assertFalse(AggregatePatternEditPolicy.isEditorEditable(aggregate, helper.getLevel()),
                "furnace aggregate was wrongly classified as editor-editable");
        IPatternDetails decoded = AggregatePatternEditPolicy.decodeForEditor(aggregate, helper.getLevel());
        helper.assertTrue(decoded instanceof AggregatePatternMarkerDetails,
                "non-infusing aggregate was unwrapped for the editor and must stay locked");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void editorNeverReadsDeselectedChildren(GameTestHelper helper) {
        List<AggregateRecipe> recipes = List.of(
                new AggregateRecipe(
                        "infusion-gold-2",
                        ResourceLocation.fromNamespaceAndPath("aeallpattern", "infusion_gold_2"),
                        AggregatePatternKind.PROCESSING,
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.REDSTONE)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT)))),
                        1),
                new AggregateRecipe(
                        "infusion-diamond-2",
                        ResourceLocation.fromNamespaceAndPath("aeallpattern", "infusion_diamond_2"),
                        AggregatePatternKind.PROCESSING,
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.LAPIS_LAZULI)))),
                        List.of(Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(Items.DIAMOND)))),
                        1));
        AggregatePatternRef ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(),
                ResourceLocation.fromNamespaceAndPath("mekanism", "basic_infusing_factory"),
                "block.mekanism.basic_infusing_factory", recipes);
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);

        // First child deselected: the editor must see the still-selected second child.
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN_SELECTION.get(),
                new AggregatePatternSelection(false, List.of("infusion-gold-2")));
        IPatternDetails editable = AggregatePatternEditPolicy.decodeForEditor(aggregate, helper.getLevel());
        helper.assertTrue(editable.getOutputs().getFirst().what() instanceof AEItemKey key
                        && key.is(Items.DIAMOND),
                "editor exposed a deselected child instead of the first selected one");

        // Everything deselected: the editor must get the placeholder marker, not any child.
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN_SELECTION.get(), AggregatePatternSelection.NONE_ENABLED);
        IPatternDetails placeholder = AggregatePatternEditPolicy.decodeForEditor(aggregate, helper.getLevel());
        helper.assertTrue(placeholder instanceof AggregatePatternMarkerDetails marker && marker.isPlaceholder(),
                "fully deselected aggregate handed a child to the editor");
        helper.succeed();
    }

    private static BindingRecord bindingFor(
            GameTestHelper helper, BlockPos relativeTarget, io.github.langqi99.aeallpattern.machine.MachineAdapter adapter) {
        return new BindingRecord(
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 1, 1))),
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relativeTarget)),
                Direction.NORTH,
                "anchor",
                "target",
                adapter.id().toString(),
                adapter.schemaVersion(),
                helper.getLevel().getGameTime(),
                helper.getLevel().getGameTime());
    }

    private static List<String> configuredModIds(String property) {
        String value = System.getProperty(property, "");
        if (value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .toList();
    }
}
