package io.github.langqi99.aeallpattern.network;

import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternLibrary;
import io.github.langqi99.aeallpattern.aggregate.AggregateRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** One bounded page of a client JEI scan. No packet can contain the whole catalog. */
public record GenerateAggregatePayload(
        UUID uploadId,
        BlockPos machinePos,
        ResourceLocation catalystId,
        String machineTranslationKey,
        UUID replacementLibraryId,
        int pageIndex,
        int pageCount,
        int totalRecipeCount,
        List<AggregateRecipe> recipes) implements CustomPacketPayload {
    public static final Type<GenerateAggregatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeAllPattern.MOD_ID, "generate_aggregate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GenerateAggregatePayload> STREAM_CODEC = StreamCodec.of(
            GenerateAggregatePayload::encode, GenerateAggregatePayload::decode);

    public GenerateAggregatePayload {
        recipes = List.copyOf(recipes);
        // Upload pages are split by an estimated byte budget on the client (protocol packet
        // limit), so a page may hold far fewer than PAGE_SIZE recipes. Allow one page per
        // recipe as the degenerate upper bound.
        if (machineTranslationKey == null || machineTranslationKey.isBlank()
                || machineTranslationKey.length() > 256
                || pageIndex < 0 || pageCount < 1 || pageCount > io.github.langqi99.aeallpattern.aggregate.AggregatePatternData.MAX_RECIPES
                || pageIndex >= pageCount
                || totalRecipeCount < (replacementLibraryId == null ? 1 : 0)
                || totalRecipeCount > io.github.langqi99.aeallpattern.aggregate.AggregatePatternData.MAX_RECIPES
                || pageCount > Math.max(1, totalRecipeCount)
                || (recipes.isEmpty() && !(replacementLibraryId != null
                        && totalRecipeCount == 0 && pageIndex == 0 && pageCount == 1))
                || recipes.size() > AggregatePatternLibrary.PAGE_SIZE) {
            throw new IllegalArgumentException("invalid aggregate upload page");
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, GenerateAggregatePayload payload) {
        buffer.writeUUID(payload.uploadId());
        buffer.writeBlockPos(payload.machinePos());
        buffer.writeResourceLocation(payload.catalystId());
        buffer.writeUtf(payload.machineTranslationKey(), 256);
        buffer.writeBoolean(payload.replacementLibraryId() != null);
        if (payload.replacementLibraryId() != null) {
            buffer.writeUUID(payload.replacementLibraryId());
        }
        buffer.writeVarInt(payload.pageIndex());
        buffer.writeVarInt(payload.pageCount());
        buffer.writeVarInt(payload.totalRecipeCount());
        buffer.writeVarInt(payload.recipes().size());
        payload.recipes().forEach(recipe -> AggregateRecipe.STREAM_CODEC.encode(buffer, recipe));
    }

    private static GenerateAggregatePayload decode(RegistryFriendlyByteBuf buffer) {
        UUID uploadId = buffer.readUUID();
        BlockPos pos = buffer.readBlockPos();
        ResourceLocation catalystId = buffer.readResourceLocation();
        String machineKey = buffer.readUtf(256);
        UUID replacementLibraryId = buffer.readBoolean() ? buffer.readUUID() : null;
        int pageIndex = buffer.readVarInt();
        int pageCount = buffer.readVarInt();
        int total = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > AggregatePatternLibrary.PAGE_SIZE) {
            throw new IllegalArgumentException("invalid aggregate upload page size: " + count);
        }
        List<AggregateRecipe> recipes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            recipes.add(AggregateRecipe.STREAM_CODEC.decode(buffer));
        }
        return new GenerateAggregatePayload(
                uploadId, pos, catalystId, machineKey, replacementLibraryId,
                pageIndex, pageCount, total, recipes);
    }
}
