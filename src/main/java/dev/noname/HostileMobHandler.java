package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * From day 8 on, hostile mobs (anything extending {@link Monster}) vanish the
 * moment a real player comes within 7 blocks of them: every tick, the mobs in
 * a 7-block box around each player are discarded on the spot — no death
 * animation, no drops, they simply cease to exist.
 */
public final class HostileMobHandler {

    /** Radius in blocks around a player inside which hostile mobs vanish. */
    private static final double VANISH_RADIUS = 7.0D;

    private HostileMobHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        if (DayCounter.currentDay(overworld) < ModConfig.scaledDay(8)
                || !ModConfig.isEnabled("hostile_clear")) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            vanishNear(player);
        }
    }

    /**
     * Dev/test hook — vanish hostile mobs near every real player right now,
     * regardless of day. Dispatched by {@code /noname event play
     * hostile_clear}.
     */
    public static void clearNearEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            vanishNear(player);
        }
    }

    /** Discards every hostile mob within the 7-block box around the player. */
    private static void vanishNear(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        AABB box = AABB.ofSize(player.position(),
                VANISH_RADIUS * 2.0D, VANISH_RADIUS * 2.0D, VANISH_RADIUS * 2.0D);
        List<Entity> hostiles = level.getEntities(player, box,
                e -> e instanceof Monster && !(e instanceof CaveZombie)
                        && !e.isRemoved());
        for (Entity hostile : hostiles) {
            hostile.discard();
        }
    }
}
