package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * From day 8 on, newly generated chunks have a 3-in-128 chance of growing a
 * tree made of flesh, shaped like a Minecraft plains oak: a 1-wide trunk, a
 * layered lumpy canopy and a couple of side branches with puff ends, all in
 * raw flesh.
 *
 * <p>The roll is made once per newly generated chunk (hooked by
 * {@code dev.noname.mixin.ChunkGenFleshTreeMixin} into
 * {@code ChunkGenerator.applyBiomeDecoration}) and is seeded from the chunk
 * position, so the same world always grows the same flesh trees. The dev
 * command {@code /noname event play flesh_tree} places one flesh tree next to
 * each player for testing.
 */
public final class FleshTreeHandler {

    /** Chance (per new chunk) that the chunk grows a flesh tree: 3 in 128. */
    public static final float TREE_CHANCE = 3.0F / 128.0F;

    /** Trunk height range: 4-5 blocks, like a plains oak. */
    private static final int TRUNK_MIN_HEIGHT = 4;
    private static final int TRUNK_MAX_HEIGHT = 5;

    /** Canopy layer radii (bottom to top), like an oak's leaf blob. */
    private static final int[] CANOPY_RADII = {2, 3, 2, 1};

    /** How far a side branch juts out past the canopy, in blocks. */
    private static final int BRANCH_MIN_LENGTH = 2;
    private static final int BRANCH_MAX_LENGTH = 3;

    /** Puff radius at a branch tip: 1-2 blocks. */
    private static final int PUFF_MIN_RADIUS = 1;
    private static final int PUFF_MAX_RADIUS = 2;

    /** Chance an edge block of the canopy is skipped, for raggedness. */
    private static final float RAGGED_EDGE_CHANCE = 0.35F;

    private FleshTreeHandler() {
    }

    /**
     * Chunk-generation hook — called once per newly generated chunk. Rolls
     * the flesh-tree chance and, on a hit, grows one tree at a random spot
     * inside the chunk. Gated on day 8+ and the overworld.
     */
    public static void maybeGrowFleshTree(WorldGenLevel level, ChunkAccess chunk) {
        if (DayCounter.currentDay(level) < ModConfig.scaledDay(8)
                || !ModConfig.isEnabled("flesh_tree")) {
            return;
        }
        if (!Level.OVERWORLD.equals(level.getLevel().dimension())) {
            return;
        }

        // Deterministic per-chunk roll: the same world always gets the same
        // flesh trees, no matter when a chunk is generated.
        var pos = chunk.getPos();
        RandomSource rng = RandomSource.create(
                level.getSeed() ^ ((long) pos.x * 0x9E3779B1L) ^ ((long) pos.z * 0x85EBCA6BL));
        if (rng.nextFloat() >= ModConfig.chance("flesh_tree", TREE_CHANCE)) {
            return;
        }

        int x = pos.getMinBlockX() + rng.nextInt(16);
        int z = pos.getMinBlockZ() + rng.nextInt(16);
        growFleshTree(level, x, z, rng);
    }

