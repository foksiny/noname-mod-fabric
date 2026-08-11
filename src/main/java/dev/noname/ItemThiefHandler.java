package dev.noname;

import dev.noname.config.ModConfig;
import dev.noname.network.ItemThiefPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Day-15+ random item theft: every 3-7 minutes spent on day 15 or later,
 * every still-alive survival/adventure player rolls a 10% chance to have a
 * random item quietly taken from their inventory. The victim gets a real
 * desktop pop-up window (out of the game, via {@code ItemThiefPayload} →
 * {@code ItemThiefWindow}) saying "i took a &lt;item name&gt; from you :)",
 * and the named item is <em>dropped</em> (not deleted): a pick-up-able
 * {@link ItemEntity} spawns at the player's feet with a small random toss.
 *
 * <p>Any non-empty slot is fair game — main inventory (36), armor (4) and
 * offhand (1) — and exactly one item is taken (a stack keeps its place, its
 * count just drops by one). Creative and spectators are skipped (nothing to
 * take that matters, and the pop-up would make no sense), as is the mod's
 * own fake player ({@link FakePlayerUtil#FAKE_UUID}).
 *
 * <p>Like all the other day-gated handlers the roll cadence, day gate and
 * probability honour {@link ModConfig}: {@link ModConfig#scaledDay(long)}
 * shifts the start day with the speed level and {@link
 * ModConfig#chance(String, float)} scales the base 10%.
 */
public final class ItemThiefHandler {

    /** Roll cadence: 3-7 minutes (3600-8400 ticks). */
    private static final int MIN_ROLL_TICKS = 20 * 60 * 3;
    private static final int MAX_ROLL_TICKS = 20 * 60 * 7;

    /** Probability that a roll actually takes an item — 10%. */
    private static final float EVENT_CHANCE = 0.10F;

    /** Player UUID -> ticks until that player's next roll. */
    private static final Map<UUID, Integer> ticksUntilRoll = new HashMap<>();

    private ItemThiefHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long day = DayCounter.currentDay(overworld);

        if (day < ModConfig.scaledDay(15) || !ModConfig.isEnabled("item_thief")) {
            ticksUntilRoll.clear();
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
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
                    < ModConfig.chance("item_thief", EVENT_CHANCE)) {
                stealFrom(player);
            }
        }
    }

    /**
     * Dev/test hook — take one item from every online real player right now,
     * bypassing the day gate and the roll. Dispatched by {@code /noname
     * event play item_thief}.
     */
    public static void triggerForAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
                continue;
            }
            stealFrom(player);
        }
    }

    /** Cancels every armed timer. Used by {@code /noname event stopall}. */
    public static void stopAll() {
        ticksUntilRoll.clear();
    }

    // ------------------------------------------------------------------
    // The theft

    /**
     * Picks one random non-empty stack out of the player's full inventory
     * (main + armor + offhand), splits exactly one item off it, drops that
     * item as a pick-up-able entity at the player's feet and tells the
     * victim what was taken via {@link ItemThiefPayload}.
     */
    private static void stealFrom(ServerPlayer player) {
        List<ItemStack> candidates = new ArrayList<>();
        var inv = player.getInventory();
        for (ItemStack stack : inv.items) {
            if (!stack.isEmpty()) {
                candidates.add(stack);
            }
        }
        for (ItemStack stack : inv.armor) {
            if (!stack.isEmpty()) {
                candidates.add(stack);
            }
        }
        ItemStack offhand = inv.offhand.get(0);
        if (!offhand.isEmpty()) {
            candidates.add(offhand);
        }
        if (candidates.isEmpty()) {
            return;
        }

        ItemStack taken = candidates.get(player.getRandom().nextInt(candidates.size()))
                .split(1);
        if (taken.isEmpty()) {
            return;
        }
        inv.setChanged();

        // Dropped, not deleted: a real item entity at the player's feet with
        // a small random toss, immediately pick-up-able.
        ServerLevel level = player.serverLevel();
        ItemEntity entity = new ItemEntity(
                level, player.getX(), player.getY() + 0.5, player.getZ(), taken);
        entity.setPickUpDelay(0);
        level.addFreshEntity(entity);

        // The window names exactly the item that was just dropped.
        ServerPlayNetworking.send(player, ItemThiefPayload.create(taken.copy()));
    }
}
