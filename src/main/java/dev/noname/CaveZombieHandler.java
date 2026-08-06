package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The day-8+ cave stalker: while a player is inside a cave, every 1 to
 * 1.5 minutes there is a 35% chance per player that a named {@link CaveZombie}
 * — "i'm here", "you should leave" or "you're not safe" — appears exactly
 * 10 blocks away, on the cave floor, and starts hunting them with its smart
 * AI ({@link CaveZombie}: always knows where the player is, tries to find a
 * short way around, and digs through whatever blocks when there is none).
 *
 * <p>Only one stalker hunts a player at a time: while it is alive no new
 * rolls happen for that player, and once it dies the countdown starts again.
 * The timer only runs while the player is actually inside a cave; leaving a
 * cave re-arms it, so re-entering always gives a fresh 1-1.5 minute grace
 * period.
 */
public final class CaveZombieHandler {

    /** Roll cadence: 1 to 1.5 minutes (1200-1800 ticks). */
    private static final int MIN_ROLL_TICKS = 20 * 60;
    private static final int MAX_ROLL_TICKS = 20 * 90;

    /** Probability that a roll actually summons the stalker. */
    private static final float EVENT_CHANCE = 0.35F;

    /** The stalker appears this far away from the player, in blocks. */
    private static final double SPAWN_DISTANCE = 10.0D;

    /** The nametags the stalker may carry. */
    private static final String[] NAMES = {
            "i'm here",
            "you should leave",
            "you're not safe"
    };

    /** How much faster the stalker walks than a vanilla zombie. */
    private static final double STALKER_SPEED = 0.26D;

    /** How far the stalker can feel the player. */
    private static final double SENSE_RANGE = 512.0D;

    /** Player -> ticks until that player's next roll. */
    private static final Map<UUID, Integer> ticksUntilRoll = new HashMap<>();

    /** Player -> the stalker currently hunting them. */
    private static final Map<UUID, CaveZombie> activeZombies = new HashMap<>();

    private CaveZombieHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long day = DayCounter.currentDay(overworld);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            UUID uuid = player.getUUID();

            // A stalker is already after this player: wait until it is gone.
            CaveZombie zombie = activeZombies.get(uuid);
            if (zombie != null) {
                if (zombie.isRemoved() || !zombie.isAlive()
                        || zombie.level() != player.level()) {
                    activeZombies.remove(uuid);
                } else {
                    continue;
                }
            }

            if (day < ModConfig.scaledDay(8) || !ModConfig.isEnabled("cave_zombie")) {
                ticksUntilRoll.remove(uuid);
                continue;
            }
            ServerLevel level = player.serverLevel();
            if (!CaveUtil.isInCave(level, player)) {
                // No rolls outside caves; leaving also re-arms the countdown.
                ticksUntilRoll.remove(uuid);
                continue;
            }
            RandomSource random = level.getRandom();
            int remaining = ticksUntilRoll.getOrDefault(uuid, nextRollInterval(random));
            if (remaining > 1) {
                ticksUntilRoll.put(uuid, remaining - 1);
                continue;
            }
            ticksUntilRoll.put(uuid, nextRollInterval(random));
            if (random.nextFloat() < ModConfig.chance("cave_zombie", EVENT_CHANCE)) {
                spawnStalker(player);
            }
        }
    }

    /** Dev/test hook — summon a stalker next to every online player right
     *  now, regardless of the day-8 gate and the roll timer. Dispatched by
     *  {@code /noname event play cave_zombie}. */
    public static void triggerOneNearEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            spawnStalker(player);
        }
    }

    /** Dev/test hook — dismiss every active stalker. Used by
     *  {@code /noname event stopall}. */
    public static void stopAll() {
        for (CaveZombie zombie : activeZombies.values()) {
            zombie.discard();
        }
        activeZombies.clear();
        ticksUntilRoll.clear();
    }

    /** Random 1-1.5 minutes (1200-1800 ticks) until the next roll. */
    private static int nextRollInterval(RandomSource random) {
        return MIN_ROLL_TICKS + random.nextInt(MAX_ROLL_TICKS - MIN_ROLL_TICKS + 1);
    }
    /** Places a named stalker 10 blocks away from the player on the cave
     *  floor, facing them, and registers it as the player's active stalker. */
    private static void spawnStalker(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos spot = findSpawnSpot(level, player);
        if (spot == null) {
            return;
        }
        CaveZombie zombie = new CaveZombie(ModEntities.CAVE_ZOMBIE, level);
        float yaw = (float) (Math.toDegrees(Math.atan2(
                player.getZ() - zombie.getZ(), player.getX() - zombie.getX())) - 90.0D);
        zombie.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, yaw, 0.0F);
        zombie.setCustomName(Component.literal(NAMES[level.random.nextInt(NAMES.length)]));
        zombie.setCustomNameVisible(true);
        zombie.setPersistenceRequired();
        zombie.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SENSE_RANGE);
        zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(STALKER_SPEED);
        zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
                MobSpawnType.TRIGGERED, null);
        zombie.setTarget(player);
        // The hostile-spawn gate would refuse a MONSTER-category entity, so
        // mark it as a deliberate spawn first.
        HostileSpawnTracker.markDeliberate(zombie, true);
        level.addFreshEntity(zombie);
        activeZombies.put(player.getUUID(), zombie);
    }

    /**
     * {@return a floor position in the same cave as the player, roughly
     * {@link #SPAWN_DISTANCE} blocks horizontally away, or null when no
     * suitable spot exists}
     */
    private static BlockPos findSpawnSpot(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.getRandom();
        BlockPos playerPos = player.blockPosition();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int dx = (int) Math.round(Math.cos(angle) * SPAWN_DISTANCE);
            int dz = (int) Math.round(Math.sin(angle) * SPAWN_DISTANCE);
            if (dx == 0 && dz == 0) {
                dz = (int) SPAWN_DISTANCE;
            }
            // Scan a vertical band around the player's height so the stalker
            // lands on the cave floor, not on the surface above.
            for (int y = playerPos.getY() + 6; y >= playerPos.getY() - 12; y--) {
                BlockPos floor = new BlockPos(playerPos.getX() + dx, y, playerPos.getZ() + dz);
                if (!level.getBlockState(floor).isSolid()) {
                    continue;
                }
                BlockPos stand = floor.above();
                if (!level.getBlockState(stand).isAir()
                        || !level.getBlockState(stand.above()).isAir()) {
                    continue;
                }
                return stand;
            }
        }
        return null;
    }
}
