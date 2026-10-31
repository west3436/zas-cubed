package io.github.west3436.zasspike.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Applies the hooks named in -Dzasspike.hooks (chunk, poi), so S1 can run a no-hook baseline. */
public final class HookPlugin implements IMixinConfigPlugin {
    private static final String HOOKS = System.getProperty("zasspike.hooks", "chunk,poi");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("LevelChunkMixin")) {
            return HOOKS.contains("chunk");
        }
        if (mixinClassName.endsWith("ServerLevelMixin")) {
            return HOOKS.contains("poi");
        }
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {}

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
