package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The day-4 choice: the moment day 4 starts (the day 3 → 4 transition while
 * the server is running — joining a world that is already on day 4 never
 * replays it), the fake player waits 5 seconds and then speaks a single
 * question in chat:
 *
 * <pre>"do you think a ????? is a good choice? yes or no"</pre>
 *
 * <p>(the ????? render as 5 rapidly-changing glitch characters).
 *
 * <p>The next chat message a real player sends is read as the answer
 * (punctuation stripped, case-insensitive):
 * <ul>
 *   <li><b>yes</b> (yes, yeah, yea, yep, yup, sure) — the player is hit from
 *       the back: damage, a violent knock forward in the direction they were
 *       facing, a tearing sound and a burst of blood; every mob within 16
 *       blocks around them dies at the same moment.</li>
 *   <li><b>no</b> (no, nope, nah, n, nay, no way) — the fake player answers
 *       "ok, thank you i guess" and the player receives the Knife, a one-use
 *       instant-kill weapon that breaks as soon as it kills something.</li>
 * </ul>
 * Anything else is ignored and the player can answer again. The event fires
 * once per session (day 4 or later joined directly never replays it).
 *
 * <p>Day 4 also runs three scheduled beats while the server is running, fired
 * when the tick-of-day crosses each threshold (joining mid-day-4 never
 * replays beats that already passed):
 * <ul>
 *   <li><b>25%</b> — the fake player says "i'm feeling hungry", a cave sound
 *       plays at every player, they get the darkness effect for 3 seconds and
 *       their hunger drops to 0.</li>
 *   <li><b>50%</b> — the fake player says "its really red and pink lol" and
 *       every player receives a flesh block.</li>
 *   <li><b>75%</b> — a window pops up on every player's client saying
 *       "help me".</li>
 * </ul>
 */
public final class Day4KnifeHandler {

    /** Delay between the event firing and the fake player's question, in
     *  server ticks — 5 seconds. */
    private static final int QUESTION_DELAY_TICKS = 20 * 5;

    /** Tick-of-day at which the 25% "i'm feeling hungry" beat fires
     *  (24000 ticks per day). */
    private static final int HUNGER_TICK_OF_DAY = 24000 / 4;

    /** Tick-of-day at which the 50% "its really red and pink lol" beat fires. */
    private static final int RED_PINK_TICK_OF_DAY = 24000 / 2;

    /** Tick-of-day at which the 75% "help me" window fires. */
    private static final int HELP_TICK_OF_DAY = 24000 * 3 / 4;

    /** How long the darkness effect from the 25% beat lasts, in ticks. */
    private static final int DARKNESS_DURATION_TICKS = 20 * 3;

    /** The fake player's line at 25% of day 4. */
    private static final String HUNGRY_LINE = "i'm feeling hungry";

    /** The fake player's line at 50% of day 4. */
    private static final String RED_PINK_LINE = "its really red and pink lol";

    /** Radius in blocks around the answering player inside which every mob
     *  dies when the answer is "yes". */
    private static final double KILL_RADIUS = 16.0D;

    /** Damage taken from the hit from the back, in half-hearts — 4 hearts,
     *  painful but survivable. */
    private static final float BACK_HIT_DAMAGE = 8.0F;

    /** How hard the back hit throws the player forward (blocks/second). */
    private static final double BACK_HIT_PUSH = 1.4D;

    /** The fake player's question, with 5 rapidly-changing characters — the
     *  obfuscated style makes the client cycle random glyphs through them, the
     *  same glitch look corrupted Minecraft text has. */
    private static final Component QUESTION = Component.literal("do you think a ")
            .append(Component.literal("?????").withStyle(ChatFormatting.OBFUSCATED))
            .append(Component.literal(" is a good choice? yes or no"));

    /** The fake player's line when the answer is "no". */
    private static final String NO_ANSWER_LINE = "ok, thank you i guess";

    /** Accepted "yes" answers — "yes" plus five variations. */
    private static final Set<String> YES_ANSWERS = Set.of(
            "yes", "yeah", "yea", "yep", "yup", "sure");

    /** Accepted "no" answers — "no" plus five variations. */
    private static final Set<String> NO_ANSWERS = Set.of(
            "no", "nope", "nah", "n", "nay", "no way");

    /** The day observed on the previous server tick, so the event fires
     *  exactly on the day 3 → 4 transition while the server is running.
     *  {@link Long#MIN_VALUE} = no observation yet (the first tick only
     *  records the current day and never fires). */
    private static long lastSeenDay = Long.MIN_VALUE;

    /** Remaining ticks before the fake player speaks the question; {@code -1}
     *  = nothing armed (pre-day-4, already asked or stopped). */
    private static int ticksUntilQuestion = -1;

