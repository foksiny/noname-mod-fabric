package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Day-8+ block blink: every 2-3 minutes spent on day 8 or later, there is a
 * 30% chance per player that roughly 30% of the blocks in a 16x16x16 cube
 * around the player vanish at once, then all re-appear together a few
 * seconds later — as if the world glitches out for an instant, then snaps
 * back whole.
 *
 * <p>Only solid, breakable, non-flesh, non-bedrock blocks with no fluid and
 * no block entity are touched, and a position a player placed themselves
 * ({@link PlayerPlacedBlocks}) is skipped so their builds do not blink.
 * Every removed state is remembered and restored by the exact same
 * {@link BlockState}, so the terrain and structures return identical.
 *
 * <p>When the blocks blink out they play the block's own breaking particles
 * and breaking sound, and invisible light blocks are scattered through the
 * cube and flicker (random light levels every few ticks) while the blink
 * runs — the light looks bugged until everything snaps back.
 *
 * <p>The swaps happen through vanilla {@link ServerLevel#setBlock} with
 * client-update flags, so every player tracking the chunks sees the blocks
 * go and come back through the normal block-update pipeline (no custom
 * packet). The restore is scheduled on the same tick for all affected
 * positions, so the blocks come back at the same time.
 */
public final class BlockBlinkHandler {

    /** Roll cadence: 2-3 minutes (2400-3600 ticks). */
    private static final int MIN_ROLL_TICKS = 20 * 120;
    private static final int MAX_ROLL_TICKS = 20 * 180;

    /** Probability that a roll actually triggers a blink. */
    private static final float EVENT_CHANCE = 0.30F;

    /** Half-width of the cube around the player, in blocks — the cube is
     *  {@code (RADIUS * 2)³} so the side length is 16 blocks (16x16x16). */
    private static final int RADIUS = 8;

    /** How long the blocks stay gone before they all snap back at once:
     *  2-3 seconds (40-60 ticks). */
    private static final int MIN_BLINK_TICKS = 40;
    private static final int MAX_BLINK_TICKS = 60;

    /** Fraction of the candidate blocks that actually blink out. */
    private static final float BLINK_FRACTION = 0.30F;

    /** Hard cap on how many positions a single blink records, so a dense
     *  cube never produces a huge state map / packet burst. */
    private static final int MAX_BLINK_BLOCKS = 120;

    /** How many invisible light blocks are scattered around to make the
     *  light flicker while the blink runs. */
    private static final int LIGHT_BLOCK_COUNT = 10;

    /** Flicker cadence: re-randomise the light blocks every 4-8 ticks. */
    private static final int MIN_FLICKER_TICKS = 4;
    private static final int MAX_FLICKER_TICKS = 8;

    /** Player -> ticks until that player's next blink roll. */
    private static final Map<UUID, Integer> ticksUntilRoll = new HashMap<>();

    /** Player -> the blink currently running for that player (until the
     *  blocks snap back). */
    private static final Map<UUID, Blink> activeBlinks = new HashMap<>();

    private BlockBlinkHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long day = DayCounter.currentDay(overworld);

        // Day 8 is when the blinking starts; before that nothing rolls. If a
        // blink is mid-flight when the day drops back or the event gets
        // disabled, restore its blocks first — never leave them gone.
        if (day < ModConfig.scaledDay(8) || !ModConfig.isEnabled("block_blink")) {
            stopAll(server);
            return;
        }

        long now = server.getTickCount();

        // Finish any blink whose restore time has come: put every removed
        // state back at once so the blocks re-appear together. Uses the
        // blink's own dimension rather than the player's current level, so a
        // player who logged out mid-blink still gets their blocks back.
        activeBlinks.entrySet().removeIf(entry -> {
            if (now < entry.getValue().restoreTick) {
                return false;
            }
            ServerLevel level = server.getLevel(entry.getValue().dimension);
            if (level != null) {
                restore(level, entry.getValue());
            }
            return true;
        });

        // Flicker the bugged light of every still-running blink.
        for (Blink blink : activeBlinks.values()) {
            if (now < blink.nextFlickerTick) {
                continue;
            }
            ServerLevel level = server.getLevel(blink.dimension);
            if (level == null) {
                continue;
            }
            flickerLight(level, blink);
            blink.nextFlickerTick = now + MIN_FLICKER_TICKS
                    + level.getRandom().nextInt(MAX_FLICKER_TICKS - MIN_FLICKER_TICKS + 1);
        }

        // Roll the next blink for each player.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;   // never target the ghost itself
            }
            UUID uuid = player.getUUID();
            if (activeBlinks.containsKey(uuid)) {
                continue;   // a blink is already running for this player
            }
            int remaining = ticksUntilRoll.getOrDefault(uuid, nextRollInterval(overworld.getRandom()));
            if (remaining > 1) {
                ticksUntilRoll.put(uuid, remaining - 1);
                continue;
            }
            ticksUntilRoll.put(uuid, nextRollInterval(overworld.getRandom()));
            if (overworld.getRandom().nextFloat() < ModConfig.chance("block_blink", EVENT_CHANCE)) {
                startBlink(server, player);
            }
        }
    }

    /** Dev/test hook — blink the blocks around every online player right
     *  now, regardless of the roll timer and the day gate. Dispatched by
     *  {@code /noname event play block_blink}. */
    public static void triggerNow(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            startBlink(server, player);
        }
    }

    /** Dev/test hook — cancel everything: restore any blocks still gone,
     *  then drop the armed rolls. Used by {@code /noname event stopall}. */
    public static void stopAll(MinecraftServer server) {
        for (Blink blink : activeBlinks.values()) {
            ServerLevel level = server.getLevel(blink.dimension);
            if (level != null) {
                restore(level, blink);
            }
        }
        activeBlinks.clear();
        ticksUntilRoll.clear();
    }

    /** Random 2-3 minutes (2400-3600 ticks) until the next roll. */
    private static int nextRollInterval(RandomSource random) {
        return MIN_ROLL_TICKS + random.nextInt(MAX_ROLL_TICKS - MIN_ROLL_TICKS + 1);
    }

    /**
     * Starts one blink for the player: gathers the candidate blocks in a
     * 16x16x16 cube around them, picks ~30%, removes them now — with their
     * breaking particles and breaking sound — scatters invisible flickering
     * light blocks through the cube, and arms the restore for 2-3 seconds
     * later.
     */
    private static void startBlink(MinecraftServer server, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        BlockPos center = player.blockPosition();

        // Collect every blinkable block in a 16x16x16 cube around the
        // player (±8 on each axis, 16 blocks per side).
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int baseX = center.getX() - RADIUS;
        int baseY = center.getY() - RADIUS;
        int baseZ = center.getZ() - RADIUS;
        for (int dx = 0; dx < RADIUS * 2; dx++) {
            for (int dy = 0; dy < RADIUS * 2; dy++) {
                for (int dz = 0; dz < RADIUS * 2; dz++) {
                    cursor.set(baseX + dx, baseY + dy, baseZ + dz);
                    if (!isBlinkable(level, cursor)) {
                        continue;
                    }
                    candidates.add(cursor.immutable());
                }
            }
        }
        if (candidates.isEmpty()) {
            return;
        }

        // Pick ~30% of them (capped) to actually blink out.
        int count = Math.min(MAX_BLINK_BLOCKS,
                Math.max(1, Math.round(candidates.size() * BLINK_FRACTION)));
        java.util.Collections.shuffle(candidates, new java.util.Random(random.nextLong()));

        // Capture each original state *before* it is swapped to air, so the
        // exact same BlockState goes back at restore time. Each removal also
        // plays the block's breaking particles and breaking sound, so the
        // vanishing looks like a real break.
        Map<BlockPos, BlockState> removed = new HashMap<>(count);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int i = 0; i < count && i < candidates.size(); i++) {
            BlockPos pos = candidates.get(i);
            BlockState state = level.getBlockState(pos);
            removed.put(pos.immutable(), state);
            level.setBlock(pos, air, 3);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    24, 0.35, 0.35, 0.35, 0.15);
            level.playSound(null, pos, state.getSoundType().getBreakSound(),
                    SoundSource.BLOCKS, 1.0F, 0.8F + random.nextFloat() * 0.4F);
        }
        if (removed.isEmpty()) {
            return;
        }

        // "Bug out" the light: scatter invisible light blocks through the
        // cube (in air, away from the removed spots) and flicker their
        // levels while the blink runs.
        List<BlockPos> lightPositions = new ArrayList<>();
        int placed = 0;
        int attempts = 0;
        while (placed < LIGHT_BLOCK_COUNT && attempts < LIGHT_BLOCK_COUNT * 10) {
            attempts++;
            BlockPos pos = new BlockPos(
                    baseX + random.nextInt(RADIUS * 2),
                    baseY + random.nextInt(RADIUS * 2),
                    baseZ + random.nextInt(RADIUS * 2));
            if (!level.getBlockState(pos).isAir() || removed.containsKey(pos)) {
                continue;
            }
            level.setBlock(pos, lightState(random.nextInt(16)), 3);
            lightPositions.add(pos.immutable());
            placed++;
        }

        int duration = MIN_BLINK_TICKS
                + random.nextInt(MAX_BLINK_TICKS - MIN_BLINK_TICKS + 1);
        Blink blink = new Blink(level.dimension(), removed, server.getTickCount() + duration);
        blink.lightPositions.addAll(lightPositions);
        blink.nextFlickerTick = server.getTickCount();   // flicker right away
        activeBlinks.put(player.getUUID(), blink);
    }

    /** Randomises the light level of every light block of a running blink,
     *  which makes the area strobe until the blocks come back. */
    private static void flickerLight(ServerLevel level, Blink blink) {
        RandomSource random = level.getRandom();
        for (BlockPos pos : blink.lightPositions) {
            if (!level.getBlockState(pos).is(Blocks.LIGHT)) {
                continue;   // something replaced it — leave it alone
            }
            level.setBlock(pos, lightState(random.nextInt(16)), 3);
        }
    }

    /** {@return a light-block state emitting {@code level} light} */
    private static BlockState lightState(int level) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
    }

    /** Restores every removed state at once after the blink duration, and
     *  removes the flickering light blocks so the light un-bugs too. */
    private static void restore(ServerLevel level, Blink blink) {
        for (BlockPos pos : blink.lightPositions) {
            if (level.getBlockState(pos).is(Blocks.LIGHT)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        for (BlockPos pos : blink.positions()) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                continue;   // something else filled the spot — do not clobber it
            }
            level.setBlock(pos, blink.stateFor(pos), 3);
        }
    }

    /**
     * {@return true if the block at {@code pos} may blink} — solid, airless,
     * no fluid, no block entity, breakable, not bedrock or a flesh block,
     * and not one the player placed themselves.
     */
    private static boolean isBlinkable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.isSolidRender(level, pos)) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;   // unbreakable (bedrock, barrier, ...)
        }
        if (state.is(ModBlocks.FLESH_BLOCK) || state.is(ModBlocks.BLOOD_FLESH_BLOCK)) {
            return false;   // never undo the mod's own flesh growth
        }
        if (level.getBlockEntity(pos) != null) {
            return false;   // chests, signs, etc. keep their contents
        }
        if (PlayerPlacedBlocks.isPlaced(level, pos)) {
            return false;   // leave the player's own builds untouched
        }
        return true;
    }

    /** One running blink: every removed position and the original state to
     *  restore there, plus the flickering light blocks. */
    private static final class Blink {

        private final ResourceKey<Level> dimension;
        private final Map<BlockPos, BlockState> removed;
        private final long restoreTick;
        private final List<BlockPos> lightPositions = new ArrayList<>();
        private long nextFlickerTick;

        private Blink(ResourceKey<Level> dimension, Map<BlockPos, BlockState> removed,
                      long restoreTick) {
            this.dimension = dimension;
            this.removed = removed;
            this.restoreTick = restoreTick;
        }

        /** {@return the original state to put back at {@code pos}} */
        BlockState stateFor(BlockPos pos) {
            return removed.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        /** {@return every removed position} */
        Set<BlockPos> positions() {
            return removed.keySet();
        }
    }
}
