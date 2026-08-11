package dev.noname;

import com.mojang.authlib.GameProfile;
import dev.noname.config.ModConfig;
import dev.noname.network.NonameEventPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/**
 * Day-10+ "look behind" jumpscare. Every 3-6 minutes spent on day 10 or
 * later there is a 30% chance per player that, for a fraction of a second,
 * the player's view is forced to rotate behind them, the screen zooms in and
 * their movement is locked while the flesh-skinned fake player stands in the
 * fog exactly where the camera now points. The ghost is removed while the
 * camera is still swinging back, so by the time the player is looking
 * forward again, it is already gone.
 *
 * <p>Server side: rolls the chance per player, spawns/removes the fake
 * player entity, pins the player in place (the client additionally zeroes
 * its movement input) and tells the victim's client to run the effect via
 * the {@code day10_look} / {@code day10_look_stop} payloads. The client
 * handler keeps the same timing constants below.
 *
 * <p>This event now integrates with {@link EventQueue}: when any player's
 * roll triggers, a "day10_look" event is queued. When the queue processes it,
 * it runs for all currently eligible players. The queue waits for all those
 * players' events to finish before moving to the next event.
 */
public final class Day10LookHandler {

    /** How long the fake player stays in the world (rotate-in + 0.35 s
     *  hold), in ticks. Must match the client's HOLD_END_TICK. */
    public static final int FAKE_LIFETIME_TICKS = 10;

    /** Whole event length, in ticks. Must match the client's TOTAL_TICKS. */
    public static final int TOTAL_EVENT_TICKS = 13;

    /** Roll cadence: 3-6 minutes (3600-7200 ticks). */
    private static final int MIN_ROLL_TICKS = 20 * 60 * 3;
    private static final int MAX_ROLL_TICKS = 20 * 60 * 6;

    /** Probability that a roll actually triggers the event. */
    private static final float EVENT_CHANCE = 0.30F;

    /** How far behind the player the ghost appears, in blocks — far enough
     *  to be sitting in the fog (heavy-fog end is ~28 blocks at render
     *  distance 5), close enough that the player sees it through the fog. */
    private static final double FAKE_DISTANCE = 20.0D;

    /** How much damage the hit at the end of the event deals. */
    private static final float HIT_DAMAGE = 2.0F;

    /** Knockback strength of the hit, along the player's pre-event facing. */
    private static final double HIT_KNOCKBACK = 0.4D;

    /** Player -> ticks until that player's next roll. */
    private static final Map<UUID, Integer> ticksUntilRoll = new HashMap<>();

    /** Player -> the fake-player entity currently behind them. */
    private static final Map<UUID, ServerPlayer> apparitions = new HashMap<>();

    /** Player -> position pinned during the event + pre-event yaw (the
     *  direction the hit knockback pushes). */
    private static final Map<UUID, FrozenPlayer> frozen = new HashMap<>();

    /** Player -> server tick at which its fake player must vanish. */
    private static final Map<UUID, Long> fakeRemoveAtTick = new HashMap<>();

    /** Player -> server tick at which the event ends (player unfreezes and
     *  takes the hit). */
    private static final Map<UUID, Long> unfreezeAtTick = new HashMap<>();

    /** Players who have rolled and are waiting for the queued event to run. */
    private static final List<UUID> pendingPlayers = new ArrayList<>();

    /** Position + pre-event yaw of a frozen player. */
    private record FrozenPlayer(Vec3 pos, float yaw) {
    }

    private Day10LookHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long now = server.getTickCount();
        long day = DayCounter.currentDay(overworld);

        freezePlayers(server);

