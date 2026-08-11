package dev.noname;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Cross: a craftable anti-apparition tool (planks + redstone, see
 * {@code data/noname/recipe/cross.json}). While held in the main hand, if an
 * apparition — the "ise it" entity or one of the mod's fake players (the
 * shared ghost UUID) — is within 5 blocks, the cross starts a 0.7 second
 * charge:
 *
 * <ol>
 *   <li>the cross sound plays and the apparition is <b>stopped</b>: frozen
 *       in place, no glitching, no chasing, no contact damage;</li>
 *   <li>a beam of electric energy visibly flows from the player's eyes into
 *       the apparition, which glows while it is held;</li>
 *   <li>after 0.7 seconds the apparition bursts into a purely visual energy
 *       explosion and is destroyed for good, ending its event;</li>
 *   <li>the cross itself is consumed in the blast — each cross is a single
 *       use.</li>
 * </ol>
 *
 * <p>Dropping the cross out of the main hand mid-charge frees the
 * apparition and aborts the sequence. Each player has their own charge, so
 * every player can use their own cross at the same time.
 */
public final class CrossItem extends Item {

    /** Max distance to a target, in blocks. */
    private static final double RANGE = 5.0D;

    /** Charge duration, in ticks (14 ticks = 0.7 seconds). */
    private static final int CHARGE_TICKS = 14;

    /** Player UUID -> the charge currently running for that player. */
    private static final Map<UUID, Charge> charges = new HashMap<>();

    /** One running charge: the pinned target and the spot it is pinned to. */
    private static final class Charge {
        final Entity target;
        final Vec3 pin;
        int ticksLeft;

        Charge(Entity target, Vec3 pin, int ticksLeft) {
            this.target = target;
            this.pin = pin;
            this.ticksLeft = ticksLeft;
        }
    }

    public CrossItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity,
                              int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        UUID uuid = player.getUUID();
        Charge charge = charges.get(uuid);

        // Dropped out of the main hand mid-charge: the cross stops working
        // and the apparition is freed.
        if (!selected || player.getMainHandItem() != stack) {
            if (charge != null) {
                cancelCharge(uuid, charge);
            }
            return;
        }

