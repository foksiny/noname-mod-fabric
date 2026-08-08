package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Day-13+ chunk deletion: every 3-6 minutes spent on day 13 or later, there
 * is a 25% chance per player that a chunk 7 to 13 chunks away from the
 * player — in the direction the player is walking, or looking if standing
 * still — is fully deleted: every block becomes air, every block entity and
 * every non-player entity is removed. What is left is a permanent void hole
 * where terrain used to be.
 *
 * <p>Everything runs chunk-section level, so there is no per-block loop and
 * no lag: each 16×16×16 section's paletted block container is replaced with
 * an all-air container in one step (at most 24 per chunk), the heightmaps
 * are zeroed, and sky light is re-propagated asynchronously on the light
 * thread. The updated chunk is re-sent to every player tracking it.
 *
 * <p>The void chunk is saved like any other change ({@link
 * LevelChunk#setUnsaved(boolean)}), so it stays deleted across reloads. The
 * chunk containing the world spawn is never deleted.
 */
public final class ChunkDeletionHandler {

    /** Roll cadence: 3-6 minutes (3600-7200 ticks). */
    private static final int MIN_ROLL_TICKS = 20 * 60 * 3;
    private static final int MAX_ROLL_TICKS = 20 * 60 * 6;

    /** Probability that a roll actually deletes a chunk. */
    private static final float DELETE_CHANCE = 0.25F;

    /** How far away (in chunks) the deleted chunk must be from the player. */
    private static final int MIN_CHUNK_DISTANCE = 7;
    private static final int MAX_CHUNK_DISTANCE = 13;

    /** Distance (in chunks) used by the fallback pick, always within range. */
    private static final double FALLBACK_CHUNK_DISTANCE = 10.0D;

    /** How many different distances are tried before the fallback. */
    private static final int PICK_ATTEMPTS = 6;

    /** Player -> ticks until that player's next deletion roll. */
    private static final Map<UUID, Integer> ticksUntilRoll = new HashMap<>();

    private ChunkDeletionHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long day = DayCounter.currentDay(overworld);
        if (day < ModConfig.scaledDay(13) || !ModConfig.isEnabled("chunk_delete")) {
            ticksUntilRoll.clear();
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;   // never target the ghost itself
            }
            int remaining = ticksUntilRoll.getOrDefault(player.getUUID(), MIN_ROLL_TICKS);
            if (--remaining > 0) {
                ticksUntilRoll.put(player.getUUID(), remaining);
                continue;
            }
            ticksUntilRoll.put(player.getUUID(), MIN_ROLL_TICKS
                    + overworld.getRandom().nextInt(MAX_ROLL_TICKS - MIN_ROLL_TICKS + 1));
            if (overworld.getRandom().nextFloat() < ModConfig.chance("chunk_delete", DELETE_CHANCE)) {
                deleteChunkNear(player);
            }
        }
    }

    /** Dev/test hook — delete one chunk near every player right now,
     *  bypassing the day-13 gate and the roll timer. Dispatched by
     *  {@code /noname event play chunk_delete}. */
    public static void triggerForEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            deleteChunkNear(player);
        }
    }

    /** Dev/test hook — cancel the armed deletion rolls. Used by
     *  {@code /noname event stopall}. */
    public static void stopAll() {
        ticksUntilRoll.clear();
    }

    /**
     * Picks the chunk 7-13 chunks away in the player's walking (or looking)
     * direction and voids it.
     */
    private static void deleteChunkNear(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = direction(player);
        ChunkPos target = pickTargetChunk(player, dir);
        if (target == null) {
            return;
        }
        // Never delete the chunk the world spawns into — respawning players
        // must not fall into the void.
        if (target.equals(new ChunkPos(level.getSharedSpawnPos()))) {
            return;
        }
        LevelChunk chunk = level.getChunk(target.x, target.z);
        deleteChunk(level, chunk);
    }

    /** {@return the player's horizontal movement direction, or the direction
     *  they are looking when standing still; never a zero vector} */
    private static Vec3 direction(ServerPlayer player) {
        Vec3 motion = player.getDeltaMovement();
        if (motion.horizontalDistanceSqr() > 1.0E-4D) {
            return new Vec3(motion.x, 0.0D, motion.z).normalize();
        }
        Vec3 look = player.getLookAngle();
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        if (horizontal > 1.0E-4D) {
            return new Vec3(look.x, 0.0D, look.z).normalize();
        }
        // Looking straight up/down (or gliding) — any direction is fine.
        double angle = player.serverLevel().getRandom().nextDouble() * Math.PI * 2.0D;
        return new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
    }

    /**
     * {@return the chunk containing the point {@code dist} chunks away from
     * the player along {@code dir}, or {@code null} when it cannot be made to
     * land within {@link #MIN_CHUNK_DISTANCE}..{@link #MAX_CHUNK_DISTANCE}}
     */
    private static ChunkPos pickTargetChunk(ServerPlayer player, Vec3 dir) {
        ChunkPos playerChunk = player.chunkPosition();
        for (int attempt = 0; attempt < PICK_ATTEMPTS; attempt++) {
            double dist = MIN_CHUNK_DISTANCE
                    + player.serverLevel().getRandom().nextDouble()
                    * (MAX_CHUNK_DISTANCE - MIN_CHUNK_DISTANCE);
            ChunkPos candidate = chunkAt(player, dir, dist);
            double chunkDistance = Math.hypot(
                    candidate.x - playerChunk.x, candidate.z - playerChunk.z);
            if (chunkDistance >= MIN_CHUNK_DISTANCE && chunkDistance <= MAX_CHUNK_DISTANCE) {
                return candidate;
            }
        }
        // The chunk containing a point 10 chunks out is always within
        // [9, 11] chunks of the player — always inside the allowed range.
        return chunkAt(player, dir, FALLBACK_CHUNK_DISTANCE);
    }

    /** {@return the chunk containing the point {@code distChunks} chunks
     *  away from the player along {@code dir}} */
    private static ChunkPos chunkAt(ServerPlayer player, Vec3 dir, double distChunks) {
        double blocks = distChunks * 16.0D;
        int x = Math.floorDiv((int) Math.floor(player.getX() + dir.x * blocks), 16);
        int z = Math.floorDiv((int) Math.floor(player.getZ() + dir.z * blocks), 16);
        return new ChunkPos(x, z);
    }

    /**
     * Deletes the chunk without lag: removes its entities and block
     * entities, swaps every non-air section's paletted container for an
     * all-air one (one operation per section), zeroes the heightmaps,
     * re-propagates sky light on the light thread and re-sends the chunk to
     * every player tracking it.
     */
    private static void deleteChunk(ServerLevel level, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();

        // Everything that is not a player falls into the void — remove it.
        AABB box = new AABB(
                pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                pos.getMaxBlockX() + 1, level.getMaxBuildHeight(), pos.getMaxBlockZ() + 1);
        for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            entity.discard();
        }

        // Block entities (chests, signs, ...) — stop them ticking entirely.
        for (BlockPos blockPos : List.copyOf(chunk.getBlockEntities().keySet())) {
            level.removeBlockEntity(blockPos);
        }

        // The void: replace every non-air section's states with a fresh
        // all-air container. 24 palette swaps max — no per-block work.
        BlockState air = Blocks.AIR.defaultBlockState();
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir()) {
                continue;
            }
            sections[i] = new LevelChunkSection(
                    new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, air,
                            PalettedContainer.Strategy.SECTION_STATES),
                    section.getBiomes());
        }

        // The void has no blocks: zero every heightmap so spawning logic
        // never uses the old terrain heights.
        for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            Heightmap heightmap = entry.getValue();
            heightmap.setRawData(chunk, entry.getKey(),
                    new long[heightmap.getRawData().length]);
        }

        // Sky light: rescan the (now empty) columns and re-propagate light
        // asynchronously on the light thread — the hole lights up without
        // hitching the game, and clients receive the light updates through
        // the normal light pipeline.
        chunk.initializeLightSources();
        level.getChunkSource().getLightEngine().propagateLightSources(pos);

        chunk.setUnsaved(true);

        // Send the voided chunk to every player tracking it.
        ClientboundLevelChunkWithLightPacket packet =
                new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(pos, false)) {
            player.connection.send(packet);
        }
    }
}