        if (!fakeRemoveAtTick.isEmpty()) {
            for (var it = fakeRemoveAtTick.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                if (now >= entry.getValue()) {
                    it.remove();
                    ServerPlayer fake = apparitions.remove(entry.getKey());
                    if (fake != null) {
                        fake.discard();
                    }
                }
            }
        }
        if (!unfreezeAtTick.isEmpty()) {
            for (var it = unfreezeAtTick.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                if (now >= entry.getValue()) {
                    it.remove();
                    FrozenPlayer frozenPlayer = frozen.remove(entry.getKey());
                    ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                    if (player != null) {
                        // The hit: real damage + a weak knockback in the
                        // direction the player was looking before the event
                        // (i.e. away from the ghost that was behind them).
                        Vec3 forward = facing(frozenPlayer.yaw());
                        player.hurt(player.level().damageSources().generic(), HIT_DAMAGE);
                        player.knockback(HIT_KNOCKBACK, forward.x, forward.z);
                        ServerPlayNetworking.send(player, NonameEventPayload.play("day10_look_stop"));
                    }
                }
            }
        }
        // The event is over for everyone (natural end or disconnect): free
        // the global lock.
        if (unfreezeAtTick.isEmpty() && !pendingPlayers.isEmpty()) {
            // All current events finished, but there might be more pending
            // The queue will handle the next batch
            EventQueue.release("day10_look");
        }

