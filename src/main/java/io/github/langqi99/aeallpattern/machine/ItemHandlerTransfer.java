package io.github.langqi99.aeallpattern.machine;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Simulation-first, whole-stack item transfers for machine adapters. */
public final class ItemHandlerTransfer {
    private ItemHandlerTransfer() {
    }

    public static IItemHandler find(ServerLevel level, BlockPos pos, Direction side) {
        var blockEntity = level.getBlockEntity(pos);
        return blockEntity == null
                ? null
                : blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
    }

    public static boolean insertFully(IItemHandler handler, ItemStack stack) {
        if (handler == null || stack.isEmpty()
                || !ItemHandlerHelper.insertItemStacked(handler, stack.copy(), true).isEmpty()) {
            return false;
        }
        return ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false).isEmpty();
    }

    public static ItemStack extractAny(IItemHandler handler, boolean simulate) {
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack present = handler.getStackInSlot(slot);
            if (present.isEmpty()) {
                continue;
            }
            ItemStack candidate = handler.extractItem(slot, present.getCount(), true);
            if (candidate.isEmpty()) {
                continue;
            }
            if (simulate) {
                return candidate;
            }
            return handler.extractItem(slot, candidate.getCount(), false);
        }
        return ItemStack.EMPTY;
    }
}
