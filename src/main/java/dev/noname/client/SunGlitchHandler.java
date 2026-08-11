package dev.noname.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.noname.DayCounter;
import dev.noname.Noname;
import dev.noname.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

import java.io.InputStream;
import java.util.Random;

/**
 * From day 11 the sun slowly stops behaving. The corruption scales with the
 * day: on day 11 it is a barely-there hint of wrongness, on day 25 the sun is
 * completely broken.
 *
 * <p>Three layers, all driven by a per-game-tick snapshot so the sun's
 * position, tint and texture stay coherent within a frame and snap between
 * frames (that snap is the stutter):
 * <ul>
 *   <li><b>Lag / stutter</b> — {@code LevelRenderer.renderSky} rotates the
 *       sky by the time of day and draws the sun quad at {@code y = 100};
 *       the mixin rotates that quad back along the sky arc by a lag angle
 *       that is redrawn every few ticks, so the sun renders at an earlier
 *       (wrong) sky position and jumps when the lag changes. Redraws come
 *       faster and the lag range grows with intensity; occasionally the sun
 *       holds its broken spot for a beat, then teleports, and at high
 *       intensity it slides backwards along its arc before snapping
 *       forward.</li>
 *   <li><b>Colour drift</b> — the sun quad is tinted through
 *       {@link RenderSystem#setShaderColor} with an RGB sway that grows with
 *       intensity, occasionally turning into a wrong tint (swapped channels,
 *       sickly green, bruised purple, inverted) or a dim flicker.</li>
 *   <li><b>Texture corruption</b> — the vanilla 32x32 {@code sun.png} is
 *       procedurally corrupted per "epoch" (re-glitched every ~19s early on,
 *       every ~1s at full intensity): torn rows, displaced blocks, inverted
 *       bands, pixel-sorted segments, colour noise and — at high intensity —
 *       transparent holes eaten into the disc.</li>
 * </ul>
 *
 * <p>The whole effect is client-side and gated by the {@code sun_glitch}
 * config event. {@link #forceNow()} (used by the {@code /noname event play
 * sun_glitch} dev command) pins the intensity at the fully-broken day-25
 * state.
 */
public final class SunGlitchHandler {

    /** The vanilla sun texture — the base image being corrupted. */
    public static final ResourceLocation SUN_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/environment/sun.png");

    /** Where the corrupted sun is registered as a dynamic texture. */
    private static final ResourceLocation GLITCHED_LOCATION =
            ResourceLocation.fromNamespaceAndPath(Noname.MODID, "sun_glitched");

    /** Day the corruption starts; day it is fully broken (both scaled by the
     *  mod speed level). */
    private static final long FIRST_GLITCH_DAY = 11L;
    private static final long FULLY_BROKEN_DAY = 25L;

    /** The sun quad is a 60x60 quad at y=100 in sky space; its vertices are
     *  (±30, 100, ±30) — exactly how we tell sun vertices apart from the
     *  moon quad (y=-100) and the sun-glow fan (y=100 but x=z=0 or x=±120). */
    private static final float SUN_HALF = 30.0F;
    private static final float SUN_Y = 100.0F;

    /** How much intensity exists already on the first glitch day, so day 11
     *  shows a hint of wrongness instead of nothing. */
    private static final float FIRST_DAY_INTENSITY = 0.08F;

    /** Sky rotation speed: 360° per 24000 ticks. */
    private static final float DEG_PER_TICK = 360.0F / 24000.0F;

    /** Backwards slide speed at high intensity, in degrees per tick. */
    private static final float REWIND_SPEED = 0.15F;

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    // ------------------------------------------------------------------
    // Per-tick snapshot: the sun's current lag, jitter and tint. All values
    // are stable within one game tick and redrawn on the stutter cadence,
    // so the four sun vertices and the tint agree within a frame.

    private static long snapshotTick = Long.MIN_VALUE;
    private static float lagDeg;
    private static float lagCos = 1.0F;
    private static float lagSin;
    private static float jitterX;
    private static float jitterY;
    private static float jitterZ;
    private static float tintR = 1.0F;
    private static float tintG = 1.0F;
    private static float tintB = 1.0F;
    private static float tintAlpha = 1.0F;
    private static long holdUntilTick = Long.MIN_VALUE;
    private static int rewindTicksLeft;
    private static long textureEpoch = Long.MIN_VALUE;

    // ------------------------------------------------------------------
    // Corrupted sun texture, cached per epoch (day + re-glitch interval).

    private static NativeImage baseSun;
    private static DynamicTexture texture;
    private static long generatedEpoch = Long.MIN_VALUE;
    private static boolean failed = false;

    /** Dev-command override: pin the effect at the fully-broken state. */
    private static boolean forced = false;

    private static final Random RANDOM = new Random();

