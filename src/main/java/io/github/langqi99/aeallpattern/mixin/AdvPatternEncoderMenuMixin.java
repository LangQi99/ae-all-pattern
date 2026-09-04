package io.github.langqi99.aeallpattern.mixin;


import appeng.api.crafting.IPatternDetails;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternEditPolicy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets Advanced AE's pattern encoder read an infusing-factory aggregate pattern.
 *
 * <p>The encoder identifies a pattern by concrete type - it only understands AE2's {@code
 * AEProcessingPattern} and its own {@code AdvProcessingPattern} - so it configures the
 * insertion side of every input. An aggregate decodes to a wrapper, which decodes to
 * another wrapper, and neither of those is one of the two types it looks for, so the
 * encoder came up blank and the player could not reconfigure the pattern at all.</p>
 *
 * <p>Redirecting the decode call defers to {@link AggregatePatternEditPolicy}: only
 * editable infusing aggregates are unwrapped down to the AE2 pattern of their first
 * selected child, so the encoder can never read deselected children or non-infusing
 * aggregates. The policy also keeps this mixin free of game logic - see {@code
 * AggregatePatternEditPolicy} for the rules.</p>
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.gui.patternencoder.AdvPatternEncoderMenu", remap = false)
public abstract class AdvPatternEncoderMenuMixin {
    @Redirect(method = {"decodeInputPattern", "copyItemToOutputSlot"},
            at = @At(value = "INVOKE",
                    target = "Lappeng/api/crafting/PatternDetailsHelper;"
                            + "decodePattern(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Z)"
                            + "Lappeng/api/crafting/IPatternDetails;"),
            remap = false)
    private IPatternDetails aeallpattern$decodeForEditor(ItemStack stack, Level level, boolean tryRecovery) {
        return AggregatePatternEditPolicy.decodeForEditor(stack, level);
    }
}
