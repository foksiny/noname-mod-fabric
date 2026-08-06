package dev.noname.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.noname.DayCounter;
import dev.noname.Noname;
import dev.noname.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

import java.io.InputStream;

/**
 * The moon slowly turns into flesh from day 1 onwards. Each day the
 * infection creeps a little further over the moon disc, growing from a few
 * specks at the rim on day 1 to a fully transformed flesh moon on day 10
 * (progress is {@code (day - 1) / 9}).
 *
 * <p>The vanilla moon texture ({@code moon_phases.png}) is a 4x2 grid of
 * 32x32 phase cells, and {@link net.minecraft.client.renderer.LevelRenderer}
 * picks the phase cell through UVs while rendering the moon quad. Instead of
 * replacing that texture through the resource pack (which would show the
 * same infection level every night), this handler procedurally regenerates
 * the grid at runtime: each phase cell keeps the vanilla moon underneath,
 * and every pixel inside the moon disc is blended toward a sample of the
 * mod's {@code flesh_block} texture when its infection score passes the
 * day's growth threshold.
 *
 * <p>The infection pattern is deterministic per position (a fixed
 * pseudo-random blob pattern, creeping from the moon's rim inward), so each
 * new day just extends the same growth front instead of repainting random
 * spots. The generated grid is cached per day and re-uploaded the first
 * time the sky is rendered on a new day.
 */
public final class MoonInfectionHandler {

    /** The vanilla moon-phases texture (the grid being infected). */
    public static final ResourceLocation MOON_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");

    /** The mod's flesh block texture — the "infection" source. */
    private static final ResourceLocation FLESH_LOCATION =
            ResourceLocation.fromNamespaceAndPath(Noname.MODID, "textures/block/flesh_block.png");

    /** Where the regenerated infected grid is registered as a dynamic
     *  texture. */
    private static final ResourceLocation INFECTED_LOCATION =
            ResourceLocation.fromNamespaceAndPath(Noname.MODID, "moon_phases_infected");

    /** The moon texture is a 4x2 grid of 32x32 phase cells. */
    private static final int GRID_COLS = 4;
    private static final int GRID_ROWS = 2;
    private static final int CELL = 32;

    /** Radius (in px) of the moon disc inside a 32x32 cell, measured from
     *  the cell centre — everything outside it is sky and stays untouched. */
    private static final float DISC_RADIUS = 15.5F;

    /** Day on which the first infection appears. */
    private static final long FIRST_INFECTED_DAY = 1L;

    /** Day on which the moon is fully infected. */
    private static final long FULL_INFECTION_DAY = 10L;

    /** How much of the score is the creep-from-the-rim vs random blobs. */
    private static final float EDGE_WEIGHT = 0.75F;
    private static final float NOISE_WEIGHT = 1.0F - EDGE_WEIGHT;

    /** Width of the soft band of the growth front, in score units. */
    private static final float FRONT_BAND = 0.09F;

    /** Fallback fleshy colour if the flesh texture has a transparent pixel. */
    private static final int FALLBACK_FLESH = 0xFF7A3B3B;

    private static NativeImage baseMoon;
    private static NativeImage flesh;
    private static DynamicTexture texture;
    private static long generatedForDay = Long.MIN_VALUE;
    private static boolean failed = false;

    private MoonInfectionHandler() {
    }