    private SunGlitchHandler() {
    }

    // ------------------------------------------------------------------
    // Dev hooks

    /** Pins the corruption at the fully-broken (day 25) state. */
    public static void forceNow() {
        forced = true;
    }

    /** Cancels the dev override; the effect returns to the day gate. */
    public static void stopAll() {
        forced = false;
    }

    // ------------------------------------------------------------------
    // Queries used by the mixins

    /** {@return whether {@code (x, y, z)} is one of the four sun-quad
     *  vertices} — the sun quad is the only sky geometry at {@code y == 100}
     *  with {@code |x| == |z| == 30}. */
    public static boolean isSunVertex(float x, float y, float z) {
        return y == SUN_Y
                && (x == SUN_HALF || x == -SUN_HALF)
                && (z == SUN_HALF || z == -SUN_HALF);
    }

    /** {@return the sun vertex's x after the per-tick displacement} */
    public static float displacedX(float x) {
        ensureSnapshot();
        return x + jitterX;
    }

    /** {@return the sun vertex's y after rotating the quad back along the
     *  sky arc by the current lag} — the sky itself rotates around the X
     *  axis by the time of day, so rotating by {@code -lagDeg} renders the
     *  sun at an earlier sky position — plus the vertical jitter. */
    public static float displacedY(float y, float z) {
        ensureSnapshot();
        return y * lagCos + z * lagSin + jitterY;
    }

    /** {@return the sun vertex's z after the same lag rotation, plus the
     *  horizontal jitter} */
    public static float displacedZ(float y, float z) {
        ensureSnapshot();
        return -y * lagSin + z * lagCos + jitterZ;
    }

    /** {@return true when {@code location} is the vanilla sun texture} */
    public static boolean isSunTexture(ResourceLocation location) {
        return SUN_LOCATION.equals(location);
    }

    /**
     * {@return the texture the sky should draw the sun with this frame: the
     * vanilla sun while the effect is off, the corrupted sun otherwise}.
     * Called from {@code LevelRenderer.renderSky} on the render thread, so
     * texture generation and uploads happen there.
     */
    public static ResourceLocation textureForThisFrame() {
        if (!active()) {
            return SUN_LOCATION;
        }
        ensureSnapshot();
        ensureTextureGenerated(textureEpoch);
        return texture != null && !failed ? GLITCHED_LOCATION : SUN_LOCATION;
    }

    /** Applies the current tint through the shader color, preserving the
     *  vanilla rain fade in the alpha channel. Called right after the sun
     *  texture is bound; the moon bind resets the color afterwards. */
    public static void applySunTint() {
        if (!active()) {
            return;
        }
        ensureSnapshot();
        RenderSystem.setShaderColor(tintR, tintG, tintB, rainFade() * tintAlpha);
    }

