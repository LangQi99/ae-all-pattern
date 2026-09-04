package io.github.langqi99.aeallpattern.registry;

import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.tianshu.TianshuRoutingMenu;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternConfigMenu;
import io.github.langqi99.aeallpattern.aggregate.AggregatePatternSelectionMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, AeAllPattern.MOD_ID);

    public static final RegistryObject<MenuType<TianshuRoutingMenu>> TIANSHU_ROUTING =
            MENUS.register("tianshu_routing", () -> IForgeMenuType.create(TianshuRoutingMenu::new));
    public static final RegistryObject<MenuType<AggregatePatternConfigMenu>> AGGREGATE_PATTERN_CONFIG =
            MENUS.register("aggregate_pattern_config", () ->
                    IForgeMenuType.create(AggregatePatternConfigMenu::new));
    public static final RegistryObject<MenuType<AggregatePatternSelectionMenu>> AGGREGATE_PATTERN_SELECTION =
            MENUS.register("aggregate_pattern_selection", () ->
                    IForgeMenuType.create(AggregatePatternSelectionMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
