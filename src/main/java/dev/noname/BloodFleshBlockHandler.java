package dev.noname;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-side ticking of every placed {@link BloodFleshBlock}: the block
 * constantly throws blood drops and, once per second, deals 1 damage to every
 * living entity within 5 blocks of it. The block itself registers its
 * positions through {@link BloodFleshBlock#onPlace}/{@link #onRemove}; this
 * handler only walks that registry (and drops positions whose block is gone,
 * e.g. after a chunk unload).
 */
public final class BloodFleshBlockHandler {

    /** Radius around the block in which entities take damage, in blocks. */
    private static final double DAMAGE_RADIUS = 5.0D;

    /** Damage dealt every second to every entity in range. */
    private static final float DAMAGE = 1.0F;

    /** How often the damage ticks, in ticks (20 ticks = 1 second). */
    private static final int DAMAGE_INTERVAL_TICKS = 20;

    /** Blood drops thrown per server tick, per block. */
    private static final int PARTICLES_PER_TICK = 2;

    /** Level -> positions of the placed blood flesh blocks in that level. */
    private static final Map<ServerLevel, Set<BlockPos>> tracked = new HashMap<>();

    private BloodFleshBlockHandler() {
    }

    /** Starts tracking a placed block. Called by {@code BloodFleshBlock}. */
    public static void track(ServerLevel level, BlockPos pos) {
        tracked.computeIfAbsent(level, k -> new HashSet<>()).add(pos.immutable());
    }

    /** Stops tracking a removed block. Called by {@code BloodFleshBlock}. */
    public static void untrack(ServerLevel level, BlockPos pos) {
        Set<BlockPos> positions = tracked.get(level);
        if (positions != null) {
            positions.remove(pos);
            if (positions.isEmpty()) {
                tracked.remove(level);
            }
        }
    }

    public static void onServerTick(MinecraftServer server) {
        if (tracked.isEmpty()) {
            return;
        }
        boolean damageTick = server.getTickCount() % DAMAGE_INTERVAL_TICKS == 0;
        for (var entry : tracked.entrySet()) {
            ServerLevel level = entry.getKey();
            entry.getValue().removeIf(pos -> !isStillBloodFlesh(level, pos));
            for (BlockPos pos : entry.getValue()) {
                tickBlock(level, pos, damageTick);
            }
        }
    }

    /** One block's tick: throw a couple of blood drops and, on damage ticks,
     *  hurt every living entity in the 5-block radius. */
    private static void tickBlock(ServerLevel level, BlockPos pos, boolean damageTick) {
        var random = level.getRandom();
        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            level.sendParticles(ModParticles.BLOOD_DROP,
                    pos.getX() + 0.2D + random.nextDouble() * 0.6D,
                    pos.getY() + 0.2D + random.nextDouble() * 0.6D,
                    pos.getZ() + 0.2D + random.nextDouble() * 0.6D,
                    1, 0.0D, 0.0D, 0.0D, 0.02D);
        }
        if (!damageTick) {
            return;
        }
        AABB box = AABB.ofSize(pos.getCenter(), DAMAGE_RADIUS * 2.0D,
                DAMAGE_RADIUS * 2.0D, DAMAGE_RADIUS * 2.0D);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> !e.isRemoved() && e.isAlive())) {
            entity.hurt(level.damageSources().generic(), DAMAGE);
        }
    }

    /** {@return true if the block at {@code pos} is still a blood flesh
     *  block} — also covers chunk unloads, since an unloaded chunk's block
     *  state reads as air. */
    private static boolean isStillBloodFlesh(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() == ModBlocks.BLOOD_FLESH_BLOCK;
    }
}
