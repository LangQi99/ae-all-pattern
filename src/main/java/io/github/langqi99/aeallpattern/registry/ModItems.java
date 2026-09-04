package io.github.langqi99.aeallpattern.registry;

import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.binding.PatternBinderItem;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternItem;
import io.github.langqi99.aeallpattern.aggregate.AllPatternGeneratorItem;
import io.github.langqi99.aeallpattern.tianshu.TianshuPatternSelectorItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AeAllPattern.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AeAllPattern.MOD_ID);

    public static final RegistryObject<PatternBinderItem> PATTERN_BINDER = ITEMS.register(
            "pattern_binder", () -> new PatternBinderItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockItem> PATTERN_LINKER = ITEMS.register(
            "pattern_linker", () -> new BlockItem(ModBlocks.PATTERN_LINKER.get(), new Item.Properties()));
    public static final RegistryObject<TianshuPatternSelectorItem> TIANSHU_PATTERN_SELECTOR = ITEMS.register(
            "tianshu_pattern_selector",
            () -> new TianshuPatternSelectorItem(ModBlocks.TIANSHU_PATTERN_SELECTOR.get(), new Item.Properties()));
    public static final RegistryObject<AllPatternGeneratorItem> ALL_PATTERN_GENERATOR = ITEMS.register(
            "all_pattern_generator", () -> new AllPatternGeneratorItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<AggregatePatternItem> AGGREGATE_PATTERN = ITEMS.register(
            "aggregate_pattern", () -> new AggregatePatternItem(new Item.Properties().stacksTo(1)));

    /**
     * Development-only registry stress fixture. Normal launches leave the property unset, so
     * release builds register no fixture items at all.
     */
    public static final int STRESS_ITEM_COUNT = registerStressItems();

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aeallpattern"))
                    .icon(() -> PATTERN_BINDER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(PATTERN_BINDER.get());
                        output.accept(PATTERN_LINKER.get());
                        output.accept(TIANSHU_PATTERN_SELECTOR.get());
                        output.accept(ALL_PATTERN_GENERATOR.get());
                    })
                    .build());

    private ModItems() {
    }

    private static int registerStressItems() {
        int count = Math.max(0, Math.min(Integer.getInteger("aeallpattern.stressItemCount", 0), 20_000));
        for (int index = 0; index < count; index++) {
            String name = "stress_item_%05d".formatted(index);
            int displayNumber = index + 1;
            ITEMS.register(name, () -> new StressTestItem(new Item.Properties(), displayNumber));
        }
        return count;
    }

    public static void register(IEventBus modBus) {
        if (STRESS_ITEM_COUNT > 0) {
            AeAllPattern.LOGGER.warn("Registering {} development stress-test items", STRESS_ITEM_COUNT);
        }
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
