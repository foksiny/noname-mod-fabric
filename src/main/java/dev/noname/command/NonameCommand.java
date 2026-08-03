package dev.noname.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.noname.DayCounter;
import dev.noname.FakePlayerHandler;
import dev.noname.KilledAnimalsLootHandler;
import dev.noname.NonameEvents;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /noname} dev/test command. Two sub-trees:
 * <ul>
 *   <li><b>{@code /noname event play <name>}</b> — fire one of the mod's
 *       timed/handled events immediately, regardless of the day it would
 *       normally wait for. Each {@code <name>} is a separate literal node,
 *       so the client's tab-complete lists every available event.
 *   <li><b>{@code /noname event stopall}</b> — cancel any Noname effect
 *       currently running or armed (on every connected client and on the
 *       server): the creepy-bass stinger, the day-2 message, disc 11, the
 *       armed "it hurts to see" countdown, etc.
 *   <li><b>{@code /noname status}</b> — report the current state and
 *       progress: the day and time, the progress toward the ghost (day 3),
 *       whether the ghost has joined, the live "it hurts to see" and
 *       animal-loot countdowns, and the status of every day-gated feature.
 * </ul>
 *
 * <p>Purely a debug aid — the same effects gate themselves on day count
 * during normal play; this command just short-circuits the gate (or, for
 * {@code status}, reports it).
 */
public final class NonameCommand {

    /** Names of every event {@code /noname event play} accepts. Listed here
     *  so the help-line message and {@link #PLAY_USAGE} stay in sync with the
     *  literal sub-nodes below. */
    private static final String[] EVENT_NAMES = {
            "ghost_join",       // force-spawn the day-3 ghost player
            "it_hurts_to_see",   // play the post-ghost stinger immediately
            "loot_pile",         // scatter one animal-loot pile by each player
            "pillar",            // place one day-6+ bedrock pillar by each player
            "creepy_bass",       // client: render-distance drop + bass stinger
            "day2_message",      // client: "why don't you like it? :(" overlay
            "disc_11",           // client: play music disc 11
            "day5_flash",        // client: black "i can't stop doing it" flash
            "day8_sky",          // client: red sky, min render distance, ambience
            "day7_fake",         // fake player appears in front of you (then speaks)
            "day4_question",     // day-4 choice: fake player asks yes/no (hit vs knife)
            "day4_hungry",       // day-4 25%: fake line + cave sound + darkness + hunger 0
            "day4_red_pink",     // day-4 50%: fake line + flesh block
            "day4_help",         // client: "help me" window pops up
            "blood_death",       // tearing sound + blood burst at each player
            "hostile_clear",     // hostile mobs near each player vanish
            "flesh_tree",        // grow one flesh tree next to each player
            "named_mob",         // spawn one named animal in front of each player
            "sign_place",        // place one creepy sign near each player
            "door_creak",        // toggle the closest door near each player
            "day3_timeskip",     // day-3 midday -> sudden jump to night (19:00)
            "day6_static",       // day-6 midday -> static overlay + text sequence
            "day10_look",        // day-10+ lag event: look behind + fake player
            "day11_chest",       // day-11+ mystery chest above an oak plank
            "day5_pig",          // spawn an infected pig in front of each player
            "he_is_here",        // "." secret event: the friend runs at you
    };

    private static final int BAR_LENGTH = 20;

    private NonameCommand() {
    }

