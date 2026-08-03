package dev.noname;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Locale;
import java.util.Random;

/**
 * Chatting with the ghost player. From day 3 on — 10 seconds after the
 * day-3 ghost player joins the tab list — the ghost starts "answering" the
 * player in chat. Every phrase it reacts to has five variations it matches
 * exactly (punctuation stripped, case-insensitive); the want/mean pair is
 * decided by the last word of the message. The ghost only replies 2 to 3
 * seconds after the player speaks:
 *
 * <pre>
 * "hello"            → "err.noresponse"
 * "who are you"      → "" (empty)
 * "what do you want" → "skin"
 * "what do you mean" → "flesh"    (only after "what do you want" was asked)
 * "flesh"            → "stop"     (on the 3rd repeat: the speaker dies on the
 *                                  spot and is kicked 0.3 s later with
 *                                  "you hypocrite")
 * "where are you"    → "dead"
 * "come here"        → the speaker dies, killed by "unknown"
 * </pre>
 *
 * <p>On day 4 the player is unable to talk with the ghost — no replies at
 * all.
 */
public final class GhostChatHandler {

    /** Delay between the ghost joining and the dialogue opening, in ticks —
     *  10 seconds. */
    private static final int DIALOGUE_OPEN_DELAY_TICKS = 20 * 10;

    /** Reply delay range, in ticks — between 2 and 3 seconds. */
    private static final int REPLY_DELAY_MIN_TICKS = 20 * 2;
    private static final int REPLY_DELAY_MAX_TICKS = 20 * 3;

    /** How many times "flesh" must be said before the ghost kills the
     *  speaker and kicks them. */
    private static final int FLESH_REPEAT_LIMIT = 3;

    /** Delay between that death and the kick, in ticks — 0.3 seconds. */
    private static final int KICK_DELAY_TICKS = 20 * 3 / 10;

    /** "hello" and its five variations. */
    private static final String[] HELLO_PHRASES = {
            "hello", "hi", "hey", "hiya", "greetings"};

    /** "who are you" and its five variations. */
    private static final String[] WHO_ARE_YOU_PHRASES = {
            "who are you", "who are u", "who r you", "what are you",
            "identify yourself"};

    /** "what do you want" and its five variations. */
    private static final String[] WHAT_DO_YOU_WANT_PHRASES = {
            "what do you want", "what do u want", "what do you want from me",
            "whats your goal", "why are you here"};

    /** "what do you mean" and its five variations. */
    private static final String[] WHAT_DO_YOU_MEAN_PHRASES = {
            "what do you mean", "what do u mean", "what does that mean",
            "what do you mean by that", "explain"};

    /** "flesh" and its five variations. */
    private static final String[] FLESH_PHRASES = {
            "flesh", "meat", "gore", "organs", "viscera"};

    /** "where are you" and its five variations. */
    private static final String[] WHERE_ARE_YOU_PHRASES = {
            "where are you", "where r you", "where are u",
            "where are you now", "where r u"};

    /** "come here" and its five variations. */
    private static final String[] COME_HERE_PHRASES = {
            "come here", "come", "come to me", "come closer", "get over here"};

    private static final Random RANDOM = new Random();

    /** Remaining ticks before the dialogue opens; {@code -1} = not armed. */
    private static int ticksUntilDialogueOpen = -1;

    /** Whether the dialogue is open (10 s after the ghost joined). */
    private static boolean dialogueOpen = false;

    /** The replies waiting to be said, in order — each is broadcast 2 to 3
     *  seconds after the previous one. */
    private static final Deque<String> pendingReplies = new ArrayDeque<>();

    /** Remaining ticks before {@link #pendingReply} is broadcast. */
    private static int ticksUntilReply = -1;

    /** Player about to be kicked ("you hypocrite"), or {@code null}. */
    private static ServerPlayer playerToKick = null;

    /** Remaining ticks before the kick. */
    private static int ticksUntilKick = -1;

    /** How many times the player has said "flesh" so far. */
    private static int fleshCount = 0;

    /** Whether the player asked what the ghost wants (gates "what do you
     *  mean"). */
    private static boolean askedWhatDoYouWant = false;

    private GhostChatHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        // The dialogue opens 10 seconds after the ghost joins.
        if (!dialogueOpen && server.getPlayerList().getPlayer(FakePlayerUtil.FAKE_UUID) != null) {
            if (ticksUntilDialogueOpen < 0) {
                ticksUntilDialogueOpen = DIALOGUE_OPEN_DELAY_TICKS;
            }
            if (--ticksUntilDialogueOpen <= 0) {
                ticksUntilDialogueOpen = -1;
                dialogueOpen = true;
            }
        }

        // Broadcast the ghost's replies 2-3 seconds apart, in order.
        if (!pendingReplies.isEmpty()) {
            if (--ticksUntilReply <= 0) {
                String reply = pendingReplies.removeFirst();
                server.getPlayerList().broadcastSystemMessage(Component.literal(
                        "<" + FakePlayerUtil.FAKE_TAB_NAME + "> " + reply), false);
                if (!pendingReplies.isEmpty()) {
                    ticksUntilReply = REPLY_DELAY_MIN_TICKS + RANDOM.nextInt(
                            REPLY_DELAY_MAX_TICKS - REPLY_DELAY_MIN_TICKS + 1);
                }
            }
        }

