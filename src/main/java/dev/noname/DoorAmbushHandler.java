package dev.noname;

import dev.noname.config.ModConfig;
import dev.noname.network.NonameEventPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Day-15+ door ambush. From day 15 on, every door the player opens has a
 * 5% chance of ambushing them from behind: the victim freezes in place for
 * 5 seconds (the server pins the position every tick, the client zeroes the
 * movement input), gets the darkness effect so nothing is visible, while on
 * their screen the FOV zooms in and footsteps on the block they stand on
 * close in from behind at a rapidly accelerating pace (both client-side,
 * see {@link dev.noname.client.DoorAmbushClient}). When the 5 seconds are
 * up everything goes back to normal and the player is hit from behind:
 * real damage plus a knockback along their current facing (i.e. away from
 * the direction the hit came from) and a whoosh played right behind them —
 * and when they look, there is nobody there.
 *
 * <p>Unlike the timed cinematic events this one is player-action-triggered
 * (like the creaking doors and the knocks), so it fires immediately when a
 * door is opened instead of going through the event queue: the ambush is
 * short, self-contained and spawns no entities.
 */
public final class DoorAmbushHandler {

    /** Whole event length in ticks (8 seconds). Must match the client's
     *  {@code AMBUSH_TICKS} in {@link dev.noname.client.DoorAmbushClient}. */
    public static final int EVENT_TICKS = 20 * 8;

    /** Probability that opening a door triggers the ambush (5%). */
    private static final float AMBUSH_CHANCE = 0.05F;

    /** Damage of the hit from behind at the end of the event. */
    private static final float HIT_DAMAGE = 3.0F;

    /** Knockback strength of the hit, along the player's current facing
     *  (i.e. away from the direction the hit came from). */
    private static final double HIT_KNOCKBACK = 0.5D;

    /** How far behind the player the end-of-event whoosh plays, in blocks. */
    private static final double HIT_SOUND_DISTANCE = 1.5D;

    /** Player -> server tick at which the event ends (unfreeze + hit). */
    private static final Map<UUID, Long> unfreezeAtTick = new HashMap<>();

    /** Player -> position pinned for the whole event. */
    private static final Map<UUID, Vec3> frozen = new HashMap<>();

    private DoorAmbushHandler() {
    }

    /** Roll + trigger hook, called by the door-interaction mixin every time
     *  a real player opens a closed door. The door itself still opens — this
     *  only decides whether the ambush starts. */
    public static void onDoorOpenAttempt(ServerPlayer player) {
        if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (DayCounter.currentDay(level) < ModConfig.scaledDay(15)
                || !ModConfig.isEnabled("door_ambush")) {
            return;
        }
        UUID uuid = player.getUUID();
        if (frozen.containsKey(uuid)) {
            return;
        }
        if (level.getRandom().nextFloat() >= ModConfig.chance("door_ambush", AMBUSH_CHANCE)) {
            return;
        }
        startAmbush(player);
    }

    /** Server tick: pins every frozen player in place, ends the event for
     *  players whose 5 seconds are up, and cleans up victims that
     *  disconnected or died mid-event. Registered against
     *  {@code ServerTickEvents.START_SERVER_TICK}. */
    public static void onServerTick(MinecraftServer server) {
        long now = server.getTickCount();

        for (var it = frozen.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                // Disconnected or died mid-event: no pin, no end-of-event hit.
                it.remove();
                unfreezeAtTick.remove(entry.getKey());
                continue;
            }
            Vec3 pos = entry.getValue();
            player.moveTo(pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
        }

        if (!unfreezeAtTick.isEmpty()) {
            for (var it = unfreezeAtTick.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                if (now < entry.getValue()) {
                    continue;
                }
                it.remove();
                frozen.remove(entry.getKey());
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null && player.isAlive()) {
                    endAmbush(player);
                }
            }
        }
    }

    /** {@return whether the player is currently pinned by an ambush} Used
     *  by the server-side lockdown mixin to drop every gameplay packet the
     *  victim sends while the event runs. */
    public static boolean isAmbushed(ServerPlayer player) {
        return frozen.containsKey(player.getUUID());
    }

    /** Dev/test hook — ambush every real online player right now, bypassing
     *  the day-15 gate and the 5% roll. Dispatched by
     *  {@code /noname event play door_ambush}. */
    public static void triggerForAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            if (frozen.containsKey(player.getUUID())) {
                continue;
            }
            startAmbush(player);
        }
    }

    /** Cancels every running ambush: unfreezes the victims, clears the
     *  darkness and tells the clients to restore the view. Called by
     *  {@code /noname event stopall}. */
    public static void stopAll(MinecraftServer server) {
        for (UUID uuid : new ArrayList<>(frozen.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.removeEffect(MobEffects.DARKNESS);
                ServerPlayNetworking.send(player, NonameEventPayload.play("door_ambush_stop"));
            }
        }
        frozen.clear();
        unfreezeAtTick.clear();
    }

    private static void startAmbush(ServerPlayer player) {
        UUID uuid = player.getUUID();
        frozen.put(uuid, player.position());
        unfreezeAtTick.put(uuid, (long) player.serverLevel().getServer().getTickCount() + EVENT_TICKS);
        // Real darkness for the whole event: with a 160-tick duration the
        // vanilla 22-tick blend window reaches full strength and holds.
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, EVENT_TICKS));
        ServerPlayNetworking.send(player, NonameEventPayload.play("door_ambush"));
    }

    /** The end-of-event hit from behind: damage, knockback away from the
     *  hit direction and a whoosh played right behind the player. */
    private static void endAmbush(ServerPlayer player) {
        player.removeEffect(MobEffects.DARKNESS);
        // The hit comes from behind the player's CURRENT facing: the blow
        // always lands from behind, pushing them forward. In 1.21.1
        // knockback(strength, x, z) reads (x, z) as the direction the blow
        // comes FROM and pushes the victim away from it, so pass the
        // reverse of the facing (the behind direction).
        Vec3 forward = facing(player.getYRot());
        Vec3 fromBehind = forward.reverse();
        player.hurt(player.level().damageSources().generic(), HIT_DAMAGE);
        player.knockback(HIT_KNOCKBACK, fromBehind.x, fromBehind.z);
        Vec3 behind = player.position().subtract(forward.scale(HIT_SOUND_DISTANCE));
        player.serverLevel().playSound(null, behind.x, behind.y, behind.z,
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 1.0F);
        ServerPlayNetworking.send(player, NonameEventPayload.play("door_ambush_stop"));
    }

    /** {@return the horizontal facing unit vector for a yaw in degrees,
     *  in vanilla's convention (yaw 0 = +Z)} */
    private static Vec3 facing(float yRot) {
        double rad = Math.toRadians(yRot);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
    }
}
