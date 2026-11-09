package io.github.west3436.zas.compat.mekanism.mixin;

import io.github.west3436.zas.compat.mekanism.MekanismVersions;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the adapter's mixins only to a Mekanism the adapter supports (MEK-001).
 *
 * <p>{@code neoforge.mods.toml} already keeps {@code zas.mekanism.mixins.json} away unless
 * Mekanism is installed ({@code requiredMods}). That can't tell one version from another, and
 * mixins are applied before {@code zas} decides whether to start the adapter, so this plugin makes
 * the same decision from the same range: outside {@link MekanismVersions#SUPPORTED} no mixin of the
 * config applies, and Mekanism runs untouched. It says nothing itself; the one warning about an
 * unsupported version is {@code zas}'s, when it leaves the adapter off.
 *
 * <p>It runs before the game's classes load, so it reads Mekanism's version from the mod list the
 * loader has built and names no class of the game's or of Mekanism's.
 */
public final class MekanismMixinPlugin implements IMixinConfigPlugin {
    /** Whether Mixin has loaded the config, which it does only when Mekanism is installed. */
    private static volatile boolean loaded;

    /** Whether the config's mixins apply: Mekanism is installed in a supported version. */
    private static volatile boolean applies;

    /**
     * Whether Mixin loaded the adapter's mixin config.
     *
     * @return true if it did, so Mekanism is installed
     */
    public static boolean loaded() {
        return loaded;
    }

    /**
     * Whether the adapter's mixins apply.
     *
     * @return true if the config was loaded and Mekanism is in a supported version
     */
    public static boolean applies() {
        return applies;
    }

    @Override
    public void onLoad(String mixinPackage) {
        loaded = true;
        ModFileInfo mekanism = LoadingModList.get().getModFileById(MekanismVersions.MOD_ID);
        applies = mekanism != null
                && mekanism.getMods().stream()
                        .filter(mod -> mod.getModId().equals(MekanismVersions.MOD_ID))
                        .anyMatch(mod ->
                                MekanismVersions.supports(mod.getVersion().toString()));
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
    public void postApply(
            String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