        if (charge == null) {
            Entity target = findTarget(player);
            if (target == null) {
                return;
            }
            charge = new Charge(target, target.position(), CHARGE_TICKS);
            charges.put(uuid, charge);
            // "ise it" is driven by its own handler; the stopped flag makes
            // it hold still (and stop attacking) for the whole charge. The
            // fake players are pinned directly every tick instead.
            if (target instanceof IseItEntity iseIt) {
                iseIt.setStoppedUntilTick(serverLevel.getServer().getTickCount() + CHARGE_TICKS);
            }
            serverLevel.playSound(null, player, ModSounds.CROSS, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        // The target vanished or escaped mid-charge: abort silently.
        if (charge.target.isRemoved() || !charge.target.isAlive()
                || charge.target.level() != level
                || charge.target.distanceToSqr(charge.pin) > 64.0D) {
            cancelCharge(uuid, charge);
            return;
        }

        if (--charge.ticksLeft <= 0) {
            explode(serverLevel, uuid, charge, stack);
            return;
        }

        // Hold the apparition in place and pour energy into it.
        pin(charge);
        beam(serverLevel, player, charge);
    }

    /** Dev hook — abort every active charge. Used by {@code /noname event
     *  stopall}. */
    public static void stopAll() {
        for (Charge charge : charges.values()) {
            if (charge.target instanceof IseItEntity iseIt) {
                iseIt.setStoppedUntilTick(0);
            }
        }
        charges.clear();
    }

    /** {@return whether the given entity is currently pinned by a charge} —
     *  the fake-player handlers check this so their target neither moves nor
     *  catches the player while the cross holds it. */
    public static boolean isStopped(Entity target) {
        for (Charge charge : charges.values()) {
            if (charge.target == target) {
                return true;
            }
        }
        return false;
    }

    /** Pins the given entity to the spot its charge recorded. Called by the
     *  fake-player handlers, which tick before the player's own tick. */
    public static void pin(Entity target) {
        for (Charge charge : charges.values()) {
            if (charge.target == target) {
                target.setPos(charge.pin.x, charge.pin.y, charge.pin.z);
                target.setDeltaMovement(Vec3.ZERO);
                return;
            }
        }
    }

    /** {@return the nearest apparition within 5 blocks of the player, or
     *  {@code null} when there is none} */
    private static Entity findTarget(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Entity best = null;
        double bestDistSq = RANGE * RANGE;
        for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(RANGE))) {
            if (e.isRemoved() || !e.isAlive()) {
                continue;
            }
            if (!(e instanceof IseItEntity) && !isFakePlayer(e)) {
                continue;
            }
            double distSq = e.position().distanceToSqr(eye);
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = e;
            }
        }
        return best;
    }

    /** {@return whether the entity is one of the mod's fake players — any
     *  world entity carrying the shared ghost UUID (flicker, apparition,
     *  stalker, friend)} */
    private static boolean isFakePlayer(Entity entity) {
        return entity instanceof ServerPlayer player
                && player.getUUID().equals(FakePlayerUtil.FAKE_UUID);
    }

    private static void cancelCharge(UUID uuid, Charge charge) {
        charges.remove(uuid);
        if (charge.target instanceof IseItEntity iseIt) {
            iseIt.setStoppedUntilTick(0);
        }
    }

    private static void pin(Charge charge) {
        charge.target.setPos(charge.pin.x, charge.pin.y, charge.pin.z);
        charge.target.setDeltaMovement(Vec3.ZERO);
    }

    /** The energy beam: electric sparks flowing from the player's eyes into
     *  the pinned apparition, plus a glow around the apparition. */
    private static void beam(ServerLevel level, ServerPlayer player, Charge charge) {
        Vec3 from = player.getEyePosition();
        Vec3 to = charge.pin.add(0.0D, 1.0D, 0.0D);
        double distance = from.distanceTo(to);
        int steps = Math.max(4, (int) (distance * 2.5D));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    p.x, p.y, p.z, 1, 0.12D, 0.12D, 0.12D, 0.02D);
        }
        level.sendParticles(ParticleTypes.END_ROD,
                charge.pin.x, charge.pin.y + 1.0D, charge.pin.z,
                3, 0.3D, 0.5D, 0.3D, 0.04D);
    }

    /** The blast: the pinned apparition bursts into a purely visual energy
     *  explosion (no block or player damage) and is destroyed for good,
     *  ending its event. The cross that was held is consumed in the blast. */
    private static void explode(ServerLevel level, UUID uuid, Charge charge,
                                ItemStack stack) {
        charges.remove(uuid);
        Vec3 pos = charge.pin;
        double x = pos.x;
        double y = pos.y + 1.0D;
        double z = pos.z;

        // The energy explosion.
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 48, 1.2D, 1.2D, 1.2D, 0.35D);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 32, 0.9D, 0.9D, 0.9D, 0.15D);
        level.playSound(null, BlockPos.containing(pos),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.2F, 0.8F);

        // The blast consumes the cross that was used.
        stack.shrink(1);

        // Destroy the apparition and end its event.
        Entity target = charge.target;
        if (target instanceof IseItEntity iseIt) {
            IseItHandler.removeEntity(iseIt);
            return;
        }
        if (target instanceof ServerPlayer fake) {
            if (Day3StalkerHandler.destroyApparition(fake)
                    || Day7FakePlayerHandler.destroyApparition(fake)
                    || Day10LookHandler.destroyApparition(fake)
                    || Day13StalkerHandler.destroyStalker(fake)
                    || HeIsHereHandler.destroyFriend(fake)) {
                return;
            }
            // Some other fake player instance: just remove it from the world.
            fake.discard();
        }
    }
}
