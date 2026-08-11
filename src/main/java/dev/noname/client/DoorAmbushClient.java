package dev.noname.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client side of the day-15+ door ambush. For the 5 seconds the server pins
 * the victim in place this handler:
 * <ul>
 *   <li>locks ALL input: movement (the server pins the position too),
 *       the mouse (look, scroll, buttons, see {@link
 *       dev.noname.mixin.DoorAmbushMouseMixin}) and every keybind
 *       (inventory, chat, hotbar... see {@link
 *       dev.noname.mixin.DoorAmbushKeybindsMixin});</li>
 *   <li>zooms the FOV in (quick fade-in, hold, fade-out before the hit);</li>
 *   <li>plays footsteps on the block the player stands on, starting about
 *       {@value #START_DISTANCE} blocks BEHIND the player and closing in at
 *       an accelerating pace, the step cadence speeding up as it nears. The
 *       source is recomputed from the player's current yaw for every step,
 *       so it always comes from behind no matter how the victim turns.</li>
 * </ul>
 * The darkness is a real server-side {@code MobEffects.DARKNESS} effect, so
 * nothing client-side is needed for it.
 *
 * <p>The timeline ({@value #AMBUSH_TICKS} ticks) must match
 * {@link dev.noname.DoorAmbushHandler#EVENT_TICKS}; the server additionally
 * sends a {@code door_ambush_stop} payload when the hit lands and on
 * stopall, which restores everything immediately.
 */
public final class DoorAmbushClient {

    /** Whole event length in ticks (8 seconds). Must match the server's
     *  {@code EVENT_TICKS}. */
    private static final int AMBUSH_TICKS = 20 * 8;

    /** Zoom fade-in and fade-out length, in ticks. */
    private static final int FADE_TICKS = 10;

    /** FOV multiplier at full zoom (zoom IN, so below 1.0). */
    private static final float ZOOM_FOV_MULTIPLIER = 0.60F;

    /** Where the footsteps start, behind the player, in blocks. */
    private static final double START_DISTANCE = 12.0D;

    /** Approach curve: 1.0 = constant speed, &gt;1 = accelerating, closing
     *  most of the distance at the end (the rapid approach). */
    private static final double APPROACH_EXPONENT = 2.0D;

    /** Ticks between steps at the start of the event. */
    private static final int STEP_START_INTERVAL = 9;

    /** Ticks between steps at the end (a rapid sprint). */
    private static final int STEP_END_INTERVAL = 4;

    /** Base step volume; the engine's distance attenuation does the rest. */
    private static final float STEP_VOLUME = 1.0F;

    /** Session tick counter, same pattern as {@link StalkerDarknessHandler}. */
    private static int sessionTick = 0;

    /** Session ticks at which the event starts/ends; -1 = inactive. */
    private static int startTick = -1;
    private static int endTick = -1;

    /** Session tick at which the next footstep plays. */
    private static int nextStepTick = 0;

    private DoorAmbushClient() {
    }

    /** Tick hook, registered against
     *  {@code ClientTickEvents.START_CLIENT_TICK}. */
    public static void onClientTick(Minecraft mc) {
        sessionTick++;
        if (mc.level == null || mc.player == null) {
            stop();
            return;
        }
        if (!isActive()) {
            return;
        }
        if (sessionTick >= endTick) {
            stop();
            return;
        }
        if (sessionTick >= nextStepTick) {
            playStep(mc);
            nextStepTick = sessionTick + stepInterval();
        }
    }

    /** Starts the ambush — called by the {@code door_ambush} event payload
     *  handler. */
    public static void start() {
        startTick = sessionTick;
        endTick = sessionTick + AMBUSH_TICKS;
        nextStepTick = sessionTick;
    }

    /** Ends the event. Zoom and movement lock are derived from the active
     *  flag, so they revert by themselves. */
    public static void stop() {
        startTick = -1;
        endTick = -1;
    }

    /** {@return whether the ambush is currently running} */
    public static boolean isActive() {
        return endTick > 0 && sessionTick >= startTick && sessionTick < endTick;
    }

    /** {@return progress 0..1 through the event} */
    private static float progress() {
        return Math.min(1.0F, (sessionTick - startTick) / (float) AMBUSH_TICKS);
    }

    /** Zoom hook: the FOV zooms in over the first ticks, holds, and fades
     *  back out right before the hit. Returns the base FOV unchanged when
     *  inactive, so the caller leaves the vanilla FOV untouched. */
    public static float applyZoom(double baseFov) {
        if (!isActive()) {
            return (float) baseFov;
        }
        int elapsed = sessionTick - startTick;
        float t;
        if (elapsed < FADE_TICKS) {
            t = elapsed / (float) FADE_TICKS;
        } else if (endTick - sessionTick <= FADE_TICKS) {
            t = (endTick - sessionTick) / (float) FADE_TICKS;
        } else {
            t = 1.0F;
        }
        t = Math.max(0.0F, Math.min(1.0F, smooth(t)));
        return (float) (baseFov * (1.0F - (1.0F - ZOOM_FOV_MULTIPLIER) * t));
    }

    /** Movement hook: blocks all movement input while the event runs (the
     *  server additionally pins the player's position). */
    public static boolean isMovementLocked() {
        return isActive();
    }

    /** Ticks between footsteps, shrinking as the walker speeds up. */
    private static int stepInterval() {
        float p = progress();
        return Math.max(1, Math.round(STEP_START_INTERVAL
                - (STEP_START_INTERVAL - STEP_END_INTERVAL) * p));
    }

    /** Plays one footstep: the step sound of the block the player stands
     *  on, at a point behind the player that closes in on them. */
    private static void playStep(Minecraft mc) {
        LocalPlayer player = mc.player;
        SoundEvent step = stepSound(player);
        if (step == null || step == SoundEvents.EMPTY) {
            return;
        }
        float p = progress();
        double dist = START_DISTANCE * Math.pow(1.0D - p, APPROACH_EXPONENT);
        double rad = Math.toRadians(player.getYRot());
        // Behind = opposite of the facing vector (-sin(yaw), 0, cos(yaw)),
        // so the source always stays behind the player's current view.
        double x = player.getX() + Math.sin(rad) * dist;
        double z = player.getZ() - Math.cos(rad) * dist;
        double y = player.getY();
        mc.getSoundManager().play(new SimpleSoundInstance(step, SoundSource.PLAYERS,
                STEP_VOLUME, 0.95F + mc.level.random.nextFloat() * 0.1F,
                mc.level.random, x, y, z));
    }

    /** {@return the step sound of the block the player stands on, falling
     *  back to the block at the player's feet when there is nothing below} */
    private static SoundEvent stepSound(LocalPlayer player) {
        SoundType type = player.getBlockStateOn().getSoundType();
        if (type == SoundType.EMPTY) {
            BlockState at = player.level().getBlockState(
                    BlockPos.containing(player.getX(), player.getY(), player.getZ()));
            type = at.getSoundType();
        }
        return type.getStepSound();
    }

    /** {@return a smoothly-interpolated 0..1 value} */
    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }
}
