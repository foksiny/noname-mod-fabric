package dev.noname.mixin;

import com.sonicether.soundphysics.SoundPhysics;
import dev.noname.client.VhsFilterManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sound Physics Remastered compatibility.
 *
 * <p>SPR routes every sound it processes through
 * {@link SoundPhysics#setEnvironment}, which re-binds the source's single
 * {@code AL_DIRECT_FILTER} slot to SPR's own low-pass filter. That happens at
 * play-time (via its {@code Channel#play} injector) and again every few ticks
 * while a moving sound plays, so the "old VHS" low-pass installed by
 * {@link ChannelMixin} keeps getting wiped. This mixin re-attaches the VHS
 * filter right after SPR has set its environment, leaving SPR's reverb
 * (carried on the separate {@code AL_AUXILIARY_SEND_FILTER} path) untouched.
 *
 * <p>SPR is compiled into neither the runtime nor {@code fabric.mod.json}
 * dependency list: the class is resolved against the compile-only jar in
 * {@code libs/}, and the mixin is only registered when the mod is actually
 * installed (see {@link NonameMixinPlugin}).
 */
@Mixin(SoundPhysics.class)
public abstract class SoundPhysicsCompatMixin {

    /**
     * Runs after every SPR environment update for a source and puts the VHS
     * filter back into the direct-filter slot that SPR just claimed.
     */
    @Inject(method = "setEnvironment", at = @At("TAIL"), remap = false)
    private static void noname$reapplyVhsFilter(int sourceID, float sendGain0, float sendGain1,
                                                float sendGain2, float sendGain3, float sendCutoff0,
                                                float sendCutoff1, float sendCutoff2, float sendCutoff3,
                                                float directCutoff, float directGain, CallbackInfo ci) {
        VhsFilterManager.apply(sourceID);
    }
}