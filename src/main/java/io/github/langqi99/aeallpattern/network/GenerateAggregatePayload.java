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
        String seriesHash,
        int batchSize,
        int batchIndex,
        int batchCount,
        int totalCatalogRecipeCount,
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
        long batchStart = (long) batchIndex * batchSize;
        int expectedBatchCount = totalCatalogRecipeCount < 1 || batchSize < 1
                ? 0 : Math.ceilDiv(totalCatalogRecipeCount, batchSize);
        int expectedRecipeCount = batchStart >= totalCatalogRecipeCount
                ? 0 : (int) Math.min(batchSize, totalCatalogRecipeCount - batchStart);
        // Upload pages are split by an estimated byte budget on the client (protocol packet
        // limit), so a page may hold far fewer than PAGE_SIZE recipes. Allow one page per
        // recipe as the degenerate upper bound.
        if (machineTranslationKey == null || machineTranslationKey.isBlank()
                || machineTranslationKey.length() > 256
                || seriesHash == null || seriesHash.length() != 64
                || batchSize < 1 || batchSize > io.github.langqi99.aeallpattern.aggregate.AggregatePatternData.MAX_RECIPES
                || batchIndex < 0 || batchCount < 1 || batchIndex >= batchCount
                || totalCatalogRecipeCount < 1 || batchCount != expectedBatchCount
                || totalRecipeCount != expectedRecipeCount
                || pageIndex < 0 || pageCount < 1 || pageCount > io.github.langqi99.aeallpattern.aggregate.AggregatePatternData.MAX_RECIPES
                || pageIndex >= pageCount
                || totalRecipeCount < 1
                || totalRecipeCount > io.github.langqi99.aeallpattern.aggregate.AggregatePatternData.MAX_RECIPES
                || pageCount > totalRecipeCount
                || recipes.isEmpty() || recipes.size() > AggregatePatternLibrary.PAGE_SIZE) {
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
        buffer.writeUtf(payload.seriesHash(), 64);
        buffer.writeVarInt(payload.batchSize());
        buffer.writeVarInt(payload.batchIndex());
        buffer.writeVarInt(payload.batchCount());
        buffer.writeVarInt(payload.totalCatalogRecipeCount());
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
        String seriesHash = buffer.readUtf(64);
        int batchSize = buffer.readVarInt();
        int batchIndex = buffer.readVarInt();
        int batchCount = buffer.readVarInt();
        int totalCatalogRecipeCount = buffer.readVarInt();
        int pageIndex = buffer.readVarInt();
        int pageCount = buffer.readVarInt();
        int total = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 1 || count > AggregatePatternLibrary.PAGE_SIZE) {
            throw new IllegalArgumentException("invalid aggregate upload page size: " + count);
        }
        List<AggregateRecipe> recipes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            recipes.add(AggregateRecipe.STREAM_CODEC.decode(buffer));
        }
        return new GenerateAggregatePayload(
                uploadId, pos, catalystId, machineKey, seriesHash, batchSize, batchIndex,
                batchCount, totalCatalogRecipeCount, pageIndex, pageCount, total, recipes);
    }
}
