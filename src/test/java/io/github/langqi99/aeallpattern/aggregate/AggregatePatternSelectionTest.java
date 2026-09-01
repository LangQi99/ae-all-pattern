package io.github.langqi99.aeallpattern.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AggregatePatternSelectionTest {
    @Test
    void absentSelectionPublishesEverything() {
        assertTrue(AggregatePatternSelection.ALL_ENABLED.isEnabled("a"));
        assertTrue(AggregatePatternSelection.ALL_ENABLED.isEnabled("b"));
        assertTrue(AggregatePatternSelection.ALL_ENABLED.isAllEnabled());
    }

    @Test
    void noneEnabledRepresentationStaysCompact() {
        var selection = AggregatePatternSelection.NONE_ENABLED;
        assertTrue(selection.isNoneEnabled());
        assertTrue(selection.ids().isEmpty());
        assertFalse(selection.isEnabled("a"));
    }

    @Test
    void togglingMovesPatternIdsBetweenEnabledAndDisabled() {
        var selection = AggregatePatternSelection.ALL_ENABLED
                .toggled("a")
                .toggled("b");
        assertFalse(selection.isEnabled("a"));
        assertFalse(selection.isEnabled("b"));
        assertTrue(selection.isEnabled("c"));

        var restored = selection.toggled("a");
        assertTrue(restored.isEnabled("a"));
        assertFalse(restored.isEnabled("b"));
        assertEquals(List.of("b"), restored.ids());
    }

    @Test
    void invertedSelectionTogglesEnabledSet() {
        var selection = AggregatePatternSelection.NONE_ENABLED.toggled("only");
        assertTrue(selection.isEnabled("only"));
        assertFalse(selection.isEnabled("other"));

        var restored = selection.toggled("only");
        assertTrue(restored.isNoneEnabled());
    }

    @Test
    void bulkChangeOnlyTouchesSuppliedPatterns() {
        var selectedSearchResults = AggregatePatternSelection.NONE_ENABLED
                .withEnabled(List.of("iron", "gold"), true);
        assertTrue(selectedSearchResults.isEnabled("iron"));
        assertTrue(selectedSearchResults.isEnabled("gold"));
        assertFalse(selectedSearchResults.isEnabled("copper"));

        var deselectedSearchResults = AggregatePatternSelection.ALL_ENABLED
                .withEnabled(List.of("iron", "gold"), false);
        assertFalse(deselectedSearchResults.isEnabled("iron"));
        assertFalse(deselectedSearchResults.isEnabled("gold"));
        assertTrue(deselectedSearchResults.isEnabled("copper"));
    }

    @Test
    void duplicateIdsAreDeduplicated() {
        var selection = new AggregatePatternSelection(false, List.of("a", "a", "b"));
        assertEquals(List.of("a", "b"), selection.ids());
    }

    @Test
    void rejectsInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> new AggregatePatternSelection(false, List.of("")));
        assertThrows(IllegalArgumentException.class,
                () -> new AggregatePatternSelection(false, List.of("x".repeat(161))));
        assertEquals(Integer.MAX_VALUE, AggregatePatternSelection.MAX_IDS);
    }
}
