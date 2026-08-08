package dev.noname.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User-facing configuration for the Noname mod, persisted as JSON in the
 * config directory ({@code config/noname.json}).
 *
 * <p>Three knobs:
 * <ul>
 *   <li><b>Speed level</b> (1-6, default 3 = the original pacing). The speed
 *       is the pace of the whole story: every day-gated event triggers on a
 *       scaled day. Level 1 runs everything twice as fast (the moon is fully
 *       infected at day 5 instead of day 10, the ghost joins at day 1),
 *       level 6 runs everything twice as slow (moon fully infected at day
 *       20, ghost at day 6). {@link #scaledDay(long)} maps a base day to the
 *       day it actually happens on.</li>
 *  <li><b>Per-event chance</b> (5%-200%, default 100%). Every event has its
 *       own chance multiplier that scales its random rolls; 100% means the
 *       event fires at its built-in base probability. {@link
 *       #chance(String, float)} scales a base probability by it.</li>
 *   <li><b>Per-event switches</b>. Every named event can be turned off
 *       entirely; {@link #isEnabled(String)} is the gate every handler
 *       checks.</li>
 * </ul>
 *
 * <p>Loaded eagerly from {@link ModConfig#load()} on both the server and the
 * client initializers, so the same file drives the integrated server and the
 * local client in singleplayer. Every setter persists the file immediately.
 */
public final class ModConfig {

    public static final int MIN_SPEED_LEVEL = 1;
    public static final int MAX_SPEED_LEVEL = 6;
    public static final int DEFAULT_SPEED_LEVEL = 3;

    public static final float MIN_EVENT_CHANCE = 0.05F;
    public static final float MAX_EVENT_CHANCE = 2.0F;
    public static final float DEFAULT_EVENT_CHANCE = 1.0F;

    /** Every toggleable event: key -> human-readable label (shown in the
     *  config screen). Keys match the {@code /noname event play} names. */
    public static final Map<String, String> EVENTS = createEventRegistry();

    /** Always-on visual features (not random events): key -> human-readable
     *  label. These get their own section in the config screen, separate
     *  from the event toggles, and have no chance setting. */
    public static final Map<String, String> VISUAL_FEATURES = createFeatureRegistry();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path configPath;
    private static int speedLevel = DEFAULT_SPEED_LEVEL;

    /** Per-event chance multipliers; an absent key means 100%. */
    private static final Map<String, Float> chances =
            Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<String, Boolean> enabled =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private ModConfig() {
    }

    private static Map<String, String> createEventRegistry() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("ghost_join",       "Day 3: ghost player joins");
        map.put("ghost_chat",       "Ghost chat dialogue");
        map.put("it_hurts_to_see",  "Ghost \"it hurts to see\" stinger");
        map.put("hostile_stop",     "Day 1+: hostile mobs stopped");
        map.put("natural_spawn_stop", "Day 9+: natural mob spawns stopped");
        map.put("village_removal",  "Day 1+: villagers & iron golems removed");
        map.put("sleep_block",      "Day 2: sleeping blocked");
        map.put("day2_null_join",   "Day 2: \"null\" visitor");
        map.put("day2_message",     "Day 2: creep message");
        map.put("disc_11",          "Day 2: music disc 11");
        map.put("day3_timeskip",    "Day 3: sudden night time-skip");
        map.put("day3_stalker",     "Day 3+: fake player flickers (20% per 1-2 min)");
        map.put("day4_question",    "Day 4: the question (yes/no)");
        map.put("day4_hungry",      "Day 4: \"i'm feeling hungry\"");
        map.put("day4_red_pink",    "Day 4: \"really red and pink\"");
        map.put("day4_help",        "Day 4: \"help me\" window");
        map.put("loot_pile",        "Day 4+: animal loot piles (2x, mostly food day 9+)");
        map.put("leafless_trees",   "Day 4+: leafless trees & broken logs");
        map.put("knife_craft",      "Infinite knife recipe (meat + knife)");
        map.put("horse_kill",       "Horses die near players");
        map.put("door_creak",       "Day 4+: creaking doors");
        map.put("door_knock",       "Day 3+: knocking on your doors");
        map.put("creepy_bass",      "Day 4+: creepy bass stinger");
        map.put("day5_flash",       "Day 5+: screen flash");
        map.put("day5_pig",         "Day 5+: infected pigs");
        map.put("day5_desktop",     "Day 5: fake player: \"look at your desktop\" (writes hello.txt)");
        map.put("pillar",           "Day 6+: bedrock pillars");
        map.put("day6_static",      "Day 6: static overlay");
        map.put("ise_it",           "Day 6+: \"ise it\" apparition");
        map.put("day7_fake",        "Day 7: fake player appears");
        map.put("day7_lonely",      "Day 7: lonely chat at midday");
        map.put("blood_death",      "Day 7+: blood mobs");
        map.put("named_mob",        "Day 7+: named mobs");
        map.put("hostile_clear",    "Day 8+: hostiles vanish");
        map.put("flesh_tree",       "Day 8+: flesh trees");
        map.put("day8_sky",         "Day 8+: red sky");
        map.put("day8_smile",       "Day 8: \":)\" window + \" .txt\" on desktop");
        map.put("cave_zombie",      "Day 8+: cave stalker zombie");
        map.put("cave_digging",     "Day 8+: cave digging sounds");
        map.put("meat_drops",       "Day 8+: meat drops");
        map.put("sign_place",       "Day 9+: creepy signs");
        map.put("camera_spasm",     "Day 1+: camera spasm (18% per 3-6 min)");
        map.put("day10_look",       "Day 10+: look-behind event");
        map.put("day10_whisper",    "Day 10+: screen whispers");
        map.put("red_rain",         "Day 10+: red rain");
        map.put("day11_chest",      "Day 11+: mystery chests");
        map.put("chunk_delete",     "Day 13+: chunk ahead of you deleted (void)");
        map.put("day13_stalker",    "Day 13+: fake player stalks you (37% per 4-9 min)");
        map.put("day14_death",       "Day 14+: random lightning death (15% per 4-9 min)");
        map.put("chat_block",        "Day 15+: chat can no longer be opened (keys do nothing)");
        map.put("he_is_here",       "Secret: \"he is here\" chase");
        map.put("scrambled_names",  "Scrambled item names");
        map.put("cave_sounds",      "Cave ambience sounds");
        map.put("moon_infection",   "Moon infection");
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> createFeatureRegistry() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("vhs_effect", "VHS effect (screen filter, overlay, audio)");
        return Collections.unmodifiableMap(map);
    }

    /** {@return whether the key names a real event or visual feature} */
    private static boolean isKnownKey(String key) {
        return EVENTS.containsKey(key) || VISUAL_FEATURES.containsKey(key);
    }

    // ------------------------------------------------------------------
    // Persistence

    /** Loads the config from {@code config/noname.json}, creating the file
     *  with defaults if it does not exist yet. */
    public static void load() {
        if (configPath == null) {
            configPath = FabricLoader.getInstance()
                    .getConfigDir().resolve("noname.json");
        }
        if (!Files.exists(configPath)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(configPath)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) {
                data = new Data();
            }
            speedLevel = clampSpeed(data.speedLevel);
            enabled.clear();
            if (data.events != null) {
                for (Map.Entry<String, Boolean> entry : data.events.entrySet()) {
                    if (isKnownKey(entry.getKey())) {
                        enabled.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            chances.clear();
            if (data.chances != null) {
                for (Map.Entry<String, Float> entry : data.chances.entrySet()) {
                    if (isKnownKey(entry.getKey())) {
                        chances.put(entry.getKey(), clampChance(entry.getValue()));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Writes the current settings to {@code config/noname.json}. */
    public static void save() {
        if (configPath == null) {
            configPath = FabricLoader.getInstance()
                    .getConfigDir().resolve("noname.json");
        }
        Data data = new Data();
        data.speedLevel = speedLevel;
        synchronized (enabled) {
            data.events = new LinkedHashMap<>();
            for (Map.Entry<String, Boolean> entry : enabled.entrySet()) {
                if (isKnownKey(entry.getKey())) {
                    data.events.put(entry.getKey(), entry.getValue());
                }
            }
        }
        synchronized (chances) {
            data.chances = new LinkedHashMap<>();
            for (Map.Entry<String, Float> entry : chances.entrySet()) {
                if (isKnownKey(entry.getKey())) {
                    data.chances.put(entry.getKey(), entry.getValue());
                }
            }
        }
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Restores every setting to its default and persists the file. */
    public static void reset() {
        speedLevel = DEFAULT_SPEED_LEVEL;
        synchronized (enabled) {
            enabled.clear();
        }
        resetChances();
    }

    // ------------------------------------------------------------------
    // Speed level

    public static int getSpeedLevel() {
        return speedLevel;
    }

    /** Sets the speed level, clamping it to {@link #MIN_SPEED_LEVEL}..
     *  {@link #MAX_SPEED_LEVEL}, and persists the config. */
    public static void setSpeedLevel(int level) {
        speedLevel = clampSpeed(level);
        save();
    }

    /**
     * {@return the day multiplier of the current speed level} — how much a
     * base event day is stretched or compressed:
     * <pre>
     * level 1: 0.5   (everything happens twice as fast)
     * level 2: 0.75
     * level 3: 1.0   (default, the original pacing)
     * level 4: 1.33
     * level 5: 1.67
     * level 6: 2.0   (everything happens twice as slow)
     * </pre>
     */
    public static float speedScale() {
        int level = speedLevel;
        if (level <= 2) {
            return 0.5F + (level - 1) * 0.25F;
        }
        return 1.0F + (level - 3) * (1.0F / 3.0F);
    }

    /**
     * {@return the day on which an event that normally fires on
     * {@code baseDay} actually fires at the current speed level}. At level 1
     * the moon's full-infection day 10 becomes day 5; at level 6 it becomes
     * day 20. Used by every day gate instead of the raw constant.
     */
    public static long scaledDay(long baseDay) {
        return (long) Math.floor(baseDay * speedScale());
    }

    // ------------------------------------------------------------------
    // Event chance

    /** {@return the chance multiplier of an event, 100% when untouched} */
    public static float getEventChance(String eventKey) {
        synchronized (chances) {
            return chances.getOrDefault(eventKey, DEFAULT_EVENT_CHANCE);
        }
    }

    /** Sets the chance multiplier of a single event, clamped to
     *  {@link #MIN_EVENT_CHANCE}..{@link #MAX_EVENT_CHANCE}, and persists
     *  the config. */
    public static void setEventChance(String eventKey, float chance) {
        synchronized (chances) {
            chances.put(eventKey, clampChance(chance));
        }
        save();
    }

    /** Restores every event's chance multiplier to 100% and persists. */
    public static void resetChances() {
        synchronized (chances) {
            chances.clear();
        }
        save();
    }

    /**
     * {@return the effective probability of an event roll}: {@code 0} when
     * the event is disabled, otherwise the base probability scaled by the
     * event's own chance multiplier (clamped to 100%).
     */
    public static float chance(String eventKey, float baseChance) {
        if (!isEnabled(eventKey)) {
            return 0.0F;
        }
        return Math.min(1.0F, baseChance * getEventChance(eventKey));
    }

    // ------------------------------------------------------------------
    // Per-event switches

    /** {@return whether the named event may fire at all} */
    public static boolean isEnabled(String eventKey) {
        synchronized (enabled) {
            return enabled.getOrDefault(eventKey, true);
        }
    }

    /** Sets the enabled flag of an event and persists the config. */
    public static void setEnabled(String eventKey, boolean value) {
        synchronized (enabled) {
            enabled.put(eventKey, value);
        }
        save();
    }

    // ------------------------------------------------------------------

    private static int clampSpeed(int level) {
        return Math.max(MIN_SPEED_LEVEL, Math.min(MAX_SPEED_LEVEL, level));
    }

    private static float clampChance(float chance) {
        return Math.max(MIN_EVENT_CHANCE, Math.min(MAX_EVENT_CHANCE, chance));
    }

    /** Mirror of the persisted JSON. */
    private static final class Data {
        int speedLevel = DEFAULT_SPEED_LEVEL;
        Map<String, Boolean> events = new LinkedHashMap<>();
        Map<String, Float> chances = new LinkedHashMap<>();
    }
}
