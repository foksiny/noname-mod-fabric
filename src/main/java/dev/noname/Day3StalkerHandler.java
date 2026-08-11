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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Day-3+ stalker flicker: every 1-2 minutes spent on day 3 or later there is
 * a 20% chance per player that the flesh-skinned fake player (the same
 * "你的朋友" as the day-3 ghost) appears half a second right in front of the
 * player, facing them, while the laggy1 sound blasts at maximum volume and
 * the player is hit with darkness II for 1 second.
 *
 * <p>Like the day-7 apparition it is a real tracked {@link ServerPlayer}
 * entity added straight into the world, so the client renders it through
 * vanilla networking. The profile is registered in the victim's tab list
 * right before the entity arrives (the day-3 ghost may not have joined in
 * this session), then the entity is removed again after 10 ticks and, like
 * the ghost, its connection is a dummy that swallows every packet it tries
 * to send.
 */
public final class Day3StalkerHandler {

    /** Roll cadence: 1-2 minutes (1200-2400 ticks). */
    private static final int MIN_ROLL_TICKS = 20 * 60;
    private static final int MAX_ROLL_TICKS = 20 * 60 * 2;

    /** Probability that a roll actually triggers the flicker — 20%. */
    private static final float EVENT_CHANCE = 0.20F;

    /** How long the fake player stays in front of the player, in ticks
     *  (10 ticks = 0.5 seconds). */
    private static final int FAKE_DURATION_TICKS = 10;

    /** Duration of the darkness effect on the player, in ticks
     *  (20 ticks = 1 second). */
    private static final int DARKNESS_DURATION_TICKS = 20;

    /** How far in front of the player the fake player appears, in blocks. */
    private static final double FAKE_DISTANCE = 2.0D;

    /** Volume of the laggy1 blast — the client sound engine clamps at 3.0,
     *  so this is as loud as a sound can get. */
    private static final float LAGGY1_VOLUME = 3.0F;

    /** Player -> ticks until that player's next roll. */
    private static final Map<UUID, Integer> ticksUntilRoll = new HashMap<>();

    /** Player -> the fake-player entity currently in front of them. */
    private static final Map<UUID, ServerPlayer> apparitions = new HashMap<>();

    /** Player -> server tick at which its fake player must vanish. */
    private static final Map<UUID, Long> removeAtTick = new HashMap<>();

    private Day3StalkerHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long now = server.getTickCount();
        long day = DayCounter.currentDay(overworld);

        // Vanish expired apparitions; drop players that disconnected while
        // their apparition was up.
        if (!removeAtTick.isEmpty()) {
            for (var it = removeAtTick.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                if (now >= entry.getValue()
                        || server.getPlayerList().getPlayer(entry.getKey()) == null) {
                    it.remove();
                    ServerPlayer fake = apparitions.remove(entry.getKey());
                    if (fake != null) {
                        fake.discard();
                    }
                }
            }
        }

        if (day < ModConfig.scaledDay(3) || !ModConfig.isEnabled("day3_stalker")) {
            ticksUntilRoll.clear();
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            if (apparitions.containsKey(player.getUUID())) {
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
                    < ModConfig.chance("day3_stalker", BloodyNightHandler.boost(EVENT_CHANCE, overworld))) {
                triggerForPlayer(server, player);
            }
        }
    }

    /** Dev/test hook — trigger the flicker for every online player right now,
     *  bypassing the day-3 gate and the roll timer. Dispatched by
     *  {@code /noname event play day3_stalker}. */
    public static void triggerForAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            triggerForPlayer(server, player);
        }
    }

    /** Dev/test hook — remove every apparition and cancel the armed rolls.
     *  Used by {@code /noname event stopall}. */
    public static void stopAll() {
        for (ServerPlayer fake : apparitions.values()) {
            fake.discard();
        }
        apparitions.clear();
        removeAtTick.clear();
        ticksUntilRoll.clear();
    }

    /** Destroys the given apparition with the Cross and removes it from
     *  every bookkeeping map. {@return whether this handler owned the
     *  apparition} */
    public static boolean destroyApparition(ServerPlayer fake) {
        for (var it = apparitions.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (entry.getValue() == fake) {
                it.remove();
                removeAtTick.remove(entry.getKey());
                fake.discard();
                return true;
            }
        }
        return false;
    }

    private static void triggerForPlayer(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (apparitions.containsKey(uuid)) {
            return;
        }
        ServerLevel level = player.serverLevel();

        // Where the player is facing, 2 blocks out (horizontal only).
        Vec3 inFront = player.position()
                .add(facing(player.getYRot()).scale(FAKE_DISTANCE));

        GameProfile profile = new GameProfile(FakePlayerUtil.FAKE_UUID, FakePlayerUtil.FAKE_NAME);
        ServerPlayer apparition = new ServerPlayer(server, level, profile,
                ClientInformation.createDefault());
        apparition.moveTo(inFront.x, inFront.y, inFront.z, player.getYRot() + 180.0F, 0.0F);
        // The player model turns with yHeadRot/yBodyRot, not yRot.
        apparition.yHeadRot = apparition.getYRot();
        apparition.yBodyRot = apparition.getYRot();
        apparition.setInvulnerable(true);
        apparition.setSilent(true);
        apparition.noPhysics = true;
        // Same dummy connection as the ghost: the player "ticks" (sends
        // health updates etc.) while in the world — every packet is swallowed
        // and never delivered.
        apparition.connection = new ServerGamePacketListenerImpl(server,
                FakePlayerHandler.createDummyConnection(), apparition,
                CommonListenerCookie.createInitial(profile, false));
        // The client only creates a player-type entity when it knows the UUID
        // from the player list (the day-3 ghost may not have joined in this
        // session), so register the profile in the victim's tab list before
        // the entity itself arrives.
        player.connection.send(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, apparition));
        level.addFreshEntity(apparition);

        apparitions.put(uuid, apparition);
        removeAtTick.put(uuid, (long) server.getTickCount() + FAKE_DURATION_TICKS);

        // The laggy1 blast at maximum volume, bound to the player entity
        // itself so it follows them and can never be walked away from.
        level.playSound(null, player,
                ModSounds.LAGGY1, SoundSource.AMBIENT, LAGGY1_VOLUME, 1.0F);

        // Darkness II for 1 second (amplifier 1 = level II). Vanilla's
        // darkness blend window (22 ticks) means a 1-second effect never
        // darkens the screen itself, so the client also draws its own
        // one-second darkness overlay via the day3_stalker payload.
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,
                DARKNESS_DURATION_TICKS, 1, false, false, false));
        ServerPlayNetworking.send(player, NonameEventPayload.play("day3_stalker"));
    }

    /** {@return the horizontal facing unit vector for a yaw in degrees, in
     *  vanilla's convention (yaw 0 = +Z, turning left rotates counterclock-
     *  wise when seen from above)} */
    private static Vec3 facing(float yRot) {
        double rad = Math.toRadians(yRot);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
    }
}
