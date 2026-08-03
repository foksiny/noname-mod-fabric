package dev.noname.client;

import dev.noname.Day10LookHandler;
import dev.noname.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

import java.util.Random;

/**
 * Client-side driver for the day-10+ "look behind" jumpscare.
 *
 * <p>Timeline (server and client agree on these numbers — see
 * {@link Day10LookHandler}):
 * <pre>
 * tick 0       day10_look received; camera snaps behind instantly, FOV
 *              zooms in, render distance drops to 5, laggy3.ogg starts
 * tick 10      fake player removed (server-side); camera swings back,
 *              FOV and render distance restore
 * tick 13      camera back to the pre-event pose; movement unlocked;
 *              the player takes the hit (server-side)
 *              day10_look_stop received; text disappears
 * </pre>
 *
 * <p>The fake player never gets to be seen while the camera points forward
 * again: it vanishes 3 ticks before the view returns (the ghost lives for
 * {@value Day10LookHandler#FAKE_LIFETIME_TICKS} ticks, the event for
 * {@value Day10LookHandler#TOTAL_EVENT_TICKS}).
 *
 * <p>While active the handler disables the player's movement input (the
 * server additionally pins the player's position), keeps the camera pinned
 * on the behind-pose until the fade-out, plays the lo-fi static clip once
 * and paints a big red "DO YOU EVEN CARE?" that teleports around the screen
 * and blinks.
 */
public final class Day10LookClient {

    private static final Random RANDOM = new Random();

    /** Ticks it takes to swing back out (10..13). The swing-in is instant. */
    private static final long SWING_BACK_START = 10;

    /** FOV multiplier at full zoom (zoom IN, so below 1.0). */
    private static final float ZOOM_FOV_MULTIPLIER = 0.70F;

    /** Render distance forced while the event runs. */
    private static final int EVENT_RENDER_DISTANCE = 5;

    /** Last server tick known to the client. */
    private static long lastTick = -1L;

    /** Start server tick of the active event, or -1. */
    private static long startTick = -1L;

    /** Whether the camera swing is currently in progress. */
    private static boolean active;

    /** Player yaw/pitch at event start — where the view must return to. */
    private static float startYaw;
    private static float startPitch;

    /** Yaw (degrees) the camera is being swung to. */
    private static float targetYaw;

    /** Pitch (degrees) the camera is being swung to. */
    private static float targetPitch;

    /** Render distance before the event, to restore on stop. */
    private static int savedRenderDistance = -1;

    /** X position of the "DO YOU EVEN CARE?" text (pixels). */
    private static float textX;

    /** Y position of the "DO YOU EVEN CARE?" text (pixels). */
    private static float textY;

    /** Ticks until the text teleports or blinks again. */
    private static int textTimer;

    private Day10LookClient() {
    }

