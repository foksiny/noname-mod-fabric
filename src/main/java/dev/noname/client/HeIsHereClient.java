package dev.noname.client;

import dev.noname.HeIsHereHandler;
import dev.noname.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Client-side driver of the "he is here" event. The server (see
 * {@link dev.noname.HeIsHereHandler}) drives the timeline; this handler plays
 * the song and keeps the chase visuals ({@link HeIsHereOverlay} draws them,
 * the fog mixins use {@link #isChaseVisible()} and {@link #intensity()}):
 *
 * <pre>
 * t = 0 s     he_is_here received: the song (cut at 27 s) starts at full
 *             volume and pitch — never re-pitched
 * t = 13 s    the white texts and the red "he's X away from you" start; the
 *             server sends the friend's distance every tick
 * catch       he_is_here:death received: everything stops and the song
 *             jumps to second 25 (the pre-cut tail plays to its end)
 * t = 27 s    he_is_here:stop received (or the song simply ends): done
 * </pre>
 *
 * <p>The blood splashes on screen get more numerous and stronger as the
 * friend approaches ({@link #intensity()} scales with the distance).
 */
public final class HeIsHereClient {

    /** The chase visuals start 13 seconds in, in ticks. */
    private static final int CHASE_START_TICKS = HeIsHereHandler.CHASE_START_TICKS;

    /** The friend's starting distance, in blocks. */
    public static final int SPAWN_DISTANCE = 150;

    /** How many splashes can be on screen at once, at full intensity. */
    private static final int MAX_SPLASHES = 34;

    /** The phrases the white texts keep flashing through. */
    static final String[] PHRASES = {
            "he's here",
            "come with me",
            "am i not your friend anymore? :(",
            "stay with me!",
            "i'm feeling so cold...",
            "why are you running?",
            "help me",
            "why don't you care?",
    };

    static final Random RANDOM = new Random();

    /** The blood splashes currently on screen. */
    static final List<Splash> splashes = new ArrayList<>();

    /** Whether the event is currently running on this client. */
    private static boolean active;

    /** Whether the chase phase (texts, fog, splashes) is visible. */
    private static boolean chaseVisible;

    /** Session tick at which the event started; {@code -1} = not running. */
    private static int startTick = -1;

    /** Session tick counter, driven by {@link #onClientTick}. */
    private static int sessionTick = 0;

    /** The friend's current distance, in blocks (server-provided). */
    private static int distance = SPAWN_DISTANCE;

    /** The song currently playing, if any. */
    private static SoundInstance song;

    private HeIsHereClient() {
    }

    /** Starts the event: plays the song and arms the 13-second timer. Called
     *  by the {@code he_is_here} payload. */
    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        stopSong();
        active = true;
        chaseVisible = false;
        distance = SPAWN_DISTANCE;
        startTick = sessionTick;
        splashes.clear();
        song = new HeIsHereSound(ModSounds.HE_IS_HERE);
        mc.getSoundManager().play(song);
    }

    /** The friend caught the player: stop everything and jump the song to
     *  second 25 (the pre-cut tail). Called by the {@code he_is_here:death}
     *  payload. */
    public static void death() {
        Minecraft mc = Minecraft.getInstance();
        stopSong();
        if (mc.player != null) {
            song = new HeIsHereSound(ModSounds.HE_IS_HERE_25);
            mc.getSoundManager().play(song);
        }
        active = false;
        chaseVisible = false;
        splashes.clear();
    }

    /** Ends the event and stops the song. Called by the {@code
     *  he_is_here:stop} payload and stopall. */
    public static void stop() {
        stopSong();
        active = false;
        chaseVisible = false;
        splashes.clear();
        distance = SPAWN_DISTANCE;
    }

    /** Updates the friend's distance from the server's per-tick payload. */
    public static void setDistance(int distance) {
        HeIsHereClient.distance = Math.max(1, distance);
        // The first distance payload means the chase phase has begun.
        if (active) {
            chaseVisible = true;
        }
    }

    /** Whether the red fog / texts / splashes should be on screen. */
    public static boolean isChaseVisible() {
        return chaseVisible;
    }

    /** The friend's current distance, in blocks. */
    public static int distance() {
        return distance;
    }

    /** How close the friend is: 0 at 150 blocks, 1 when it has reached the
     *  player. Drives the fog strength and the splash amount. */
    public static float intensity() {
        return Math.max(0.0F, Math.min(1.0F,
                1.0F - (float) distance / SPAWN_DISTANCE));
    }

    /** Session tick counter — the overlay re-rolls its texts every few
     *  ticks by comparing with this. */
    public static int sessionTick() {
        return sessionTick;
    }

    public static void onClientTick(Minecraft mc) {
        sessionTick++;
        // Leaving the world mid-event: put everything back.
        if (mc.level == null || mc.player == null) {
            if (active) {
                stop();
            }
            return;
        }
        if (!active || startTick < 0) {
            return;
        }
        if (!chaseVisible && sessionTick - startTick >= CHASE_START_TICKS) {
            // The 13-second mark: the server is spawning the friend; the
            // visuals start even if its first distance payload is late.
            chaseVisible = true;
        }
        if (!chaseVisible) {
            return;
        }
        // Keep the splash count proportional to how close the friend is.
        int target = (int) (intensity() * MAX_SPLASHES);
        while (splashes.size() > target && !splashes.isEmpty()) {
            splashes.remove(splashes.size() - 1);
        }
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        while (splashes.size() < target) {
            splashes.add(new Splash(
                    RANDOM.nextFloat() * width,
                    RANDOM.nextFloat() * height,
                    20.0F + RANDOM.nextFloat() * 120.0F,
                    0.25F + RANDOM.nextFloat() * 0.55F,
                    10 + RANDOM.nextInt(16)));
        }
        splashes.removeIf(s -> --s.life <= 0);
    }

    private static void stopSong() {
        if (song != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().stop(song);
            }
            song = null;
        }
    }

    /** One blood splash: a soft red blotch fading in and out on screen. */
    static final class Splash {
        final float x;
        final float y;
        final float size;
        final float maxAlpha;
        int life;
        final int maxLife;

        Splash(float x, float y, float size, float maxAlpha, int life) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.maxAlpha = maxAlpha;
            this.life = life;
            this.maxLife = life;
        }

        /** Alpha along a fade-in / fade-out curve over the splash's life. */
        float alpha() {
            return maxAlpha * (float) Math.sin(Math.PI * (1.0F - (float) life / maxLife));
        }
    }

    /** The song, played relative to the player (always full volume, pitch
     *  exactly 1 — the song never changes speed or pitch). */
    private static final class HeIsHereSound extends AbstractSoundInstance {

        HeIsHereSound(SoundEvent event) {
            super(event, SoundSource.MASTER, RandomSource.create());
            this.relative = true;
        }
    }
}
