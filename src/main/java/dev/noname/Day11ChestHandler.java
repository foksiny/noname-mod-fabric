package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Day-11+ mystery chests. Every 2-4 minutes spent on day 11 or later, there
     * is a 15% chance per player that a chest appears above an oak plank 3 blocks
 * away from them. The chest opens with the mod's day-11 loot table: one of
 * the blood flesh block, the "." or the knife.
 *
 * <p>Like the day-10 look handler, the roll is per player and per player only
 * while the server is running; the chest itself is a real vanilla chest in
 * the world and stays where it spawned.
 */
public final class Day11ChestHandler {

    /** Roll cadence: 2-4 minutes (2400-4800 ticks). */
    private static final int MIN_ROLL_TICKS = 20 * 60 * 2;
    private static final int MAX_ROLL_TICKS = 20 * 60 * 4;

    /** Probability that a roll actually spawns a chest. */
    private static final float EVENT_CHANCE = 0.15F;

    /** How far around the player the chest may appear, in blocks. */
    private static final double SPAWN_DISTANCE = 3.0D;

    /** The loot table the spawned chests open with. */
    private static final ResourceKey<LootTable> CHEST_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(Noname.MODID, "chests/day11_chest"));

    /** Player -> ticks until that player's next roll. */
    private static final Map<UUID, Integer> ticksUntilRoll = new HashMap<>();

    private Day11ChestHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long day = DayCounter.currentDay(overworld);
        if (day < ModConfig.scaledDay(11) || !ModConfig.isEnabled("day11_chest")) {
            ticksUntilRoll.clear();
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            int remaining = ticksUntilRoll.getOrDefault(player.getUUID(), MIN_ROLL_TICKS);
            if (remaining > 1) {
                ticksUntilRoll.put(player.getUUID(), remaining - 1);
                continue;
            }
            ticksUntilRoll.put(player.getUUID(), MIN_ROLL_TICKS
                    + overworld.getRandom().nextInt(MAX_ROLL_TICKS - MIN_ROLL_TICKS + 1));
            if (overworld.getRandom().nextFloat() < ModConfig.chance("day11_chest", EVENT_CHANCE)) {
                spawnChestNear(player);
            }
        }
    }

    /** Dev/test hook — spawn one chest next to every online player right now,
     *  regardless of the day-11 gate and the roll timer. Dispatched by
     *  {@code /noname event play day11_chest}. */
    public static void triggerOneNearEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            spawnChestNear(player);
        }
    }

    /** Places an oak plank on the ground 3 blocks away from the player and a
     *  loot-table chest on top of it (only if the spot is actually clear). */
    private static void spawnChestNear(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        var random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int dx = (int) Math.round(Math.cos(angle) * SPAWN_DISTANCE);
        int dz = (int) Math.round(Math.sin(angle) * SPAWN_DISTANCE);
        if (dx == 0 && dz == 0) {
            dz = 3;
        }
        BlockPos base = player.blockPosition().offset(dx, 0, dz);
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, base.getX(), base.getZ());
        BlockPos plankPos = new BlockPos(base.getX(), groundY, base.getZ());
        BlockPos chestPos = plankPos.above();
        // The chest needs its own spot and the space above it free to open.
        if (!level.getBlockState(chestPos).isAir()
                || !level.getBlockState(chestPos.above()).isAir()) {
            return;
        }
        level.setBlock(plankPos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(CHEST_LOOT);
        }
    }
}