    public static void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher,
                                          CommandBuildContext registryAccess,
                                          Commands.CommandSelection environment) {
        var root = literal("noname")
                .then(literal("status").executes(NonameCommand::runStatus))
                .then(literal("event")
                        .then(buildPlayNode())
                        .then(literal("stopall")
                                .executes(NonameCommand::runStopAll)))
                .executes(NonameCommand::runRoot);
        dispatcher.register(root);
    }

    /**
     * Builds the {@code play} sub-tree as one literal child per event name,
     * so tab-complete lists every event for free.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildPlayNode() {
        var play = literal("play");
        for (String name : EVENT_NAMES) {
            play.then(literal(name).executes(ctx -> runPlay(ctx, name)));
        }
        play.executes(NonameCommand::runPlayWithoutName);
        return play;
    }

    private static int runPlay(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack src = ctx.getSource();
        NonameEvents.playOnServer(src.getServer(), name);
        src.sendSuccess(() -> Component.literal("[Noname] Playing event: " + name), true);
        return 1;
    }

    private static int runPlayWithoutName(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Noname] Usage: /noname event play <name> — known events: "
                        + String.join(", ", EVENT_NAMES)),
                false);
        return 0;
    }

    private static int runStopAll(CommandContext<CommandSourceStack> ctx) {
        NonameEvents.stopAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Noname] Stopping all running events..."), true);
        return 1;
    }

    private static int runStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();
        ServerLevel overworld = server.overworld();

        long dayTime = overworld.getDayTime();
        long day = dayTime / 24000L;
        long tickOfDay = dayTime % 24000L;

        StringBuilder sb = new StringBuilder();
        sb.append("[Noname] Status — day ").append(day)
                .append(", ").append(timeOfDay(tickOfDay)).append('\n');

        // Progress toward the ghost's arrival, as a fraction of the 3-day clock.
        double progress = Math.min(1.0, dayTime / (3.0 * 24000.0));
        sb.append("  Progress to ghost (day 3): ")
                .append(Math.round(progress * 100.0)).append("%  ")
                .append(progressBar(progress)).append('\n');

        sb.append("  Ghost player: ")
                .append(FakePlayerHandler.isGhostSpawned(server)
                        ? "spawned"
                        : "not spawned")
                .append('\n');
        sb.append("  It hurts to see: ")
                .append(countdown(FakePlayerHandler.getItHurtsRemainingTicks(),
                        "fires 60 s after the ghost joins"))
                .append('\n');
        if (FakePlayerHandler.allGhostLinesSent(server)) {
            sb.append("  Ghost messages: already sent (saved for this world)")
                    .append('\n');
        } else {
            sb.append("  Ghost messages: ")
                    .append(countdown(FakePlayerHandler.getGhostLineRemainingTicks(),
                            "start 5 s after the ghost joins"))
                    .append('\n');
        }
        sb.append("  Animal loot piles: ")
                .append(countdown(KilledAnimalsLootHandler.getNextPileRemainingTicks(),
                        "starts day 4"))
                .append('\n');

        sb.append("  Day gates:\n");
        addGate(sb, day, 1, Long.MAX_VALUE, "hostile mobs stopped", "day 1+");
        addGate(sb, day, 1, Long.MAX_VALUE, "classic (alpha) textures & sounds", "always");
        addGate(sb, day, 1, Long.MAX_VALUE, "villages & golems removed", "day 1+");
        addGate(sb, day, 2, 2, "sleeping blocked", "day 2");
        sb.append("    day 2   day-2 creep (client) .......... ")
                .append(clientGateStatus(day, 2)).append('\n');
        addGate(sb, day, 3, 3, "sudden midday -> night time-skip", "day 3");
        addGate(sb, day, 6, 6, "midday static overlay + text sequence", "day 6");
        addGate(sb, day, 3, Long.MAX_VALUE, "ghost player joins", "day 3");
        addGate(sb, day, 3, Long.MAX_VALUE, "ghost chat dialogue (not on day 4)", "day 3+");
        addGate(sb, day, 4, Long.MAX_VALUE, "loot piles / creepy bass", "day 4+");
        addGate(sb, day, 4, Long.MAX_VALUE, "day-4 question (fake player, yes/no)", "day 4");
        addGate(sb, day, 4, Long.MAX_VALUE, "day-4 hunger/red-pink/help (25/50/75%)", "day 4");
        addGate(sb, day, 4, 6, "leafless trees, broken logs", "days 4-6");
        addGate(sb, day, 5, Long.MAX_VALUE, "screen flash (25%/min, client)", "day 5+");
        addGate(sb, day, 6, Long.MAX_VALUE, "bedrock pillars (1.5% per chunk)", "day 6+");
        addGate(sb, day, 7, Long.MAX_VALUE, "fake player appears (once)", "day 7+");
        addGate(sb, day, 7, Long.MAX_VALUE, "blood mobs (4% per spawn, land only)", "day 7+");
        addGate(sb, day, 7, Long.MAX_VALUE, "named mobs (nametag, shake & stare)", "day 7+");
        addGate(sb, day, 8, Long.MAX_VALUE, "hostiles vanish near player (7 blocks)", "day 8+");
        addGate(sb, day, 8, Long.MAX_VALUE, "flesh trees (3 in 128 chunks)", "day 8+");
        addGate(sb, day, 8, Long.MAX_VALUE, "red sky: heavy fog + VHS (15% per 1-3 min, client)", "day 8+");
        addGate(sb, day, 9, Long.MAX_VALUE, "creepy signs (15% per 2-4 min)", "day 9+");
        addGate(sb, day, 4, Long.MAX_VALUE, "doors creak open/closed (15% per 2-4 min)", "day 4+");
        addGate(sb, day, 10, Long.MAX_VALUE, "red rain (always)", "day 10+");
        addGate(sb, day, 10, Long.MAX_VALUE, "lag event: look behind, fake player (30% per 3-6 min)", "day 10+");
        addGate(sb, day, 11, Long.MAX_VALUE, "mystery chests (5% per 2-4 min)", "day 11+");

        src.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int runRoot(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Noname] Subcommands: status, event play <name>, event stopall"),
                false);
        return 0;
    }

    /** Appends one day-gated feature row with its live status. */
    private static void addGate(StringBuilder sb, long day, long minDay, long maxDay,
                                String label, String gateLabel) {
        String status = day >= minDay && day <= maxDay ? "active" : "pending";
        sb.append("    ").append(String.format("%-8s", gateLabel)).append("  ")
                .append(String.format("%-36s", label)).append(" ").append(status).append('\n');
    }

    /** Status of the client-only day-2 creep, reasoned from the server day. */
    private static String clientGateStatus(long day, long gateDay) {
        if (day < gateDay) {
            return "pending";
        }
        return day == gateDay ? "active" : "passed";
    }

    /** {@code "in m:ss"} for an armed countdown, or {@code "idle (note)"}. */
    private static String countdown(int ticks, String note) {
        if (ticks < 0) {
            return "idle (" + note + ")";
        }
        int seconds = (ticks + 19) / 20;
        return "in " + (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    /** {@return the vanilla-style clock as {@code HH:MM}, 06:00 at dawn}. */
    private static String timeOfDay(long tickOfDay) {
        long hour = (tickOfDay / 1000L + 6L) % 24L;
        long minute = (tickOfDay % 1000L) * 60L / 1000L;
        return String.format("%02d:%02d", hour, minute);
    }

    /** {@return a 20-cell text bar filled in proportion to {@code fraction}}. */
    private static String progressBar(double fraction) {
        int filled = (int) Math.round(fraction * BAR_LENGTH);
        return "\u2588".repeat(filled) + "\u2591".repeat(BAR_LENGTH - filled);
    }
}
