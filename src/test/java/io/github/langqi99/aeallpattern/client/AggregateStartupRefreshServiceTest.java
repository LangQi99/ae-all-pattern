package io.github.langqi99.aeallpattern.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.langqi99.aeallpattern.aggregate.AggregateMetadataView;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class AggregateStartupRefreshServiceTest {
    private static final ResourceLocation CATALYST =
            ResourceLocation.fromNamespaceAndPath("aeallpattern", "refresh_test");

    @Test
    void queuesOnlyPersistedSingleItemsStillPendingThisServerStartup() {
        var required = entry(true, 1);
        var alreadyDone = entry(false, 1);
        var legacyPart = entry(true, 2);

        assertEquals(List.of(required), AggregateStartupRefreshService.entriesNeedingRefresh(
                List.of(required, alreadyDone, legacyPart)));
    }

    private static AggregateMetadataView.Entry entry(boolean required, int batchCount) {
        String hash = "a".repeat(64);
        return new AggregateMetadataView.Entry(
                UUID.randomUUID(), CATALYST, "block.test", hash, 3,
                hash, 3, 0, batchCount, batchCount == 1 ? 3 : 6, required);
    }
}
