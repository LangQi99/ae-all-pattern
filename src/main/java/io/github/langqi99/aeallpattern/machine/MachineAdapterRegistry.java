package io.github.langqi99.aeallpattern.machine;

import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.compat.mekanism.MekanismAdapters;
import io.github.langqi99.aeallpattern.machine.vanilla.VanillaFurnaceAdapter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

public final class MachineAdapterRegistry {
    private static final List<MachineAdapter> ADAPTERS = new ArrayList<>();

    static {
        register(new VanillaFurnaceAdapter());
        if (ModList.get().isLoaded("mekanism")) {
            MekanismAdapters.registerAll();
        }
        if (ModList.get().isLoaded("packagedexcrafting") || ModList.get().isLoaded("packagedavaritia")) {
            register(new PackagedCraftingAdapter());
        }
    }

    private MachineAdapterRegistry() {
    }

    public static void initialize() {
        // Triggers deterministic built-in and conditional compatibility registration.
    }

    public static synchronized void register(MachineAdapter adapter) {
        if (ADAPTERS.stream().anyMatch(existing -> existing.id().equals(adapter.id()))) {
            throw new IllegalArgumentException("duplicate machine adapter id: " + adapter.id());
        }
        ADAPTERS.add(adapter);
        ADAPTERS.sort(Comparator.comparing(candidate -> candidate.id().toString()));
    }

    public static synchronized Optional<MachineAdapter> find(ServerLevel level, BlockEntity target) {
        List<MachineAdapter> matches = ADAPTERS.stream()
                .filter(adapter -> adapter.supports(level, target))
                .toList();
        if (matches.size() > 1) {
            AeAllPattern.LOGGER.error("Multiple machine adapters match {} at {}: {}",
                    target.getType(), target.getBlockPos(),
                    matches.stream().map(MachineAdapter::id).toList());
            return Optional.empty();
        }
        return matches.stream().findFirst();
    }

    public static synchronized Optional<MachineAdapter> byId(ResourceLocation id) {
        return ADAPTERS.stream().filter(adapter -> adapter.id().equals(id)).findFirst();
    }
}
