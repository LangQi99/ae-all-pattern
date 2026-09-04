package io.github.langqi99.aeallpattern.linker;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.GlobalPos;
import io.github.langqi99.aeallpattern.binding.BindingSavedData;
import io.github.langqi99.aeallpattern.network.BindingSyncService;
import java.util.ArrayList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import net.minecraftforge.network.NetworkHooks;

public final class PatternLinkerBlock extends BaseEntityBlock {
    public PatternLinkerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new PatternLinkerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide()
                ? null
                : createTickerHelper(type, io.github.langqi99.aeallpattern.registry.ModBlockEntities.PATTERN_LINKER.get(),
                        PatternLinkerBlockEntity::serverTick);
    }

    @Override
    public @NotNull InteractionResult use(
            @NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player,
            @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (!level.isClientSide()) {
            if (!(player instanceof ServerPlayer serverPlayer)
                    || !(level.getBlockEntity(pos) instanceof PatternLinkerBlockEntity linker)
                    || !linker.isOwnedBy(player)) {
                return InteractionResult.FAIL;
            }
            NetworkHooks.openScreen(serverPlayer, linker, data -> {
                data.writeBoolean(true);
                data.writeBlockPos(pos);
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void setPlacedBy(
            @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof PatternLinkerBlockEntity linker) {
            linker.setOwner(player);
        }
    }

    @Override
    public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            if (level.getBlockEntity(pos) instanceof PatternLinkerBlockEntity linker) {
                var bufferedDrops = new ArrayList<ItemStack>();
                linker.addAdditionalDrops(level, pos, bufferedDrops);
                bufferedDrops.forEach(stack -> Block.popResource(level, pos, stack));
                linker.clearContent();
            }
            BindingSavedData.get(serverLevel.getServer())
                    .removeByAnchor(GlobalPos.of(serverLevel.dimension(), pos.immutable()));
            BindingSyncService.sendToOnlinePlayers(serverLevel.getServer());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