    /**
     * {@return the moon-phases texture the sky should use this frame: the
     * vanilla grid while the world is on day 0, the infected grid from day 1
     * onwards}. Called from {@code LevelRenderer.renderSky} on the render
     * thread, so texture generation and uploads happen there.
     */
    public static ResourceLocation textureForCurrentDay() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || failed || !ModConfig.isEnabled("moon_infection")) {
            return MOON_LOCATION;
        }
        long day = DayCounter.currentDay(mc.level);
        if (day < ModConfig.scaledDay(FIRST_INFECTED_DAY)) {
            return MOON_LOCATION;
        }
        ensureGenerated(day);
        return texture != null ? INFECTED_LOCATION : MOON_LOCATION;
    }

    /** {@return true when {@code location} is the vanilla moon grid} — used
     *  by the mixin to swap only the moon texture, leaving the sun and the
     *  clouds alone. */
    public static boolean isMoonTexture(ResourceLocation location) {
        return MOON_LOCATION.equals(location);
    }

    /** {@return the infection progress in {@code [0, 1]}: 0 at the first
     *  infected day, 1 at the full-infection day — both scaled by the mod
     *  speed, so at speed level 1 the moon is fully infected at day 5 and
     *  at level 6 only at day 20}. */
    static float progressForDay(long day) {
        long first = ModConfig.scaledDay(FIRST_INFECTED_DAY);
        long full = ModConfig.scaledDay(FULL_INFECTION_DAY);
        if (day <= first) {
            return 0.0F;
        }
        if (day >= full) {
            return 1.0F;
        }
        return (float) (day - first) / (full - first);
    }

    private static void ensureGenerated(long day) {
        if (generatedForDay == day && texture != null) {
            return;
        }
        NativeImage infected = generate(day);
        if (infected == null) {
            failed = true;
            return;
        }
        if (texture == null) {
            texture = new DynamicTexture(infected);
            Minecraft.getInstance().getTextureManager().register(INFECTED_LOCATION, texture);
        } else {
            texture.setPixels(infected);
        }
        texture.upload();
        generatedForDay = day;
    }

    private static NativeImage generate(long day) {
        if ((baseMoon == null || flesh == null) && !loadTextures()) {
            return null;
        }
        float t = progressForDay(day);
        NativeImage out = new NativeImage(GRID_COLS * CELL, GRID_ROWS * CELL, true);
        for (int phase = 0; phase < GRID_COLS * GRID_ROWS; phase++) {
            int ox = (phase % GRID_COLS) * CELL;
            int oy = (phase / GRID_COLS) * CELL;
            for (int y = 0; y < CELL; y++) {
                for (int x = 0; x < CELL; x++) {
                    int base = baseMoon.getPixelRGBA(ox + x, oy + y);

                    float dx = x - (CELL - 1) / 2.0F;
                    float dy = y - (CELL - 1) / 2.0F;
                    float dist = Mth.sqrt(dx * dx + dy * dy);
                    if (dist > DISC_RADIUS) {
                        // Sky outside the moon disc: never infected.
                        out.setPixelRGBA(ox + x, oy + y, base);
                        continue;
                    }

                    // The growth front: rim-first, organic blobs mixed in.
                    // The squared radius makes the creeping front add about
                    // the same area every day (the disc's area grows
                    // quadratically with the radius, so a linear front
                    // would swallow most of the moon by day 7).
                    float edge = (dist / DISC_RADIUS) * (dist / DISC_RADIUS);
                    float score = EDGE_WEIGHT * edge
                            + NOISE_WEIGHT * hash01(phase, x, y, 0);
                    float s = Mth.clamp(
                            (score - (1.0F - t)) / FRONT_BAND + 0.5F,
                            0.0F, 1.0F);
                    out.setPixelRGBA(ox + x, oy + y,
                            s <= 0.0F ? base : blend(base, sampleFlesh(phase, x, y), s));
                }
            }
        }
        return out;
    }

    private static boolean loadTextures() {
        try {
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            try (InputStream in = rm.getResource(MOON_LOCATION).orElseThrow().open()) {
                baseMoon = NativeImage.read(in);
            }
            try (InputStream in = rm.getResource(FLESH_LOCATION).orElseThrow().open()) {
                flesh = NativeImage.read(in);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Samples the flesh block texture for a cell pixel, jittered per
     *  position so the meat pattern doesn't tile visibly, with a brightness
     *  variation and a darker vein wherever the noise says so. */
    private static int sampleFlesh(int phase, int x, int y) {
        int seed = phase * 13 + 5;
        int fx = (x + seed + (int) (hash01(phase, x, y, 1) * 3.0F)) & 15;
        int fy = (y + seed * 5 + (int) (hash01(phase, x, y, 2) * 3.0F)) & 15;
        int c = flesh.getPixelRGBA(fx, fy);
        if (((c >> 24) & 0xFF) == 0) {
            c = FALLBACK_FLESH;
        }
        float v = 0.8F + 0.4F * hash01(phase, x, y, 3);
        if (hash01(phase, x, y, 4) < 0.12F) {
            v *= 0.55F;
        }
        int r = Math.min(255, (int) ((c & 0xFF) * v));
        int g = Math.min(255, (int) (((c >> 8) & 0xFF) * v));
        int b = Math.min(255, (int) (((c >> 16) & 0xFF) * v));
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    /** Blends the vanilla moon pixel toward the flesh colour by {@code s},
     *  keeping the moon's alpha. */
    private static int blend(int base, int fleshColor, float s) {
        int a = base & 0xFF000000;
        int br = base & 0xFF;
        int bg = (base >> 8) & 0xFF;
        int bb = (base >> 16) & 0xFF;
        int fr = fleshColor & 0xFF;
        int fg = (fleshColor >> 8) & 0xFF;
        int fb = (fleshColor >> 16) & 0xFF;
        int r = (int) (br + (fr - br) * s);
        int g = (int) (bg + (fg - bg) * s);
        int b = (int) (bb + (fb - bb) * s);
        return a | (b << 16) | (g << 8) | r;
    }

    /** {@return a deterministic pseudo-random value in {@code [0, 1)} from
     *  an integer key} — stable across frames and days, so the infection
     *  pattern grows instead of repainting. */
    private static float hash01(int a, int b, int c, int d) {
        long h = a * 374761393L + b * 668265263L + c * 1274126177L + d * 113546779L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0F;
    }
}
