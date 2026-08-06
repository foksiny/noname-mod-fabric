package dev.noname;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The day-8+ cave stalker: a zombie with a far smarter brain than the
 * vanilla kind.
 *
 * <ul>
 *   <li>It knows where the player is at any distance up to
 *       {@link #SENSE_RANGE} blocks — no line of sight needed, no follow
 *       range limit, it simply always feels the player.</li>
 *   <li>It runs a custom chase-and-attack brain instead of the vanilla
 *       zombie goal soup.</li>
 *   <li>When there is no short walkable route to the player (the player
 *       walled themselves in, or the terrain leaves no quick way around),
 *       it stops and digs through — preferring blocks the player themselves
 *       placed ({@link PlayerPlacedBlocks}), but digging any solid natural
 *       block when actually needed, exactly like a bare-handed player would:
 *       same mining time, crack animation, break particles and drops.</li>
 * </ul>
 *
 * <p>Spawning is driven by {@link CaveZombieHandler}; the entity itself is
 * fully self-contained once it exists.
 */
public class CaveZombie extends Zombie {

    /** How far away the stalker can still feel the player, in blocks. */
    public static final double SENSE_RANGE = 512.0D;

    public CaveZombie(EntityType<? extends CaveZombie> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(1, new StalkPlayerTargetGoal(this));
        this.goalSelector.addGoal(1, new BreakPlayerBlocksGoal(this));
        this.goalSelector.addGoal(2, new StalkAttackGoal(this));
    }

    /** The stalker is what it is — it never turns into a drowned. */
    @Override
    protected boolean convertsInWater() {
        return false;
    }

    /** Keeps the player as the target no matter how far away they are. */
    private static final class StalkPlayerTargetGoal extends Goal {

        private final CaveZombie zombie;
        private ServerPlayer target;
        private int recheckTimer;

        StalkPlayerTargetGoal(CaveZombie zombie) {
            this.zombie = zombie;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            this.target = this.nearestPlayer();
            return this.target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.target != null && this.target.isAlive()
                    && this.zombie.distanceToSqr(this.target) <= SENSE_RANGE * SENSE_RANGE;
        }

        @Override
        public void start() {
            this.zombie.setTarget(this.target);
        }

        @Override
        public void stop() {
            if (this.zombie.getTarget() == this.target) {
                this.zombie.setTarget(null);
            }
            this.target = null;
        }

        @Override
        public void tick() {
            if (--this.recheckTimer <= 0) {
                this.recheckTimer = 20;
                ServerPlayer nearest = this.nearestPlayer();
                if (nearest != null) {
                    this.target = nearest;
                }
            }
            if (this.target != null) {
                this.zombie.setTarget(this.target);
            }
        }

        /** {@return the nearest real player within sensing range, or null} */
        private ServerPlayer nearestPlayer() {
            if (!(this.zombie.level() instanceof ServerLevel level)) {
                return null;
            }
            ServerPlayer best = null;
            double bestDistance = SENSE_RANGE * SENSE_RANGE;
            for (ServerPlayer player : level.players()) {
                if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                    continue;
                }
                double distance = this.zombie.distanceToSqr(player);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = player;
                }
            }
            return best;
        }
    }

    /** Chases the target and melee-attacks it when close enough. */
    private static final class StalkAttackGoal extends Goal {

        private static final int ATTACK_COOLDOWN_TICKS = 20;
        private static final int REPATH_TICKS = 20;

        private final CaveZombie zombie;
        private int attackCooldown;
        private int repathTimer;

        StalkAttackGoal(CaveZombie zombie) {
            this.zombie = zombie;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.zombie.getTarget() != null && this.zombie.getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void stop() {
            this.zombie.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = this.zombie.getTarget();
            if (target == null) {
                return;
            }
            this.zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double reach = this.zombie.getBbWidth() + 2.0D;
            double reachSq = reach * reach;
            double distanceSq = this.zombie.distanceToSqr(target);
            if (--this.attackCooldown <= 0 && distanceSq <= reachSq
                    && this.zombie.hasLineOfSight(target)) {
                this.zombie.doHurtTarget(target);
                this.attackCooldown = ATTACK_COOLDOWN_TICKS;
            } else if (distanceSq > reachSq
                    && (--this.repathTimer <= 0 || this.zombie.getNavigation().isDone())) {
                this.zombie.getNavigation().moveTo(target, 1.0D);
                this.repathTimer = REPATH_TICKS;
            }
        }
    }

    /**
     * The smart part. When the stalker cannot reach the player, it first
     * gives its pathfinder a few seconds to find a way around — a route
     * that is short enough to be worth walking is always taken. Only when
     * no such route exists does it stop and dig, like a bare-handed player:
     * vanilla hand mining speed, crack animation, break particles and drops.
     * It prefers blocks the player placed ({@link PlayerPlacedBlocks}), but
     * when actually needed it digs any solid natural block too. The moment
     * the dug gap opens a path again it stops digging and runs normally.
     */
    private static final class BreakPlayerBlocksGoal extends Goal {

        /** How often the stalker retries pathfinding while stuck or digging. */
        private static final int REPATH_TICKS = 20;

        /** How long the stalker keeps hunting for a walkable route around
         *  before it decides digging is faster: ~3 seconds. */
        private static final int STUCK_TICKS_BEFORE_DIGGING = 60;

        private final CaveZombie zombie;
        private BlockPos breakingPos;
        private float damage;
        private int lastStage = -1;
        private int repathTimer;
        private int stuckTicks;

        BreakPlayerBlocksGoal(CaveZombie zombie) {
            this.zombie = zombie;
        }

        @Override
        public boolean canUse() {
            return this.zombie.getTarget() != null && this.zombie.getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void stop() {
            if (this.breakingPos != null) {
                this.cancelDigging((ServerLevel) this.zombie.level());
            }
        }

        @Override
        public void tick() {
            if (this.breakingPos != null) {
                this.tickDigging();
                return;
            }
            LivingEntity target = this.zombie.getTarget();
            if (target == null || !target.isAlive()) {
                this.stuckTicks = 0;
                return;
            }
            if (this.zombie.distanceToSqr(target) <= 3.5D * 3.5D) {
                this.stuckTicks = 0;
                return;
            }
            if (!this.zombie.getNavigation().isDone()) {
                // Already walking a valid route to the player.
                this.stuckTicks = 0;
                return;
            }
            // No path right now: keep asking the pathfinder for a route
            // around before giving in and digging.
            if (--this.repathTimer <= 0) {
                this.repathTimer = REPATH_TICKS;
                if (this.zombie.getNavigation().moveTo(target, 1.0D)) {
                    this.stuckTicks = 0;
                    return;
                }
            }
            if (++this.stuckTicks < STUCK_TICKS_BEFORE_DIGGING) {
                return;
            }
            BlockPos block = this.findBlockToBreak();
            if (block != null) {
                this.breakingPos = block;
                this.damage = 0.0F;
                this.lastStage = -1;
                this.stuckTicks = 0;
            }
        }

        private void tickDigging() {
            ServerLevel level = (ServerLevel) this.zombie.level();
            LivingEntity target = this.zombie.getTarget();
            if (target == null || this.zombie.isRemoved()) {
                this.cancelDigging(level);
                return;
            }
            BlockState state = level.getBlockState(this.breakingPos);
            boolean solid = state.isSolid() && !state.isAir();
            boolean breakable = state.getDestroySpeed(level, this.breakingPos) >= 0.0F;
            if (!solid || !breakable
                    || this.zombie.distanceToSqr(this.breakingPos.getCenter()) > 5.0D * 5.0D) {
                this.cancelDigging(level);
                return;
            }
            // Every so often try to path again: the moment the dug gap opens
            // a route to the player, stop digging and run normally.
            if (--this.repathTimer <= 0) {
                this.repathTimer = REPATH_TICKS;
                if (this.zombie.getNavigation().moveTo(target, 1.0D)) {
                    this.cancelDigging(level);
                    return;
                }
            }
            // Bare-handed player progress: 1 / hardness / 100 per tick.
            this.damage += 1.0F / state.getDestroySpeed(level, this.breakingPos) / 100.0F;
            int stage = Math.min(9, (int) (this.damage * 10.0F));
            if (stage != this.lastStage) {
                this.lastStage = stage;
                level.destroyBlockProgress(this.zombie.getId(), this.breakingPos, stage);
            }
            if (this.damage >= 1.0F) {
                level.destroyBlockProgress(this.zombie.getId(), this.breakingPos, -1);
                level.destroyBlock(this.breakingPos, true);
                PlayerPlacedBlocks.remove(level, this.breakingPos);
                this.breakingPos = null;
                this.damage = 0.0F;
                this.lastStage = -1;
                // The wall is still there: keep digging straight away instead
                // of wasting the think-time again.
                if (this.zombie.getNavigation().isDone()) {
                    BlockPos next = this.findBlockToBreak();
                    if (next != null) {
                        this.breakingPos = next;
                    }
                }
            }
        }

        private void cancelDigging(ServerLevel level) {
            if (this.breakingPos != null) {
                level.destroyBlockProgress(this.zombie.getId(), this.breakingPos, -1);
            }
            this.breakingPos = null;
            this.damage = 0.0F;
            this.lastStage = -1;
        }

        /** {@return the nearest diggable block standing between the stalker
         *  and the player — a player-placed block wins over natural terrain,
         *  but when nothing the player built is in the way any solid block
         *  will do; null when there is nothing to dig} */
        private BlockPos findBlockToBreak() {
            ServerLevel level = (ServerLevel) this.zombie.level();
            LivingEntity target = this.zombie.getTarget();
            if (target == null) {
                return null;
            }
            Vec3 toTarget = target.position().subtract(this.zombie.position());
            double toTargetLength = toTarget.length();
            if (toTargetLength < 1.0E-4D) {
                return null;
            }
            toTarget = toTarget.scale(1.0D / toTargetLength);

            BlockPos feet = this.zombie.blockPosition();
            BlockPos bestPlaced = null;
            BlockPos bestNatural = null;
            double bestPlacedDistance = Double.MAX_VALUE;
            double bestNaturalDistance = Double.MAX_VALUE;
            for (int dy = -1; dy <= 3; dy++) {
                int y = feet.getY() + dy;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos pos = new BlockPos(feet.getX() + dx, y, feet.getZ() + dz);
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir() || !state.isSolid()) {
                            continue;
                        }
                        if (state.getDestroySpeed(level, pos) < 0.0F) {
                            continue;
                        }
                        // Only blocks that lie in the direction of the player:
                        // the wall standing between the stalker and its prey.
                        Vec3 toBlock = pos.getCenter().subtract(this.zombie.position());
                        if (toBlock.dot(toTarget) <= 0.3D) {
                            continue;
                        }
                        double distance = toBlock.lengthSqr();
                        if (PlayerPlacedBlocks.isPlaced(level, pos)) {
                            if (distance < bestPlacedDistance) {
                                bestPlacedDistance = distance;
                                bestPlaced = pos;
                            }
                        } else if (distance < bestNaturalDistance) {
                            bestNaturalDistance = distance;
                            bestNatural = pos;
                        }
                    }
                }
            }
            return bestPlaced != null ? bestPlaced : bestNatural;
        }
    }
}
