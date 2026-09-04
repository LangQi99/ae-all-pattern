package io.github.langqi99.aeallpattern.binding;

import io.github.langqi99.aeallpattern.config.AeAllPatternCommonConfig;
import io.github.langqi99.aeallpattern.linker.PatternLinkerBlockEntity;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.machine.MachineAdapterRegistry;
import io.github.langqi99.aeallpattern.machine.MachineTargetResolver;
import io.github.langqi99.aeallpattern.network.BindingSyncService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** Server-authoritative two-step binding tool. */
@SuppressWarnings("deprecation")
public final class PatternBinderItem extends Item {
    public PatternBinderItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.FAIL;
        }

        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.FAIL;
        }

        BlockPos clickedPos = context.getClickedPos();
        BlockEntity clickedBlockEntity = level.getBlockEntity(clickedPos);
        ItemStack binder = context.getItemInHand();

        if (context.isSecondaryUseActive() && clickedBlockEntity instanceof PatternLinkerBlockEntity linker) {
            return selectAnchor(level, clickedPos, player, binder, linker);
        }
        if (context.isSecondaryUseActive()) {
            BlockPos targetPos = MachineTargetResolver.resolvePosition(level, clickedPos);
            return finishBinding(level, targetPos, context, player, binder, level.getBlockEntity(targetPos));
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult selectAnchor(
            ServerLevel level,
            BlockPos clickedPos,
            Player player,
            ItemStack binder,
            PatternLinkerBlockEntity linker) {
        if (linker.getOwnerId().isEmpty()) {
            linker.setOwner(player);
        }
        if (!linker.isOwnedBy(player)) {
            show(player, "message.aeallpattern.binding.wrong_owner");
            return InteractionResult.FAIL;
        }

        AnchorSelection selection = AnchorSelection.create(
                player.getUUID(),
                GlobalPos.of(level.dimension(), clickedPos.immutable()),
                BlockEntityFingerprint.of(linker),
                level.getGameTime());
        ModDataComponents.setAnchorSelection(binder, selection);
        show(player, "message.aeallpattern.binding.anchor_selected",
                clickedPos.getX(), clickedPos.getY(), clickedPos.getZ());
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult finishBinding(
            ServerLevel targetLevel,
            BlockPos targetPos,
            UseOnContext context,
            Player player,
            ItemStack binder,
            BlockEntity targetBlockEntity) {
        AnchorSelection selection = ModDataComponents.getAnchorSelection(binder);
        if (selection == null) {
            show(player, "message.aeallpattern.binding.missing_selection");
            return InteractionResult.FAIL;
        }

        ServerLevel anchorLevel = targetLevel.getServer().getLevel(selection.anchor().dimension());
        boolean sameDimension = selection.anchor().dimension().equals(targetLevel.dimension());
        boolean dimensionAllowed = sameDimension || AeAllPatternCommonConfig.LINKER_ALLOW_CROSS_DIMENSION.get();
        boolean anchorLoaded = anchorLevel != null && anchorLevel.hasChunkAt(selection.anchor().pos());
        BlockEntity rawAnchor = anchorLoaded ? anchorLevel.getBlockEntity(selection.anchor().pos()) : null;
        PatternLinkerBlockEntity linker = rawAnchor instanceof PatternLinkerBlockEntity found ? found : null;
        boolean ownerMatches = selection.ownerId().equals(player.getUUID())
                && (linker == null || linker.isOwnedBy(player));
        double maxDistanceSquared = AeAllPatternCommonConfig.maxBindingDistanceSquared();
        boolean withinRange = player.distanceToSqr(targetPos.getCenter()) <= maxDistanceSquared
                && (!sameDimension
                        || player.distanceToSqr(selection.anchor().pos().getCenter()) <= maxDistanceSquared
                                && selection.anchor().pos().distSqr(targetPos) <= maxDistanceSquared);
        boolean anchorMatches = linker != null
                && selection.anchorFingerprint().equals(BlockEntityFingerprint.of(linker));
        var targetAdapter = targetBlockEntity == null
                ? Optional.<io.github.langqi99.aeallpattern.machine.MachineAdapter>empty()
                : MachineAdapterRegistry.find(targetLevel, targetBlockEntity);
        boolean targetSupported = targetAdapter.isPresent();

        GlobalPos target = GlobalPos.of(targetLevel.dimension(), targetPos.immutable());
        BindingSavedData data = BindingSavedData.get(targetLevel.getServer());
        Optional<BindingRecord> existing = data.findByTarget(target);
        boolean targetAvailable = existing.isEmpty()
                || existing.get().ownerId().equals(player.getUUID())
                        && existing.get().anchor().equals(selection.anchor());

        BindingDecision decision = BindingValidator.validate(new BindingValidator.Context(
                true,
                selection.hasSupportedSchema(),
                ownerMatches,
                dimensionAllowed,
                withinRange,
                anchorLoaded,
                anchorMatches,
                linker != null && linker.getMainNode().isOnline(),
                targetSupported,
                targetAvailable));
        if (decision != BindingDecision.SUCCESS) {
            if (decision == BindingDecision.TOO_FAR) {
                show(player, "message.aeallpattern.binding.too_far",
                        AeAllPatternCommonConfig.LINKER_MAX_BINDING_DISTANCE.get());
            } else {
                show(player, "message.aeallpattern.binding." + decision.name().toLowerCase());
            }
            return InteractionResult.FAIL;
        }

        if (existing.isPresent()) {
            if (linker != null) {
                linker.cancelBinding(existing.get().bindingId());
            }
            data.remove(existing.get().bindingId());
            if (linker != null) {
                linker.refreshPatterns();
            }
            if (player instanceof ServerPlayer serverPlayer) {
                BindingSyncService.send(serverPlayer);
            }
            show(player, "message.aeallpattern.binding.removed");
            return InteractionResult.SUCCESS;
        }

        long gameTime = targetLevel.getGameTime();
        BindingRecord record = null;
        if (targetBlockEntity != null) {
            record = new BindingRecord(
                    BindingRecord.CURRENT_SCHEMA_VERSION,
                    UUID.randomUUID(),
                    player.getUUID(),
                    selection.anchor(),
                    target,
                    context.getClickedFace(),
                    selection.anchorFingerprint(),
                    BlockEntityFingerprint.of(targetBlockEntity),
                    targetAdapter.orElseThrow().id().toString(),
                    targetAdapter.orElseThrow().schemaVersion(),
                    gameTime,
                    gameTime);
        }
        data.put(record);
        if (linker != null) {
            linker.refreshPatterns();
        }
        if (player instanceof ServerPlayer serverPlayer) {
            BindingSyncService.send(serverPlayer);
        }
        show(player, "message.aeallpattern.binding.created", targetPos.getX(), targetPos.getY(), targetPos.getZ());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(
            @NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        AnchorSelection selection = ModDataComponents.getAnchorSelection(stack);
        if (selection == null) {
            tooltip.add(Component.translatable("tooltip.aeallpattern.pattern_binder.unbound"));
            return;
        }
        BlockPos pos = selection.anchor().pos();
        tooltip.add(Component.translatable(
                "tooltip.aeallpattern.pattern_binder.selected",
                selection.anchor().dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ()));
        tooltip.add(Component.translatable("tooltip.aeallpattern.pattern_binder.continuous"));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return ModDataComponents.hasAnchorSelection(stack) || super.isFoil(stack);
    }

    private static void show(Player player, String translationKey, Object... arguments) {
        player.displayClientMessage(Component.translatable(translationKey, arguments), true);
    }
}
