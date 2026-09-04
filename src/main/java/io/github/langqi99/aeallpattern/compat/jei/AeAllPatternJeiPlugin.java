package io.github.langqi99.aeallpattern.compat.jei;

import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import mezz.jei.api.runtime.IJeiRuntime;
import java.util.Optional;

/** Optional client-side JEI help; recipe discovery remains server-authoritative. */
@JeiPlugin
public final class AeAllPatternJeiPlugin implements IModPlugin {
    private static volatile IJeiRuntime runtime;
    private static final ResourceLocation ID =
            new ResourceLocation(AeAllPattern.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime availableRuntime) {
        runtime = availableRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    public static Optional<IJeiRuntime> runtime() {
        return Optional.ofNullable(runtime);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(
                ModItems.PATTERN_BINDER.get().getDefaultInstance(),
                Component.translatable("jei.aeallpattern.pattern_binder.info"));
        registration.addItemStackInfo(
                ModItems.PATTERN_LINKER.get().getDefaultInstance(),
                Component.translatable("jei.aeallpattern.pattern_linker.info"));
    }
}
