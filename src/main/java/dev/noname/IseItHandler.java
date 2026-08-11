package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The day-6+ "ise it" apparition. From day 6 onwards, every 3 to 5 minutes
 * there is a 10% chance that the entity spawns once for every real player,
 * exactly 15 blocks away, and watches them:
 *
 * <ul>
 *   <li>While nobody looks at it, it stays in place, glitching: every few
 *       ticks it stutters to a nearby random spot (it "shakes and looks
 *       laggy and stuttery"), always facing the player.</li>
 *   <li>The moment a player looks at it (their view ray crosses its body),
 *       it starts to finally move, slowly creeping toward that player in
 *       short stuttery jumps.</li>
 *   <li>Once within 3 blocks it starts dealing 4 damage every second (with
 *       a tearing-flesh sound and a blood burst at the player).</li>
 *   <li>It despawns 2 minutes after spawning, or immediately if its target
 *       dies (it "kills" the player).</li>
 * </ul>
 *
 * <p>The entity itself is a dumb billboard ({@link IseItEntity}); all of
 * this is driven from the server tick, and the renderer adds a per-frame
 * stuttery shake on top of the server-side glitch teleports.
 */
public final class IseItHandler {

    /** Day from which the apparition can appear. */
    private static final long START_DAY = 6L;

    /** Window between spawn rolls: 3 to 5 minutes. */
    private static final int MIN_INTERVAL_TICKS = 20 * 60 * 3;
    private static final int MAX_INTERVAL_TICKS = 20 * 60 * 5;

    /** Chance that a roll actually spawns during the day — 10%. */
    private static final float SPAWN_CHANCE = 0.10F;

    /** Chance that a roll actually spawns at night — 30%. */
    private static final float NIGHT_SPAWN_CHANCE = 0.30F;

    /** The entity spawns exactly this far (blocks) from the player. */
    private static final double SPAWN_DISTANCE = 15.0D;

    /** It despawns 2 minutes after spawning. */
    private static final int DESPAWN_AFTER_TICKS = 20 * 60 * 2;

    /** Idle glitching: a stutter teleport of up to this many blocks. */
    private static final double IDLE_JITTER = 0.35D;

    /** Idle stutter every 8 to 14 ticks (a random interval per jump). */
    private static final int JITTER_MIN_INTERVAL = 8;
    private static final int JITTER_MAX_INTERVAL = 14;

    /** Slow chase: 0.12 blocks per tick, in bursts of 4 ticks. */
    private static final double CHASE_SPEED = 0.12D;
    private static final int CHASE_STEP_TICKS = 4;

    /** Chance of a sudden "lag burst" (a bigger jump closer) per chase step. */
    private static final float LAG_BURST_CHANCE = 0.15F;
    private static final double LAG_BURST_MULTIPLIER = 1.8D;

    /** Within this range it starts damaging the player (max block range). */
    private static final double ATTACK_RANGE = 3.0D;

    /** Damage per hit — 4. */
    private static final float ATTACK_DAMAGE = 4.0F;

    /** Cooldown between hits, in ticks (1 second). */
    private static final int ATTACK_COOLDOWN_TICKS = 20;

    /** Cosine threshold for "looking at it" (~31.8° half-angle cone). */
    private static final double LOOK_COSINE = 0.85D;

    /** Every active apparition (one per player). */
    private static final List<IseItEntity> entities = new ArrayList<>();

    /** Ticks until the next 10% spawn roll. */
    private static int ticksUntilNextRoll = MIN_INTERVAL_TICKS;

    private IseItHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        // Day 6+: count down the 3-5 minute window, then roll — 10% by day,
        // 30% at night.
        long day = DayCounter.currentDay(overworld);
        if (day >= ModConfig.scaledDay(START_DAY) && ModConfig.isEnabled("ise_it")
                && !server.getPlayerList().getPlayers().isEmpty()) {
            if (--ticksUntilNextRoll <= 0) {
                ticksUntilNextRoll = MIN_INTERVAL_TICKS + overworld.getRandom()
                        .nextInt(MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS + 1);
                float chance = overworld.isNight() ? NIGHT_SPAWN_CHANCE : SPAWN_CHANCE;
                if (overworld.getRandom().nextFloat() < ModConfig.chance("ise_it", chance)) {
                    spawnForEachPlayer(server);
                }
            }
        }

        // Drive every active apparition.
        long now = server.getTickCount();
        Iterator<IseItEntity> it = entities.iterator();
        while (it.hasNext()) {
            IseItEntity entity = it.next();
            ServerPlayer target = server.getPlayerList()
                    .getPlayer(entity.getTargetUuid());

            // Despawn after 2 minutes, if its target is gone/dead (it "kills"
            // the player), or if the target left the world.
            if (target == null || !target.isAlive()
                    || now - entity.getSpawnedAtTick() >= DESPAWN_AFTER_TICKS) {
                entity.discard();
                it.remove();
                continue;
            }

            // Always face the player.
            Vec3 delta = target.position().subtract(entity.position());
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            entity.setYRot(yaw);
            entity.setYHeadRot(yaw);

            // Pinned by the Cross: hold perfectly still — no glitch, no
            // chase, no contact damage — until the charge completes.
            if (entity.isStopped(now)) {
                continue;
            }

            // The first time the player looks at it, it starts moving.
            if (!entity.isChasing() && isLookingAt(target, entity)) {
                entity.setChasing(true);
            }

            if (entity.isChasing()) {
                stepToward(overworld, entity, delta, now);
            } else {
                glitchIdle(overworld, entity, now);
            }

            // Contact damage: within 3 blocks, 4 damage, once per second.
            if (entity.distanceToSqr(target) <= ATTACK_RANGE * ATTACK_RANGE
                    && now - entity.getLastAttackTick() >= ATTACK_COOLDOWN_TICKS) {
                entity.setLastAttackTick(now);
                target.hurt(target.damageSources().generic(), ATTACK_DAMAGE);
                overworld.playSound(null, target,
                        ModSounds.TEARING_FLESH, SoundSource.HOSTILE, 1.0F, 1.0F);
                overworld.sendParticles(ModParticles.BLOOD_DROP,
                        target.getX(), target.getY() + 1.0D, target.getZ(),
                        30, 0.5D, 0.4D, 0.5D, 0.1D);
            }
        }
    }

    /** Dev/test hook — spawn one "ise it" per real player right now.
     *  Dispatched by {@code /noname event play ise_it}. */
    public static void triggerNow(MinecraftServer server) {
        spawnForEachPlayer(server);
    }

    /** Cancels every active apparition. Used by {@code /noname event
     *  stopall}. */
    public static void stopAll() {
        for (IseItEntity entity : entities) {
            entity.discard();
        }
        entities.clear();
    }

    /** Removes the given apparition from the world and the active list for
     *  good — used by {@link CrossItem} when the Cross destroys "ise it". */
    public static void removeEntity(IseItEntity entity) {
        entities.remove(entity);
        entity.discard();
    }

    /** Spawns one apparition exactly 15 blocks away from each real player. */
    private static void spawnForEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            Vec3 spot = findSpawnSpot(player);
            IseItEntity entity = new IseItEntity(ModEntities.ISE_IT, level);
            entity.setPos(spot.x, spot.y, spot.z);
            entity.setCustomName(Component.literal("ise it"));
            entity.setCustomNameVisible(true);
            entity.setTargetUuid(player.getUUID());
            entity.setSpawnedAtTick(server.getTickCount());
            entity.setNextJitterInterval(nextJitterInterval(level));
            level.addFreshEntity(entity);
            entities.add(entity);
            level.playSound(null, player,
                    ModSounds.DO_YOU_SEE_ME, SoundSource.AMBIENT, 1.0F, 1.0F);
        }
    }

    /** {@return whether an apparition is currently active for the given
     *  player} — used to block sleeping while "ise it" is around. */
    public static boolean isActiveFor(ServerPlayer player) {
        for (IseItEntity entity : entities) {
            if (entity.isAlive()
                    && entity.getTargetUuid().equals(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /** {@return whether the player's view ray is crossing the entity}. */
    private static boolean isLookingAt(ServerPlayer player, IseItEntity entity) {
        Vec3 toEntity = entity.position().add(0.0D, 1.5D, 0.0D)
                .subtract(player.getEyePosition()).normalize();
        return toEntity.dot(player.getLookAngle()) >= LOOK_COSINE;
    }

    /** A spot exactly 15 blocks from the player, snapped to the ground. */
    private static Vec3 findSpawnSpot(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
        double dx = Math.sin(angle) * SPAWN_DISTANCE;
        double dz = Math.cos(angle) * SPAWN_DISTANCE;
        int x = (int) Math.floor(player.getX() + dx);
        int z = (int) Math.floor(player.getZ() + dz);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                x, (int) Math.ceil(player.getY()), z);
        for (int i = 0; i < 32; i++) {
            pos.move(0, -1, 0);
            BlockState state = level.getBlockState(pos);
            if (state.isSolid()) {
                return new Vec3(x + 0.5D, pos.getY() + 1.0D, z + 0.5D);
            }
        }
        return new Vec3(x + 0.5D, player.getY(), z + 0.5D);
    }

    /** Idle glitch: a stutter teleport in a random direction, on a random
     *  8-14 tick interval. */
    private static void glitchIdle(ServerLevel level, IseItEntity entity, long now) {
        if (now - entity.getLastJitterTick() < entity.getNextJitterInterval()) {
            return;
        }
        entity.setLastJitterTick(now);
        entity.setNextJitterInterval(nextJitterInterval(level));
        double jx = (level.getRandom().nextDouble() - 0.5D) * IDLE_JITTER;
        double jy = (level.getRandom().nextDouble() - 0.5D) * 0.15D;
        double jz = (level.getRandom().nextDouble() - 0.5D) * IDLE_JITTER;
        entity.setPos(entity.getX() + jx, entity.getY() + jy, entity.getZ() + jz);
    }

    /** The slow chase: a stuttery burst toward the player every 4 ticks,
     *  with a chance of a sudden "lag burst" jump. */
    private static void stepToward(ServerLevel level, IseItEntity entity,
                                   Vec3 delta, long now) {
        if (now % CHASE_STEP_TICKS != 0) {
            return;
        }
        Vec3 dir = delta.normalize();
        double step = CHASE_SPEED * CHASE_STEP_TICKS;
        if (level.getRandom().nextFloat() < LAG_BURST_CHANCE) {
            step *= LAG_BURST_MULTIPLIER;
        }
        double jx = (level.getRandom().nextDouble() - 0.5D) * 0.1D;
        double jz = (level.getRandom().nextDouble() - 0.5D) * 0.1D;
        entity.setPos(entity.getX() + dir.x * step + jx,
                entity.getY() + dir.y * step,
                entity.getZ() + dir.z * step + jz);
    }

    private static int nextJitterInterval(ServerLevel level) {
        return JITTER_MIN_INTERVAL + level.getRandom()
                .nextInt(JITTER_MAX_INTERVAL - JITTER_MIN_INTERVAL + 1);
    }
}
