package io.github.langqi99.aeallpattern.network;

import appeng.api.stacks.GenericStack;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternSelectionMenu.Entry;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import io.github.langqi99.aeallpattern.network.FriendlyStreamCodec;

/**
 * One bounded page of a search result. A full result is several pages; the client assembles
 * them by request id and page index. Each page stays well below the protocol packet limit.
 */
public record AggregateSearchResultPayload(
        UUID requestId,
        int chunkIndex,
        int chunkCount,
        int resultPageIndex,
        int resultPageCount,
        int totalResults,
        int selectedResults,
        List<Entry> entries,
        List<Boolean> enabledStates) {
    public static final int MAX_ENTRIES_PER_PAGE = 64;
    private static final int MAX_STACKS_PER_LIST = 81;

    public static final FriendlyStreamCodec<AggregateSearchResultPayload> STREAM_CODEC =
            FriendlyStreamCodec.of(AggregateSearchResultPayload::encode, AggregateSearchResultPayload::decode);

    public AggregateSearchResultPayload {
        entries = List.copyOf(entries);
        enabledStates = List.copyOf(enabledStates);
        if (chunkIndex < 0 || chunkCount < 1 || chunkIndex >= chunkCount
                || resultPageIndex < 0 || resultPageCount < 1 || resultPageIndex >= resultPageCount
                || totalResults < 0 || selectedResults < 0 || selectedResults > totalResults
                || entries.size() > MAX_ENTRIES_PER_PAGE || enabledStates.size() != entries.size()) {
            throw new IllegalArgumentException("invalid aggregate search result page");
        }
    }

    private static void encode(FriendlyByteBuf buffer, AggregateSearchResultPayload payload) {
        buffer.writeUUID(payload.requestId());
        buffer.writeVarInt(payload.chunkIndex());
        buffer.writeVarInt(payload.chunkCount());
        buffer.writeVarInt(payload.resultPageIndex());
        buffer.writeVarInt(payload.resultPageCount());
        buffer.writeVarInt(payload.totalResults());
        buffer.writeVarInt(payload.selectedResults());
        buffer.writeVarInt(payload.entries().size());
        for (int index = 0; index < payload.entries().size(); index++) {
            Entry entry = payload.entries().get(index);
            buffer.writeUtf(entry.patternId(), AggregatePatternSelection.MAX_ID_LENGTH);
            writeStacks(buffer, entry.inputs());
            writeStacks(buffer, entry.outputs());
            buffer.writeBoolean(payload.enabledStates().get(index));
        }
    }

    private static AggregateSearchResultPayload decode(FriendlyByteBuf buffer) {
        UUID requestId = buffer.readUUID();
        int chunkIndex = buffer.readVarInt();
        int chunkCount = buffer.readVarInt();
        int resultPageIndex = buffer.readVarInt();
        int resultPageCount = buffer.readVarInt();
        int totalResults = buffer.readVarInt();
        int selectedResults = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES_PER_PAGE) {
            throw new IllegalArgumentException("invalid search result entry count: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        List<Boolean> enabledStates = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String patternId = buffer.readUtf(AggregatePatternSelection.MAX_ID_LENGTH);
            entries.add(new Entry(patternId, readStacks(buffer), readStacks(buffer)));
            enabledStates.add(buffer.readBoolean());
        }
        return new AggregateSearchResultPayload(
                requestId,
                chunkIndex,
                chunkCount,
                resultPageIndex,
                resultPageCount,
                totalResults,
                selectedResults,
                entries,
                enabledStates);
    }

    private static void writeStacks(FriendlyByteBuf buffer, List<GenericStack> stacks) {
        buffer.writeVarInt(stacks.size());
        stacks.forEach(stack -> GenericStack.writeBuffer(stack, buffer));
    }

    private static List<GenericStack> readStacks(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_STACKS_PER_LIST) {
            throw new IllegalArgumentException("invalid search result stack count: " + count);
        }
        List<GenericStack> stacks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            stacks.add(java.util.Objects.requireNonNull(GenericStack.readBuffer(buffer)));
        }
        return stacks;
    }
}
