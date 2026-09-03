package io.github.langqi99.aeallpattern.aggregate;

import io.github.langqi99.aeallpattern.machine.MachineAdapterRegistry;
import io.github.langqi99.aeallpattern.recipe.RecipeIndexService;
import io.github.langqi99.aeallpattern.registry.ModDataComponents;
import io.github.langqi99.aeallpattern.registry.ModItems;
import io.github.langqi99.aeallpattern.network.AggregateMetadataSyncService;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;

/** Captures all deterministic recipes exposed by one supported machine. */
public final class AllPatternGeneratorItem extends Item {
    public AllPatternGeneratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.FAIL;
        }
        if (!context.isSecondaryUseActive()) {
            if (!context.getLevel().isClientSide()) {
                show(player, "message.aeallpattern.generator.sneak_required");
            }
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.FAIL;
        }

        BlockPos pos = context.getClickedPos();
        BlockEntity target = level.getBlockEntity(pos);
        if (target == null) {
            // A crafting table has no block entity. Its recipes are consumed directly
            // by AE's crafting-pattern path, so accept the client-side crafting scan.
            if (level.getBlockState(pos).is(Blocks.CRAFTING_TABLE) && ModList.get().isLoaded("jei")) {
                return InteractionResult.SUCCESS;
            }
            show(player, "message.aeallpattern.generator.unsupported");
            return InteractionResult.FAIL;
        }
        var adapter = MachineAdapterRegistry.find(level, target);
        if (adapter.isEmpty()) {
            // Unsupported machines are discovered through the client's JEI view. The
            // scanner accepts only a JEI category owned by the target machine's mod.
            if (ModList.get().isLoaded("jei")) {
                return InteractionResult.SUCCESS;
            }
            show(player, "message.aeallpattern.generator.unsupported");
            return InteractionResult.FAIL;
        }
        var catalog = RecipeIndexService.catalog(level, target, adapter.orElseThrow());
        if (catalog.recipes().isEmpty()) {
            show(player, "message.aeallpattern.generator.empty");
            return InteractionResult.FAIL;
        }
        List<AggregateRecipe> recipes = catalog.recipes().stream()
                .limit(AggregatePatternData.configuredRecipeLimit())
                .map(AggregateRecipe::from)
                .toList();
        var catalystId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                level.getBlockState(pos).getBlock());
        var library = AggregatePatternLibrary.get(level.getServer());
        ItemStack aggregate = new ItemStack(ModItems.AGGREGATE_PATTERN.get());
        String machineTranslationKey = target.getBlockState().getBlock().getDescriptionId();
        var ref = library.put(level.getServer(), catalystId, machineTranslationKey, recipes);
        aggregate.set(ModDataComponents.AGGREGATE_PATTERN.get(), ref);
        AggregateMetadataSyncService.sendToOnlinePlayers(level.getServer());
        if (!player.addItem(aggregate)) {
            player.drop(aggregate, false);
        }
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.25F);
        show(player, "message.aeallpattern.generator.created",
                Component.translatable(machineTranslationKey), recipes.size());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(
            @NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.aeallpattern.generator.usage"));
    }

    private static void show(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

}