    /** Whether the fake player is currently waiting for an answer in chat. */
    private static boolean awaitingAnswer = false;

    /** Whether the once-per-session day-4 event already happened. */
    private static boolean done = false;

    /** The tick-of-day observed on the previous server tick while day 4 is
     *  running, so the 25%/50%/75% beats fire exactly when they are crossed.
     *  {@link Long#MIN_VALUE} = no observation yet (the first day-4 tick of a
     *  session only records the current time, so joining mid-day-4 never
     *  replays the beats that already passed). */
    private static long lastSeenTickOfDay = Long.MIN_VALUE;

    private Day4KnifeHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        // Fire exactly when day 4 starts (the day 3 → 4 transition while the
        // server is running). The first tick of a session only records the
        // current day, so joining a world that is already on day 4 never
        // replays the event.
        long day = DayCounter.currentDay(overworld);
        if (lastSeenDay == Long.MIN_VALUE) {
            lastSeenDay = day;
        } else if (lastSeenDay < ModConfig.scaledDay(4) && day >= ModConfig.scaledDay(4)
                && !done && ModConfig.isEnabled("day4_question")) {
            EventQueue.queueEvent("day4_knife", Day4KnifeHandler::shouldRunDay4Event,
                    () -> start(server));
        }
        lastSeenDay = day;

        // Count down to the fake player's question.
        if (ticksUntilQuestion >= 0) {
            tickQuestion(server);
        }

