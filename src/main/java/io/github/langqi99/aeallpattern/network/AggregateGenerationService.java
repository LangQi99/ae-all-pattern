package io.github.langqi99.aeallpattern.network;

import io.github.langqi99.aeallpattern.aggregate.AggregatePatternLibrary;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternData;
import io.github.langqi99.aeallpattern.aggregate.AggregateRecipe;
import io.github.langqi99.aeallpattern.machine.MachineAdapterRegistry;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** Validates and assembles bounded JEI scan pages before updating the server library. */
@SuppressWarnings("deprecation")
public final class AggregateGenerationService {
    private static final long UPLOAD_TIMEOUT_TICKS = 20L * 120L;
    private static final Map<UploadKey, Upload> UPLOADS = new HashMap<>();

    private AggregateGenerationService() {
    }

    public static void handle(GenerateAggregatePayload payload, ServerPlayer player) {
        long now = player.level().getGameTime();
        UPLOADS.entrySet().removeIf(entry -> now - entry.getValue().lastUpdateTick > UPLOAD_TIMEOUT_TICKS);
        if (!validTarget(payload, player)) {
            return;
        }
        if (payload.batchSize() > AggregatePatternData.configuredRecipeLimit()
                || payload.totalRecipeCount() > AggregatePatternData.configuredRecipeLimit()) {
            return;
        }

        UploadKey key = new UploadKey(player.getUUID(), payload.uploadId());
        Upload upload = UPLOADS.computeIfAbsent(key, ignored -> new Upload(payload, now));
        if (!upload.matches(payload) || !upload.add(payload, now)) {
            UPLOADS.remove(key);
            return;
        }
        if (!upload.complete()) {
            return;
        }
        UPLOADS.remove(key);
        List<AggregateRecipe> recipes = upload.flatten();
        if (recipes.size() != payload.totalRecipeCount()) {
            return;
        }
        var library = AggregatePatternLibrary.get(Objects.requireNonNull(player.getServer()));
        if (payload.batchCount() > 1 && library.findBatch(payload.catalystId(), payload.seriesHash(),
                payload.batchSize(), payload.batchIndex()).isPresent()) {
            AggregateMetadataSyncService.send(player);
            int next = library.nextMissingBatch(payload.catalystId(), payload.seriesHash(),
                    payload.batchSize(), payload.batchCount());
            player.displayClientMessage(Component.translatable(
                    next >= payload.batchCount()
                            ? "message.aeallpattern.generator.series_complete"
                            : "message.aeallpattern.generator.progress_updated",
                    next >= payload.batchCount() ? payload.batchCount() : next + 1), true);
            return;
        }
        var ref = payload.batchCount() == 1
                ? library.put(player.getServer(), payload.catalystId(), payload.machineTranslationKey(), recipes)
                : library.putBatch(player.getServer(), payload.catalystId(), payload.machineTranslationKey(), recipes,
                        payload.seriesHash(), payload.batchSize(), payload.batchIndex(), payload.batchCount(),
                        payload.totalCatalogRecipeCount());
        AggregateMetadataSyncService.sendToOnlinePlayers(player.getServer());

        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);
        if (!player.addItem(aggregate)) {
            player.drop(aggregate, false);
        }
        player.level().playSound(
                null, payload.machinePos(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.6F, 1.25F);
        showCreated(player, payload, recipes.size());
    }

    private static boolean validTarget(GenerateAggregatePayload payload, ServerPlayer player) {
        // The client scan runs across many ticks, so the player legitimately walks away from
        // the machine while it completes. Enforce the machine identity instead of proximity:
        // the clicked block must still be the same block, and the player must still hold the
        // generator item.
        if (!holdsGenerator(player)
                || !player.level().hasChunkAt(payload.machinePos())) {
            return false;
        }
        var block = player.level().getBlockState(payload.machinePos()).getBlock();
        if (!BuiltInRegistries.BLOCK.getKey(block).equals(payload.catalystId())
                || !block.getDescriptionId().equals(payload.machineTranslationKey())) {
            return false;
        }
        // Exact server-side adapters own their recipe catalog. Do not let a client
        // JEI scan create a second, category-wide aggregate for the same machine.
        var target = player.level().getBlockEntity(payload.machinePos());
        // Blocks without block entities (currently the crafting table) use native AE
        // crafting patterns and have no server-side machine adapter to validate.
        return target == null || MachineAdapterRegistry.find(player.serverLevel(), target).isEmpty();
    }

    private static boolean holdsGenerator(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.ALL_PATTERN_GENERATOR.get())
                || player.getOffhandItem().is(ModItems.ALL_PATTERN_GENERATOR.get());
    }

    private static void showCreated(ServerPlayer player, GenerateAggregatePayload payload, int recipeCount) {
        Component machine = Component.translatable(payload.machineTranslationKey());
        if (payload.batchCount() <= 1) {
            player.displayClientMessage(Component.translatable(
                    "message.aeallpattern.generator.created", machine, recipeCount), true);
        } else if (payload.batchIndex() + 1 < payload.batchCount()) {
            player.displayClientMessage(Component.translatable(
                    "message.aeallpattern.generator.created_part", machine, payload.batchIndex() + 1,
                    payload.batchCount(), recipeCount, payload.batchIndex() + 2), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    "message.aeallpattern.generator.created_last_part", machine,
                    payload.batchIndex() + 1, payload.batchCount(), recipeCount), true);
        }
    }

    private record UploadKey(UUID playerId, UUID uploadId) {
    }

    private static final class Upload {
        private final BlockPos machinePos;
        private final ResourceLocation catalystId;
        private final String machineKey;
        private final String seriesHash;
        private final int batchSize;
        private final int batchIndex;
        private final int batchCount;
        private final int totalCatalogRecipeCount;
        private final int pageCount;
        private final int totalRecipeCount;
        private final Map<Integer, List<AggregateRecipe>> pages = new HashMap<>();
        private long lastUpdateTick;

        private Upload(GenerateAggregatePayload first, long now) {
            machinePos = first.machinePos();
            catalystId = first.catalystId();
            machineKey = first.machineTranslationKey();
            seriesHash = first.seriesHash();
            batchSize = first.batchSize();
            batchIndex = first.batchIndex();
            batchCount = first.batchCount();
            totalCatalogRecipeCount = first.totalCatalogRecipeCount();
            pageCount = first.pageCount();
            totalRecipeCount = first.totalRecipeCount();
            lastUpdateTick = now;
        }

        private boolean matches(GenerateAggregatePayload page) {
            return machinePos.equals(page.machinePos())
                    && catalystId.equals(page.catalystId())
                    && machineKey.equals(page.machineTranslationKey())
                    && seriesHash.equals(page.seriesHash())
                    && batchSize == page.batchSize()
                    && batchIndex == page.batchIndex()
                    && batchCount == page.batchCount()
                    && totalCatalogRecipeCount == page.totalCatalogRecipeCount()
                    && pageCount == page.pageCount()
                    && totalRecipeCount == page.totalRecipeCount();
        }

        private boolean add(GenerateAggregatePayload page, long now) {
            List<AggregateRecipe> previous = pages.get(page.pageIndex());
            if (previous != null && !previous.equals(page.recipes())) {
                return false;
            }
            pages.put(page.pageIndex(), page.recipes());
            lastUpdateTick = now;
            return true;
        }

        private boolean complete() {
            return pages.size() == pageCount;
        }

        private List<AggregateRecipe> flatten() {
            List<AggregateRecipe> recipes = new ArrayList<>(totalRecipeCount);
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                recipes.addAll(pages.get(pageIndex));
            }
            return List.copyOf(recipes);
        }
    }
}
