package io.github.langqi99.aeallpattern.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/** One uniquely registered output used only by the opt-in development stress fixture. */
final class StressTestItem extends Item {
    private final int displayNumber;

    StressTestItem(Properties properties, int displayNumber) {
        super(properties);
        this.displayNumber = displayNumber;
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return Component.literal("AE 压力测试物品 " + displayNumber);
    }
}
