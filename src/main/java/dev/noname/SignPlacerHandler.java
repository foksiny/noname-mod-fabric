package dev.noname;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Day-9+ creepy signs. Every 2-4 minutes spent on day 9 or later, there is a
 * 15% chance that a sign appears near each player — standing on the open
 * ground within a 5-block radius, just outside the player's field of view
 * (behind them, never under a roof or inside a wall), so it stays out of
 * sight until the player turns around and spots it easily — carrying one of
 * the creeping messages.
 *
 * <p>Purely server-side: the block placement and the sign text sync to every
 * client through the normal block/block-entity packets. The dev command
 * {@code /noname event play sign_place} places one sign near every online
 * player for testing.
 */
public final class SignPlacerHandler {

    /** Max distance (blocks) from the player a sign may appear. */
    public static final double MAX_PLACE_RADIUS = 5.0D;

    /** Roll cadence: 2-4 minutes (2400-4800 ticks). */
    private static final int MIN_INTERVAL_TICKS = 20 * 60 * 2;
    private static final int MAX_INTERVAL_TICKS = 20 * 60 * 4;

    /** Probability that a roll actually places a sign. */
    private static final float PLACE_CHANCE = 0.15F;

    /** How many random spots are tried before giving up. */
    private static final int MAX_ATTEMPTS = 12;

    /** Signs never spawn closer than this (blocks). */
    private static final double MIN_PLACE_RADIUS = 2.0D;

    /** Signs are wrapped to lines of at most this many characters. */
    private static final int MAX_LINE_CHARS = 16;

    /** The messages the signs carry. */
    private static final String[] MESSAGES = {
            "i feel like i'm burning",
            "it hurts so bad",
            "i think i should stop",
            "i can't stop, and i'm bleeding so much",
            "why didn't you help me?",
            "you should have done more",
            "i look beautiful! why don't you want to look like me?",
            "it tastes like porkchop :)",
            "err.flesh.aXQncyBzbyByZWQgYW5kIHN0aWNreQ==",
    };

    /** Ticks until the next roll; reset whenever the player is not on day 9+,
     *  so the first attempt happens 2-4 minutes into day 9. */
    private static int ticksUntilNextRoll = MIN_INTERVAL_TICKS;

    private SignPlacerHandler() {
    }

    /** Server tick: rolls the chance and places signs. Registered against
     *  {@code ServerTickEvents.START_SERVER_TICK}. */
    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (DayCounter.currentDay(overworld) < 9) {
            ticksUntilNextRoll = MIN_INTERVAL_TICKS;
            return;
        }
        if (--ticksUntilNextRoll > 0) {
            return;
        }
        ticksUntilNextRoll = MIN_INTERVAL_TICKS
                + overworld.getRandom().nextInt(MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS + 1);
        if (overworld.getRandom().nextFloat() >= PLACE_CHANCE) {
            return;
        }
        placeOneNearEachPlayer(server);
    }

    /**
     * Dev/test hook — place one creepy sign near every online player right
     * now, bypassing the day-9 gate and the roll timer. Dispatched by
     * {@code /noname event play sign_place}.
     */
    public static void placeOneNearEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            tryPlaceSign(player);
        }
    }

    /** Dev/test hook — reset the roll timer (used by stopall). */
    public static void cancelArmed() {
        ticksUntilNextRoll = MIN_INTERVAL_TICKS;
    }

    /** Tries random spots behind the player until one works. */
    private static void tryPlaceSign(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource rng = level.getRandom();
        String message = MESSAGES[rng.nextInt(MESSAGES.length)];

        Vec3 look = player.getLookAngle();
        Vec3 lookXZ = new Vec3(look.x, 0.0D, look.z).normalize();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = MIN_PLACE_RADIUS
                    + rng.nextDouble() * (MAX_PLACE_RADIUS - MIN_PLACE_RADIUS);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
            Vec3 toXZ = new Vec3(x + 0.5 - player.getX(), 0.0D, z + 0.5 - player.getZ()).normalize();
            if (toXZ.dot(lookXZ) >= 0.0D) {
                continue; // in front of the player — they would notice
            }
            if (tryPlaceAt(level, x, z, player, message, rng)) {
                return;
            }
        }
    }

    /** Finds a spot on the ground, checks it is out of the player's sight
     *  and places the sign there. */
    private static boolean tryPlaceAt(ServerLevel level, int x, int z, ServerPlayer player,
                                      String message, RandomSource rng) {
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (groundY <= level.getMinBuildHeight() + 1) {
            return false;
        }
        if (level.getBlockState(new BlockPos(x, groundY, z)).getFluidState().is(FluidTags.WATER)) {
            return false;
        }

        BlockPos pos = findGroundSpot(level, x, z, groundY);
        if (pos == null) {
            return false;
        }
        BlockState state = Blocks.OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, rng.nextInt(16));

        // Out of sight, yet easy to find: the sign must not be inside the
        // player's forward half-sphere, but it stands on open ground right
        // behind them — a glance or a few steps reveals it.
        Vec3 toPos = Vec3.atCenterOf(pos).subtract(player.getEyePosition()).normalize();
        if (toPos.dot(player.getLookAngle()) >= 0.0D) {
            return false;
        }

        level.setBlock(pos, state, 3);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            String[] lines = wrapMessage(message);
            sign.updateText(signText -> {
                SignText text = signText;
                for (int i = 0; i < lines.length && i < 4; i++) {
                    text = text.setMessage(i, Component.literal(lines[i]));
                }
                return text;
            }, true);
        }
        return true;
    }

    /** A standing-sign spot directly on the ground, or {@code null}. */
    private static BlockPos findGroundSpot(ServerLevel level, int x, int z, int groundY) {
        BlockPos pos = new BlockPos(x, groundY + 1, z);
        if (!level.getBlockState(pos).canBeReplaced()) {
            return null;
        }
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isRedstoneConductor(level, below)) {
            return null;
        }
        return pos;
    }

    /** Wraps a message into lines of at most {@value #MAX_LINE_CHARS}
     *  characters, breaking at spaces (hard-breaking long words). */
    private static String[] wrapMessage(String message) {
        if (message.length() <= MAX_LINE_CHARS) {
            return new String[]{message};
        }
        List<String> lines = new ArrayList<>();
        String remaining = message;
        while (remaining.length() > MAX_LINE_CHARS) {
            int cut = remaining.lastIndexOf(' ', MAX_LINE_CHARS);
            if (cut <= 0) {
                cut = MAX_LINE_CHARS;
            }
            lines.add(remaining.substring(0, cut));
            remaining = remaining.substring(cut).trim();
        }
        lines.add(remaining);
        return lines.toArray(new String[0]);
    }
}