    /**
     * Dev/test hook — {@link dev.noname.command.NonameCommand} calls this to
     * grow one flesh tree next to every online player right now, bypassing
     * the day-8 + random-chance gate.
     */
    public static void growOneNearEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = (ServerLevel) player.level();
            RandomSource rng = level.getRandom();
            int radius = 8 + rng.nextInt(17);
            double angle = rng.nextDouble() * (Math.PI * 2.0);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * radius);
            growFleshTree(level, x, z, rng);
        }
    }

    /**
     * Grows one flesh tree on top of the surface (the first motion-blocking,
     * leaf-excluding height) at the given column, shaped like a plains oak:
     * 1-wide trunk, layered canopy blob, and a few side branches with puffs.
     * Land only: never underwater, never floating on top of other trees, and
     * never on/through a bedrock pillar.
     */
    private static void growFleshTree(ServerLevelAccessor level, int x, int z, RandomSource rng) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int trunkHeight = TRUNK_MIN_HEIGHT
                + rng.nextInt(TRUNK_MAX_HEIGHT - TRUNK_MIN_HEIGHT + 1);
        if (surfaceY + trunkHeight + CANOPY_RADII.length + 2 >= level.getMaxBuildHeight()) {
            return;
        }

        // Land only: skip ocean / river / beach columns entirely.
        var biome = level.getBiome(new BlockPos(x, surfaceY, z));
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)
                || biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_RIVER)) {
            return;
        }

        // Walk down to real ground: skip trees (logs/leaves), other flesh
        // trees and bedrock pillars; bail out if the column is underwater.
        int groundY = surfaceY;
        boolean foundGround = false;
        for (int depth = 0; depth < 8 && groundY > level.getMinBuildHeight() + 2; depth++) {
            BlockPos belowPos = new BlockPos(x, groundY - 1, z);
            BlockState below = level.getBlockState(belowPos);
            if (below.getFluidState().is(FluidTags.WATER)) {
                return;
            }
            if (isValidGround(below, level, belowPos)) {
                foundGround = true;
                break;
            }
            groundY--;
        }
        if (!foundGround) {
            return;
        }

        // A bedrock pillar may already occupy this column (it generates
        // before us): never grow through one.
        for (int y = surfaceY + 1; y <= surfaceY + trunkHeight + CANOPY_RADII.length + 1; y++) {
            if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.BEDROCK)) {
                return;
            }
        }

        BlockState flesh = ModBlocks.FLESH_BLOCK.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Trunk.
        cursor.set(x, groundY + 1, z);
        for (int i = 0; i < trunkHeight; i++) {
            level.setBlock(cursor, flesh, 2);
            cursor.move(Direction.UP);
        }

        int crownY = groundY + trunkHeight;

        // Canopy: one lumpy disc per layer, wider in the middle, ragged edge.
        for (int dy = 0; dy < CANOPY_RADII.length; dy++) {
            int radius = CANOPY_RADII[dy];
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > radius + 0.4) {
                        continue;
                    }
                    if (dist > radius - 0.6 && rng.nextFloat() < RAGGED_EDGE_CHANCE) {
                        continue;
                    }
                    cursor.set(x + dx, crownY + dy, z + dz);
                    if (level.getBlockState(cursor).canBeReplaced()) {
                        level.setBlock(cursor, flesh, 2);
                    }
                }
            }
        }

        // Side branches: an arm sticking out of the upper canopy with a puff
        // of flesh at the end, like oak branches with leaf clumps.
        int branches = 1 + rng.nextInt(3);
        for (int i = 0; i < branches; i++) {
            int branchY = crownY + 1 + rng.nextInt(2);
            int dxDir = rng.nextBoolean() ? 1 : -1;
            int dzDir = 0;
            if (rng.nextBoolean()) {
                int swap = dxDir;
                dxDir = dzDir;
                dzDir = swap;
            }
            int length = BRANCH_MIN_LENGTH
                    + rng.nextInt(BRANCH_MAX_LENGTH - BRANCH_MIN_LENGTH + 1);
            for (int s = 1; s <= length; s++) {
                cursor.set(x + dxDir * s, branchY, z + dzDir * s);
                if (level.getBlockState(cursor).canBeReplaced()) {
                    level.setBlock(cursor, flesh, 2);
                }
            }
            placePuff(level, cursor, x + dxDir * (length + 1), branchY,
                    z + dzDir * (length + 1), flesh, rng);
        }
    }

    /** One round lump of flesh at a branch tip. */
    private static void placePuff(ServerLevelAccessor level, BlockPos.MutableBlockPos cursor,
                                  int cx, int cy, int cz, BlockState flesh, RandomSource rng) {
        int radius = PUFF_MIN_RADIUS
                + rng.nextInt(PUFF_MAX_RADIUS - PUFF_MIN_RADIUS + 1);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > radius + 0.4) {
                        continue;
                    }
                    if (dist > radius - 0.6 && rng.nextFloat() < RAGGED_EDGE_CHANCE) {
                        continue;
                    }
                    cursor.set(cx + dx, cy + dy, cz + dz);
                    if (level.getBlockState(cursor).canBeReplaced()) {
                        level.setBlock(cursor, flesh, 2);
                    }
                }
            }
        }
    }

    /** {@return true if the block is real, solid land: a full block, not
     *  part of a (flesh) tree, not a bedrock pillar}. */
    private static boolean isValidGround(BlockState state, ServerLevelAccessor level, BlockPos pos) {
        if (state.isAir() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            return false;
        }
        if (state.is(ModBlocks.FLESH_BLOCK) || state.is(Blocks.BEDROCK)) {
            return false;
        }
        return state.isCollisionShapeFullBlock(level, pos);
    }
}
