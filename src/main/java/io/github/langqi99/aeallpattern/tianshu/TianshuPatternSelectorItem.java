package io.github.langqi99.aeallpattern.tianshu;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

public final class TianshuPatternSelectorItem extends BlockItem {
    public TianshuPatternSelectorItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(
            @NotNull ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.aeallpattern.tianshu_pattern_selector.cpu")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.aeallpattern.tianshu_pattern_selector.pending")
                .withStyle(ChatFormatting.GRAY));
    }
}
