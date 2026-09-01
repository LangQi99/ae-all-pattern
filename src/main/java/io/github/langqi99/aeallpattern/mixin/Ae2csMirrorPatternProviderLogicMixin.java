package io.github.langqi99.aeallpattern.mixin;

import io.github.langqi99.aeallpattern.aggregate.AggregateProviderRefreshService;
import io.github.langqi99.aeallpattern.util.Reflect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Refreshes AE2CS mirror providers after their target aggregate list is rebuilt. */
@Mixin(targets = "io.github.lounode.ae2cs.common.me.logic.MirrorPatternProviderLogic", remap = false)
public abstract class Ae2csMirrorPatternProviderLogicMixin {
    @Inject(method = "updatePatterns", at = @At("HEAD"))
    private void aeallpattern$trackReloadRefresh(CallbackInfo callback) {
        Object host = Reflect.field(this, "host");
        if (host == null) return;
        try {
            Object blockEntity = Reflect.invoke(host, "getBlockEntity");
            if (blockEntity instanceof BlockEntity entity && entity.getLevel() instanceof ServerLevel level) {
                AggregateProviderRefreshService.track(level.getServer(), this, owner -> {
                    try {
                        Reflect.invoke(owner, "updatePatterns");
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                    }
                });
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }
}