    /** Starts the event, looking straight at the fake player's position
     *  (angles computed server-side). Called by the client payload handler.
     *  The no-arg form is the fallback for a plain {@code day10_look} event:
     *  a straight 180-degree turn. */
    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        start(mc.player.getYRot() + 180.0F, mc.player.getXRot());
    }

    /** Starts the event, looking at the given yaw/pitch (the fake player's
     *  position, as computed by the server). */
    public static void start(float lookYaw, float lookPitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        long tick = tickOf(mc);
        startTick = tick;
        lastTick = tick;
        active = true;
        // The ghost stands behind the player: camera swings to face it.
        startYaw = mc.player.getYRot();
        startPitch = mc.player.getXRot();
        targetYaw = lookYaw;
        targetPitch = lookPitch;
        textX = 0.0F;
        textY = 0.0F;
        textTimer = 0;
        // The lag: render distance drops and the lo-fi clip plays.
        savedRenderDistance = mc.options.renderDistance().get();
        mc.options.renderDistance().set(EVENT_RENDER_DISTANCE);
        // Bound to the player entity itself so the lo-fi clip always
        // follows the player and can never be walked away from.
        PlayerSound.play(mc, ModSounds.LAGGY3, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Ends the event and restores the pre-event pose. Called by the client
     *  payload handler, {@link #tick()} on expiry and stopall. */
    public static void stop() {
        if (!active) {
            return;
        }
        active = false;
        startTick = -1L;
        lastTick = -1L;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.setYRot(startYaw);
            mc.player.setXRot(startPitch);
            if (savedRenderDistance >= 0) {
                mc.options.renderDistance().set(savedRenderDistance);
                savedRenderDistance = -1;
            }
        }
    }

    public static boolean isActive() {
        return active;
    }

    /** Ticks elapsed since the event started, or 0 when inactive. */
    private static long elapsedTicks() {
        if (startTick < 0) {
            return 0L;
        }
        return lastTick - startTick;
    }

    private static long tickOf(Minecraft mc) {
        return mc.level != null && mc.level.getGameTime() > 0
                ? mc.level.getGameTime()
                : mc.gui.getGuiTicks();
    }

    /** First-person camera hook: snaps the camera behind the player, holds
     *  it there, then swings it back. Returns false when inactive, so the
     *  caller leaves the vanilla camera untouched. */
    public static boolean applyCamera(CameraPose pose) {
        if (!active || startTick < 0) {
            return false;
        }
        long elapsed = elapsedTicks();
        if (elapsed < SWING_BACK_START) {
            pose.set(targetYaw, targetPitch);
        } else {
            // Swinging back to where the player was looking. The fake
            // player is already gone by now.
            float t = smooth(Math.min(1.0F,
                    (float) (elapsed - SWING_BACK_START)
                            / (Day10LookHandler.TOTAL_EVENT_TICKS - SWING_BACK_START)));
            pose.set(targetYaw + (startYaw - targetYaw) * t,
                    targetPitch + (startPitch - targetPitch) * t);
        }
        return true;
    }

    /** Zoom hook: zooms the FOV in (zoom IN = smaller FOV), instantly on the
     *  snap, ramping out when the camera swings back. */
    public static float applyZoom(double baseFov) {
        if (!active || startTick < 0) {
            return (float) baseFov;
        }
        long elapsed = elapsedTicks();
        float t;
        if (elapsed < SWING_BACK_START) {
            t = 1.0F;
        } else {
            t = 1.0F - (float) (elapsed - SWING_BACK_START)
                    / (Day10LookHandler.TOTAL_EVENT_TICKS - SWING_BACK_START);
        }
        t = Math.max(0.0F, Math.min(1.0F, t));
        return (float) (baseFov * (1.0F - (1.0F - ZOOM_FOV_MULTIPLIER) * smooth(t)));
    }

    /** Movement hook: blocks all movement input while the event runs. The
     *  server additionally pins the player's position. */
    public static boolean isMovementLocked() {
        return active;
    }

    /** HUD hook: on the teleport cadence picks a new random position, then
     *  returns the current blink alpha (0 to skip drawing this frame). */
    public static float textAlpha() {
        if (!active || startTick < 0) {
            return 0.0F;
        }
        if (textTimer <= 0) {
            Minecraft mc = Minecraft.getInstance();
            textX = RANDOM.nextInt(Math.max(10, mc.getWindow().getGuiScaledWidth() - 80));
            textY = RANDOM.nextInt(Math.max(10, mc.getWindow().getGuiScaledHeight() - 30));
            textTimer = 3 + RANDOM.nextInt(6);
        }
        textTimer--;
        return 0.35F + RANDOM.nextFloat() * 0.65F;
    }

    /** Current text position (pixels), set by {@link #textAlpha()}. */
    public static float textX() {
        return textX;
    }

    /** Current text position (pixels), set by {@link #textAlpha()}. */
    public static float textY() {
        return textY;
    }

    /** Tick hook: called by the client mixin at the end of every player
     *  tick. Keeps the timeline moving and expires the event. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        lastTick = tickOf(mc);
        if (!active) {
            return;
        }
        long elapsed = elapsedTicks();
        if (elapsed >= Day10LookHandler.TOTAL_EVENT_TICKS) {
            stop();
        } else if (elapsed < SWING_BACK_START) {
            // Hold: keep the player's view pinned on the behind-pose.
            mc.player.setYRot(targetYaw);
            mc.player.setXRot(targetPitch);
        }
    }

    /** {@return a smoothly-interpolated 0..1 value} */
    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    /** Mutable camera pose used by the mixin hook. */
    public static final class CameraPose {
        private float yaw;
        private float pitch;

        public void set(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public float yaw() {
            return yaw;
        }

        public float pitch() {
            return pitch;
        }
    }
}
