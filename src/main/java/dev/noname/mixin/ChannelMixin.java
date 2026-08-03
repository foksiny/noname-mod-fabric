package dev.noname.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Always-on "muffled, old VHS" sound effect.
 *
 * <p>Applied globally to every sound channel:
 * <ul>
 *   <li>A low-pass filter (AL_FILTER_LOWPASS) cuts off high frequencies,
 *       creating that distinct muffled, "behind a wall" or "low-bitrate tape"
 *       sensation.</li>
 *   <li>The direct source gain and high-frequency gain are tuned to match
 *       the frequency response of an old camcorder.</li>
 *   <li>Subtle pitch warble: every time a sound plays, it gets a tiny
 *       random pitch offset to simulate tape speed instability.</li>
 * </ul>
 */
@Mixin(Channel.class)
public abstract class ChannelMixin {

    @Shadow
    @Final
    private int source;

    @Unique
    private int filter = -1;

    /**
     * When a channel is created, generate an OpenAL filter and configure it
     * as a low-pass filter.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void noname$initVhsFilter(int source, CallbackInfo ci) {
        try {
            this.filter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(this.filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            
            // GAIN (overall volume): 1.0 = normal.
            // GAINHF (high frequency volume): 0.2 = heavily muffled.
            EXTEfx.alFilterf(this.filter, EXTEfx.AL_LOWPASS_GAIN, 1.0F);
            EXTEfx.alFilterf(this.filter, EXTEfx.AL_LOWPASS_GAINHF, 0.18F);

            // Attach the filter to the source.
            AL10.alSourcei(this.source, EXTEfx.AL_DIRECT_FILTER, this.filter);
        } catch (Exception e) {
            // If EFX isn't supported or fails, we just don't get the effect.
            this.filter = -1;
        }
    }

    /**
     * Every time a sound starts playing, we reinforce the filter and add
     * a tiny bit of "wow/flutter" — a random pitch shift that stays for
     * the duration of this play-call (or until the next pitch update).
     */
    @Inject(method = "play", at = @At("HEAD"))
    private void noname$applyVhsPitchWarble(CallbackInfo ci) {
        if (this.filter != -1) {
            AL10.alSourcei(this.source, EXTEfx.AL_DIRECT_FILTER, this.filter);
        }

        // Slight speed/pitch instability (tape warble).
        // +/- 1.5% pitch variation.
        float warble = 0.985F + (float) Math.random() * 0.03F;
        AL10.alSourcef(this.source, AL10.AL_PITCH, warble);
    }

    /**
     * Clean up the filter when the channel is destroyed to avoid memory leaks
     * in the OpenAL driver.
     */
    @Inject(method = "destroy", at = @At("HEAD"))
    private void noname$cleanupVhsFilter(CallbackInfo ci) {
        if (this.filter != -1) {
            EXTEfx.alDeleteFilters(this.filter);
            this.filter = -1;
        }
    }
}
