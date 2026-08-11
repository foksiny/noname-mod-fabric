package dev.noname;

import com.mojang.authlib.GameProfile;
import dev.noname.config.ModConfig;
import dev.noname.network.NonameEventPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The "he is here" secret event, started by the "." item (20% chance when it
 * disappears from the hand). The timeline, agreed between this handler and
 * {@code dev.noname.client.HeIsHereClient}:
 *
 * <pre>
 * t = 0 s     event starts; the "he is here" song plays (client-side) and
 *             every mob around the player dies
 * t = 13 s    the player gets the darkness effect and the fake player ("你的
 *             friend") appears at a random place 150 blocks away, then runs
 *             at 15 blocks per second straight at the player; the client
 *             starts the white texts and the red "he's X away from you"
 * t = 23+ s   if the friend catches the player (they stopped, got stuck...),
 *             the player is killed — death message: "was killed by friend" —
 *             and the client jumps the song to second 25
 * t = 27 s    if the player is still alive, everything just stops and goes
 *             back to normal (the song ends exactly at second 27)
 * </pre>
 *
 * <p>The song never changes speed or pitch — it always plays at volume and
 * pitch 1, and the "jump to second 25" is done with a pre-cut tail segment.
 */
public final class HeIsHereHandler {

    /** The chase begins (darkness + spawn + run) 13 seconds in, in ticks. */
    public static final int CHASE_START_TICKS = 20 * 13;

    /** The event ends 27 seconds in, in ticks. */
    public static final int EVENT_END_TICKS = 20 * 27;

    /** How far away the friend spawns, in blocks. */
    private static final double SPAWN_DISTANCE = 150.0D;

    /** How fast the friend runs, in blocks per second (0.75 blocks/tick). */
    private static final double CHASE_SPEED = 15.0D / 20.0D;

    /** Distance at which the friend has caught the player, in blocks. */
    private static final double CATCH_DISTANCE = 1.2D;

    /** Radius around the player in which every mob dies at the start. */
    private static final double MOB_KILL_RADIUS = 50.0D;

    /** How long the darkness effect from second 13 lasts, in ticks. */
    private static final int DARKNESS_TICKS = 20 * 5;

    /** Damage of the killing blow — enough to end anything. */
    private static final float KILL_DAMAGE = 100000.0F;

    /** Player UUID -> the chase currently running for that player. */
    private static final Map<UUID, Chase> chases = new HashMap<>();

    /** One running chase: the victim and the friend entity chasing them. */
    private static final class Chase {
        final ServerPlayer player;
        final long startedAtTick;
        ServerPlayer friend;   // null until second 13
        boolean spawned;       // whether the chase phase already began

        Chase(ServerPlayer player, long startedAtTick) {
            this.player = player;
            this.startedAtTick = startedAtTick;
        }
    }

    private HeIsHereHandler() {
    }

    /**
     * Starts the event for one player, from the "." item. Queues the event
     * and kills every mob around the player and tells the client to
     * start the song.
     */
    public static void start(MinecraftServer server, ServerPlayer player) {
        if (chases.containsKey(player.getUUID()) || !ModConfig.isEnabled("he_is_here")) {
            return;
        }
        EventQueue.queueEvent("he_is_here", 
                () -> !chases.containsKey(player.getUUID()),
                () -> startInternal(server, player));
    }

    private static void startInternal(MinecraftServer server, ServerPlayer player) {
        killMobsAround(player);
        chases.put(player.getUUID(), new Chase(player, server.getTickCount()));
        ServerPlayNetworking.send(player, NonameEventPayload.play("he_is_here"));
        server.sendSystemMessage(
                Component.literal("[Noname] he is here... (" + player.getName().getString() + ")"));
    }

    /** Dev/test hook — start the event for every online player right now,
     *  bypassing the lock (the command always fires regardless of other
     *  events). */
    public static void triggerForEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            start(server, player);
        }
    }

    /** Dev/test hook — end every running chase and release the lock. Used by
     *  {@code /noname event stopall}. */
    public static void stopAll() {
        for (Chase chase : chases.values()) {
            discardFriend(chase);
        }
        chases.clear();
        EventQueue.release("he_is_here");
    }

    public static void onServerTick(MinecraftServer server) {
        if (chases.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (var it = chases.entrySet().iterator(); it.hasNext(); ) {
            Chase chase = it.next().getValue();
            ServerPlayer player = chase.player;
            ServerPlayer online = server.getPlayerList().getPlayer(player.getUUID());

            // Player died on their own, disconnected or left the world:
            // end cleanly (the client still gets told to stop if it is there).
            if (online == null || player.isRemoved() || !player.isAlive()
                    || (chase.friend != null && chase.friend.level() != player.level())) {
                if (online != null && !player.isRemoved() && !player.isAlive()) {
                    ServerPlayNetworking.send(player, NonameEventPayload.play("he_is_here:stop"));
                }
                discardFriend(chase);
                it.remove();
                if (chases.isEmpty()) {
                    EventQueue.release("he_is_here");
                }
                continue;
            }

            // Pinned by the Cross: the friend cannot move, catch anyone or
            // end the event on its own — until the charge completes.
            if (chase.friend != null && CrossItem.isStopped(chase.friend)) {
                CrossItem.pin(chase.friend);
                continue;
            }

            // Second 13: the darkness and the friend appear.
            if (!chase.spawned && now - chase.startedAtTick >= CHASE_START_TICKS) {
                chase.spawned = true;
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_TICKS, 0));
                spawnFriend(chase);
            }

            if (!chase.spawned) {
                continue;
            }

            // The friend runs at the player; if it gets close enough, the
            // player is killed (message: "was killed by friend").
            ServerPlayer friend = chase.friend;
            Vec3 toPlayer = player.position().subtract(friend.position());
            double distance = toPlayer.horizontalDistance();
            if (distance <= CATCH_DISTANCE) {
                killPlayer(chase);
                ServerPlayNetworking.send(player, NonameEventPayload.play("he_is_here:death"));
                discardFriend(chase);
                it.remove();
                if (chases.isEmpty()) {
                    EventQueue.release("he_is_here");
                }
                continue;
            }

            // Second 27: the event just ends and everything goes back to
            // normal (the song, cut at 27 s, ends on its own).
            if (now - chase.startedAtTick >= EVENT_END_TICKS) {
                ServerPlayNetworking.send(player, NonameEventPayload.play("he_is_here:stop"));
                discardFriend(chase);
                it.remove();
                if (chases.isEmpty()) {
                    EventQueue.release("he_is_here");
                }
                continue;
            }

            // Run: straight at the player, through everything, at 15 b/s.
            Vec3 step = toPlayer.normalize().scale(Math.min(CHASE_SPEED, distance));
            float yaw = faceYaw(player, friend);
            friend.moveTo(friend.getX() + step.x, friend.getY() + step.y,
                    friend.getZ() + step.z, yaw, 0.0F);
            friend.yHeadRot = friend.getYRot();
            friend.yBodyRot = friend.getYRot();
            friend.setDeltaMovement(Vec3.ZERO);

            // Tell the client the current distance every tick, so the red
            // "he's X away from you" text, the fog and the blood splashes
            // follow the friend's approach exactly.
            ServerPlayNetworking.send(player, NonameEventPayload.play(
                    "he_is_here:d:" + Math.max(1, (int) Math.ceil(distance))));
        }
    }

    /** Kills the victim with the "friend" damage type (death message: "%1$s
     *  was killed by friend"). */
    private static void killPlayer(Chase chase) {
        ServerPlayer player = chase.player;
        ServerLevel level = player.serverLevel();
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,
                        ResourceLocation.fromNamespaceAndPath(Noname.MODID, "friend")));
        player.hurt(new DamageSource(type, chase.friend), KILL_DAMAGE);
    }

    /** Every mob within the kill radius around the player dies at the start
     *  of the event. */
    private static void killMobsAround(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB box = AABB.ofSize(player.position(),
                MOB_KILL_RADIUS * 2.0D, MOB_KILL_RADIUS * 2.0D, MOB_KILL_RADIUS * 2.0D);
        for (Entity entity : level.getEntities(player, box,
                e -> e instanceof LivingEntity
                        && !(e instanceof ServerPlayer)
                        && !e.isRemoved())) {
            ((LivingEntity) entity).kill();
        }
    }

    /** Spawns the friend at a random place 150 blocks around the player, on
     *  the ground, facing the player, with the flesh skin (same fake profile
     *  as the day-3 ghost). */
    private static void spawnFriend(Chase chase) {
        ServerPlayer player = chase.player;
        ServerLevel level = player.serverLevel();
        var random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        Vec3 offset = new Vec3(Math.cos(angle) * SPAWN_DISTANCE, 0.0D,
                Math.sin(angle) * SPAWN_DISTANCE);
        int groundX = (int) Math.floor(player.getX() + offset.x);
        int groundZ = (int) Math.floor(player.getZ() + offset.z);
        double groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                groundX, groundZ) + 1.0D;

        GameProfile profile = new GameProfile(FakePlayerUtil.FAKE_UUID, FakePlayerUtil.FAKE_NAME);
        ServerPlayer friend = new ServerPlayer(level.getServer(), level, profile,
                ClientInformation.createDefault());
        friend.moveTo(player.getX() + offset.x, groundY, player.getZ() + offset.z,
                faceYaw(player, friend), 0.0F);
        friend.yHeadRot = friend.getYRot();
        friend.yBodyRot = friend.getYRot();
        friend.setInvulnerable(true);
        friend.setSilent(true);
        friend.noPhysics = true;
        friend.connection = new ServerGamePacketListenerImpl(level.getServer(),
                FakePlayerHandler.createDummyConnection(), friend,
                CommonListenerCookie.createInitial(profile, false));
        // The client only renders player-type entities it knows from the tab
        // list, so register the profile for the victim before the entity.
        player.connection.send(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, friend));
        level.addFreshEntity(friend);
        chase.friend = friend;
    }

    /** Removes the friend entity from the world, if it exists. */
    private static void discardFriend(Chase chase) {
        if (chase.friend != null) {
            ServerPlayer friend = chase.friend;
            if (friend.level() instanceof ServerLevel level) {
                for (ServerPlayer viewer : level.players()) {
                    if (viewer.getUUID().equals(chase.player.getUUID())) {
                        viewer.connection.send(new ClientboundPlayerInfoRemovePacket(
                                java.util.List.of(friend.getUUID())));
                        break;
                    }
                }
            }
            friend.discard();
            chase.friend = null;
        }
    }

    /** Destroys the friend with the Cross: the event ends, the victim's
     *  client stops the song and the tab entry is removed.
     *  {@return whether this handler owned the friend} */
    public static boolean destroyFriend(ServerPlayer friend) {
        for (var it = chases.entrySet().iterator(); it.hasNext(); ) {
            Chase chase = it.next().getValue();
            if (chase.friend == friend) {
                it.remove();
                if (chases.isEmpty()) {
                    EventQueue.release("he_is_here");
                }
                ServerPlayer victim = friend.level().getServer()
                        .getPlayerList().getPlayer(chase.player.getUUID());
                if (victim != null) {
                    ServerPlayNetworking.send(victim, NonameEventPayload.play("he_is_here:stop"));
                }
                discardFriend(chase);
                return true;
            }
        }
        return false;
    }

    /** {@return the yaw that makes {@code friend} face {@code player}} */
    private static float faceYaw(ServerPlayer player, ServerPlayer friend) {
        Vec3 delta = player.position().subtract(friend.position());
        return (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D);
    }
}
