package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Horses die the moment a real player comes within 5 blocks of them: every
 * tick, every horse (anything extending {@link AbstractHorse}) in a 5-block
 * box around each player is killed outright — a real death with drops, not a
 * silent vanish.
 */
public final class HorseKillHandler {

    /** Radius in blocks around a player inside which horses die. */
    private static final double DEATH_RADIUS = 5.0D;

    private HorseKillHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        if (!ModConfig.isEnabled("horse_kill")) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            killNear(player);
        }
    }

    /** Kills every horse within the 5-block box around the player. */
    private static void killNear(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        AABB box = AABB.ofSize(player.position(),
                DEATH_RADIUS * 2.0D, DEATH_RADIUS * 2.0D, DEATH_RADIUS * 2.0D);
        List<Entity> horses = level.getEntities(player, box,
                e -> e instanceof AbstractHorse && !e.isRemoved());
        for (Entity horse : horses) {
            horse.hurt(level.damageSources().generic(), Float.MAX_VALUE);
        }
    }
}
