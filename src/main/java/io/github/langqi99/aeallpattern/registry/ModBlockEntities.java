package io.github.langqi99.aeallpattern.registry;

import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.linker.PatternLinkerBlockEntity;
import io.github.langqi99.aeallpattern.tianshu.TianshuPatternSelectorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AeAllPattern.MOD_ID);
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<PatternLinkerBlockEntity>> PATTERN_LINKER =
            BLOCK_ENTITIES.register("pattern_linker", () -> BlockEntityType.Builder.of(
                    PatternLinkerBlockEntity::new, ModBlocks.PATTERN_LINKER.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TianshuPatternSelectorBlockEntity>>
            TIANSHU_PATTERN_SELECTOR = BLOCK_ENTITIES.register(
                    "tianshu_pattern_selector",
                    () -> BlockEntityType.Builder.of(
                            TianshuPatternSelectorBlockEntity::new,
                            ModBlocks.TIANSHU_PATTERN_SELECTOR.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
