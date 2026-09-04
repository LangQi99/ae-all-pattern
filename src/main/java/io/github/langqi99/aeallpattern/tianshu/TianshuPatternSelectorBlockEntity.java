package io.github.langqi99.aeallpattern.tianshu;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkBlockEntity;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.CraftingRoutePolicy;
import io.github.langqi99.aeallpattern.internal.routing.ae2.crafting.SecondaryOutputPatternSource;
import io.github.langqi99.aeallpattern.registry.ModBlockEntities;
import io.github.langqi99.aeallpattern.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/** Networked route-planning controller. Crafting execution remains the responsibility of normal AE CPUs. */
public final class TianshuPatternSelectorBlockEntity extends AENetworkBlockEntity implements MenuProvider {
    private static final double IDLE_POWER_USAGE = 16.0D;
    private static final String ROUTING_POLICY_TAG = "RoutingPolicy";

    private CraftingRoutePolicy routingPolicy = CraftingRoutePolicy.DEFAULT;

    public TianshuPatternSelectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIANSHU_PATTERN_SELECTOR.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("tianshu_pattern_selector")
                .setVisualRepresentation(ModBlocks.TIANSHU_PATTERN_SELECTOR.get())
                .setIdlePowerUsage(IDLE_POWER_USAGE);
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.DENSE_SMART;
    }

    public IGrid getGrid() {
        return getMainNode().getGrid();
    }

    public boolean isRouterOnline() {
        return getMainNode().isActive() && getMainNode().getGrid() != null;
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.aeallpattern.tianshu_pattern_selector");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new TianshuRoutingMenu(id, inventory, this);
    }

    public CraftingRoutePolicy getRoutingPolicy() {
        return routingPolicy;
    }

    public void setRoutingPolicy(CraftingRoutePolicy routingPolicy) {
        CraftingRoutePolicy normalized = routingPolicy == null ? CraftingRoutePolicy.DEFAULT : routingPolicy;
        if (!this.routingPolicy.equals(normalized)) {
            boolean secondaryIndexChanged = this.routingPolicy.allowByproductOrders()
                    != normalized.allowByproductOrders();
            this.routingPolicy = normalized;
            saveChanges();
            if (secondaryIndexChanged && getGrid() != null
                    && getGrid().getCraftingService() instanceof SecondaryOutputPatternSource source) {
                source.aeallpattern$secondaryOutputsChanged();
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag policyTag = new CompoundTag();
        policyTag.putInt("AggregatePriority", routingPolicy.aggregatePriority());
        policyTag.putBoolean("RequireFeasible", routingPolicy.requireFeasible());
        policyTag.putInt("PathPreference", routingPolicy.pathPreference());
        policyTag.putInt("StockSurplusPreference", routingPolicy.stockSurplusPreference());
        policyTag.putInt("YieldPreference", routingPolicy.yieldPreference());
        policyTag.putBoolean("PreferFast", routingPolicy.preferFast());
        policyTag.putInt("PreferenceOrder", routingPolicy.preferenceOrder());
        policyTag.putBoolean("AllowByproductOrders", routingPolicy.allowByproductOrders());
        policyTag.putBoolean("AllowAmplifyingCycles", routingPolicy.allowAmplifyingCycles());
        tag.put(ROUTING_POLICY_TAG, policyTag);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        if (tag.contains(ROUTING_POLICY_TAG, CompoundTag.TAG_COMPOUND)) {
            CompoundTag policyTag = tag.getCompound(ROUTING_POLICY_TAG);
            routingPolicy = new CraftingRoutePolicy(
                    policyTag.contains("AggregatePriority") ? policyTag.getInt("AggregatePriority") : -1,
                    !policyTag.contains("RequireFeasible") || policyTag.getBoolean("RequireFeasible"),
                    policyTag.getInt("PathPreference"),
                    readDirection(policyTag, "StockSurplusPreference", "PreferStockSurplus"),
                    readDirection(policyTag, "YieldPreference", "PreferHighYield"),
                    policyTag.getBoolean("PreferFast"),
                    policyTag.contains("PreferenceOrder")
                            ? policyTag.getInt("PreferenceOrder")
                            : CraftingRoutePolicy.DEFAULT_PREFERENCE_ORDER,
                    policyTag.getBoolean("AllowByproductOrders"),
                    !policyTag.contains("AllowAmplifyingCycles")
                            || policyTag.getBoolean("AllowAmplifyingCycles"));
        } else {
            routingPolicy = CraftingRoutePolicy.DEFAULT;
        }
    }

    private static int readDirection(CompoundTag tag, String key, String legacyBooleanKey) {
        if (tag.contains(key)) {
            return tag.getInt(key);
        }
        return tag.contains(legacyBooleanKey) && tag.getBoolean(legacyBooleanKey) ? 1 : 0;
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModBlocks.TIANSHU_PATTERN_SELECTOR.get().asItem();
    }

    public static void serverTick(
            Level level,
            BlockPos pos,
            BlockState state,
            TianshuPatternSelectorBlockEntity selector) {
        boolean active = selector.isRouterOnline();
        if (state.getValue(TianshuPatternSelectorBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(TianshuPatternSelectorBlock.ACTIVE, active), 3);
        }
    }
}