        // Kick the flesh-repeater 0.3 seconds after the death.
        if (playerToKick != null) {
            if (--ticksUntilKick <= 0) {
                playerToKick.connection.disconnect(Component.literal("you hypocrite"));
                playerToKick = null;
            }
        }
    }

    /**
     * Dev/test hook — cancel a pending reply or kick and reset the
     * conversation state. Used by {@code /noname event stopall}. The dialogue
     * re-opens 10 s after the ghost is next seen.
     */
    public static void stopAll() {
        ticksUntilDialogueOpen = -1;
        dialogueOpen = false;
        pendingReplies.clear();
        ticksUntilReply = -1;
        playerToKick = null;
        ticksUntilKick = -1;
        fleshCount = 0;
        askedWhatDoYouWant = false;
    }

    /**
     * Reads the player's chat messages and answers them as the ghost.
     * Registered against {@code ServerMessageEvents.ALLOW_CHAT_MESSAGE} — the
     * messages themselves always pass through; they are only observed here.
     */
    public static boolean onChatMessage(PlayerChatMessage message, ServerPlayer player,
                                        ChatType.Bound params) {
        // A pending reply never blocks new questions — they are queued, so
        // "what do you want" followed immediately by "what do you mean" is
        // answered in order.
        if (!dialogueOpen || player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
            return true;
        }
        // Day 4: the player is unable to talk with the ghost.
        if (DayCounter.currentDay(player.serverLevel()) == 4) {
            return true;
        }

        String text = normalize(message.decoratedContent().getString());

        // "flesh" (and repeats) — checked first, before the other phrases.
        if (matches(text, FLESH_PHRASES)) {
            fleshCount++;
            if (fleshCount >= FLESH_REPEAT_LIMIT) {
                fleshCount = 0;
                player.kill();
                playerToKick = player;
                ticksUntilKick = KICK_DELAY_TICKS;
            } else {
                scheduleReply("stop");
            }
            return true;
        }

        // "come here" — immediate death, killed by "unknown".
        if (matches(text, COME_HERE_PHRASES)) {
            killByUnknown(player);
            return true;
        }

        if (matches(text, HELLO_PHRASES)) {
            scheduleReply("err.noresponse");
        } else if (matches(text, WHO_ARE_YOU_PHRASES)) {
            scheduleReply("");
        } else {
            // The want/mean pair is decided by the ending-word subchecker
            // (the full phrases are near-identical), with the exact phrase
            // match as a fallback net. The two checks are mutually
            // exclusive, so the gated mean question can never be swallowed
            // by a wants variation.
            boolean wants = matches(text, WHAT_DO_YOU_WANT_PHRASES)
                    || endsWithVariant(text, "want");
            boolean means = matches(text, WHAT_DO_YOU_MEAN_PHRASES)
                    || endsWithVariant(text, "mean");
            if (means && !wants) {
                // "what do you mean" is only answered once the wants question
                // was asked; before that it gets no reply at all.
                if (askedWhatDoYouWant) {
                    scheduleReply("flesh");
                }
            } else if (wants) {
                askedWhatDoYouWant = true;
                scheduleReply("skin");
            } else if (matches(text, WHERE_ARE_YOU_PHRASES)) {
                scheduleReply("dead");
            }
        }
        return true;
    }

    /** Queues the ghost's reply — the first one goes out 2-3 seconds from
     *  now, each following one 2-3 seconds after the previous. */
    private static void scheduleReply(String content) {
        if (pendingReplies.isEmpty()) {
            ticksUntilReply = REPLY_DELAY_MIN_TICKS
                    + RANDOM.nextInt(REPLY_DELAY_MAX_TICKS - REPLY_DELAY_MIN_TICKS + 1);
        }
        pendingReplies.addLast(content);
    }

    /** Kills the player with the mod's "unknown" damage type, so the death
     *  message reads "... was killed by unknown". */
    private static void killByUnknown(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Registry<DamageType> registry =
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Holder<DamageType> holder = registry.getHolderOrThrow(ResourceKey.create(
                Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(Noname.MODID, "unknown")));
        player.hurt(new DamageSource(holder, null, null), Float.MAX_VALUE);
    }

    /** {@return true if the normalized input is exactly one of the phrase
     *  variations} */
    private static boolean matches(String input, String[] phrases) {
        for (String phrase : phrases) {
            if (input.equals(phrase)) {
                return true;
            }
        }
        return false;
    }

    /** Tiny subchecker: the last word of the message decides between the
     *  "want" and "mean" questions, because the full phrases are
     *  near-identical. "want"/"watn"/"awnt" (same letters, any order) count
     *  as "want"; "mean"/"mena"/"eman" count as "mean". */
    private static boolean endsWithVariant(String text, String target) {
        String word = lastWord(text);
        return !word.isEmpty() && sameLetters(word, target);
    }

    /** {@return the last word of the input, or "" if it has none} */
    private static String lastWord(String text) {
        int space = text.lastIndexOf(' ');
        return space < 0 ? text : text.substring(space + 1);
    }

    /** {@return true if both words contain exactly the same letters, in any
     *  order — "watn" and "awnt" are "want"} */
    private static boolean sameLetters(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        char[] ac = a.toCharArray();
        char[] bc = b.toCharArray();
        Arrays.sort(ac);
        Arrays.sort(bc);
        return Arrays.equals(ac, bc);
    }

    /** Strips every non-letter/non-digit character, lowercases and collapses
     *  whitespace, so "HELLO!", "who r u?" and "come here." all match
     *  cleanly. */
    private static String normalize(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ') {
                sb.append(c);
            }
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }
}
