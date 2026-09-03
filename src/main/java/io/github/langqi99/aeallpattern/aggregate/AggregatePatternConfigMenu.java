package io.github.langqi99.aeallpattern.aggregate;

import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.registry.ModItems;
import io.github.langqi99.aeallpattern.registry.ModMenus;
import io.github.langqi99.aeallpattern.linker.PatternLinkerBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Server-authoritative editor for the aggregate pattern held by the player. */
public final class AggregatePatternConfigMenu extends AbstractContainerMenu {
    public static final int TOGGLE_SPLIT_SAME_ITEMS = 0;
    public static final int TOGGLE_IGNORE_OUTPUT_COMPONENTS = 1;
    public static final int TOGGLE_SKIP_PROBABILISTIC_MAIN_OUTPUT = 2;
    public static final int TOGGLE_IGNORE_PROBABILISTIC_BYPRODUCTS = 3;
    public static final int TOGGLE_REMOVE_PROCESSING_CATALYSTS = 4;
    public static final int TOGGLE_ALLOW_ITEM_SUBSTITUTIONS = 5;
    public static final int TOGGLE_ALLOW_FLUID_SUBSTITUTIONS = 6;
    public static final int TOGGLE_REMOVE_INPUT_FLUIDS = 7;
    public static final int TOGGLE_REMOVE_OUTPUT_FLUIDS = 8;
    public static final int TOGGLE_REMOVE_INPUT_CHEMICALS = 9;
    public static final int TOGGLE_REMOVE_OUTPUT_CHEMICALS = 10;
    public static final int TOGGLE_SWAP_FIRST_AND_LAST_INPUTS = 11;
    public static final int TOGGLE_SKIP_DURABILITY_CONSUMING_RECIPES = 12;

    private final Inventory inventory;
    @Nullable
    private final InteractionHand hand;
    @Nullable
    private final BlockPos linkerPos;
    private int optionFlags;

    public AggregatePatternConfigMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, readTarget(data));
    }

    public AggregatePatternConfigMenu(int id, Inventory inventory, InteractionHand hand) {
        this(id, inventory, new Target(hand, null));
    }

    public AggregatePatternConfigMenu(int id, Inventory inventory, BlockPos linkerPos) {
        this(id, inventory, new Target(null, linkerPos.immutable()));
    }

    private AggregatePatternConfigMenu(int id, Inventory inventory, Target target) {
        super(ModMenus.AGGREGATE_PATTERN_CONFIG.get(), id);
        this.inventory = inventory;
        this.hand = target.hand;
        this.linkerPos = target.linkerPos;
        optionFlags = currentOptions().flags();
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return inventory.player.level().isClientSide() ? optionFlags : currentOptions().flags();
            }

            @Override
            public void set(int value) {
                optionFlags = value & 8191;
            }
        });
    }

    public AggregatePatternOptions getOptions() {
        return AggregatePatternOptions.fromFlags(optionFlags);
    }

    public ItemStack stack() {
        return hand == null ? ItemStack.EMPTY : inventory.player.getItemInHand(hand);
    }

    public boolean isLinkerConfiguration() {
        return linkerPos != null;
    }

    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (id < TOGGLE_SPLIT_SAME_ITEMS || id > TOGGLE_SKIP_DURABILITY_CONSUMING_RECIPES) {
            return false;
        }
        ItemStack stack = stack();
        if (linkerPos == null && !isConfigurable(stack)) {
            return false;
        }
        int mask = 1 << id;
        if (player.level().isClientSide()) {
            optionFlags ^= mask;
            return true;
        }
        if (linkerPos != null) {
            PatternLinkerBlockEntity linker = linker(player);
            if (linker == null) {
                return false;
            }
            optionFlags = linker.getPatternOptions().flags() ^ mask;
            linker.setPatternOptions(AggregatePatternOptions.fromFlags(optionFlags));
        } else {
            optionFlags = options(stack).flags() ^ mask;
            stack.set(ModDataComponents.AGGREGATE_PATTERN_OPTIONS.get(), AggregatePatternOptions.fromFlags(optionFlags));
            player.getInventory().setChanged();
        }
        broadcastChanges();
        return true;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (linkerPos != null) {
            return player.distanceToSqr(linkerPos.getCenter()) <= 64
                    && player.level().getBlockEntity(linkerPos) instanceof PatternLinkerBlockEntity linker
                    && (player.level().isClientSide() || linker.isOwnedBy(player));
        }
        return hand != null && isConfigurable(player.getItemInHand(hand));
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    private static boolean isConfigurable(ItemStack stack) {
        return stack.is(ModItems.AGGREGATE_PATTERN.get())
                && stack.has(ModDataComponents.AGGREGATE_PATTERN.get());
    }

    private static AggregatePatternOptions options(ItemStack stack) {
        AggregatePatternOptions options = stack.get(ModDataComponents.AGGREGATE_PATTERN_OPTIONS.get());
        return options == null ? AggregatePatternOptions.DEFAULT : options;
    }

    private AggregatePatternOptions currentOptions() {
        if (linkerPos != null
                && inventory.player.level().getBlockEntity(linkerPos) instanceof PatternLinkerBlockEntity linker) {
            return linker.getPatternOptions();
        }
        return options(stack());
    }

    @Nullable
    private PatternLinkerBlockEntity linker(Player player) {
        if (linkerPos == null || player.distanceToSqr(linkerPos.getCenter()) > 64
                || !(player.level().getBlockEntity(linkerPos) instanceof PatternLinkerBlockEntity linker)
                || !linker.isOwnedBy(player)) {
            return null;
        }
        return linker;
    }

    private static Target readTarget(RegistryFriendlyByteBuf data) {
        return data.readBoolean()
                ? new Target(null, data.readBlockPos())
                : new Target(data.readEnum(InteractionHand.class), null);
    }

    private record Target(@Nullable InteractionHand hand, @Nullable BlockPos linkerPos) {
    }
}
