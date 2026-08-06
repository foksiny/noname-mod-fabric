package dev.noname;

import dev.noname.client.CreepyBassStingerHandler;
import dev.noname.client.Day2CreepHandler;
import dev.noname.client.Day4HelpPopup;
import dev.noname.client.Day5FlashHandler;
import dev.noname.client.Day6Handler;
import dev.noname.client.Day8SkyHandler;
import dev.noname.client.Day10LookClient;
import dev.noname.client.Day10WhisperHandler;
import dev.noname.client.HeIsHereClient;
import dev.noname.network.NonameEventPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Central dispatcher for the {@code /noname event} dev command. Every
 * Noname "event" the testers want to fire has a name here; the server side
 * routes triggered events to the matching handler, falling back to a
 * {@link NonameEventPayload} packet for client-only effects (the creepy-bass
 * stinger, the day-2 message, disc 11). On the receiving client, the payload
 * handler calls {@link #handleClientEvent} which routes names back to the
 * same handler methods.
 *
 * <p>Adding a new event is a single entry here (and, if it lives on the
 * client, an extra branch in {@link #handleClientEvent}).
 */
public final class NonameEvents {

    private NonameEvents() {
    }

    /**
     * Triggers a named event from the server side. {@code eventName} is
     * matched case-insensitively against the known set. Server-only events
     * are executed immediately; client-only events are dispatched to each
     * real player via {@link NonameEventPayload}. Unknown names are no-ops
     * (the command itself rejects them earlier).
     */
    public static void playOnServer(MinecraftServer server, String eventName) {
        String key = eventName.toLowerCase(Locale.ROOT);
        switch (key) {
            case "ghost_join"      -> FakePlayerHandler.forceSpawn(server);
            case "it_hurts_to_see" -> FakePlayerHandler.playItHurtsNow(server);
            case "loot_pile"       -> KilledAnimalsLootHandler.scatterOneNearEachPlayer(server);
            case "pillar"          -> PillarHandler.placeOneNearEachPlayer(server);
            case "day4_question"   -> Day4KnifeHandler.triggerNow(server);
            case "day4_hungry"     -> Day4KnifeHandler.triggerHungryNow(server);
            case "day4_red_pink"   -> Day4KnifeHandler.triggerRedPinkNow(server);
            case "day7_fake"       -> Day7FakePlayerHandler.triggerNow(server);
            case "blood_death"     -> BloodMobHandler.spawnBloodAtEachPlayer(server);
            case "hostile_clear"   -> HostileMobHandler.clearNearEachPlayer(server);
            case "flesh_tree"      -> FleshTreeHandler.growOneNearEachPlayer(server);
            case "named_mob"       -> NamedMobBehaviourHandler.spawnOneNearEachPlayer(server);
            case "sign_place"      -> SignPlacerHandler.placeOneNearEachPlayer(server);
            case "door_creak"      -> DoorHandler.toggleDoorNow(server);
            case "door_knock"      -> DoorKnockHandler.triggerNow(server);
            case "day3_timeskip"   -> Day3TimeSkipHandler.triggerNow(server);
            case "day2_null_join"  -> Day2NullJoinHandler.triggerNow(server);
            case "day6_static"     -> sendToAllPlayers(server, NonameEventPayload.play("day6_static"));
            case "day10_look"      -> Day10LookHandler.triggerForAllPlayers(server);
            case "day11_chest"     -> Day11ChestHandler.triggerOneNearEachPlayer(server);
case "he_is_here" -> HeIsHereHandler.triggerForEachPlayer(server);
            case "day5_pig" -> Day5PigHandler.spawnOneNearEachPlayer(server);
            case "ise_it"        -> IseItHandler.triggerNow(server);
            case "cave_zombie"   -> CaveZombieHandler.triggerOneNearEachPlayer(server);
            case "cave_digging"  -> CaveDiggingSoundHandler.triggerForEachPlayer(server);

            // Client-only events: dispatch the play payload to every online player.
            case "creepy_bass", "day2_message", "disc_11", "day5_flash", "day8_sky",
                    "day4_help", "day10_whisper" ->
                    sendToAllPlayers(server, NonameEventPayload.play(key));

            default -> { /* unknown — the command rejects names before we get here */ }
        }
    }

    /**
     * Stops every Noname effect currently running on the named player's
     * machine and any server-side timer-backed effect. Sent as a single
     * {@code stopall} payload (which the client dispatches to its handlers)
     * and, on the server, cancels the waiting countdowns.
     */
    public static void stopAll(MinecraftServer server) {
        sendToAllPlayers(server, NonameEventPayload.stopAll());

        // Free the global event lock and cancel server-side armed timers so
        // they don't fire after the stop.
        EventQueue.releaseAll();
        FakePlayerHandler.cancelArmedItHurts();
        FakePlayerHandler.cancelArmedGhostLines();
        GhostChatHandler.stopAll();
        Day4KnifeHandler.stopAll();
        Day3TimeSkipHandler.stopAll();
        Day2NullJoinHandler.stopAll();
        Day6Handler.stopAll();
        Day7FakePlayerHandler.stopAll();
        SignPlacerHandler.cancelArmed();
        DoorHandler.stopAll();
        DoorKnockHandler.stopAll();
        Day10LookHandler.stopAll();
        HeIsHereHandler.stopAll();
        Day5PigHandler.stopAll();
        IseItHandler.stopAll();
        CaveZombieHandler.stopAll();
        CaveDiggingSoundHandler.stopAll();
    }

    /**
     * Client-side dispatch for an incoming {@link NonameEventPayload}: route
     * the event name to the matching client handler. Called by
     * {@code ModPayloads} on the receiving client.
     */
    public static void handleClientEvent(String eventName) {
        if (NonameEventPayload.STOPALL.equals(eventName)) {
            stopAllClient();
            return;
        }
        switch (eventName) {
            case "creepy_bass"   -> CreepyBassStingerHandler.triggerStingerNow();
            case "day2_message"  -> Day2CreepHandler.showMessageNow();
            case "disc_11"       -> Day2CreepHandler.playDisc11Now();
            case "day5_flash"    -> Day5FlashHandler.triggerFlashNow();
            case "day8_sky"      -> Day8SkyHandler.triggerNow();
            case "day6_static"   -> Day6Handler.triggerNow();
            case "day4_help"     -> Day4HelpPopup.showNow();
            case "day10_look"    -> Day10LookClient.start();
            case "day10_whisper" -> Day10WhisperHandler.triggerNow();
            case "day10_look_stop" -> Day10LookClient.stop();
            case "he_is_here"    -> HeIsHereClient.start();
            case "he_is_here:death" -> HeIsHereClient.death();
            case "he_is_here:stop" -> HeIsHereClient.stop();
            default              -> {
                // The lag event packs its look target into the name
                // ("day10_look:yaw:pitch") so the camera faces the ghost.
                if (eventName.startsWith("day10_look:")) {
                    String[] parts = eventName.split(":");
                    try {
                        Day10LookClient.start(
                                Float.parseFloat(parts[1]),
                                Float.parseFloat(parts[2]));
                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                        Day10LookClient.start();
                    }
                } else if (eventName.startsWith("he_is_here:d:")) {
                    // The "he is here" chase packs the friend's distance into
                    // the name ("he_is_here:d:132") every tick.
                    try {
                        HeIsHereClient.setDistance(Integer.parseInt(
                                eventName.substring("he_is_here:d:".length())));
                    } catch (NumberFormatException e) {
                        // ignore a malformed distance
                    }
                }
            }
        }
    }

    /** Cancels every client-side Noname effect on this machine. */
    private static void stopAllClient() {
        CreepyBassStingerHandler.stopAll();
        Day2CreepHandler.stopAll();
        Day5FlashHandler.stopAll();
        Day8SkyHandler.stopAll();
        Day10LookClient.stop();
        Day10WhisperHandler.stopAll();
        Day4HelpPopup.closeNow();
        HeIsHereClient.stop();
    }

    private static void sendToAllPlayers(MinecraftServer server, NonameEventPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
