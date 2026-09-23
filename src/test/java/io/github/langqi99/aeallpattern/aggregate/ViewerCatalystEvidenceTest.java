package io.github.langqi99.aeallpattern.aggregate;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ViewerCatalystEvidenceTest {
    // Equality represents complete viewer stack identity, including quantity and components.
    record Stack(String item, int count, String nbt) {}
    private static final Stack TOOL = new Stack("gear", 1, "damage=0");
    @Test void explicitDeclarationIsRequired() {
        assertTrue(ViewerCatalystEvidence.explicitlyDeclared(List.of(TOOL), List.of(List.of(TOOL))));
        assertFalse(ViewerCatalystEvidence.explicitlyDeclared(List.of(TOOL), List.of()));
    }
    @Test void workstationDoesNotMatchMaterial() {
        assertFalse(ViewerCatalystEvidence.explicitlyDeclared(List.of(TOOL),
                List.of(List.of(new Stack("machine", 1, "")))));
    }
    @Test void partialAlternativeMatchIsNotEnough() {
        var other = new Stack("other_gear", 1, "");
        assertFalse(ViewerCatalystEvidence.explicitlyDeclared(List.of(TOOL, other), List.of(List.of(TOOL))));
        assertTrue(ViewerCatalystEvidence.explicitlyDeclared(List.of(TOOL, other), List.of(List.of(other, TOOL))));
    }
    @Test void countsAndNbtMustMatch() {
        for (var mismatch : List.of(new Stack("gear", 2, "damage=0"), new Stack("gear", 1, "damage=1"))) {
            assertFalse(ViewerCatalystEvidence.explicitlyDeclared(List.of(TOOL), List.of(List.of(mismatch))));
            assertFalse(ViewerCatalystEvidence.exactRemainder(TOOL, mismatch, 1F, 1F));
        }
    }
    @Test void exactGuaranteedViewerRemainderIsAccepted() {
        assertTrue(ViewerCatalystEvidence.exactRemainder(TOOL, TOOL, 1F, 1F));
    }
    @Test void probabilityAndAbsentRemaindersAreRejected() {
        assertFalse(ViewerCatalystEvidence.exactRemainder(TOOL, null, 1F, 1F));
        assertFalse(ViewerCatalystEvidence.exactRemainder(null, null, 1F, 1F));
        assertFalse(ViewerCatalystEvidence.exactRemainder(TOOL, TOOL, 0.5F, 1F));
        assertFalse(ViewerCatalystEvidence.exactRemainder(TOOL, TOOL, 1F, 0.5F));
        assertFalse(ViewerCatalystEvidence.exactRemainder(TOOL, TOOL, Float.NaN, 1F));
    }
    @Test void emptySlotsAreNotCatalysts() {
        assertFalse(ViewerCatalystEvidence.explicitlyDeclared(List.of(), List.of(List.of())));
    }
}
