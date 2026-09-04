package io.github.langqi99.aeallpattern.mixin;

import java.util.List;
import java.util.Set;
import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class AeAllPatternMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (mixin.contains("Ae2cs")) {
            return loaded("ae2cs");
        }
        if (mixin.endsWith("ECOCraftingPatternBusBlockEntityMixin")) {
            return loaded("neoecoae");
        }
        if (mixin.endsWith("MatrixPatternStorageBlockEntityMixin")) {
            return loaded("ae2lt");
        }
        if (mixin.startsWith("io.github.langqi99.aeallpattern.mixin.ExtendedAePlus")) {
            return loaded("extendedae_plus");
        }
        if (mixin.startsWith("io.github.langqi99.aeallpattern.mixin.ExtendedAe")) {
            return loaded("expatternprovider");
        }
        if (mixin.endsWith("PigmeePatternProviderBlockEntityMixin")) {
            return loaded("ae2lt");
        }
        if (mixin.endsWith("AdvPatternProviderLogicMixin")
                || mixin.endsWith("AdvPatternEncoderMenuMixin")) {
            return loaded("advanced_ae");
        }
        if (mixin.endsWith("StablePatternProviderLogicMixin")) {
            return loaded("ae2ltpp");
        }
        if (mixin.endsWith("OverloadedProviderPatternCatalogMixin")) {
            return loaded("ae2lt");
        }
        if (mixin.endsWith("AdvancedAlloyFurnaceAeManagerMixin")) {
            return loaded("useless_mod");
        }
        if (mixin.endsWith("PackagedAutoPackagingProviderItemHandlerMixin")
                || mixin.endsWith("PackagedAutoPackagingProviderBlockEntityMixin")) {
            return loaded("packagedauto");
        }
        return true;
    }
    private static boolean loaded(String modId) {
        return FMLLoader.getLoadingModList().getModFileById(modId) != null;
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
