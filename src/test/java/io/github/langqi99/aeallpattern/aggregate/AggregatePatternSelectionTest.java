package io.github.langqi99.aeallpattern.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
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

    @Test
    void serializedComponentContainsOnlyModeAndRecipeIds() {
        var selection = new AggregatePatternSelection(true, List.of("mod:recipe/a", "mod:recipe/b"));
        var encoded = AggregatePatternSelection.CODEC.encodeStart(JsonOps.INSTANCE, selection)
                .getOrThrow().getAsJsonObject();

        assertEquals(2, encoded.size());
        assertTrue(encoded.has("inverted"));
        assertTrue(encoded.has("ids"));
        assertEquals(selection, AggregatePatternSelection.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void reconciliationStoresTheSmallerDisabledSide() {
        var selection = AggregatePatternSelection.ALL_ENABLED
                .withEnabled(List.of("a", "b"), false)
                .reconciled(List.of("a", "b", "c", "d", "e"));

        assertFalse(selection.inverted());
        assertEquals(List.of("a", "b"), selection.ids());
        assertTrue(selection.isEnabled("c"));
    }

    @Test
    void reconciliationFlipsToTheSmallerEnabledSide() {
        var selection = AggregatePatternSelection.ALL_ENABLED
                .withEnabled(List.of("a", "b", "c", "d"), false)
                .reconciled(List.of("a", "b", "c", "d", "e"));

        assertTrue(selection.inverted());
        assertEquals(List.of("e"), selection.ids());
        assertTrue(selection.isEnabled("e"));
        assertFalse(selection.isEnabled("a"));
    }

    @Test
    void deletedIdsAreRemovedWithoutChangingSurvivors() {
        var selection = new AggregatePatternSelection(false, List.of("deleted", "disabled"))
                .reconciled(List.of("disabled", "enabled"));

        assertFalse(selection.isEnabled("disabled"));
        assertTrue(selection.isEnabled("enabled"));
        assertEquals(List.of("disabled"), selection.ids());
    }

    @Test
    void newRecipesFollowTheMajorityDefaultBeforeCompaction() {
        var disabledList = new AggregatePatternSelection(false, List.of("off"))
                .reconciled(List.of("off", "on", "new"));
        assertTrue(disabledList.isEnabled("new"));

        var enabledList = new AggregatePatternSelection(true, List.of("on"))
                .reconciled(List.of("off", "on", "new"));
        assertFalse(enabledList.isEnabled("new"));
    }

    @Test
    void addDeleteAndToggleCombinationKeepsIntentAndMinimumStorage() {
        var original = new AggregatePatternSelection(false, List.of("b", "removed"));
        var refreshed = original.reconciled(List.of("a", "b", "c", "new"));
        var toggled = refreshed.toggled("c", List.of("a", "b", "c", "new"));

        assertFalse(toggled.isEnabled("b"));
        assertFalse(toggled.isEnabled("c"));
        assertTrue(toggled.isEnabled("a"));
        assertTrue(toggled.isEnabled("new"));
        assertEquals(2, toggled.ids().size());
    }

    @Test
    void equalSidesPreserveUnknownRecipePolicy() {
        var disabledDefault = new AggregatePatternSelection(false, List.of("a"))
                .reconciled(List.of("a", "b"));
        var enabledDefault = new AggregatePatternSelection(true, List.of("b"))
                .reconciled(List.of("a", "b"));

        assertFalse(disabledDefault.inverted());
        assertTrue(disabledDefault.isEnabled("future"));
        assertTrue(enabledDefault.inverted());
        assertFalse(enabledDefault.isEnabled("future"));
    }

    @Test
    void emptyCatalogRetainsFutureRecipePolicy() {
        assertEquals(AggregatePatternSelection.ALL_ENABLED,
                new AggregatePatternSelection(false, List.of("removed")).reconciled(List.of()));
        assertEquals(AggregatePatternSelection.NONE_ENABLED,
                new AggregatePatternSelection(true, List.of("removed")).reconciled(List.of()));
    }

    @Test
    void duplicatesAndInvalidCurrentIdsDoNotInflateStorage() {
        var selection = new AggregatePatternSelection(false, List.of("off", "gone"))
                .reconciled(java.util.Arrays.asList("off", "on", "on", null, ""));

        assertEquals(List.of("off"), selection.ids());
        assertFalse(selection.isEnabled("off"));
        assertTrue(selection.isEnabled("on"));
    }

    @Test
    void exhaustiveAddDeleteSelectionAndToggleCombinationsStayEquivalentAndMinimal() {
        List<String> oldCatalog = List.of("a", "b", "c", "d");
        List<String> possibleNewCatalog = List.of("a", "b", "c", "d", "new-x", "new-y");

        for (boolean inverted : List.of(false, true)) {
            for (int storedMask = 0; storedMask < (1 << oldCatalog.size()); storedMask++) {
                AggregatePatternSelection original = new AggregatePatternSelection(
                        inverted, subset(oldCatalog, storedMask));
                for (int catalogMask = 0; catalogMask < (1 << possibleNewCatalog.size()); catalogMask++) {
                    List<String> current = subset(possibleNewCatalog, catalogMask);
                    AggregatePatternSelection reconciled = original.reconciled(current);

                    int enabled = 0;
                    for (String id : current) {
                        assertEquals(original.isEnabled(id), reconciled.isEnabled(id),
                                () -> "reconciliation changed " + id + " for " + original + " -> " + current);
                        if (reconciled.isEnabled(id)) {
                            enabled++;
                        }
                    }
                    assertTrue(current.containsAll(reconciled.ids()),
                            () -> "reconciliation retained a deleted id: " + reconciled);
                    assertEquals(Math.min(enabled, current.size() - enabled), reconciled.ids().size(),
                            () -> "reconciliation did not choose the smaller side: " + reconciled);

                    for (String toggledId : current) {
                        AggregatePatternSelection toggled = reconciled.toggled(toggledId, current);
                        for (String id : current) {
                            boolean expected = id.equals(toggledId)
                                    ? !reconciled.isEnabled(id)
                                    : reconciled.isEnabled(id);
                            assertEquals(expected, toggled.isEnabled(id),
                                    () -> "toggle changed the wrong id for " + reconciled + " -> " + current);
                        }
                        long toggledEnabled = current.stream().filter(toggled::isEnabled).count();
                        assertEquals(Math.min(toggledEnabled, current.size() - toggledEnabled), toggled.ids().size());
                    }
                }
            }
        }
    }

    @Test
    void repeatedRefreshWithNoChangesIsIdempotent() {
        List<String> catalog = List.of("a", "b", "c", "d", "e");
        var once = new AggregatePatternSelection(false, List.of("a", "b", "deleted"))
                .reconciled(catalog);

        assertEquals(once, once.reconciled(catalog));
    }

    @Test
    void bulkChangesAfterAddAndDeleteStillUseMinimumStorage() {
        List<String> refreshed = List.of("a", "c", "new-x", "new-y", "new-z");
        var selection = new AggregatePatternSelection(false, List.of("b", "deleted"))
                .reconciled(refreshed)
                .withEnabled(List.of("new-x", "new-y", "missing"), false, refreshed);

        assertFalse(selection.isEnabled("new-x"));
        assertFalse(selection.isEnabled("new-y"));
        assertTrue(selection.isEnabled("new-z"));
        assertTrue(selection.isEnabled("a"));
        assertTrue(selection.isEnabled("c"));
        assertEquals(2, selection.ids().size());
    }

    @Test
    void majorityPolicyCanFlipAfterCatalogShrinkThenControlsLaterAdditions() {
        var original = new AggregatePatternSelection(false, List.of("off-a", "off-b"));
        var afterShrink = original.reconciled(List.of("off-a", "off-b", "on"));
        assertTrue(afterShrink.inverted());
        assertEquals(List.of("on"), afterShrink.ids());

        var afterAddition = afterShrink.reconciled(List.of("off-a", "off-b", "on", "new"));
        assertFalse(afterAddition.isEnabled("new"));
        assertTrue(afterAddition.isEnabled("on"));
    }

    private static List<String> subset(List<String> values, int mask) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            if ((mask & (1 << index)) != 0) {
                result.add(values.get(index));
            }
        }
        return result;
    }
}
