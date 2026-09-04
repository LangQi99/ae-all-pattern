package io.github.langqi99.aeallpattern.registry;

import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.linker.PatternLinkerBlock;
import io.github.langqi99.aeallpattern.tianshu.TianshuPatternSelectorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, AeAllPattern.MOD_ID);

    public static final RegistryObject<PatternLinkerBlock> PATTERN_LINKER = BLOCKS.register(
            "pattern_linker",
            () -> new PatternLinkerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .sound(SoundType.METAL)
                    .strength(2.2F, 10.0F)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<TianshuPatternSelectorBlock> TIANSHU_PATTERN_SELECTOR = BLOCKS.register(
            "tianshu_pattern_selector",
            () -> new TianshuPatternSelectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .sound(SoundType.METAL)
                    .strength(5.0F, 12.0F)
                    .lightLevel(state -> state.getValue(TianshuPatternSelectorBlock.ACTIVE) ? 7 : 1)
                    .requiresCorrectToolForDrops()));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
