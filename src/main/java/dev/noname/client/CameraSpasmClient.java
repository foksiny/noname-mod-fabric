package dev.noname.client;

import net.minecraft.client.Minecraft;

import java.util.Random;

/**
 * Client-side driver for the day-1+ "camera spasm".
 *
 * <p>Timeline (server and client agree on these numbers — see
 * {@link dev.noname.CameraSpasmHandler}):
 * <pre>
 * tick 0       camera_spasm received; the camera whips to a new random
 *              yaw/pitch every tick
 * tick 20      event over; the camera snaps back to where the player is
 *              actually looking (their true rotation was never touched)
 * </pre>
 *
 * <p>The camera is overridden at the end of {@code Camera.setup}, exactly
 * like the day-10+ look-behind event, so only the rendered view jerks
 * around — the player's real aim, movement direction and hitbox stay put.
 * While the day-10+ event is running, the spasm stands down so the two
 * camera mixins never fight.
 */
public final class CameraSpasmClient {

    /** Whole event length, in ticks (1 second). Must match the server's
     *  TOTAL_EVENT_TICKS. */
    private static final int TOTAL_TICKS = 20;

    private static final Random RANDOM = new Random();

    /** Whether the spasm is currently in progress. */
    private static boolean active;

    /** Start client tick of the active event, or -1. */
    private static long startTick = -1L;

    /** Last client tick known to this handler. */
    private static long lastTick = -1L;

    /** Tick the last random target was picked on. */
    private static long lastElapsed = -1L;

    /** Yaw (degrees) the camera is currently whipping to. */
    private static float targetYaw;

    /** Pitch (degrees) the camera is currently whipping to. */
    private static float targetPitch;

    private CameraSpasmClient() {
    }

    /** Starts the event. Called by the client payload handler. */
    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || Day10LookClient.isActive()) {
            return;
        }
        startTick = tickOf(mc);
        lastTick = startTick;
        lastElapsed = -1L;
        active = true;
        pickTarget();
    }

    /** Ends the event. Called by the payload stopall and by
     *  {@link #onClientTick()} on expiry. */
    public static void stop() {
        if (!active) {
            return;
        }
        active = false;
        startTick = -1L;
        lastTick = -1L;
        lastElapsed = -1L;
    }

    public static boolean isActive() {
        return active;
    }

    /** Tick hook: called by the client initializer every client tick. Keeps
     *  the timeline moving, picks a new random direction each tick and
     *  expires the event. */
    public static void onClientTick(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        lastTick = tickOf(mc);
        if (!active) {
            return;
        }
        long elapsed = lastTick - startTick;
        if (elapsed >= TOTAL_TICKS) {
            stop();
            return;
        }
        if (elapsed != lastElapsed) {
            lastElapsed = elapsed;
            pickTarget();
        }
    }

    /** First-person camera hook: whips the camera to the current random
     *  target. Returns false when inactive (or while the day-10+ look event
     *  owns the camera), so the caller leaves the vanilla camera untouched. */
    public static boolean applyCamera(CameraPose pose) {
        if (!active || startTick < 0 || Day10LookClient.isActive()) {
            return false;
        }
        pose.set(targetYaw, targetPitch);
        return true;
    }

    /** Picks the next random direction the camera whips to. */
    private static void pickTarget() {
        targetYaw = RANDOM.nextFloat() * 360.0F;
        targetPitch = -85.0F + RANDOM.nextFloat() * 170.0F;
    }

    private static long tickOf(Minecraft mc) {
        return mc.level != null && mc.level.getGameTime() > 0
                ? mc.level.getGameTime()
                : mc.gui.getGuiTicks();
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
