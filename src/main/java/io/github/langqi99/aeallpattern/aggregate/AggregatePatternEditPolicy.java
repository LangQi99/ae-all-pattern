package io.github.langqi99.aeallpattern.aggregate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Which aggregate patterns an external pattern editor (Advanced AE's advanced pattern
 * encoder) may open, and what it is allowed to see.
 *
 * <p>Two rules, per design:
 * <ul>
 *   <li>Only infusing-factory aggregates are editable. Every other aggregate decodes to its
 *       marker wrapper, which the editor does not recognise, so it stays non-editable.</li>
 *   <li>The editor only ever sees the aggregate's first <em>selected</em> child. Children
 *       the player deselected in the selection screen are never handed out: the decoder
 *       itself only expands selected children, and this policy only unwraps what the
 *       decoder produced.</li>
 * </ul>
 */
public final class AggregatePatternEditPolicy {
    /**
     * Path fragment shared by every infusing-factory tier (basic/advanced/elite/ultimate,
     * Mekanism or addon machines) — e.g. {@code mekanism:advanced_infusing_factory} or
     * {@code mekmm:advanced_infusing_factory}.
     */
    static final String INFUSING_CATALYST_FRAGMENT = "infusing";

    private AggregatePatternEditPolicy() {
    }

    /**
     * Decode used by external pattern editors. Plain encoded patterns pass through
     * unchanged; aggregate patterns are unwrapped only when they are editable infusing
     * aggregates, otherwise the marker wrapper is returned so the editor cannot
     * recognise them.
     */
    public static IPatternDetails decodeForEditor(ItemStack stack, Level level) {
        IPatternDetails details = PatternDetailsHelper.decodePattern(stack, level);
        if (!(details instanceof AggregatePatternMarkerDetails marker) || marker.isPlaceholder()) {
            return details;
        }
        if (!isEditorEditable(stack, level)) {
            return details;
        }
        return unwrapForEditor(details);
    }

    /**
     * True when this aggregate pattern may be opened in an external pattern editor.
     * Determined by the machine catalyst the aggregate was scanned from.
     */
    public static boolean isEditorEditable(ItemStack stack, Level level) {
        if (!stack.is(ModItems.AGGREGATE_PATTERN.get())) {
            return false;
        }
        AggregatePatternRef ref = ModDataComponents.getAggregatePattern(stack);
        if (ref == null || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return AggregatePatternLibrary.get(serverLevel.getServer())
                .find(ref.libraryId())
                .map(AggregatePatternLibrary.Entry::catalystId)
                .map(id -> id.getPath().contains(INFUSING_CATALYST_FRAGMENT))
                .orElse(false);
    }

    /**
     * Unwraps marker and child-wrapper layers down to the AE2 pattern the aggregate's
     * first selected child expands into. The chain only ever contains selected children,
     * because the decoder only exposes the first selected child.
     */
    public static IPatternDetails unwrapForEditor(IPatternDetails details) {
        IPatternDetails current = details;
        for (int depth = 0; depth < 4 && current != null; depth++) {
            if (current instanceof AggregatePatternMarkerDetails marker) {
                current = marker.delegate();
            } else if (current instanceof AggregatePatternDetails aggregate) {
                current = aggregate.delegate();
            } else {
                break;
            }
        }
        return current == null ? details : current;
    }
}
