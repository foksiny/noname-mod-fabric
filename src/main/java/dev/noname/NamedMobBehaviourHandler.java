package dev.noname;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives the creepy behaviour of the named "blood" animals (any mob carrying
 * {@link BloodMobHandler#NAMED_MOB_NAME}): every 1 to 3 seconds the mob
 * shudders (a short burst of head-and-body jitters), and whenever a player
 * comes within 10 blocks it turns its head toward that player and keeps
 * staring, non-stop.
 *
 * <p>Ran from a server tick, scanning every loaded entity — cheap, and it
 * also covers mobs renamed via anvil rather than only mixin-named ones. The
 * dev command {@code /noname event play named_mob} spawns a fresh named
 * animal in front of every online player for testing.
 */
public final class NamedMobBehaviourHandler {

    /** Distance (blocks) within which a named mob starts staring at a player. */
    public static final double STARE_RADIUS = 10.0D;

    /** Shake cadence: a burst starts every 1-3 seconds (20-60 ticks). */
    private static final int SHAKE_MIN_INTERVAL_TICKS = 20;
    private static final int SHAKE_MAX_INTERVAL_TICKS = 60;

    /** How many ticks one shake burst lasts. */
    private static final int SHAKE_DURATION_TICKS = 8;

    /** How far the whole body twitches per shake tick. */
    private static final float SHAKE_BODY_JITTER = 4.0F;

    /** How far the head twitches per shake tick. */
    private static final float SHAKE_HEAD_JITTER = 12.0F;

    /** Distance in front of the player the test animal spawns. */
    private static final float SPAWN_DISTANCE = 3.0F;

    /** Farm animals the test event may spawn. */
    private static final List<EntityType<?>> FARM_ANIMALS =
            List.of(EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN);

    /** Per-mob shake schedule, keyed by entity id (unique while alive). */
    private static final Map<Integer, ShakeState> shakeStates = new HashMap<>();

    private record ShakeState(int ticksUntilShake, int shakeTicksLeft) {
    }

    private NamedMobBehaviourHandler() {
    }

    /**
     * Server tick: scans every loaded entity for a blood-mob nametag and
     * drives the stare + shake for each one found. Registered against
     * {@code ServerTickEvents.START_SERVER_TICK}.
     */
    public static void onServerTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob) || mob.isRemoved()) {
                    continue;
                }
                if (!mob.hasCustomName()
                        || !BloodMobHandler.NAMED_MOB_NAME.equals(mob.getCustomName().getString())) {
                    shakeStates.remove(mob.getId());
                    continue;
                }
                tickNamedMob(level, mob);
            }
        }
    }

    /**
     * Dev/test hook — spawns one named farm animal in front of every online
     * player, regardless of day or the 1% natural chance. Dispatched by
     * {@code /noname event play named_mob}.
     */
    public static void spawnOneNearEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            EntityType<?> type = FARM_ANIMALS.get(level.random.nextInt(FARM_ANIMALS.size()));
            var forward = player.getForward();
            int x = (int) Math.floor(player.getX() + forward.x() * SPAWN_DISTANCE);
            int z = (int) Math.floor(player.getZ() + forward.z() * SPAWN_DISTANCE);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
            Entity spawned = type.spawn(level, new BlockPos(x, y, z), MobSpawnType.COMMAND);
            if (spawned instanceof Mob mob) {
                mob.setCustomName(Component.literal(BloodMobHandler.NAMED_MOB_NAME));
                mob.setCustomNameVisible(true);
            }
        }
    }

    /** Stare + shake for one named mob for this tick. */
    private static void tickNamedMob(ServerLevel level, Mob mob) {
        // Always show the tag, even for mobs renamed via anvil.
        mob.setCustomNameVisible(true);

        // Stare: track the nearest real player within 10 blocks, non-stop.
        ServerPlayer nearest = null;
        double best = STARE_RADIUS * STARE_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            double d = player.distanceToSqr(mob);
            if (d < best) {
                best = d;
                nearest = player;
            }
        }
        if (nearest != null) {
            mob.getLookControl().setLookAt(nearest, 90.0F, 90.0F);
        }

        RandomSource rng = level.getRandom();
        ShakeState state = shakeStates.get(mob.getId());
        int ticksUntilShake = state == null ? nextShakeInterval(rng) : state.ticksUntilShake();
        int shakeTicksLeft = state == null ? 0 : state.shakeTicksLeft();

        if (shakeTicksLeft > 0) {
            jitter(mob, rng);
            shakeTicksLeft--;
            if (shakeTicksLeft == 0) {
                ticksUntilShake = nextShakeInterval(rng);
            }
        } else if (ticksUntilShake <= 0) {
            shakeTicksLeft = SHAKE_DURATION_TICKS;
        } else {
            ticksUntilShake--;
        }
        shakeStates.put(mob.getId(), new ShakeState(ticksUntilShake, shakeTicksLeft));
    }

    /** Random 1-3 s (20-60 ticks) until the next shake burst. */
    private static int nextShakeInterval(RandomSource rng) {
        return SHAKE_MIN_INTERVAL_TICKS
                + rng.nextInt(SHAKE_MAX_INTERVAL_TICKS - SHAKE_MIN_INTERVAL_TICKS + 1);
    }

    /** One shake tick: small random twitch of the body and a bigger one of
     *  the head. */
    private static void jitter(Mob mob, RandomSource rng) {
        float body = (rng.nextFloat() * 2.0F - 1.0F) * SHAKE_BODY_JITTER;
        float head = (rng.nextFloat() * 2.0F - 1.0F) * SHAKE_HEAD_JITTER;
        mob.setYRot(mob.getYRot() + body);
        mob.setYBodyRot(mob.yBodyRot + body);
        mob.setYHeadRot(mob.getYHeadRot() + head);
    }
}
