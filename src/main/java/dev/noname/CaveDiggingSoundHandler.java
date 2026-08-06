package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The invisible cave digger: from day 3 on, while a player is inside a cave,
 * every 1.5 to 3 minutes there is a 40% chance per player that, for 10 to
 * 20 seconds straight, someone breaks stone blocks around them — the exact
 * sounds a real miner makes (the periodic stone-hit crackles while digging,
 * the stone break when a block goes). There is nobody there: the sounds come
 * from a spot that starts 24-36 blocks away and walks in a straight line
 * toward the player's current position, following them as they move, so the
 * digging always approaches and grows naturally louder.
 *
 * <p>The sounds are played server-side at world positions (vanilla stone
 * sounds), so the player hears them with the normal distance falloff —
 * nothing is ever spawned in the world.
 */
public final class CaveDiggingSoundHandler {

    /** Roll cadence: 1.5-3 minutes (1800-3600 ticks). */
    private static final int MIN_ROLL_TICKS = 20 * 90;
    private static final int MAX_ROLL_TICKS = 20 * 180;

    /** Probability that a roll actually starts the digging. */
    private static final float EVENT_CHANCE = 0.40F;

    /** How long the digging lasts: 10-20 seconds (200-400 ticks). */
    private static final int MIN_EVENT_TICKS = 20 * 10;
    private static final int MAX_EVENT_TICKS = 20 * 20;

    /** Time between two digging sounds: 0.8-2 seconds (16-40 ticks). */
    private static final int MIN_SOUND_INTERVAL_TICKS = 16;
    private static final int MAX_SOUND_INTERVAL_TICKS = 40;

    /** Chance that a digging sound is a stone break instead of a hit
     *  crackle — like a player who digs one block every few hits. */
    private static final float BREAK_SOUND_CHANCE = 0.30F;

    /** How far away the digging starts, in blocks. */
    private static final double MIN_START_DISTANCE = 24.0D;
    private static final double MAX_START_DISTANCE = 36.0D;

    /** Base volume: audible up to ~48 blocks, so the approach is heard the
     *  whole way and only gets louder as it comes closer. */
    private static final float SOUND_VOLUME = 3.0F;

    /** How long after the event starts the first digging sound plays. */
    private static final int FIRST_SOUND_TICKS = 20;

    /** Player -> ticks until that player's next roll. */
    private static final Map<UUID, Integer> ticksUntilRoll = new HashMap<>();

    /** Player -> the digging event currently running for that player. */
    private static final Map<UUID, DiggingEvent> activeEvents = new HashMap<>();

    /** One running digging event for a player. */
    private record DiggingEvent(long startedAtTick, int durationTicks, long nextSoundTick,
                                Vec3 startPos, float volume) {
    }

    private CaveDiggingSoundHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long now = server.getTickCount();
        RandomSource random = overworld.getRandom();
        long day = DayCounter.currentDay(overworld);

        // Day 3 is when the digging starts; before that nothing rolls.
        if (day < ModConfig.scaledDay(3) || !ModConfig.isEnabled("cave_digging")) {
            ticksUntilRoll.clear();
            return;
        }

        // Advance every running digging event.
        if (!activeEvents.isEmpty()) {
            for (var it = activeEvents.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null || player.isRemoved() || !player.isAlive()) {
                    it.remove();
                    continue;
                }
                DiggingEvent event = entry.getValue();
                long elapsed = now - event.startedAtTick();
                if (elapsed >= event.durationTicks()) {
                    it.remove();
                    continue;
                }
                if (now >= event.nextSoundTick()) {
                    playDigSound(player, event, elapsed);
                    entry.setValue(new DiggingEvent(event.startedAtTick(), event.durationTicks(),
                            now + nextSoundInterval(random), event.startPos(), event.volume()));
                }
            }
        }

        // Roll the next event for each player still in a cave.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            UUID uuid = player.getUUID();
            if (activeEvents.containsKey(uuid)) {
                continue;
            }
            if (!CaveUtil.isInCave(player.serverLevel(), player)) {
                // No rolls outside caves; leaving also re-arms the countdown.
                ticksUntilRoll.remove(uuid);
                continue;
            }
            int remaining = ticksUntilRoll.getOrDefault(uuid, nextRollInterval(random));
            if (remaining > 1) {
                ticksUntilRoll.put(uuid, remaining - 1);
                continue;
            }
            ticksUntilRoll.put(uuid, nextRollInterval(random));
            if (random.nextFloat() < ModConfig.chance("cave_digging", EVENT_CHANCE)) {
                startEvent(server, player);
            }
        }
    }

    /** Dev/test hook — start the digging right now for every online player,
     *  regardless of the roll timer and the cave check. Dispatched by
     *  {@code /noname event play cave_digging}. */
    public static void triggerForEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            startEvent(server, player);
        }
    }

    /** Dev/test hook — stop every running digging event. Used by
     *  {@code /noname event stopall}. */
    public static void stopAll() {
        activeEvents.clear();
        ticksUntilRoll.clear();
    }

    /** Random 1.5-3 minutes (1800-3600 ticks) until the next roll. */
    private static int nextRollInterval(RandomSource random) {
        return MIN_ROLL_TICKS + random.nextInt(MAX_ROLL_TICKS - MIN_ROLL_TICKS + 1);
    }

    /** Random 0.8-2 seconds (16-40 ticks) until the next digging sound. */
    private static int nextSoundInterval(RandomSource random) {
        return MIN_SOUND_INTERVAL_TICKS
                + random.nextInt(MAX_SOUND_INTERVAL_TICKS - MIN_SOUND_INTERVAL_TICKS + 1);
    }

    /** Starts a digging event: the digger appears 24-36 blocks away and
     *  begins to dig toward the player. */
    private static void startEvent(MinecraftServer server, ServerPlayer player) {
        RandomSource random = player.serverLevel().getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = MIN_START_DISTANCE
                + random.nextDouble() * (MAX_START_DISTANCE - MIN_START_DISTANCE);
        Vec3 start = player.position().add(0.0D, 1.0D, 0.0D)
                .add(new Vec3(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance));
        int duration = MIN_EVENT_TICKS
                + random.nextInt(MAX_EVENT_TICKS - MIN_EVENT_TICKS + 1);
        float volume = SOUND_VOLUME + random.nextFloat() * 0.5F - 0.25F;
        activeEvents.put(player.getUUID(), new DiggingEvent(server.getTickCount(),
                duration, server.getTickCount() + FIRST_SOUND_TICKS, start, volume));
    }

    /** Plays one digging sound at the digger's current spot: on a straight
     *  line from where it started toward the player's current position, so
     *  the digging always approaches and follows the player. */
    private static void playDigSound(ServerPlayer player, DiggingEvent event, long elapsed) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        double progress = Math.min(1.0D, (double) elapsed / event.durationTicks());
        Vec3 toward = player.position().add(0.0D, 1.0D, 0.0D).subtract(event.startPos());
        Vec3 spot = event.startPos().add(toward.scale(progress));
        boolean breakSound = random.nextFloat() < BREAK_SOUND_CHANCE;
        level.playSound(null, spot.x, spot.y, spot.z,
                breakSound ? SoundEvents.STONE_BREAK : SoundEvents.STONE_HIT,
                SoundSource.BLOCKS, event.volume(), random.nextFloat() * 0.2F + 0.9F);
    }
}
