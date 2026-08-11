package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The "animals fight back" twist: from day 16 on natural mob spawns resume
 * ({@link dev.noname.mixin.HostileSpawnGateMixin}), but every non-hostile
 * mob that a real player hits turns on them: it drops whatever it was doing,
 * chases the player and, when it reaches them, hits back for exactly one
 * heart (2 HP) once per second.
 *
 * <p>Only mobs outside the MONSTER category take part — hostile mobs are
 * already gone from the world by then. The rage is per-mob and keyed by mob
 * identity ({@link WeakHashMap}), so it evaporates with the mob itself; an
 * enraged mob calms down when its target dies, logs off, goes
 * creative/spectator, leaves the dimension or gets more than 24 blocks away.
 *
 * <p>The dev/test hook {@link #enrageNearEachPlayer} is dispatched by
 * {@code /noname event play animal_revenge}.
 */
public final class AnimalRevengeHandler {

    /** Damage of the revenge hit — 2 HP = exactly one heart. */
    private static final float ATTACK_DAMAGE = 2.0F;

    /** How often an enraged mob can hit: once per second. */
    private static final int ATTACK_COOLDOWN_TICKS = 20;

    /** Beyond this distance an enraged mob gives up and calms down. */
    private static final double MAX_CHASE_DISTANCE = 24.0D;

    /** Speed factor handed to the navigation while chasing. */
    private static final double CHASE_SPEED = 1.0D;

    /** Enraged mob -> its chase state (target + attack cooldown). */
    private static final Map<Mob, State> ENRAGED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AnimalRevengeHandler() {
    }

    /**
     * Enrages a non-hostile mob the moment a real player damages it, but
     * only from day 16 on. Registered against
     * {@code ServerLivingEntityEvents.AFTER_DAMAGE} (server-side only).
     */
    public static void onAfterDamage(LivingEntity entity, DamageSource source,
                                     float baseDamageTaken, float damageTaken, boolean blocked) {
        if (!(entity instanceof Mob mob) || !mob.isAlive() || damageTaken <= 0.0F) {
            return;
        }
        // Hostile mobs are already gone from the world by day 16; only
        // non-hostiles (animals, fish, ambient mobs, ...) take part.
        if (mob.getType().getCategory() == MobCategory.MONSTER) {
            return;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)
                || player.getUUID().equals(FakePlayerUtil.FAKE_UUID)
                || player.isCreative() || player.isSpectator()) {
            return;
        }
        long day = DayCounter.currentDay(mob.level());
        if (day < ModConfig.scaledDay(16) || !ModConfig.isEnabled("animal_revenge")) {
            return;
        }
        ENRAGED.put(mob, new State(player.getUUID()));
    }

    /**
     * Drives every enraged mob: chases its target and lands the 1-heart hit
     * when in reach. Registered against
     * {@code ServerTickEvents.START_SERVER_TICK}.
     */
    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long day = DayCounter.currentDay(overworld);
        boolean active = ModConfig.isEnabled("animal_revenge")
                && day >= ModConfig.scaledDay(16);

        synchronized (ENRAGED) {
            Iterator<Map.Entry<Mob, State>> it = ENRAGED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Mob, State> entry = it.next();
                Mob mob = entry.getKey();
                State state = entry.getValue();

                // The gate closed (event off, or day < 16 again): calm down.
                if (!active || mob.isRemoved() || !mob.isAlive()) {
                    calmDown(mob);
                    it.remove();
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(state.target);
                if (player == null || !player.isAlive()
                        || player.isCreative() || player.isSpectator()
                        || player.level() != mob.level()
                        || mob.distanceToSqr(player) > MAX_CHASE_DISTANCE * MAX_CHASE_DISTANCE) {
                    calmDown(mob);
                    it.remove();
                    continue;
                }

                // Chase.
                mob.setTarget(player);
                if (mob.getNavigation() != null) {
                    mob.getNavigation().moveTo(player, CHASE_SPEED);
                }

                // The revenge hit: once per second, when in melee reach.
                if (state.cooldown > 0) {
                    state.cooldown--;
                    continue;
                }
                double reach = mob.getBbWidth() * 0.5D + player.getBbWidth() * 0.5D + 1.0D;
                if (mob.distanceToSqr(player) <= reach * reach) {
                    player.hurt(player.damageSources().mobAttack(mob), ATTACK_DAMAGE);
                    state.cooldown = ATTACK_COOLDOWN_TICKS;
                }
            }
        }
    }

    /**
     * Dev/test hook — turn every non-hostile mob within 24 blocks of each
     * real player against them, regardless of day. Dispatched by
     * {@code /noname event play animal_revenge}.
     */
    public static void enrageNearEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            AABB box = player.getBoundingBox().inflate(MAX_CHASE_DISTANCE);
            for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, box,
                    m -> m.isAlive()
                            && m.getType().getCategory() != MobCategory.MONSTER)) {
                ENRAGED.put(mob, new State(player.getUUID()));
            }
        }
    }

    /** Calms every enraged mob. Used by {@code /noname event stopall}. */
    public static void stopAll() {
        synchronized (ENRAGED) {
            for (Mob mob : ENRAGED.keySet()) {
                calmDown(mob);
            }
            ENRAGED.clear();
        }
    }

    /** Drops the mob's target so it stops chasing. */
    private static void calmDown(Mob mob) {
        mob.setTarget(null);
    }

    /** Per-mob chase state: the UUID of the player to hunt, and the ticks
     *  left before the mob may land its next revenge hit. */
    private static final class State {
        final UUID target;
        int cooldown;

        State(UUID target) {
            this.target = target;
        }
    }
}
