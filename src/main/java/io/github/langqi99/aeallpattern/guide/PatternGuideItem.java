package io.github.langqi99.aeallpattern.guide;

import guideme.GuidesCommon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Uses GuideME's common API so the item is safe on dedicated servers. */
public final class PatternGuideItem extends Item {
    public static final ResourceLocation GUIDE_ID = ResourceLocation.fromNamespaceAndPath("aeallpattern", "guide");

    public PatternGuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            GuidesCommon.openGuide(player, GUIDE_ID);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
