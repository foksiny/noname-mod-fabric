package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Day-4+ lightning: every 3-7 minutes spent on day 4 or later, every
 * still-alive survival/adventure player rolls a 10% chance to have a
 * lightning bolt strike right on them. The bolt is
 * {@link LightningBolt#setVisualOnly(boolean) visual-only}, so vanilla never
 * applies its own hit damage ({@code Entity.thunderHit}) and never ignites the
 * ground around it — the player hears the thunder and sees the strike but is
 * not damaged by the bolt itself. The only thing that hurts is the fire the
 * handler lights on the player directly: {@code setSecondsOnFire} makes the
 * slow burn — and only the burn — chip away at their health.
 *
 * <p>Creative and spectators are skipped (the bolt would still render but
 * there is no health to burn away), as is the mod's own fake player
 * ({@link FakePlayerUtil#FAKE_UUID}).
 *
 * <p>Like all the other day-gated handlers the roll cadence, day gate and
 * probability honour {@link ModConfig}: {@link ModConfig#scaledDay(long)}
 * shifts the start day with the speed level and {@link
 * ModConfig#chance(String, float)} scales the base 10%.
 */
public final class Day4LightningHandler {

    /** Roll cadence: 3-7 minutes (3600-8400 ticks). */
    private static final int MIN_ROLL_TICKS = 20 * 60 * 3;
    private static final int MAX_ROLL_TICKS = 20 * 60 * 7;

    /** Probability that a roll actually strikes the player — 10%. */
    private static final float EVENT_CHANCE = 0.10F;

    /** How long the struck player burns, in seconds. Only the fire hurts. */
    private static final int FIRE_SECONDS = 8;

    /** Player UUID -> ticks until that player's next roll. */
    private static final Map<UUID, Integer> ticksUntilRoll = new HashMap<>();

    private Day4LightningHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long day = DayCounter.currentDay(overworld);

        if (day < ModConfig.scaledDay(4) || !ModConfig.isEnabled("day4_lightning")) {
            ticksUntilRoll.clear();
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
                continue;
            }
            int remaining = ticksUntilRoll.getOrDefault(player.getUUID(), MIN_ROLL_TICKS);
            if (remaining > 1) {
                ticksUntilRoll.put(player.getUUID(), remaining - 1);
                continue;
            }
            ticksUntilRoll.put(player.getUUID(), MIN_ROLL_TICKS
                    + overworld.getRandom().nextInt(MAX_ROLL_TICKS - MIN_ROLL_TICKS + 1));
            if (overworld.getRandom().nextFloat()
                    < ModConfig.chance("day4_lightning", EVENT_CHANCE)) {
                strike(player);
            }
        }
    }

    /**
     * Dev/test hook — strike every online real player right now, bypassing
     * the day gate and the roll. Dispatched by {@code /noname event play
     * day4_lightning}.
     */
    public static void triggerForAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
                continue;
            }
            strike(player);
        }
    }

    /** Cancels every armed timer. Used by {@code /noname event stopall}. */
    public static void stopAll() {
        ticksUntilRoll.clear();
    }

    // ------------------------------------------------------------------
    // The strike

    /**
     * Spawns a visual-only lightning bolt exactly on the player (so it
     * visually strikes them and the thunder plays, but vanilla never applies
     * the bolt's own hit or ground fire) and then sets the player on fire for
     * {@link #FIRE_SECONDS} seconds — the only source of damage.
     */
    private static void strike(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
            // Visual-only: skips the thunderHit damage loop and the ground-
            // fire spawning in LightningBolt.tick(), so the bolt itself never
            // hurts the player. The only damage comes from the fire below.
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        player.igniteForSeconds(FIRE_SECONDS);
    }
}
