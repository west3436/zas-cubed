package io.github.west3436.zas.compat.aeronautics.mixin;

import io.github.west3436.zas.compat.aeronautics.AeronauticsVersions;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the adapter's hook only beside a Create Aeronautics the adapter supports (COMPAT-002).
 *
 * <p>{@code neoforge.mods.toml} already keeps {@code zas.aeronautics.mixins.json} away unless
 * Aeronautics and Sable are installed ({@code requiredMods}). That can't tell one version from
 * another, and mixins are applied before {@code zas} decides whether to start the adapter, so this
 * plugin makes the same decision from the same range: outside {@link AeronauticsVersions#SUPPORTED}
 * the hook doesn't apply, and Sable runs untouched. It says nothing itself; the one warning about
 * an unsupported version is {@code zas}'s, when it leaves the adapter off.
 *
 * <p>It runs before the game's classes load, so it reads the version from the mod list the loader
 * has built and names no class of the game's, Aeronautics' or Sable's.
 */
public final class AeronauticsMixinPlugin implements IMixinConfigPlugin {
    /** Whether Mixin has loaded the config, which it does only with Aeronautics and Sable installed. */
    private static volatile boolean loaded;

    /** Whether the config's mixins apply: Aeronautics is installed in a supported version. */
    private static volatile boolean applies;

    /**
     * Whether Mixin loaded the adapter's mixin config.
     *
     * @return true if it did, so Aeronautics and Sable are installed
     */
    public static boolean loaded() {
        return loaded;
    }

    /**
     * Whether the adapter's hook applies.
     *
     * @return true if the config was loaded and Aeronautics is in a supported version
     */
    public static boolean applies() {
        return applies;
    }

    @Override
    public void onLoad(String mixinPackage) {
        loaded = true;
        applies = LoadingModList.get().getMods().stream()
                .filter(mod -> mod.getModId().equals(AeronauticsVersions.MOD_ID))
                .map(ModInfo::getVersion)
                .anyMatch(version -> AeronauticsVersions.supports(version.toString()));
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return applies;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
