package io.github.langqi99.aeallpattern.aggregate;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Optional;

/** Preserve positive slot evidence with the catalog, leaving ambiguous outputs untouched. */
public final class ViewerCatalystSlots {
    private ViewerCatalystSlots() {}

    public static AggregateInputSlot mark(AggregateInputSlot input, List<AggregateInputSlot> catalysts,
            List<GenericStack> outputs, boolean exactViewerRemainder) {
        boolean itemsOnly = input.alternatives().stream().allMatch(s -> s.what() instanceof AEItemKey);
        // This feature removes inputs only. If the viewer also publishes a tool as an output,
        // keep the input until paired-output semantics are implemented; never create phantom output.
        boolean appearsInOutputs = outputs.stream().anyMatch(o -> input.alternatives().stream()
                .anyMatch(i -> i.what().equals(o.what())));
        boolean declared = ViewerCatalystEvidence.explicitlyDeclared(input.alternatives(),
                catalysts.stream().map(AggregateInputSlot::alternatives).toList());
        if (itemsOnly && !appearsInOutputs && (declared || exactViewerRemainder)) {
            // Evidence covers these concrete candidates, not items added to a tag after a reload.
            return new AggregateInputSlot(input.alternatives(), Optional.empty(), true);
        }
        return input;
    }
}