    /** {@return the vanilla sun quad alpha, i.e. 1 - rain level} */
    public static float rainFade() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 1.0F : 1.0F - mc.level.getRainLevel(1.0F);
    }

    // ------------------------------------------------------------------
    // Intensity

    /** {@return whether the sun corruption may show right now} */
    private static boolean active() {
        if (forced) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ModConfig.isEnabled("sun_glitch")) {
            return false;
        }
        return DayCounter.currentDay(mc.level) >= ModConfig.scaledDay(FIRST_GLITCH_DAY);
    }

    /** {@return the corruption intensity in {@code [0, 1]}: a hint on the
     *  first glitch day, fully broken from the day-25 gate on — both scaled
     *  by the mod speed}. */
    private static float currentIntensity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0.0F;
        }
        if (forced) {
            return 1.0F;
        }
        return intensityForDay(DayCounter.currentDay(mc.level));
    }

    private static float intensityForDay(long day) {
        long first = ModConfig.scaledDay(FIRST_GLITCH_DAY);
        long full = ModConfig.scaledDay(FULLY_BROKEN_DAY);
        if (day <= first) {
            return 0.0F;
        }
        if (day >= full) {
            return 1.0F;
        }
        float t = (float) (day - first) / (full - first);
        return FIRST_DAY_INTENSITY + (1.0F - FIRST_DAY_INTENSITY) * t;
    }

    // ------------------------------------------------------------------
    // Per-tick snapshot

    private static void ensureSnapshot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long tick = mc.level.getGameTime();
        if (snapshotTick == tick) {
            return;
        }
        snapshotTick = tick;
        float t = currentIntensity();
        long day = DayCounter.currentDay(mc.level);

        // Backwards slide: the sun creeps backward along its arc.
        if (rewindTicksLeft > 0) {
            lagDeg += REWIND_SPEED;
            rewindTicksLeft--;
        } else if (tick >= holdUntilTick && tick % stutterInterval(t) == 0L) {
            redrawSnapshot(t, tick);
        }

        lagCos = Mth.cos(lagDeg * DEG_TO_RAD);
        lagSin = Mth.sin(lagDeg * DEG_TO_RAD);

        // The texture re-glitches on its own cadence (slow at first, then
        // constantly), keyed by day so each day starts a fresh corruption.
        int intervalTicks = (int) (400.0F - 380.0F * t);
        textureEpoch = day * 100000L + tick / Math.max(1, intervalTicks);
    }

    /** {@return ticks between stutter redraws: 9 on the first glitch day, 2
     *  at full intensity} */
    private static int stutterInterval(float t) {
        return Math.max(2, 10 - Math.round(t * 8.0F));
    }

    private static void redrawSnapshot(float t, long tick) {
        holdUntilTick = Long.MIN_VALUE;

        // High intensity: the sun sometimes slides backward along its arc
        // for a moment instead of jumping.
        if (t > 0.55F && RANDOM.nextFloat() < 0.30F) {
            rewindTicksLeft = 10 + RANDOM.nextInt(1 + Math.round(t * 30.0F));
            return;
        }

        // Lag angle: the sun renders at an earlier sky position, jumping
        // here every stutter interval. Teleports happen more and more often.
        float teleport = RANDOM.nextFloat() < t * 0.35F
                ? (20.0F + RANDOM.nextFloat() * 70.0F) * t
                : 0.0F;
        lagDeg = RANDOM.nextFloat() * 90.0F * t + teleport;

        // Position jitter off the arc, growing with intensity.
        jitterX = (RANDOM.nextFloat() * 2.0F - 1.0F) * 12.0F * t;
        jitterY = (RANDOM.nextFloat() * 2.0F - 1.0F) * 8.0F * t;
        jitterZ = (RANDOM.nextFloat() * 2.0F - 1.0F) * 12.0F * t;

        // Occasionally the sun holds its broken spot for a beat, then jumps
        // again.
        if (RANDOM.nextFloat() < 0.35F * t) {
            holdUntilTick = tick + 20 + RANDOM.nextInt(1 + Math.round(t * 200.0F));
        }

        // Colour: gentle RGB sway, occasionally a wrong tint or a dim
        // flicker at high intensity.
        tintR = 1.0F + (RANDOM.nextFloat() - 0.5F) * 0.6F * t;
        tintG = 1.0F + (RANDOM.nextFloat() - 0.5F) * 0.6F * t;
        tintB = 1.0F + (RANDOM.nextFloat() - 0.5F) * 0.6F * t;
        tintAlpha = 1.0F;
        if (t > 0.5F && RANDOM.nextFloat() < 0.30F * t) {
            applyWrongTint(t);
        }
        if (t > 0.6F && RANDOM.nextFloat() < 0.30F) {
            tintAlpha = 0.35F + RANDOM.nextFloat() * 0.65F;
        }
    }

    /** Pushes the tint into a "wrong colour" variant, scaled by intensity. */
    private static void applyWrongTint(float t) {
        float s = 0.4F + 0.6F * t;
        switch (RANDOM.nextInt(4)) {
            case 0 -> { // channels swapped
                float r = tintR;
                tintR = tintB;
                tintB = r;
            }
            case 1 -> { // sickly green
                tintR *= 1.0F - 0.5F * s;
                tintG *= 1.0F + 0.6F * s;
                tintB *= 1.0F - 0.5F * s;
            }
            case 2 -> { // bruised purple
                tintR *= 1.0F + 0.5F * s;
                tintG *= 1.0F - 0.5F * s;
                tintB *= 1.0F + 0.5F * s;
            }
            default -> { // inverted
                tintR = 1.0F - tintR;
                tintG = 1.0F - tintG;
                tintB = 1.0F - tintB;
            }
        }
    }

    // ------------------------------------------------------------------
    // Corrupted sun texture

    private static void ensureTextureGenerated(long epoch) {
        if (generatedEpoch == epoch && texture != null) {
            return;
        }
        NativeImage corrupted = generate(epoch, currentIntensity());
        if (corrupted == null) {
            failed = true;
            return;
        }
        if (texture == null) {
            texture = new DynamicTexture(corrupted);
            Minecraft.getInstance().getTextureManager().register(GLITCHED_LOCATION, texture);
        } else {
            texture.setPixels(corrupted);
        }
        texture.upload();
        generatedEpoch = epoch;
    }

    /**
     * Corrupts a copy of the vanilla sun ({@code sun.png}, 32x32) with a
     * deterministic glitch set for this epoch: torn rows, displaced blocks,
     * inverted bands, pixel-sorted segments, colour noise, and — at high
     * intensity — transparent holes punched into the disc.
     */
    private static NativeImage generate(long epoch, float t) {
        if (baseSun == null && !loadBase()) {
            return null;
        }
        Random rnd = new Random(epoch * 2654435761L ^ 0x5DEECE66DL);
        NativeImage out = new NativeImage(32, 32, true);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                out.setPixelRGBA(x, y, baseSun.getPixelRGBA(x, y));
            }
        }
        int passes = 1 + Math.round(t * 5.5F);
        int variants = t > 0.55F ? 6 : 5;
        for (int p = 0; p < passes; p++) {
            switch (rnd.nextInt(variants)) {
                case 0 -> tearRows(rnd, out, t);
                case 1 -> moveBlock(rnd, out, t);
                case 2 -> noisePixels(rnd, out, t);
                case 3 -> invertBand(rnd, out, t);
                case 4 -> sortRowSegment(rnd, out);
                default -> punchHole(rnd, out, t);
            }
        }
        return out;
    }

    private static boolean loadBase() {
        try {
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            try (InputStream in = rm.getResource(SUN_LOCATION).orElseThrow().open()) {
                baseSun = NativeImage.read(in);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Shifts a short horizontal band of rows sideways (VHS tear). */
    private static void tearRows(Random rnd, NativeImage img, float t) {
        int y0 = rnd.nextInt(32);
        int h = 1 + rnd.nextInt(1 + (int) (t * 3.0F));
        int shift = rnd.nextInt(33) - 16;
        for (int y = y0; y < Math.min(32, y0 + h); y++) {
            int[] row = new int[32];
            for (int x = 0; x < 32; x++) {
                row[x] = img.getPixelRGBA(x, y);
            }
            for (int x = 0; x < 32; x++) {
                img.setPixelRGBA(x, y, row[Math.floorMod(x - shift, 32)]);
            }
        }
    }

    /** Copies a random block of the sun onto another spot. */
    private static void moveBlock(Random rnd, NativeImage img, float t) {
        int size = 4 + rnd.nextInt(1 + (int) (t * 10.0F));
        int sx = rnd.nextInt(32 - size);
        int sy = rnd.nextInt(32 - size);
        int dx = rnd.nextInt(32 - size);
        int dy = rnd.nextInt(32 - size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                img.setPixelRGBA(dx + x, dy + y, img.getPixelRGBA(sx + x, sy + y));
            }
        }
    }

    /** Sprinkles random-colour pixels, denser at high intensity. */
    private static void noisePixels(Random rnd, NativeImage img, float t) {
        int n = 4 + rnd.nextInt(1 + (int) (t * 24.0F));
        for (int i = 0; i < n; i++) {
            img.setPixelRGBA(rnd.nextInt(32), rnd.nextInt(32),
                    0xFF000000 | rnd.nextInt(0x1000000));
        }
    }

    /** Inverts the colours of a horizontal band. */
    private static void invertBand(Random rnd, NativeImage img, float t) {
        int y0 = rnd.nextInt(32);
        int h = 1 + rnd.nextInt(1 + (int) (t * 6.0F));
        for (int y = y0; y < Math.min(32, y0 + h); y++) {
            for (int x = 0; x < 32; x++) {
                int c = img.getPixelRGBA(x, y);
                img.setPixelRGBA(x, y, (c & 0xFF000000) | (0xFFFFFF - (c & 0xFFFFFF)));
            }
        }
    }

    /** Sorts a horizontal segment of one row by brightness. */
    private static void sortRowSegment(Random rnd, NativeImage img) {
        int y = rnd.nextInt(32);
        int x0 = rnd.nextInt(32);
        int len = 4 + rnd.nextInt(25);
        int[] seg = new int[Math.min(len, 32 - x0)];
        for (int i = 0; i < seg.length; i++) {
            seg[i] = img.getPixelRGBA(x0 + i, y);
        }
        for (int i = 1; i < seg.length; i++) {
            int v = seg[i];
            int j = i - 1;
            while (j >= 0 && luminance(seg[j]) > luminance(v)) {
                seg[j + 1] = seg[j];
                j--;
            }
            seg[j + 1] = v;
        }
        for (int i = 0; i < seg.length; i++) {
            img.setPixelRGBA(x0 + i, y, seg[i]);
        }
    }

    /** Punches a transparent hole into the sun (high intensity only). */
    private static void punchHole(Random rnd, NativeImage img, float t) {
        int w = 2 + rnd.nextInt(1 + (int) (t * 8.0F));
        int h = 2 + rnd.nextInt(1 + (int) (t * 8.0F));
        int x0 = rnd.nextInt(32 - w);
        int y0 = rnd.nextInt(32 - h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setPixelRGBA(x0 + x, y0 + y, 0);
            }
        }
    }

    private static int luminance(int rgba) {
        int r = rgba & 0xFF;
        int g = (rgba >> 8) & 0xFF;
        int b = (rgba >> 16) & 0xFF;
        return (r + g + b) / 3;
    }
}
