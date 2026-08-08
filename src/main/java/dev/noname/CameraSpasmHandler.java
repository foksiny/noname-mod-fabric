package dev.noname;

import dev.noname.config.ModConfig;
import dev.noname.network.NonameEventPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Day-1+ "camera spasm". Every 3-6 minutes spent on day 1 or later there is
 * an 18% chance per player that, for exactly 1 second (20 ticks), the
 * player's camera whips to a new random direction every tick.
 *
 * <p>Server side: rolls the chance per player and tells the victim's client
 * to run the effect via the {@code camera_spasm} payload. The client handler
 * keeps the same timing constant ({@link #TOTAL_EVENT_TICKS}).
 *
 * <p>This event integrates with {@link EventQueue} (like
 * {@link Day10LookHandler}): when any player's roll triggers, a
 * {@code camera_spasm} event is queued, and when the queue processes it, it
 * runs for all currently eligible players. The queue waits for the players'
 * spasms to finish (the end-at ticks drain) before moving to the next event,
 * so it never overlaps another camera-controlling event.
 */
public final class CameraSpasmHandler {

    /** Whole event length, in ticks (1 second). Must match the client's
     *  TOTAL_TICKS. */
    public static final int TOTAL_EVENT_TICKS = 20;

    /** Roll cadence: 3-6 minutes (3600-7200 ticks). */
    private static final int MIN_ROLL_TICKS = 20 * 60 * 3;
    private static final int MAX_ROLL_TICKS = 20 * 60 * 6;

    /** Probability that a roll actually triggers the event. */
    private static final float EVENT_CHANCE = 0.18F;

    /** Player -> ticks until that player's next roll. */
    private static final Map<UUID, Integer> ticksUntilRoll = new HashMap<>();

    /** Player -> server tick at which its spasm ends. */
    private static final Map<UUID, Long> endAtTick = new HashMap<>();

    /** Players who have rolled and are waiting for the queued event to run. */
    private static final List<UUID> pendingPlayers = new ArrayList<>();

    private CameraSpasmHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long now = server.getTickCount();
        long day = DayCounter.currentDay(overworld);

        // Expire finished spasms; drop players that disconnected mid-spasm
        // so the queued event releases.
        if (!endAtTick.isEmpty()) {
            for (var it = endAtTick.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                if (now >= entry.getValue()
                        || server.getPlayerList().getPlayer(entry.getKey()) == null) {
                    it.remove();
                }
            }
        }
        // The event is over for everyone (natural end or disconnect): free
        // the global lock.
        if (endAtTick.isEmpty()) {
            EventQueue.release("camera_spasm");
        }

        if (day < ModConfig.scaledDay(1) || !ModConfig.isEnabled("camera_spasm")) {
            ticksUntilRoll.clear();
            pendingPlayers.clear();
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            if (endAtTick.containsKey(player.getUUID())) {
                continue;
            }
            int remaining = ticksUntilRoll.getOrDefault(player.getUUID(), MIN_ROLL_TICKS);
            if (remaining > 1) {
                ticksUntilRoll.put(player.getUUID(), remaining - 1);
                continue;
            }
            ticksUntilRoll.put(player.getUUID(), MIN_ROLL_TICKS
                    + overworld.getRandom().nextInt(MAX_ROLL_TICKS - MIN_ROLL_TICKS + 1));
            if (overworld.getRandom().nextFloat() < ModConfig.chance("camera_spasm", EVENT_CHANCE)) {
                // Player rolled successfully - add to pending list.
                if (!pendingPlayers.contains(player.getUUID())) {
                    pendingPlayers.add(player.getUUID());
                }
                // Queue the event if not already queued/running.
                if (!EventQueue.isRunning() && EventQueue.queueSize() == 0) {
                    EventQueue.queueEvent("camera_spasm", CameraSpasmHandler::hasPendingPlayers,
                            () -> triggerForPendingPlayers(server));
                }
            }
        }
    }

    private static boolean hasPendingPlayers() {
        return !pendingPlayers.isEmpty();
    }

    private static void triggerForPendingPlayers(MinecraftServer server) {
        // Process all currently pending players.
        List<UUID> toProcess = new ArrayList<>(pendingPlayers);
        pendingPlayers.clear();
        long end = server.getTickCount() + TOTAL_EVENT_TICKS;
        for (UUID uuid : toProcess) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && !endAtTick.containsKey(uuid)) {
                endAtTick.put(uuid, end);
                ServerPlayNetworking.send(player, NonameEventPayload.play("camera_spasm"));
            }
        }
    }

    /** Dev/test hook — trigger the event for every online player right now,
     *  bypassing the day-1 gate and the roll timer. Dispatched by
     *  {@code /noname event play camera_spasm}. */
    public static void triggerForAllPlayers(MinecraftServer server) {
        EventQueue.queueEvent("camera_spasm", () -> true,
                () -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                            continue;
                        }
                        if (!endAtTick.containsKey(player.getUUID())) {
                            endAtTick.put(player.getUUID(),
                                    (long) server.getTickCount() + TOTAL_EVENT_TICKS);
                            ServerPlayNetworking.send(player,
                                    NonameEventPayload.play("camera_spasm"));
                        }
                    }
                });
    }

    /** Dev/test hook — cancel the armed rolls and the running spasms. Used
     *  by {@code /noname event stopall}. */
    public static void stopAll() {
        endAtTick.clear();
        ticksUntilRoll.clear();
        pendingPlayers.clear();
        EventQueue.release("camera_spasm");
    }
}
