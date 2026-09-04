package io.github.langqi99.aeallpattern.linker;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.grid.AENetworkBlockEntity;
import io.github.langqi99.aeallpattern.ae.VirtualCraftingProvider;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternConfigMenu;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternOptions;
import io.github.langqi99.aeallpattern.recipe.RecipeIndexService;
import io.github.langqi99.aeallpattern.registry.ModBlockEntities;
import io.github.langqi99.aeallpattern.registry.ModItems;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

/** AE-owned anchor for bindings. It consumes one channel and a small idle power budget. */
public final class PatternLinkerBlockEntity extends AENetworkBlockEntity implements MenuProvider {
    private static final String OWNER_TAG = "Owner";
    private static final String OPTIONS_TAG = "PatternOptions";
    private static final double IDLE_POWER_USAGE = 2.0;

    @Nullable
    private UUID ownerId;
    private final IncomingBuffer incomingBuffer = new IncomingBuffer();
    private final VirtualCraftingProvider craftingProvider;
    private AggregatePatternOptions patternOptions = AggregatePatternOptions.DEFAULT;

    public PatternLinkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PATTERN_LINKER.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(IDLE_POWER_USAGE);
        craftingProvider = new VirtualCraftingProvider(this, incomingBuffer);
        getMainNode().addService(ICraftingProvider.class, craftingProvider);
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModItems.PATTERN_LINKER.get();
    }

    @Override
    public void setOwner(Player player) {
        super.setOwner(player);
        ownerId = player.getUUID();
        saveChanges();
    }

    public Optional<UUID> getOwnerId() {
        return Optional.ofNullable(ownerId);
    }

    public boolean isOwnedBy(Player player) {
        return ownerId == null || ownerId.equals(player.getUUID());
    }

    public void refreshPatterns() {
        craftingProvider.refresh();
    }

    public AggregatePatternOptions getPatternOptions() {
        return patternOptions;
    }

    public void setPatternOptions(AggregatePatternOptions options) {
        if (patternOptions.equals(options)) {
            return;
        }
        patternOptions = options;
        saveChanges();
        refreshPatterns();
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("gui.aeallpattern.linker_config.title");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new AggregatePatternConfigMenu(id, inventory, worldPosition);
    }

    public int insertIntoNetwork(ItemStack stack, Actionable mode) {
        if (stack.isEmpty() || !getMainNode().isOnline()) {
            return 0;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        long inserted = grid.getStorageService().getInventory().insert(
                AEItemKey.of(stack), stack.getCount(), mode, IActionSource.ofMachine(this));
        return (int) Math.min(stack.getCount(), inserted);
    }

    public void cancelBinding(UUID bindingId) {
        if (level == null || level.isClientSide()) {
            return;
        }
        incomingBuffer.removeBinding(bindingId).forEach(stack -> Block.popResource(level, worldPosition, stack));
        saveChanges();
    }

    @Override
    public void onReady() {
        super.onReady();
        refreshPatterns();
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        ownerId = tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
        patternOptions = tag.contains(OPTIONS_TAG)
                ? AggregatePatternOptions.fromFlags(tag.getInt(OPTIONS_TAG))
                : AggregatePatternOptions.DEFAULT;
        incomingBuffer.load(tag);
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerId != null) {
            tag.putUUID(OWNER_TAG, ownerId);
        }
        tag.putInt(OPTIONS_TAG, patternOptions.flags());
        incomingBuffer.save(tag);
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        drops.addAll(incomingBuffer.recoverableDrops());
    }

    @Override
    public void clearContent() {
        super.clearContent();
        incomingBuffer.clear();
    }

    public static void serverTick(
            Level level, BlockPos pos, BlockState state, PatternLinkerBlockEntity linker) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (linker.incomingBuffer.tick(serverLevel, linker)) {
            linker.saveChanges();
        }
        if (linker.craftingProvider.catalogGeneration() != RecipeIndexService.generation()) {
            linker.craftingProvider.refresh();
        }
        linker.craftingProvider.tickRefresh();
        linker.craftingProvider.tickAvailability();
    }
}
