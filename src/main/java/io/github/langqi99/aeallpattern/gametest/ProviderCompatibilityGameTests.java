package io.github.langqi99.aeallpattern.gametest;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.*;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Uses actual addon inventories, not a mocked slot filter. */
@GameTestHolder(AeAllPattern.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProviderCompatibilityGameTests {
    private static final BlockPos ECO_RESTART = new BlockPos(1032, 80, 1032);
    private static final BlockPos ALLOY_RESTART = new BlockPos(40, 80, 8);
    @net.minecraft.gametest.framework.BeforeBatch(batch = "provider_compat")
    public static void coldExpansion(net.minecraft.server.level.ServerLevel level) {
        AggregatePatternExpander.setSynchronous(false);
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = "provider_compat")
    public static void restoreExpansionMode(net.minecraft.server.level.ServerLevel level) {
        AggregatePatternExpander.setSynchronous(true);
    }

    private static ResourceLocation id(String value) { return new ResourceLocation(value); }

    private static ItemStack craftingAggregate(GameTestHelper helper) {
        return craftingAggregate(helper, "");
    }

    private static ItemStack craftingAggregate(GameTestHelper helper, String salt) {
        var recipes = List.of(new AggregateRecipe("compat-planks" + salt, id("minecraft:oak_planks"),
                AggregatePatternKind.CRAFTING,
                List.of(new GenericStack(AEItemKey.of(Items.OAK_LOG), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4)), 1),
                new AggregateRecipe("compat-birch-planks" + salt, id("minecraft:birch_planks"),
                        AggregatePatternKind.CRAFTING,
                        List.of(new GenericStack(AEItemKey.of(Items.BIRCH_LOG), 1)),
                        List.of(new GenericStack(AEItemKey.of(Items.BIRCH_PLANKS), 4)), 1));
        var ref = AggregatePatternLibrary.get(helper.getLevel().getServer()).put(
                helper.getLevel().getServer(), id("minecraft:crafting_table"),
                "block.minecraft.crafting_table", recipes);
        var stack = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        ModDataComponents.setAggregatePattern(stack, ref);
        return stack;
    }

    @GameTest(batch = "provider_compat", template = "empty", timeoutTicks = 100)
    public static void ecoPublishesAggregateAndDoesNotDuplicateOnRefresh(GameTestHelper helper) {
        var block = BuiltInRegistries.BLOCK.getOptional(id("neoecoae:crafting_pattern_bus"));
        if (block.isEmpty()) { helper.succeed(); return; }
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, block.get());
        helper.runAfterDelay(10, () -> {
            try {
                Object bus = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
                var field = bus.getClass().getDeclaredField("inventory");
                field.setAccessible(true);
                var inventory = (InternalInventory) field.get(bus);
                var aggregate = craftingAggregate(helper);
                helper.assertTrue(inventory.isItemValid(0, aggregate), "ECO rejected aggregate slot insertion");
                inventory.setItemDirect(0, aggregate);
                var update = bus.getClass().getDeclaredMethod("updatePatternDetails");
                update.setAccessible(true);
                update.invoke(bus);
                helper.runAfterDelay(10, () -> {
                    try {
                        var patterns = (List<?>) bus.getClass().getMethod("getAvailablePatterns").invoke(bus);
                        helper.assertTrue(patterns.size() == 2, "ECO did not publish every child exactly once: " + patterns.size());
                        update.invoke(bus);
                        helper.runAfterDelay(10, () -> {
                            try {
                                var again = (List<?>) bus.getClass().getMethod("getAvailablePatterns").invoke(bus);
                                helper.assertTrue(again.size() == 2, "ECO duplicated aggregate children on refresh");
                                inventory.setItemDirect(0, ItemStack.EMPTY);
                                update.invoke(bus);
                                helper.runAfterDelay(10, () -> {
                                    try {
                                        helper.assertTrue(((List<?>) bus.getClass().getMethod("getAvailablePatterns").invoke(bus)).isEmpty(),
                                                "ECO retained a removed aggregate");
                                        helper.succeed();
                                    } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                                });
                            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                        });
                    } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                });
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        });
    }

    @GameTest(batch = "provider_compat", template = "empty", timeoutTicks = 100)
    public static void ecoColdCallbackSkipsDestroyedNode(GameTestHelper helper) {
        var block = BuiltInRegistries.BLOCK.getOptional(id("neoecoae:crafting_pattern_bus"));
        if (block.isEmpty()) { helper.succeed(); return; }
        try {
            Class.forName("cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusCatalog");
        } catch (ClassNotFoundException legacy) { helper.succeed(); return; }
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, block.get());
        helper.runAfterDelay(10, () -> {
            try {
                var bus = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
                var field = bus.getClass().getDeclaredField("inventory");
                field.setAccessible(true);
                var inventory = (InternalInventory) field.get(bus);
                inventory.setItemDirect(0, craftingAggregate(helper));
                var node = (appeng.api.networking.IManagedGridNode) bus.getClass().getMethod("getMainNode").invoke(bus);
                node.destroy();
                var update = bus.getClass().getDeclaredMethod("updatePatternDetails");
                update.setAccessible(true);
                update.invoke(bus);
                helper.runAfterDelay(10, () -> {
                    helper.assertTrue(node.getNode() == null, "Test did not destroy the ECO node");
                    helper.assertTrue(inventory.getStackInSlot(0).is(ModItems.AGGREGATE_PATTERN.get()),
                            "Deferred refresh lost the aggregate inventory");
                    helper.succeed();
                });
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        });
    }

    @GameTest(batch = "provider_compat", template = "empty", timeoutTicks = 60)
    public static void alloyAssemblyAcceptsAggregateButRejectsUnrelatedItems(GameTestHelper helper) {
        var block = BuiltInRegistries.BLOCK.getOptional(id("useless_mod:me_pattern_assembly"));
        if (block.isEmpty()) { helper.succeed(); return; }
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, block.get());
        helper.runAfterDelay(5, () -> {
            try {
                Object assembly = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
                Object inventory = assembly.getClass().getMethod("getPatterns").invoke(assembly);
                var valid = inventory.getClass().getMethod("isItemValid", int.class, ItemStack.class);
                var aggregate = craftingAggregate(helper);
                helper.assertTrue((boolean) valid.invoke(inventory, 0, aggregate), "Alloy assembly rejected aggregate");
                helper.assertTrue(!(boolean) valid.invoke(inventory, 0, new ItemStack(Items.DIRT)), "Alloy assembly accepted dirt");
                var insert = inventory.getClass().getMethod("insertItem", int.class, ItemStack.class, boolean.class);
                helper.assertTrue(((ItemStack) insert.invoke(inventory, 0, aggregate, false)).isEmpty(),
                        "Alloy assembly did not accept aggregate through real insertion path");
                helper.succeed();
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        });
    }

    @GameTest(batch = "provider_compat", template = "empty", timeoutTicks = 160)
    public static void ecoAggregateSurvivesRealServerRestart(GameTestHelper helper) {
        String phase = System.getProperty("aeallpattern.persistencePhase", "");
        var block = BuiltInRegistries.BLOCK.getOptional(id("neoecoae:crafting_pattern_bus"));
        if (phase.isEmpty() || block.isEmpty()) { helper.succeed(); return; }
        var level = helper.getLevel();
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.PORTAL,
                new net.minecraft.world.level.ChunkPos(ECO_RESTART), 3, ECO_RESTART, true);
        level.getChunkAt(ECO_RESTART);
        if (phase.equals("seed")) {
            level.setBlockAndUpdate(ECO_RESTART, block.get().defaultBlockState());
            level.setBlockAndUpdate(ECO_RESTART.south(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        }
        helper.runAfterDelay(15, () -> {
            try {
                var bus = level.getBlockEntity(ECO_RESTART);
                helper.assertTrue(bus != null, "ECO bus missing on restart");
                var field = bus.getClass().getDeclaredField("inventory");
                field.setAccessible(true);
                var inventory = (InternalInventory) field.get(bus);
                if (phase.equals("seed")) {
                    inventory.setItemDirect(0, craftingAggregate(helper, "-eco-restart"));
                    bus.setChanged();
                }
                // Verify must not touch the inventory or call a refresh method.
                helper.runAfterDelay(30, () -> {
                    try {
                        helper.assertTrue(inventory.getStackInSlot(0).is(ModItems.AGGREGATE_PATTERN.get()),
                                "ECO lost aggregate on restart");
                        var patterns = (List<?>) bus.getClass().getMethod("getAvailablePatterns").invoke(bus);
                        helper.assertTrue(patterns.size() == 2, "ECO needs reinsertion after restart: " + patterns.size());
                        var node = (appeng.api.networking.IManagedGridNode) bus.getClass().getMethod("getMainNode").invoke(bus);
                        helper.assertTrue(node.getGrid() != null
                                        && node.getGrid().getCraftingService().isCraftable(AEItemKey.of(Items.BIRCH_PLANKS)),
                                "ECO restored local patterns but did not republish them to AE; node="
                                        + (node.getNode() != null) + ", active=" + node.isActive()
                                        + ", online=" + node.isOnline() + ", grid=" + (node.getGrid() != null));
                        AeAllPattern.LOGGER.info("ECO_RESTART_{}_PASSED", phase);
                        helper.succeed();
                    } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                });
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        });
    }

    @GameTest(batch = "provider_compat", template = "empty", timeoutTicks = 120)
    public static void vanillaAsyncAggregateSurvivesRestart(GameTestHelper helper) {
        String phase = System.getProperty("aeallpattern.persistencePhase", "");
        if (phase.isEmpty()) { helper.succeed(); return; }
        var pos = new BlockPos(1096, 80, 1032);
        var level = helper.getLevel();
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.PORTAL,
                new net.minecraft.world.level.ChunkPos(pos), 3, pos, true);
        level.getChunkAt(pos);
        if (phase.equals("seed")) {
            level.setBlockAndUpdate(pos, appeng.core.definitions.AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
            level.setBlockAndUpdate(pos.south(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        }
        helper.runAfterDelay(15, () -> {
            var provider = (appeng.blockentity.crafting.PatternProviderBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(provider != null, "Vanilla provider lost on restart");
            if (phase.equals("seed")) {
                provider.getLogic().getPatternInv().setItemDirect(0, craftingAggregate(helper, "-vanilla-restart"));
                provider.setChanged();
            }
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(provider.getLogic().getAvailablePatterns().size() == 2,
                        "Vanilla provider needs reinsertion after cold asynchronous load");
                var grid = provider.getMainNode().getGrid();
                helper.assertTrue(grid != null && grid.getCraftingService().isCraftable(AEItemKey.of(Items.BIRCH_PLANKS)),
                        "Vanilla provider did not republish to the AE network after cold load");
                AeAllPattern.LOGGER.info("VANILLA_ASYNC_RESTART_{}_PASSED", phase);
                helper.succeed();
            });
        });
    }

    @GameTest(batch = "provider_compat", template = "empty", timeoutTicks = 160)
    public static void formedAlloyPublishesAggregateAndSurvivesRestart(GameTestHelper helper) {
        var coreBlock = BuiltInRegistries.BLOCK.getOptional(id("useless_mod:multiblock_alloy_furnace_core"));
        if (coreBlock.isEmpty()) { helper.succeed(); return; }
        String phase = System.getProperty("aeallpattern.persistencePhase", "");
        var level = helper.getLevel();
        BlockPos corePos = phase.isEmpty() ? helper.absolutePos(new BlockPos(3, 1, 3)) : ALLOY_RESTART;
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.PORTAL,
                new net.minecraft.world.level.ChunkPos(corePos), 3, corePos, true);
        level.getChunkAt(corePos);
        try {
            var structure = Class.forName("com.sorrowmist.useless.content.blocks.multiblock.OmniversalAlloyFurnaceStructure");
            var direction = net.minecraft.core.Direction.NORTH;
            var transform = structure.getMethod("toWorld", BlockPos.class, net.minecraft.core.Direction.class, BlockPos.class);
            BlockPos assemblyPos = (BlockPos) transform.invoke(null, corePos, direction, new BlockPos(-1, 0, 0));
            if (!phase.equals("verify")) {
                for (Object entry : (List<?>) structure.getMethod("entries").invoke(null)) {
                    BlockPos local = (BlockPos) entry.getClass().getMethod("localPos").invoke(entry);
                    String part = entry.getClass().getMethod("part").invoke(entry).toString();
                    String blockId = switch (part) {
                        case "CORE" -> "useless_mod:multiblock_alloy_furnace_core";
                        case "COIL" -> "useless_mod:useless_coil_tier_1";
                        case "AIR" -> "minecraft:air";
                        default -> local.equals(new BlockPos(-1, 0, 0)) ? "useless_mod:me_pattern_assembly"
                                : local.equals(new BlockPos(1, 0, 0)) ? "useless_mod:omniversal_mold_hub"
                                : "useless_mod:omniversal_furnace_casing";
                    };
                    var state = BuiltInRegistries.BLOCK.getOptional(id(blockId)).orElseThrow().defaultBlockState();
                    if (part.equals("CORE")) state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, direction);
                    level.setBlockAndUpdate((BlockPos) transform.invoke(null, corePos, direction, local), state);
                }
                // The assembly is at the west edge, so this does not replace a structure block.
                level.setBlockAndUpdate(assemblyPos.west(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            }
            helper.runAfterDelay(20, () -> {
                try {
                    Object core = level.getBlockEntity(corePos);
                    helper.assertTrue((boolean) core.getClass().getMethod("canPublishPatterns").invoke(core), "Alloy fixture did not form");
                    Object assembly = level.getBlockEntity(assemblyPos);
                    Object inventory = assembly.getClass().getMethod("getPatterns").invoke(assembly);
                    if (!phase.equals("verify")) {
                        // Seed may be rerun against the same dedicated fixture directory.
                        inventory.getClass().getMethod("setStackInSlot", int.class, ItemStack.class)
                                .invoke(inventory, 0, ItemStack.EMPTY);
                        var insert = inventory.getClass().getMethod("insertItem", int.class, ItemStack.class, boolean.class);
                        helper.assertTrue(((ItemStack) insert.invoke(inventory, 0, craftingAggregate(helper), false)).isEmpty(), "Alloy rejected aggregate");
                    }
                    var stored = new appeng.api.stacks.KeyCounter();
                    var assemblyNode = (appeng.api.networking.IManagedGridNode) assembly.getClass().getMethod("getMainNode").invoke(assembly);
                    assemblyNode.getGrid().getStorageService().addGlobalStorageProvider(mounts -> mounts.mount(new appeng.api.storage.MEStorage() {
                        @Override public long insert(appeng.api.stacks.AEKey key, long amount,
                                appeng.api.config.Actionable mode, appeng.api.networking.security.IActionSource source) {
                            if (mode == appeng.api.config.Actionable.MODULATE) stored.add(key, amount);
                            return amount;
                        }
                        @Override public void getAvailableStacks(appeng.api.stacks.KeyCounter out) { out.addAll(stored); }
                        @Override public net.minecraft.network.chat.Component getDescription() {
                            return net.minecraft.network.chat.Component.literal("Alloy regression output sink");
                        }
                    }));
                    helper.runAfterDelay(30, () -> {
                        try {
                            var patterns = (List<?>) core.getClass().getMethod("getAvailablePatterns").invoke(core);
                            helper.assertTrue(patterns.size() == 2, "Formed alloy did not publish every child exactly once: " + patterns.size());
                            var node = (appeng.api.networking.IManagedGridNode) assembly.getClass().getMethod("getMainNode").invoke(assembly);
                            helper.assertTrue(node.getGrid() != null && node.getGrid().getCraftingService().isCraftable(AEItemKey.of(Items.OAK_PLANKS)),
                                    "Alloy aggregate not craftable in AE network");
                            // Exercise real dispatch, not only slot acceptance and publication.
                            IPatternDetails first = (IPatternDetails) patterns.get(0);
                            var expectedOutput = first.getPrimaryOutput();
                            long before = stored.get(expectedOutput.what());
                            var inputs = first.getInputs();
                            var holders = new appeng.api.stacks.KeyCounter[inputs.length];
                            for (int i = 0; i < inputs.length; i++) {
                                holders[i] = new appeng.api.stacks.KeyCounter();
                                var possible = inputs[i].getPossibleInputs()[0];
                                holders[i].add(possible.what(), inputs[i].getMultiplier());
                            }
                            boolean accepted = (boolean) core.getClass().getMethod("pushPattern",
                                    IPatternDetails.class, appeng.api.stacks.KeyCounter[].class)
                                    .invoke(core, first, holders);
                            helper.assertTrue(accepted, "Alloy refused the published child at dispatch");
                            for (var holder : holders) helper.assertTrue(holder.isEmpty(), "Alloy did not consume owned inputs");
                            helper.runAfterDelay(10, () -> {
                                helper.assertTrue(stored.get(expectedOutput.what()) - before == expectedOutput.amount(),
                                        "Alloy output was lost or duplicated");
                                AeAllPattern.LOGGER.info("ALLOY_RESTART_{}_PASSED", phase);
                                helper.succeed();
                            });
                        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                    });
                } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            });
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
