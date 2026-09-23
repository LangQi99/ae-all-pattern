package io.github.langqi99.aeallpattern.gametest;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternConfigMenu;
import io.github.langqi99.aeallpattern.binding.*;
import io.github.langqi99.aeallpattern.linker.*;
import io.github.langqi99.aeallpattern.machine.MachineAdapterRegistry;
import io.github.langqi99.aeallpattern.recipe.*;
import io.github.langqi99.aeallpattern.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AeAllPattern.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LinkerOperationGameTests {
    private record Fixture(PatternLinkerBlockEntity linker, FurnaceBlockEntity furnace,
                           BindingRecord binding, RecipeSnapshot recipe) {}

    private static Fixture setup(GameTestHelper helper) {
        BlockPos linkerPos = new BlockPos(1, 1, 1);
        BlockPos furnacePos = new BlockPos(3, 1, 1);
        helper.setBlock(linkerPos, ModBlocks.PATTERN_LINKER.get());
        helper.setBlock(linkerPos.south(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(furnacePos, Blocks.FURNACE);
        var level = helper.getLevel();
        var linker = (PatternLinkerBlockEntity) level.getBlockEntity(helper.absolutePos(linkerPos));
        var furnace = (FurnaceBlockEntity) level.getBlockEntity(helper.absolutePos(furnacePos));
        var adapter = MachineAdapterRegistry.find(level, furnace).orElseThrow();
        var binding = new BindingRecord(1, UUID.randomUUID(), UUID.randomUUID(),
                GlobalPos.of(level.dimension(), linker.getBlockPos()), GlobalPos.of(level.dimension(), furnace.getBlockPos()),
                Direction.NORTH, BlockEntityFingerprint.of(linker), BlockEntityFingerprint.of(furnace),
                adapter.id().toString(), adapter.schemaVersion(), level.getGameTime(), level.getGameTime());
        BindingSavedData.get(level.getServer()).put(binding);
        var recipe = new RecipeSnapshot(ResourceLocation.fromNamespaceAndPath("aeallpattern", "linker_test"),
                new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT),
                new RecipeFingerprint(adapter.id().toString(), "test", "raw_iron", "iron", 1), 200);
        return new Fixture(linker, furnace, binding, recipe);
    }

    private static void enqueue(IncomingBuffer buffer, Fixture f, int count) {
        for (int i = 0; i < count; i++) {
            buffer.enqueue(f.binding, "iron", f.recipe, List.of(new ItemStack(Items.RAW_IRON)),
                    new ItemStack(Items.IRON_INGOT), 200, true);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void linkerSettingsDefaultsMenuPermissionsAndPersistence(GameTestHelper helper) {
        var f = setup(helper);
        helper.assertTrue(f.linker.getOperationOptions().equals(LinkerOperationOptions.DEFAULT), "wrong defaults");
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.setPos(f.linker.getBlockPos().getCenter());
        f.linker.setOwner(player);
        var menu = new AggregatePatternConfigMenu(1, player.getInventory(), f.linker.getBlockPos());
        for (int id = 100; id <= 102; id++) {
            helper.assertTrue(menu.clickMenuButton(player, id), "operation toggle rejected");
        }
        var expected = new LinkerOperationOptions(true, false, false);
        helper.assertTrue(f.linker.getOperationOptions().equals(expected), "toggles changed wrong bits");
        menu.setData(0, 16383);
        menu.setData(1, 6);
        helper.assertTrue(menu.getOptions().flags() == 16383 && menu.getOperationOptions().flags() == 6,
                "network synchronization mixed operation and pattern flags");
        var saved = f.linker.saveWithFullMetadata(helper.getLevel().registryAccess());
        var restored = (PatternLinkerBlockEntity) BlockEntity.loadStatic(f.linker.getBlockPos(),
                f.linker.getBlockState(), saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getOperationOptions().equals(expected), "settings lost on load");
        saved.remove("LinkerOperations");
        var legacy = (PatternLinkerBlockEntity) BlockEntity.loadStatic(f.linker.getBlockPos(),
                f.linker.getBlockState(), saved, helper.getLevel().registryAccess());
        helper.assertTrue(legacy.getOperationOptions().equals(LinkerOperationOptions.DEFAULT), "legacy defaults incorrect");
        helper.assertFalse(menu.clickMenuButton(player, 103), "unknown button accepted");
        var stranger = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        stranger.setPos(f.linker.getBlockPos().getCenter());
        helper.assertFalse(menu.clickMenuButton(stranger, 100), "non-owner changed settings");
        player.setPos(f.linker.getBlockPos().getCenter().add(100, 0, 0));
        helper.assertFalse(menu.clickMenuButton(player, 100), "remote player changed settings");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blockingWaitsForInputsButNotFuelOrOutputs(GameTestHelper helper) {
        var f = setup(helper);
        var buffer = new IncomingBuffer();
        enqueue(buffer, f, 1);
        f.linker.setOperationOptions(new LinkerOperationOptions(true, true, false));
        f.furnace.setItem(0, new ItemStack(Items.RAW_IRON, 8));
        buffer.tick(helper.getLevel(), f.linker);
        helper.assertTrue(f.furnace.getItem(0).getCount() == 8 && buffer.recoverableDrops().size() == 1,
                "blocking inserted into occupied input or lost queued material");
        f.furnace.setItem(0, ItemStack.EMPTY);
        f.furnace.setItem(1, new ItemStack(Items.COAL));
        f.furnace.setItem(2, new ItemStack(Items.IRON_INGOT));
        buffer.tick(helper.getLevel(), f.linker);
        helper.assertTrue(f.furnace.getItem(0).getCount() == 1 && buffer.recoverableDrops().isEmpty(),
                "fuel/output wrongly prevented dispatch");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void smartBatchRampsAndFallsBackWithoutDuplicatingInputs(GameTestHelper helper) {
        var f = setup(helper);
        f.linker.setOperationOptions(new LinkerOperationOptions(false, true, false));
        var buffer = new IncomingBuffer();
        enqueue(buffer, f, 9);
        for (int expected : new int[]{1, 3, 7}) {
            buffer.tick(helper.getLevel(), f.linker);
            helper.assertTrue(f.furnace.getItem(0).getCount() == expected, "dispatch did not ramp 1/2/4");
        }
        f.furnace.setItem(0, new ItemStack(Items.RAW_IRON, 64));
        buffer.tick(helper.getLevel(), f.linker);
        helper.assertTrue(buffer.recoverableDrops().size() == 2, "full machine lost input");
        f.linker.setOperationOptions(new LinkerOperationOptions(false, false, false));
        buffer.tick(helper.getLevel(), f.linker);
        helper.assertTrue(buffer.recoverableDrops().size() == 2, "disabled batching lost queued material");
        f.furnace.setItem(0, ItemStack.EMPTY);
        buffer.tick(helper.getLevel(), f.linker);
        helper.assertTrue(f.furnace.getItem(0).getCount() == 1 && buffer.recoverableDrops().size() == 1,
                "disabled batching should deliver exactly one craft per tick");
        f.linker.setOperationOptions(new LinkerOperationOptions(false, true, false));
        buffer.resetDispatchBudgets();
        f.furnace.setItem(0, ItemStack.EMPTY);
        buffer.tick(helper.getLevel(), f.linker);
        helper.assertTrue(f.furnace.getItem(0).getCount() == 1 && buffer.recoverableDrops().isEmpty(),
                "reenabling should restart at one without discarding queued work");
        helper.assertFalse(buffer.canAccept(f.binding.bindingId(), "different_recipe", true), "mixed recipe accepted in batch");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void parallelQueueIsBoundedAndSurvivesReload(GameTestHelper helper) {
        var f = setup(helper);
        var buffer = new IncomingBuffer();
        enqueue(buffer, f, 64);
        helper.assertFalse(buffer.canAccept(f.binding.bindingId(), "iron", true), "unbounded queue");
        helper.assertFalse(buffer.canAccept(UUID.randomUUID(), "iron", true), "global limit bypassed");
        var saved = new net.minecraft.nbt.CompoundTag();
        buffer.save(saved, helper.getLevel().registryAccess());
        var restored = new IncomingBuffer();
        restored.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.recoverableDrops().stream().mapToInt(ItemStack::getCount).sum() == 64,
                "reload lost or duplicated buffered items");
        helper.assertTrue(restored.removeBinding(f.binding.bindingId()).stream().mapToInt(ItemStack::getCount).sum() == 64,
                "unbind lost queued items");
        helper.assertTrue(restored.canAccept(f.binding.bindingId(), "iron", true), "unbind did not free capacity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void mekanismBlockingExcludesPowerAndOutputSlots(GameTestHelper helper) throws ReflectiveOperationException {
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("mekanism", "energized_smelter"));
        if (block == Blocks.AIR) { helper.succeed(); return; }
        var f = setup(helper);
        helper.setBlock(new BlockPos(3, 1, 1), block);
        var target = helper.getLevel().getBlockEntity(f.furnace.getBlockPos());
        var adapter = MachineAdapterRegistry.find(helper.getLevel(), target).orElseThrow();
        var slots = (List<?>) target.getClass().getMethod("getInventorySlots", Direction.class).invoke(target, new Object[]{null});
        Object input = null;
        for (Object slot : slots) {
            if (slot.getClass().getSimpleName().equals("InputInventorySlot")) input = slot;
            if (slot.getClass().getSimpleName().equals("OutputInventorySlot"))
                slot.getClass().getMethod("setStack", ItemStack.class).invoke(slot, new ItemStack(Items.IRON_INGOT));
            if (slot.getClass().getSimpleName().equals("EnergyInventorySlot"))
                slot.getClass().getMethod("setStack", ItemStack.class).invoke(slot, new ItemStack(Items.REDSTONE));
        }
        helper.assertTrue(input != null, "Mekanism fixture has no input slot");
        helper.assertFalse(adapter.isInputBlocked(helper.getLevel(), f.binding), "Mekanism fuel/output blocked dispatch");
        input.getClass().getMethod("setStack", ItemStack.class).invoke(input, new ItemStack(Items.RAW_IRON));
        helper.assertTrue(adapter.isInputBlocked(helper.getLevel(), f.binding), "Mekanism occupied input was ignored");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void providerAppliesSettingsAndAcceptsActualNbtVariants(GameTestHelper helper) {
        var f = setup(helper);
        helper.runAfterDelay(10, () -> {
            var buffer = new IncomingBuffer();
            var provider = new io.github.langqi99.aeallpattern.ae.VirtualCraftingProvider(f.linker, buffer);
            provider.refresh();
            var strict = provider.getAvailablePatterns().stream().filter(pattern ->
                    pattern.getInputs().length == 1
                    && pattern.getInputs()[0].getPossibleInputs()[0].what() instanceof AEItemKey key
                    && key.getItem() == Items.RAW_IRON).findFirst().orElseThrow();
            ItemStack named = new ItemStack(Items.RAW_IRON);
            named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Named ore"));
            KeyCounter counter = new KeyCounter();
            counter.add(AEItemKey.of(named), 1);
            helper.assertFalse(provider.pushPattern(strict, new KeyCounter[]{counter}), "strict provider accepted NBT variant");
            f.linker.setPatternOptions(io.github.langqi99.aeallpattern.aggregate.AggregatePatternOptions.fromFlags(
                    io.github.langqi99.aeallpattern.aggregate.AggregatePatternOptions.DEFAULT.flags() | 8192));
            provider.refresh();
            var relaxed = provider.getAvailablePatterns().stream().filter(pattern ->
                    pattern.getInputs().length == 1 && pattern.getInputs()[0].isValid(AEItemKey.of(named), helper.getLevel()))
                    .findFirst().orElseThrow();
            helper.assertFalse(strict.equals(relaxed), "pattern settings did not invalidate virtual definition");
            helper.assertFalse(provider.pushPattern(strict, new KeyCounter[]{counter}), "stale pattern accepted");
            f.linker.setOperationOptions(new LinkerOperationOptions(true, true, false));
            f.furnace.setItem(0, new ItemStack(Items.RAW_IRON));
            helper.assertFalse(provider.pushPattern(relaxed, new KeyCounter[]{counter}), "blocking provider accepted busy input");
            helper.assertTrue(counter.get(AEItemKey.of(named)) == 1, "rejected push consumed material");
            f.furnace.setItem(0, ItemStack.EMPTY);
            helper.assertTrue(provider.pushPattern(relaxed, new KeyCounter[]{counter}), "valid NBT payload rejected");
            helper.assertTrue(AEItemKey.of(buffer.recoverableDrops().get(0)).equals(AEItemKey.of(named)),
                    "provider stripped accepted NBT");
            f.linker.setOperationOptions(new LinkerOperationOptions(false, true, false));
            helper.assertTrue(provider.pushPattern(relaxed, new KeyCounter[]{counter}), "parallel mode rejected same-pattern queue");
            f.linker.setOperationOptions(new LinkerOperationOptions(false, false, false));
            helper.assertTrue(provider.pushPattern(relaxed, new KeyCounter[]{counter}), "disabled batching incorrectly enabled blocking");
            buffer.tick(helper.getLevel(), f.linker);
            helper.assertTrue(f.furnace.getItem(0).getCount() == 1, "disabled batching dispatched more than one craft");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void autoReturnHonorsToggleFullStorageAndOwnedRecovery(GameTestHelper helper) {
        var f = setup(helper);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(f.linker.getMainNode().isOnline(), "linker offline");
            KeyCounter stored = new KeyCounter();
            boolean[] accepting = {false, false};
            MEStorage storage = new MEStorage() {
                @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                    if (!accepting[mode == Actionable.SIMULATE ? 0 : 1]) return 0;
                    if (mode == Actionable.MODULATE) stored.add(key, amount);
                    return amount;
                }
                @Override public void getAvailableStacks(KeyCounter out) { out.addAll(stored); }
                @Override public Component getDescription() { return Component.literal("Linker return test"); }
            };
            f.linker.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(mounts -> mounts.mount(storage));
            var buffer = new IncomingBuffer();
            f.furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 4));
            buffer.tick(helper.getLevel(), f.linker);
            helper.assertTrue(f.furnace.getItem(2).getCount() == 4, "full network pulled output");
            accepting[0] = true;
            f.linker.setOperationOptions(new LinkerOperationOptions(false, true, false));
            buffer.tick(helper.getLevel(), f.linker);
            helper.assertTrue(f.furnace.getItem(2).getCount() == 4, "disabled return still extracted");
            f.linker.setOperationOptions(LinkerOperationOptions.DEFAULT);
            buffer.tick(helper.getLevel(), f.linker);
            helper.assertTrue(f.furnace.getItem(2).isEmpty() && buffer.recoverableDrops().size() == 1,
                    "simulate/commit rejection lost recovered output");
            accepting[1] = true;
            f.linker.setOperationOptions(new LinkerOperationOptions(false, true, false));
            buffer.tick(helper.getLevel(), f.linker);
            helper.assertTrue(stored.get(AEItemKey.of(Items.IRON_INGOT)) == 4 && buffer.recoverableDrops().isEmpty(),
                    "already-owned recovery stranded when auto-return was disabled");
            helper.succeed();
        });
    }
}
