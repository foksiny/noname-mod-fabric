package dev.noname.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates optional, cross-mod mixins on whether the mod they target is loaded.
 *
 * <p>{@link SoundPhysicsCompatMixin} targets a class that only exists when
 * Sound Physics Remastered is installed. Mixins can't be made optional per
 * entry in the config JSON, so this plugin vetoes the compat mixin unless the
 * class is present, keeping the game bootable with or without SPR.
 */
public final class NonameMixinPlugin implements IMixinConfigPlugin {

    private static final String SOUND_PHYSICS_COMPAT_MIXIN =
            "dev.noname.mixin.SoundPhysicsCompatMixin";
    private static final String SOUND_PHYSICS_CLASS =
            "com.sonicether.soundphysics.SoundPhysics";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (SOUND_PHYSICS_COMPAT_MIXIN.equals(mixinClassName)) {
            return isSoundPhysicsLoaded();
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isSoundPhysicsLoaded() {
        try {
            Class.forName(SOUND_PHYSICS_CLASS, false, NonameMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}