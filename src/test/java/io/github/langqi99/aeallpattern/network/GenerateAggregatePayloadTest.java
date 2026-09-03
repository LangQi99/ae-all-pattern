package io.github.langqi99.aeallpattern.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class GenerateAggregatePayloadTest {
    private static final ResourceLocation CATALYST =
            ResourceLocation.fromNamespaceAndPath("aeallpattern", "test_machine");

    @Test
    void emptyCatalogIsValidOnlyForAnExistingLibraryRefresh() {
        assertDoesNotThrow(() -> new GenerateAggregatePayload(
                UUID.randomUUID(), BlockPos.ZERO, CATALYST, "block.test",
                UUID.randomUUID(), 0, 1, 0, List.of()));

        assertThrows(IllegalArgumentException.class, () -> new GenerateAggregatePayload(
                UUID.randomUUID(), BlockPos.ZERO, CATALYST, "block.test",
                null, 0, 1, 0, List.of()));
    }

    @Test
    void emptyRefreshCannotPretendToBeOnePageOfANonEmptyCatalog() {
        assertThrows(IllegalArgumentException.class, () -> new GenerateAggregatePayload(
                UUID.randomUUID(), BlockPos.ZERO, CATALYST, "block.test",
                UUID.randomUUID(), 0, 1, 1, List.of()));
    }
}
