package io.github.langqi99.aeallpattern.network;

import io.github.langqi99.aeallpattern.compat.mekanism.RotaryCondensentratorSupport;
import io.github.langqi99.aeallpattern.machine.MachineTargetResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Reads a rotary condensentrator's direction from the server, where the value actually lives. */
public final class RotaryDirectionService {
    /** The machine must be close to the requesting player; this is only a state read, not an action. */
    private static final double MAX_DISTANCE_SQUARED = 64.0 * 64.0;

    private RotaryDirectionService() {
    }

    public static void handle(ServerPlayer player, BlockPos clickedPos) {
        var level = player.serverLevel();
        BlockPos pos = clickedPos;
        Boolean direction = null;
        if (level.hasChunkAt(clickedPos)
                && player.distanceToSqr(clickedPos.getCenter()) <= MAX_DISTANCE_SQUARED) {
            pos = MachineTargetResolver.resolvePosition(level, clickedPos);
            direction = RotaryCondensentratorSupport.condensentrating(level, pos);
        }
        // Always answer: an unanswered query would leave the client waiting to scan.
        PacketDistributor.sendToPlayer(player, new RotaryDirectionPayload(
                pos, direction != null, Boolean.TRUE.equals(direction)));
    }
}