        if (day < ModConfig.scaledDay(10) || !ModConfig.isEnabled("day10_look")) {
            ticksUntilRoll.clear();
            pendingPlayers.clear();
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            if (frozen.containsKey(player.getUUID())) {
                continue;
            }
            int remaining = ticksUntilRoll.getOrDefault(player.getUUID(), MIN_ROLL_TICKS);
            if (remaining > 1) {
                ticksUntilRoll.put(player.getUUID(), remaining - 1);
                continue;
            }
            ticksUntilRoll.put(player.getUUID(), MIN_ROLL_TICKS
                    + overworld.getRandom().nextInt(MAX_ROLL_TICKS - MIN_ROLL_TICKS + 1));
            if (overworld.getRandom().nextFloat()
                    < ModConfig.chance("day10_look", BloodyNightHandler.boost(EVENT_CHANCE, overworld))) {
                // Player rolled successfully - add to pending list
                if (!pendingPlayers.contains(player.getUUID())) {
                    pendingPlayers.add(player.getUUID());
                }
                // Queue the event if not already queued/running
                if (!EventQueue.isRunning() && EventQueue.queueSize() == 0) {
                    EventQueue.queueEvent("day10_look", Day10LookHandler::hasPendingPlayers,
                            () -> triggerForPendingPlayers(server));
                }
            }
        }
    }

    private static boolean hasPendingPlayers() {
        return !pendingPlayers.isEmpty();
    }

    private static void triggerForPendingPlayers(MinecraftServer server) {
        // Process all currently pending players
        List<UUID> toProcess = new ArrayList<>(pendingPlayers);
        pendingPlayers.clear();
        for (UUID uuid : toProcess) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && !frozen.containsKey(uuid)) {
                triggerForPlayer(server, player);
            }
        }
    }

    /** Dev/test hook — trigger the event for every online player right now,
     *  bypassing the day-10 gate and the roll timer. Dispatched by
     *  {@code /noname event play day10_look}. */
    public static void triggerForAllPlayers(MinecraftServer server) {
        EventQueue.queueEvent("day10_look", () -> true,
                () -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                            continue;
                        }
                        triggerForPlayer(server, player);
                    }
                });
    }

    /** Dev/test hook — remove every apparition, unfreeze every player and
     *  cancel the armed rolls. Used by {@code /noname event stopall}. */
    public static void stopAll() {
        for (ServerPlayer fake : apparitions.values()) {
            fake.discard();
        }
        apparitions.clear();
        frozen.clear();
        fakeRemoveAtTick.clear();
        unfreezeAtTick.clear();
        ticksUntilRoll.clear();
        pendingPlayers.clear();
        EventQueue.release("day10_look");
    }

    /** Destroys the given apparition with the Cross: removes it, unfreezes
     *  its victim immediately and cancels the end-of-event hit (the client
     *  camera is restored via the {@code day10_look_stop} payload).
     *  {@return whether this handler owned the apparition} */
    public static boolean destroyApparition(ServerPlayer fake) {
        for (var it = apparitions.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (entry.getValue() == fake) {
                UUID victimUuid = entry.getKey();
                it.remove();
                fakeRemoveAtTick.remove(victimUuid);
                unfreezeAtTick.remove(victimUuid);
                frozen.remove(victimUuid);
                pendingPlayers.remove(victimUuid);
                fake.discard();
                MinecraftServer server = ((ServerLevel) fake.level()).getServer();
                ServerPlayer victim = server.getPlayerList().getPlayer(victimUuid);
                if (victim != null) {
                    ServerPlayNetworking.send(victim, NonameEventPayload.play("day10_look_stop"));
                }
                EventQueue.release("day10_look");
                return true;
            }
        }
        return false;
    }

    private static void triggerForPlayer(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (frozen.containsKey(uuid)) {
            return;
        }
        ServerLevel level = player.serverLevel();

        // The ghost stands in the fog exactly where the rotated camera will
        // look: behind the player, on the ground.
        Vec3 behind = player.position().subtract(facing(player.getYRot()).scale(FAKE_DISTANCE));
        int groundX = (int) Math.floor(behind.x);
        int groundZ = (int) Math.floor(behind.z);
        double groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, groundX, groundZ) + 1.0D;

        GameProfile profile = new GameProfile(FakePlayerUtil.FAKE_UUID, FakePlayerUtil.FAKE_NAME);
        ServerPlayer apparition = new ServerPlayer(server, level, profile,
                ClientInformation.createDefault());
        apparition.moveTo(behind.x, groundY, behind.z, player.getYRot(), 0.0F);
        // The player model turns with yHeadRot/yBodyRot, not yRot.
        apparition.yHeadRot = apparition.getYRot();
        apparition.yBodyRot = apparition.getYRot();
        apparition.setInvulnerable(true);
        apparition.setSilent(true);
        apparition.noPhysics = true;
        // Same dummy connection as the ghost: the player "ticks" while in
        // the world — every packet it tries to send is swallowed.
        apparition.connection = new ServerGamePacketListenerImpl(server,
                FakePlayerHandler.createDummyConnection(), apparition,
                CommonListenerCookie.createInitial(profile, false));
        // The client only creates a player-type entity when it knows the
        // UUID from the player list (the day-3 ghost may not have joined in
        // this session), so register the profile in the victim's tab list
        // before the entity itself arrives.
        player.connection.send(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, apparition));
        level.addFreshEntity(apparition);

        apparitions.put(uuid, apparition);
        frozen.put(uuid, new FrozenPlayer(player.position(), player.getYRot()));
        fakeRemoveAtTick.put(uuid, (long) server.getTickCount() + FAKE_LIFETIME_TICKS);
        unfreezeAtTick.put(uuid, (long) server.getTickCount() + TOTAL_EVENT_TICKS);
        // Tell the client exactly where to look: the camera must face the
        // ghost, not just a plain 180-degree turn. Angles are packed into
        // the event name so the existing string payload carries them.
        Vec3 look = apparition.getEyePosition().subtract(player.getEyePosition());
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        float lookYaw = (float) Math.toDegrees(Math.atan2(look.z, look.x)) - 90.0F;
        float lookPitch = (float) -(Math.toDegrees(Math.atan2(look.y, horizontal)));
        ServerPlayNetworking.send(player, NonameEventPayload.play(String.format(
                Locale.ROOT, "day10_look:%.2f:%.2f", lookYaw, lookPitch)));
    }

    /** Pins every frozen player to the position recorded at trigger time;
     *  cleans up players that disconnected mid-event. */
    private static void freezePlayers(MinecraftServer server) {
        for (var it = frozen.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                ServerPlayer fake = apparitions.remove(entry.getKey());
                if (fake != null) {
                    fake.discard();
                }
                fakeRemoveAtTick.remove(entry.getKey());
                unfreezeAtTick.remove(entry.getKey());
            } else {
                Vec3 pos = entry.getValue().pos();
                player.moveTo(pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
                player.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /** {@return the horizontal facing unit vector for a yaw in degrees, in
     *  vanilla's convention (yaw 0 = +Z, turning left rotates counterclock-
     *  wise when seen from above)} */
    private static Vec3 facing(float yRot) {
        double rad = Math.toRadians(yRot);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
    }
}
