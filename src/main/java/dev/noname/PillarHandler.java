package dev.noname;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * From day 6 on, newly generated chunks have a small chance (1.5%) of
 * containing a lone 10-block-tall pillar of bedrock: a rare, unbreakable
 * monolith sticking up from the ground, as if something in the world is
 * hardening into place.
 *
 * <p>The roll is made once per newly generated chunk (hooked by
 * {@code dev.noname.mixin.ChunkGenPillarMixin} into
 * {@code ChunkGenerator.applyBiomeDecoration}) and is seeded from the chunk
 * position, so the same world always grows the same pillars. The dev command
 * {@code /noname event play pillar} places one pillar next to each player for
 * testing.
 */
public final class PillarHandler {

    /** Chance (per new chunk) that the chunk contains a pillar. */
    public static final float PILLAR_CHANCE = 0.015F;

    /** Height of a pillar, in blocks. */
    public static final int PILLAR_HEIGHT = 10;

    private PillarHandler() {
    }

    /**
     * Chunk-generation hook — called once per newly generated chunk. Rolls the
     * pillar chance and, on a hit, places one pillar at a random spot inside
     * the chunk. Gated on day 6+ and the overworld (nether/end keep their own
     * look).
     */
    public static void maybePlacePillar(WorldGenLevel level, ChunkAccess chunk) {
        if (DayCounter.currentDay(level) < 6) {
            return;
        }
        if (!Level.OVERWORLD.equals(level.getLevel().dimension())) {
            return;
        }

        // Deterministic per-chunk roll: the same world always gets the same
        // pillars, no matter when a chunk is generated.
        var pos = chunk.getPos();
        RandomSource rng = RandomSource.create(
                level.getSeed() ^ ((long) pos.x * 0x9E3779B1L) ^ ((long) pos.z * 0x85EBCA6BL));
        if (rng.nextFloat() >= PILLAR_CHANCE) {
            return;
        }

        int x = pos.getMinBlockX() + rng.nextInt(16);
        int z = pos.getMinBlockZ() + rng.nextInt(16);
        placePillar(level, x, z);
    }

    /**
     * Dev/test hook — {@link dev.noname.command.NonameCommand} calls this to
     * place one pillar next to every online player right now, bypassing the
     * day-6 + random-chance gate.
     */
    public static void placeOneNearEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = (ServerLevel) player.level();
            RandomSource rng = level.getRandom();
            int radius = 8 + rng.nextInt(17);
            double angle = rng.nextDouble() * (Math.PI * 2.0);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * radius);
            placePillar(level, x, z);
        }
    }

    /**
     * Stacks {@link #PILLAR_HEIGHT} bedrock blocks directly on top of the
     * surface (the first motion-blocking, leaf-excluding height) at the given
     * column, skipping the spot if the pillar would not fit under the build
     * limit.
     */
    private static void placePillar(ServerLevelAccessor level, int x, int z) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (surfaceY + PILLAR_HEIGHT >= level.getMaxBuildHeight()) {
            return;
        }

        // Never punch through a flesh tree: skip the column if one is here.
        for (int i = 1; i <= PILLAR_HEIGHT + 2; i++) {
            if (level.getBlockState(new BlockPos(x, surfaceY + i, z)).is(ModBlocks.FLESH_BLOCK)) {
                return;
            }
        }

        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, surfaceY + 1, z);
        for (int i = 0; i < PILLAR_HEIGHT; i++) {
            level.setBlock(cursor, bedrock, 2);
            cursor.move(Direction.UP);
        }
    }
}