        // The day-4 scheduled beats: fire when the tick-of-day crosses 25%,
        // 50% and 75% while the server is running.
        long tickOfDay = overworld.getDayTime() % 24000L;
        if (day == ModConfig.scaledDay(4)) {
            if (lastSeenTickOfDay == Long.MIN_VALUE) {
                lastSeenTickOfDay = tickOfDay;
            } else {
                if (lastSeenTickOfDay < HUNGER_TICK_OF_DAY && tickOfDay >= HUNGER_TICK_OF_DAY
                        && ModConfig.isEnabled("day4_hungry")) {
                    triggerHungry(server);
                }
                if (lastSeenTickOfDay < RED_PINK_TICK_OF_DAY && tickOfDay >= RED_PINK_TICK_OF_DAY
                        && ModConfig.isEnabled("day4_red_pink")) {
                    triggerRedPink(server);
                }
                if (lastSeenTickOfDay < HELP_TICK_OF_DAY && tickOfDay >= HELP_TICK_OF_DAY
                        && ModConfig.isEnabled("day4_help")) {
                    triggerHelp(server);
                }
                lastSeenTickOfDay = tickOfDay;
            }
        } else {
            lastSeenTickOfDay = Long.MIN_VALUE;
        }
    }

    private static boolean shouldRunDay4Event() {
        return !done;
    }

    /**
     * Dev/test hook — fire the event right now, regardless of the day.
     * Dispatched by {@code /noname event play day4_question}. Does not mark
     * the event as done, so the natural day-4 trigger still fires when day 4
     * arrives.
     */
    public static void triggerNow(MinecraftServer server) {
        EventQueue.queueEvent("day4_knife", () -> true, () -> start(server));
    }

    /**
     * Dev/test hook — fire the 25% "i'm feeling hungry" beat right now,
     * regardless of the day. Dispatched by {@code /noname event play
     * day4_hungry}.
     */
    public static void triggerHungryNow(MinecraftServer server) {
        triggerHungry(server);
    }

    /**
     * Dev/test hook — fire the 50% "its really red and pink lol" beat right
     * now, regardless of the day. Dispatched by {@code /noname event play
     * day4_red_pink}.
     */
    public static void triggerRedPinkNow(MinecraftServer server) {
        triggerRedPink(server);
    }

    /**
     * Dev/test hook — cancel an armed question or an awaiting-answer state and
     * free the event lock. Used by {@code /noname event stopall}.
     */
    public static void stopAll() {
        ticksUntilQuestion = -1;
        awaitingAnswer = false;
        EventQueue.release("day4_knife");
    }

    /**
     * Reads the next chat message while the fake player awaits an answer.
     * Registered against {@code ServerMessageEvents.ALLOW_CHAT_MESSAGE} — the
     * message itself always passes through; it is only observed here.
     */
    public static boolean onChatMessage(PlayerChatMessage message, ServerPlayer player,
                                        ChatType.Bound params) {
        if (!awaitingAnswer || done || !ModConfig.isEnabled("day4_question")
                || player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
            return true;
        }
        String answer = normalize(message.decoratedContent().getString());
        if (YES_ANSWERS.contains(answer)) {
            awaitingAnswer = false;
            done = true;
            answerYes(player);
            EventQueue.release("day4_knife");
        } else if (NO_ANSWERS.contains(answer)) {
            awaitingAnswer = false;
            done = true;
            answerNo(player);
            EventQueue.release("day4_knife");
        }
        return true;
    }

    /** Counts down to the fake player's question and broadcasts it exactly
     *  once, then waits for an answer. */
    private static void tickQuestion(MinecraftServer server) {
        if (--ticksUntilQuestion > 0) {
            return;
        }
        ticksUntilQuestion = -1;
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("<" + FakePlayerUtil.FAKE_TAB_NAME + "> ").append(QUESTION),
                false);
        awaitingAnswer = true;
    }

    /** The "yes" branch: hit the player from the back and kill every mob
     *  around them. */
    private static void answerYes(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        // The blow comes from behind: damage plus a violent throw in the
        // direction the player was facing at the moment of the hit.
        player.hurt(player.damageSources().generic(), BACK_HIT_DAMAGE);
        Vec3 push = facing(player.getYRot());
        player.setDeltaMovement(
                push.x * BACK_HIT_PUSH, 0.35D, push.z * BACK_HIT_PUSH);
        player.hurtMarked = true;
        player.hasImpulse = true;

        // The tearing flesh sound and a blood burst at the player. The sound
        // is bound to the player entity itself so it follows them and can
        // never be walked away from.
        level.playSound(null, player,
                ModSounds.TEARING_FLESH, SoundSource.HOSTILE, 1.0F, 1.0F);
        level.sendParticles(ModParticles.BLOOD_DROP,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                200, 0.8D, 0.6D, 0.8D, 0.35D);

        // Every mob within 16 blocks around the player dies.
        AABB box = AABB.ofSize(player.position(),
                KILL_RADIUS * 2.0D, KILL_RADIUS * 2.0D, KILL_RADIUS * 2.0D);
        List<Entity> mobs = level.getEntities(player, box,
                e -> e instanceof LivingEntity
                        && !(e instanceof ServerPlayer)
                        && !e.isRemoved());
        for (Entity mob : mobs) {
            ((LivingEntity) mob).kill();
        }
    }

    /** The "no" branch: the fake player acknowledges and hands over the
     *  Knife. */
    private static void answerNo(ServerPlayer player) {
        player.serverLevel().getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("<" + FakePlayerUtil.FAKE_TAB_NAME + "> " + NO_ANSWER_LINE),
                false);
        ItemStack knife = new ItemStack(ModItems.KNIFE);
        if (!player.getInventory().add(knife)) {
            player.drop(knife, false);
        }
    }

    /** Arms the once-per-session event: 5 seconds, then the question. */
    private static void start(MinecraftServer server) {
        ticksUntilQuestion = QUESTION_DELAY_TICKS;
        server.sendSystemMessage(
                Component.literal("[Noname] Day-4 question armed: fake player speaks in 5 s"));
    }

    /** The 25% beat: the fake player complains about hunger, a cave sound
     *  plays at every player, darkness hits for 3 seconds and hunger drops
     *  to 0. */
    private static void triggerHungry(MinecraftServer server) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "<" + FakePlayerUtil.FAKE_TAB_NAME + "> " + HUNGRY_LINE), false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            player.serverLevel().playSound(null, player,
                    SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 2.0F, 1.0F);
            player.addEffect(new MobEffectInstance(
                    MobEffects.DARKNESS, DARKNESS_DURATION_TICKS, 0));
            player.getFoodData().setFoodLevel(0);
            player.getFoodData().setSaturation(0.0F);
        }
    }

    /** The 50% beat: the fake player remarks on the red and pink, and every
     *  player receives a flesh block. */
    private static void triggerRedPink(MinecraftServer server) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "<" + FakePlayerUtil.FAKE_TAB_NAME + "> " + RED_PINK_LINE), false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            ItemStack flesh = new ItemStack(ModBlocks.FLESH_BLOCK);
            if (!player.getInventory().add(flesh)) {
                player.drop(flesh, false);
            }
        }
    }

    /** The 75% beat: pop the "help me" window on every player's client. */
    private static void triggerHelp(MinecraftServer server) {
        NonameEvents.playOnServer(server, "day4_help");
    }

    /** Strips every non-letter/non-digit character, lowercases and collapses
     *  whitespace, so "YES!", "sure..." or "No Way" all match cleanly. */
    private static String normalize(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ') {
                sb.append(c);
            }
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    /** {@return the horizontal facing unit vector for a yaw in degrees, in
     *  vanilla's convention (yaw 0 = +Z, turning left rotates counterclock-
     *  wise when seen from above)} */
    private static Vec3 facing(float yRot) {
        double rad = Math.toRadians(yRot);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
    }
}
