package io.github.langqi99.aeallpattern.aggregate;

import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/** One physical AE pattern item that publishes every captured child recipe. */
public final class AggregatePatternItem extends Item {
    public AggregatePatternItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.has(ModDataComponents.AGGREGATE_PATTERN.get())) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            openSelectionMenu(serverPlayer, stack, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static void openSelectionMenu(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        AggregatePatternRef ref = stack.get(ModDataComponents.AGGREGATE_PATTERN.get());
        if (ref == null) {
            return;
        }
        List<AggregateRecipe> recipes = AggregatePatternLibrary.get(player.server)
                .recipes(player.server, ref.libraryId())
                .orElseGet(List::of);
        final AggregatePatternSelection finalSelection = stack
                .getOrDefault(ModDataComponents.AGGREGATE_PATTERN_SELECTION.get(),
                        AggregatePatternSelection.ALL_ENABLED)
                .reconciled(recipes.stream().map(AggregateRecipe::patternId).toList());
        if (finalSelection.isAllEnabled()) {
            stack.remove(ModDataComponents.AGGREGATE_PATTERN_SELECTION.get());
        } else {
            stack.set(ModDataComponents.AGGREGATE_PATTERN_SELECTION.get(), finalSelection);
        }
        player.getInventory().setChanged();
        player.openMenu(
                new SimpleMenuProvider(
                        (id, inventory, ignored) -> new AggregatePatternSelectionMenu(
                                id, inventory, hand, List.of(), finalSelection),
                        Component.translatable("gui.aeallpattern.aggregate_management.title")),
                data -> data.writeEnum(hand));
    }

    @Override
    public @NotNull Component getName(ItemStack stack) {
        AggregatePatternRef ref = stack.get(ModDataComponents.AGGREGATE_PATTERN.get());
        if (ref == null) {
            return super.getName(stack);
        }
        var metadata = AggregateMetadataView.find(ref.libraryId());
        String machineKey = metadata.map(AggregateMetadataView.Entry::machineTranslationKey)
                .orElseGet(() -> BuiltInRegistries.BLOCK.get(ref.catalystId()).getDescriptionId());
        if (metadata.isPresent() && metadata.orElseThrow().batchCount() > 1) {
            var entry = metadata.orElseThrow();
            return Component.translatable(
                    "item.aeallpattern.aggregate_pattern.named_part",
                    Component.translatable(machineKey), entry.batchIndex() + 1, entry.batchCount());
        }
        return Component.translatable(
                "item.aeallpattern.aggregate_pattern.named",
                Component.translatable(machineKey));
    }

    @Override
    public void appendHoverText(
            @NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        AggregatePatternRef ref = stack.get(ModDataComponents.AGGREGATE_PATTERN.get());
        if (ref == null) {
            tooltip.add(Component.translatable("tooltip.aeallpattern.aggregate_pattern.empty")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        var metadata = AggregateMetadataView.find(ref.libraryId());
        if (metadata.isPresent()) {
            var entry = metadata.orElseThrow();
            if (entry.batchCount() > 1) {
                long firstRecipe = (long) entry.batchIndex() * entry.batchSize() + 1;
                long lastRecipe = firstRecipe + entry.recipeCount() - 1L;
                tooltip.add(Component.translatable(
                        "tooltip.aeallpattern.aggregate_pattern.part",
                        entry.batchIndex() + 1, entry.batchCount(), firstRecipe,
                        lastRecipe, entry.totalRecipeCount())
                        .withStyle(ChatFormatting.AQUA));
            }
            tooltip.add(Component.translatable(
                    "tooltip.aeallpattern.aggregate_pattern.count", entry.recipeCount())
                    .withStyle(ChatFormatting.GRAY));
            int total = entry.recipeCount();
            int enabled = enabledCount(stack.get(ModDataComponents.AGGREGATE_PATTERN_SELECTION.get()), total);
            if (enabled >= 0) {
                tooltip.add(Component.translatable(
                        "tooltip.aeallpattern.aggregate_pattern.selected_count", enabled, total)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.aeallpattern.aggregate_pattern.syncing")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.aeallpattern.aggregate_pattern.provider")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.aeallpattern.aggregate_pattern.configure")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.AGGREGATE_PATTERN.get()) || super.isFoil(stack);
    }

    /** Returns the enabled pattern count, or -1 when it cannot be derived from the item alone. */
    private static int enabledCount(AggregatePatternSelection selection, int total) {
        if (selection == null) {
            return -1;
        }
        if (selection.isAllEnabled()) {
            return total;
        }
        if (selection.inverted()) {
            return Math.min(selection.ids().size(), total);
        }
        return Math.max(0, total - selection.ids().size());
    }
}
