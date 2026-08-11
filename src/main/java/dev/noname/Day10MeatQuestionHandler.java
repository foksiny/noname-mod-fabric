package dev.noname;

import dev.noname.config.ModConfig;
import dev.noname.network.ModPayloads;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The day-10 question: the moment day 10 starts (the day 9 → 10 transition
 * while the server is running — joining a world that is already on day 10
 * never replays it), every online real player gets a real desktop window
 * (title {@code question}, text {@code do you like meat}) with a yes and a
 * no button. The answer decides the player's fate:
 * <ul>
 *   <li><b>yes</b> — the player takes damage down to exactly half a heart
 *       (computed from their current health, so absorption or extra max
 *       hearts can't change the outcome). Nothing else happens.</li>
 *   <li><b>no</b> — the player permanently loses half of their max hearts
 *       (the {@link Attributes#MAX_HEALTH} base value is halved at runtime,
 *       so it always matches the player's actual heart count and persists in
 *       their NBT), then gets kicked from the world with the reason
 *       {@code you don't like me then.} On the next join, the punishment
 *       lands: every leaf of every tree within 100 blocks of where they
 *       rejoin is deleted, half of their player-made structures
 *       ({@link PlayerPlacedBlocks}) in that radius is deleted, half of
 *       their inventory slots are wiped out of existence, and an oak sign
 *       stands 2 blocks in front of them reading
 *       {@code i see that you don't like me then.}</li>
 * </ul>
 *
 * <p>Each player's state is saved per-world in {@link NonameSavedData}
 * (keyed by player UUID): {@link #STATE_PENDING} = asked but not yet
 * answered (a player who logs out with the window up is asked again on
 * rejoin), {@link #STATE_PUNISH} = answered "no", punishment still owed (it
 * is applied exactly once, on the next join, even across server restarts).
 *
 * <p>Closing the window without pressing "yes" counts as "no" (the client
 * sends that), so the event can never hang. The heavy part of the
 * punishment — the leaf scan and the structure deletions — is spread over
 * ticks so the server never stalls: one chunk per tick for the leaves (a
 * few full passes over the 13×13-chunk radius, so chunks that load in late
 * get cleaned too) and 50 block deletions per tick.
 *
 * <p>Like all the other day-gated handlers the trigger day honours
 * {@link ModConfig}: {@link ModConfig#scaledDay(long)} shifts it with the
 * speed level, and {@link ModConfig#isEnabled(String)} can turn the whole
 * event off.
 */
public final class Day10MeatQuestionHandler {

    /** No question asked, no punishment owed. */
    private static final byte STATE_NONE = 0;

    /** The question was shown but not answered yet. */
    private static final byte STATE_PENDING = 1;

    /** The player answered "no" — the rejoin punishment is still owed. */
    private static final byte STATE_PUNISH = 2;

    /** Radius in blocks around the player inside which leaves and
     *  player-made structures are deleted on rejoin. */
    private static final double PUNISH_RADIUS = 100.0D;

    /** How many full passes the leaf scan makes over the radius (one chunk
     *  per tick): 6 passes × 169 chunks ≈ 50 seconds, long enough for the
     *  chunks around the spawn point to finish loading. */
    private static final int LEAF_SCAN_PASSES = 6;

    /** How many player-placed blocks are deleted per tick. */
    private static final int STRUCTURES_PER_TICK = 50;

    /** The sign placed 2 blocks in front of the player on rejoin. */
    private static final String SIGN_TEXT = "i see that you don't like me then.";

    /** How many blocks in front of the player the sign stands. */
    private static final int SIGN_DISTANCE = 2;

    /** The kick reason of the "no" branch. */
    private static final Component KICK_REASON = Component.literal("you don't like me then.");

    /** The day observed on the previous server tick, so the event fires
     *  exactly on the day 9 → 10 transition while the server is running.
     *  {@link Long#MIN_VALUE} = no observation yet (the first tick only
     *  records the current day and never fires). */
    private static long lastSeenDay = Long.MIN_VALUE;

    /** Whether the once-per-session day-10 question already happened. */
    private static boolean done = false;

    /** Player UUID -> punishment currently being applied (leaf scan and
     *  structure deletions, spread over ticks). */
    private static final Map<UUID, Punishment> punishments = new HashMap<>();

    private Day10MeatQuestionHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        // Fire exactly when day 10 starts (the day 9 → 10 transition while
        // the server is running). The first tick of a session only records
        // the current day, so joining a world that is already on day 10
        // never replays the event.
        long day = DayCounter.currentDay(overworld);
        if (lastSeenDay == Long.MIN_VALUE) {
            lastSeenDay = day;
        } else if (lastSeenDay < ModConfig.scaledDay(10) && day >= ModConfig.scaledDay(10)
                && !done && ModConfig.isEnabled("day10_question")) {
            EventQueue.queueEvent("day10_question", () -> !done,
                    () -> start(server));
        }
        lastSeenDay = day;

        // Progress the ongoing punishments; finished ones drop out on their
        // own (each is bounded, so none can run forever).
        punishments.values().removeIf(punishment -> !punishment.tick(server));
    }

    /**
     * Join hook: applies an owed punishment exactly once, or re-asks the
     * question if the player left it unanswered.
     */
    public static void onPlayerJoin(ServerGamePacketListenerImpl handler, PacketSender sender,
                                    MinecraftServer server) {
        ServerPlayer player = handler.player;
        if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
            return;
        }
        byte state = savedData(server).getMeatQuestionState(player.getUUID());
        if (state == STATE_PUNISH) {
            savedData(server).setMeatQuestionState(player.getUUID(), STATE_NONE);
            applyPunishment(player);
        } else if (state == STATE_PENDING && ModConfig.isEnabled("day10_question")) {
            askPlayer(player);
        }
    }

    /**
     * Dev/test hook — ask every online real player right now, regardless of
     * the day. Dispatched by {@code /noname event play day10_question}. Does
     * not mark the event as done, so the natural day-10 trigger still fires
     * when day 10 arrives.
     */
    public static void triggerNow(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            askPlayer(player);
        }
    }

    /** Cancels an armed event. Used by {@code /noname event stopall}.
     *  Ongoing punishments are consequences, not armed events — they are
     *  left to finish. */
    public static void stopAll() {
        EventQueue.release("day10_question");
    }

    /**
     * Receives the client's answer (dispatched by the {@code
     * meat_question_answer} payload on the server thread). Only a player
     * currently waiting for an answer counts; anything else is ignored.
     */
    public static void onAnswer(ServerPlayer player, boolean yes) {
        if (!ModConfig.isEnabled("day10_question")) {
            return;
        }
        byte state = savedData(player.getServer()).getMeatQuestionState(player.getUUID());
        if (state != STATE_PENDING) {
            return;
        }
        if (yes) {
            savedData(player.getServer()).setMeatQuestionState(player.getUUID(), STATE_NONE);
            answerYes(player);
        } else {
            savedData(player.getServer()).setMeatQuestionState(player.getUUID(), STATE_PUNISH);
            answerNo(player);
        }
    }

    // ------------------------------------------------------------------
    // Asking

    /** Asks one player: marks them pending and pops the window on their
     *  client. */
    private static void askPlayer(ServerPlayer player) {
        savedData(player.getServer()).setMeatQuestionState(player.getUUID(), STATE_PENDING);
        ModPayloads.sendShowMeatQuestion(player);
    }

    /** The day-10 transition: ask every online real player, once. */
    private static void start(MinecraftServer server) {
        done = true;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            askPlayer(player);
        }
        server.sendSystemMessage(
                Component.literal("[Noname] Day-10 question asked: do you like meat"));
        EventQueue.release("day10_question");
    }

    // ------------------------------------------------------------------
    // The answers

    /** The "yes" branch: damage down to exactly half a heart (1 HP). The
     *  damage is computed from the player's current health, so it always
     *  leaves half a heart — and absorption hearts are clamped away
     *  afterwards so they can't soften the outcome. */
    private static void answerYes(ServerPlayer player) {
        float damage = player.getHealth() - 1.0F;
        if (damage > 0.0F) {
            player.hurt(player.damageSources().generic(), damage);
        }
        player.setHealth(Math.min(player.getHealth(), 1.0F));
    }

    /** The "no" branch: halve the player's max hearts permanently (the
     *  attribute base value is what NBT saves, so it survives deaths and
     *  restarts), then kick them with the punchline. */
    private static void answerNo(ServerPlayer player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(Math.max(1.0D, maxHealth.getBaseValue() / 2.0D));
        }
        player.connection.disconnect(KICK_REASON);
    }

    // ------------------------------------------------------------------
    // The rejoin punishment

    /** Applies the full punishment once, at the player's join position:
     *  half the inventory wiped, the sign in front, then the leaves and
     *  structures over the following ticks. */
    private static void applyPunishment(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();

        wipeHalfInventory(player);
        placeSign(player);

        // Half of the player-made structures inside the radius, picked at
        // random; positions whose block no longer matches are skipped when
        // the deletions run.
        List<BlockPos> placed = PlayerPlacedBlocks.placedWithin(level, center, PUNISH_RADIUS);
        int toDelete = (placed.size() + 1) / 2;
        RandomSource rng = level.getRandom();
        Set<BlockPos> victims = new HashSet<>();
        while (victims.size() < toDelete) {
            victims.add(placed.get(rng.nextInt(placed.size())));
        }
        punishments.put(player.getUUID(),
                new Punishment(level.dimension(), center, new ArrayList<>(victims)));
    }

    /** Wipes exactly half (rounded up) of the occupied inventory slots —
     *  main, armor and offhand — completely out of existence. */
    private static void wipeHalfInventory(ServerPlayer player) {
        var inv = player.getInventory();
        List<Integer> occupied = new ArrayList<>();
        for (int i = 0; i < inv.items.size(); i++) {
            if (!inv.items.get(i).isEmpty()) {
                occupied.add(i);
            }
        }
        for (int i = 0; i < inv.armor.size(); i++) {
            if (!inv.armor.get(i).isEmpty()) {
                occupied.add(100 + i);
            }
        }
        if (!inv.offhand.get(0).isEmpty()) {
            occupied.add(200);
        }
        if (occupied.isEmpty()) {
            return;
        }

        RandomSource rng = player.getRandom();
        int toWipe = (occupied.size() + 1) / 2;
        Set<Integer> wiped = new HashSet<>();
        while (wiped.size() < toWipe) {
            wiped.add(occupied.get(rng.nextInt(occupied.size())));
        }
        for (int slot : wiped) {
            if (slot >= 200) {
                inv.offhand.set(0, ItemStack.EMPTY);
            } else if (slot >= 100) {
                inv.armor.set(slot - 100, ItemStack.EMPTY);
            } else {
                inv.items.set(slot, ItemStack.EMPTY);
            }
        }
        inv.setChanged();
    }

    /** Places an oak standing sign 2 blocks in front of the player (their
     *  facing direction), text side toward them, reading
     *  {@value #SIGN_TEXT}. Tries the exact spot first, then a block closer
     *  or further away, in case the exact spot is blocked. */
    private static void placeSign(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 facing = Day4KnifeHandler.facing(player.getYRot());
        // Standing-sign rotation is a 1/16 turn; the sign's text faces
        // {@code rotation * 22.5°} (0 = south). Facing the player means the
        // opposite of their own facing: yaw + 180° = rotation + 8.
        int rotation = (Math.round(player.getYRot() / 22.5F) + 8) & 15;
        for (int distance : new int[]{SIGN_DISTANCE, 1, 3}) {
            int x = (int) Math.floor(player.getX() + facing.x * distance);
            int z = (int) Math.floor(player.getZ() + facing.z * distance);
            if (tryPlaceSignAt(level, x, z, rotation)) {
                return;
            }
        }
    }

    /** Tries one spot: the sign must stand on solid ground, out of the way
     *  of whatever occupies the spot. {@code true} when placed. */
    private static boolean tryPlaceSignAt(ServerLevel level, int x, int z, int rotation) {
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (groundY <= level.getMinBuildHeight() + 1) {
            return false;
        }
        BlockPos pos = new BlockPos(x, groundY + 1, z);
        if (!level.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isRedstoneConductor(level, below)) {
            return false;
        }

        level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, rotation), 3);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            String[] lines = SignPlacerHandler.wrapMessage(SIGN_TEXT);
            sign.updateText(text -> {
                SignText result = text;
                for (int i = 0; i < lines.length && i < 4; i++) {
                    result = result.setMessage(i, Component.literal(lines[i]));
                }
                return result;
            }, true);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // The tick-spread deletion work

    /**
     * One player's in-progress rejoin punishment: deletes the chosen half
     * of their player-placed blocks ({@value #STRUCTURES_PER_TICK} per
     * tick) and scans the {@value #PUNISH_RADIUS}-block radius for leaves
     * chunk by chunk (one chunk per tick) for {@value #LEAF_SCAN_PASSES}
     * full passes, so chunks that load in late get cleaned too. Finishes on
     * its own — no external release needed.
     */
    private static final class Punishment {

        private final ResourceKey<Level> dimension;
        private final BlockPos center;
        private final List<BlockPos> structures;
        private final int chunkSpan;
        private final int chunkX0;
        private final int chunkZ0;
        private final int totalChunks;
        private int chunkIndex;
        private int passesLeft;

        Punishment(ResourceKey<Level> dimension, BlockPos center, List<BlockPos> structures) {
            this.dimension = dimension;
            this.center = center;
            this.structures = structures;
            int chunkRadius = (int) Math.ceil(PUNISH_RADIUS / 16.0D);
            this.chunkSpan = chunkRadius * 2 + 1;
            this.chunkX0 = (center.getX() >> 4) - chunkRadius;
            this.chunkZ0 = (center.getZ() >> 4) - chunkRadius;
            this.totalChunks = chunkSpan * chunkSpan;
            this.passesLeft = LEAF_SCAN_PASSES;
        }

        /** {@return false when the punishment is fully done} */
        boolean tick(MinecraftServer server) {
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                return false;
            }

            // Half of the player-made structures: a bounded batch per tick
            // so the block-update packet flood stays gentle even for huge
            // builds.
            int budget = STRUCTURES_PER_TICK;
            while (budget > 0 && !structures.isEmpty()) {
                BlockPos pos = structures.remove(structures.size() - 1);
                if (PlayerPlacedBlocks.isPlaced(level, pos)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
                budget--;
            }

            // All leaves in the radius: one chunk per tick, a few full
            // passes over the chunk grid.
            if (passesLeft > 0) {
                if (chunkIndex >= totalChunks) {
                    chunkIndex = 0;
                    passesLeft--;
                }
                if (passesLeft > 0) {
                    scanOneChunk(level);
                    chunkIndex++;
                }
            }
            return !structures.isEmpty() || passesLeft > 0;
        }

        /** Scans one chunk column by column and deletes every leaf inside
         *  the horizontal radius. */
        private void scanOneChunk(ServerLevel level) {
            int cx = chunkX0 + chunkIndex % chunkSpan;
            int cz = chunkZ0 + chunkIndex / chunkSpan;
            BlockPos corner = new BlockPos(cx << 4, level.getMinBuildHeight(), cz << 4);
            if (!level.isLoaded(corner)) {
                return;
            }
            double radiusSq = PUNISH_RADIUS * PUNISH_RADIUS;
            int minY = level.getMinBuildHeight();
            int maxY = level.getMaxBuildHeight();
            for (int dx = 0; dx < 16; dx++) {
                int x = (cx << 4) + dx;
                long offX = x - center.getX();
                for (int dz = 0; dz < 16; dz++) {
                    int z = (cz << 4) + dz;
                    long offZ = z - center.getZ();
                    if (offX * offX + offZ * offZ > radiusSq) {
                        continue; // column outside the radius
                    }
                    for (int y = minY; y < maxY; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        if (state.is(BlockTags.LEAVES)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------

    private static NonameSavedData savedData(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                NonameSavedData.factory(), NonameSavedData.ID);
    }
}
