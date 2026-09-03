package io.github.langqi99.aeallpattern.tianshu;

import appeng.menu.AEBaseMenu;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.CraftingRoutePolicy;
import io.github.langqi99.aeallpattern.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Server-authoritative editor for the network's default route policy. */
public final class TianshuRoutingMenu extends AEBaseMenu {
    private static final String UPDATE_POLICY = "aeallpattern:updateTianshuRoutingPolicy";

    private final TianshuPatternSelectorBlockEntity router;
    private int aggregatePriority = -1;
    private int pathPreference;
    private int preferenceFlags = 90;
    private int preferenceOrder = CraftingRoutePolicy.DEFAULT_PREFERENCE_ORDER;

    public TianshuRoutingMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, blockEntity(inventory, data.readBlockPos()));
    }

    public TianshuRoutingMenu(
            int id, Inventory inventory, TianshuPatternSelectorBlockEntity router) {
        super(ModMenus.TIANSHU_ROUTING.get(), id, inventory, router);
        this.router = router;
        if (router != null) {
            setLocalPolicy(router.getRoutingPolicy());
        }
        registerClientAction(UPDATE_POLICY, String.class,
                serialized -> applyServerPolicy(CraftingRoutePolicy.deserialize(serialized)));
        addDataSlot(policySlot(0));
        addDataSlot(policySlot(1));
        addDataSlot(policySlot(2));
        addDataSlot(policySlot(3));
    }

    private static TianshuPatternSelectorBlockEntity blockEntity(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof TianshuPatternSelectorBlockEntity router
                ? router
                : null;
    }

    private DataSlot policySlot(int index) {
        return new DataSlot() {
            @Override
            public int get() {
                CraftingRoutePolicy policy = router == null ? getPolicy() : router.getRoutingPolicy();
                return switch (index) {
                    case 0 -> policy.aggregatePriority();
                    case 1 -> policy.pathPreference();
                    case 2 -> flags(policy);
                    default -> policy.preferenceOrder();
                };
            }

            @Override
            public void set(int value) {
                switch (index) {
                    case 0 -> aggregatePriority = value;
                    case 1 -> pathPreference = value;
                    case 2 -> preferenceFlags = value;
                    default -> preferenceOrder = value;
                }
            }
        };
    }

    public CraftingRoutePolicy getPolicy() {
        return new CraftingRoutePolicy(
                aggregatePriority,
                true,
                pathPreference,
                unpackDirection(preferenceFlags, 0),
                unpackDirection(preferenceFlags, 2),
                (preferenceFlags & 16) != 0,
                preferenceOrder,
                (preferenceFlags & 32) != 0,
                (preferenceFlags & 64) != 0);
    }

    /** Applies immediately on the client and persists authoritatively on the server. */
    public void updatePolicy(CraftingRoutePolicy policy) {
        CraftingRoutePolicy normalized = forceFeasible(policy);
        setLocalPolicy(normalized);
        if (isClientSide()) {
            sendClientAction(UPDATE_POLICY, normalized.serialize());
        } else {
            applyServerPolicy(normalized);
        }
    }

    private void applyServerPolicy(CraftingRoutePolicy policy) {
        if (router == null || getPlayer().level().isClientSide()) {
            return;
        }
        CraftingRoutePolicy normalized = forceFeasible(policy);
        router.setRoutingPolicy(normalized);
        setLocalPolicy(normalized);
    }

    @Override
    public boolean stillValid(Player player) {
        return router == null || (!router.isRemoved()
                && player.distanceToSqr(router.getBlockPos().getCenter()) <= 64.0D);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private void setLocalPolicy(CraftingRoutePolicy policy) {
        aggregatePriority = policy.aggregatePriority();
        pathPreference = policy.pathPreference();
        preferenceFlags = flags(policy);
        preferenceOrder = policy.preferenceOrder();
    }

    private static int flags(CraftingRoutePolicy policy) {
        return packDirection(policy.stockSurplusPreference(), 0)
                | packDirection(policy.yieldPreference(), 2)
                | (policy.preferFast() ? 16 : 0)
                | (policy.allowByproductOrders() ? 32 : 0)
                | (policy.allowAmplifyingCycles() ? 64 : 0);
    }

    private static int packDirection(int direction, int shift) {
        return (Math.clamp(direction, -1, 1) + 1) << shift;
    }

    private static int unpackDirection(int flags, int shift) {
        return ((flags >>> shift) & 3) - 1;
    }

    private static CraftingRoutePolicy forceFeasible(CraftingRoutePolicy policy) {
        CraftingRoutePolicy value = policy == null ? CraftingRoutePolicy.DEFAULT : policy;
        return new CraftingRoutePolicy(
                value.aggregatePriority(), true, value.pathPreference(), value.stockSurplusPreference(),
                value.yieldPreference(), value.preferFast(), value.preferenceOrder(),
                value.allowByproductOrders(), value.allowAmplifyingCycles());
    }
}
