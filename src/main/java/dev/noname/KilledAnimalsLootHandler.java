package dev.noname;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * From day 4 onward, players can occasionally stumble on dropped items that
 * look like the loot a killed non-hostile animal would leave behind —
 * leather, beef, porkchop, mutton, chicken, feathers, wool, rabbit hide /
 * foot, eggs, ink sacs, raw cod / salmon. Hostile-mob loot (rotten flesh,
 * bones, string, arrows, gunpowder, …) is deliberately never spawned.
 *
 * <p>The stings are server-side: on a randomized timer (averaging roughly one
 * pile every few minutes of play) and only while there are players near, a
 * small pile of 1–2 animal-loot stacks is dropped at a random spot near each
 * player — close enough to be stumbled on, far enough that it may take a few
 * moments to spot. The entities are real {@link ItemEntity}s, so they obey
 * despawn / pickup / physics like any other drop.
 */
public final class KilledAnimalsLootHandler {

    /**
     * Vanilla drops that originate from non-hostile (animal/passive) mobs.
     * Hostile-mob drops are intentionally absent from this list. Cooked
     * variants are excluded too — "killed" animals drop raw meat.
     */
    private static final Item[] ANIMAL_DROPS = {
            Items.LEATHER,
            Items.BEEF,
            Items.PORKCHOP,
            Items.MUTTON,
            Items.CHICKEN,
            Items.RABBIT,
            Items.FEATHER,
            Items.RABBIT_HIDE,
            Items.RABBIT_FOOT,
            Items.WHITE_WOOL,
            Items.EGG,
            Items.INK_SAC,
            Items.COD,
            Items.SALMON,
    };

    /** Min ticks between trigger checks (kicks off the random wait). */
    private static final int MIN_WAIT_TICKS = 20 * 60;       // 60 s
    /** Max ticks between trigger checks. */
    private static final int MAX_WAIT_TICKS = 20 * 180;      // 180 s

    /** Min / max horizontal offset (blocks) of a pile from the player. */
    private static final int MIN_RADIUS = 8;
    private static final int MAX_RADIUS = 24;

    /** Min / max stacks per pile. */
    private static final int MIN_ITEMS_PER_PILE = 1;
    private static final int MAX_ITEMS_PER_PILE = 2;

    /** Min / max stack size of each spawned stack. */
    private static final int MIN_COUNT_PER_STACK = 1;
    private static final int MAX_COUNT_PER_STACK = 3;

    /** Remaining ticks before the next ambient loot spawn; -1 = idle. */
    private static int ticksUntilNextPile = -1;

    private KilledAnimalsLootHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        // Only arm the loop from day 4 on; before that, nothing happens.
        if (DayCounter.currentDay(overworld) < 4) {
            ticksUntilNextPile = -1;
            return;
        }
        if (server.getPlayerList().getPlayers().isEmpty()) {
            ticksUntilNextPile = -1;
            return;
        }

        // (Re-)arm the countdown when none is active.
        if (ticksUntilNextPile < 0) {
            RandomSource rng = overworld.getRandom();
            ticksUntilNextPile = MIN_WAIT_TICKS
                    + rng.nextInt(MAX_WAIT_TICKS - MIN_WAIT_TICKS + 1);
        }

        if (--ticksUntilNextPile > 0) {
            return;
        }
        ticksUntilNextPile = -1;

        EventQueue.queueEvent("loot_pile", KilledAnimalsLootHandler::shouldRunLootEvent,
                () -> {
                    scatterOneNearEachPlayer(server);
                    EventQueue.release("loot_pile");
                });
    }

    /**
     * Dev/test hook — {@link dev.noname.command.NonameCommand} calls this to
     * spawn one animal-loot pile around every online player right now,
     * bypassing the day-4 + random-timer gate.
     */
    public static void scatterOneNearEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            scatterPileNear(player);
        }
    }

    /** {@return remaining server ticks before the next animal-loot pile
     *  spawns, or {@code -1} if the loop is idle (pre-day-4, no players, or
     *  currently scattering)} */
    public static int getNextPileRemainingTicks() {
        return ticksUntilNextPile;
    }

    private static boolean shouldRunLootEvent() {
        // The event runs if we're on day 4+ and have players
        MinecraftServer server = EventQueue.getServerForCheck();
        if (server == null) return false;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return false;
        return DayCounter.currentDay(overworld) >= 4 && !server.getPlayerList().getPlayers().isEmpty();
    }

    private static void scatterPileNear(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        RandomSource rng = level.getRandom();

        // Random offset around the player, kept roughly on the same horizontal
        // plane so the stash is findable rather than buried or floating.
        int radius = MIN_RADIUS + rng.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        double angle = rng.nextDouble() * (Math.PI * 2.0);
        double dx = Math.cos(angle) * radius;
        double dz = Math.sin(angle) * radius;
        double x = player.getX() + dx;
        double y = Math.max(level.getMinBuildHeight() + 1, player.getY());
        double z = player.getZ() + dz;

        int stacks = MIN_ITEMS_PER_PILE
                + rng.nextInt(MAX_ITEMS_PER_PILE - MIN_ITEMS_PER_PILE + 1);
        for (int i = 0; i < stacks; i++) {
            Item type = ANIMAL_DROPS[rng.nextInt(ANIMAL_DROPS.length)];
            int count = MIN_COUNT_PER_STACK
                    + rng.nextInt(MAX_COUNT_PER_STACK - MIN_COUNT_PER_STACK + 1);
            ItemStack stack = new ItemStack(type, count);

            ItemEntity drop = new ItemEntity(level, x, y, z, stack);
            drop.setDefaultPickUpDelay();
            // Small scatter so the pile looks like scattered loot, not one point.
            drop.setDeltaMovement(
                    rng.nextDouble() * 0.2 - 0.1,
                    rng.nextDouble() * 0.15,
                    rng.nextDouble() * 0.2 - 0.1);
            level.addFreshEntity(drop);
        }
    }
}
