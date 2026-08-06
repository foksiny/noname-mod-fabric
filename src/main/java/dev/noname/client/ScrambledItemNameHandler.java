package dev.noname.client;

import dev.noname.DayCounter;
import dev.noname.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Day-1+ "scrambled held item name" effect.
 *
 * <p>Whenever the player switches to a different hotbar slot, there is a
 * 10% chance that the HUD banner that pops up showing the held item's name
 * appears as a string of 5--10 random alphanumerics instead of the real
 * name. The decoy name is rolled once per fresh "hold" (selected-slot
 * change) and used only by {@code Gui.renderSelectedItemName}, so hovering
 * the very same stack in an inventory screen still shows its true name.
 *
 * <p>Selecting the same slot again later performs a fresh 10% roll, so on
 * average the item comes back unscrambled the next time it is held --
 * matching the player's described "if held again, goes back to normal".
 *
 * <p>The decision lives on the client only: the scramble layer above the HUD
 * banner is purely cosmetic and never reaches any vanilla tooltip path, so
 * no server reload or payload is involved.
 */
public final class ScrambledItemNameHandler {

    /** Day on which the effect is first allowed to show. The mod counts day
     *  0 as the very first day of the world. */
    private static final long FIRST_DAY = 1L;

    /** Per-hold chance that the held item's HUD banner is scrambled. */
    private static final float SCRAMBLE_CHANCE = 0.10F;

    /** Shortest random decoy name we ever produce. */
    private static final int MIN_LENGTH = 5;

    /** Longest random decoy name we ever produce. */
    private static final int MAX_LENGTH = 10;

    /** Pool of characters the decoy name draws from. */
    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    /** Last hotbar slot the client saw selected; changing it starts a fresh
     *  scramble roll on the next call to {@link #onClientTick}. */
    private static int lastSelectedSlot = -1;

    /** Last identity-hash of the stack we rolled for, so the same slot being
     *  repopulated with a different item triggers a fresh roll instead of
     *  silently inheriting an old decision. */
    private static int lastStackIdentityHashCode = 0;

    /** Decoy name that should replace the held item's real name in the HUD
     *  banner, or {@code null} if the current hold should display normally.
     *  It is wiped on every fresh slot change so the next hold starts with
     *  a clean slate. */
    private static String activeDecoy = null;

    private ScrambledItemNameHandler() {
    }

    /**
     * {@link net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents#START_CLIENT_TICK}
     * callback: watches the player's selected hotbar slot and rolls a fresh
     * scramble decision each time that index (or the stack living at it)
     * changes.
     */
    public static void onClientTick(Minecraft mc) {
        if (mc.player == null) {
            lastSelectedSlot = -1;
            lastStackIdentityHashCode = 0;
            activeDecoy = null;
            return;
        }
        Inventory inv = mc.player.getInventory();
        int selected = inv.selected;
        ItemStack stack = inv.getSelected();
        int identity = System.identityHashCode(stack);
        boolean slotChanged = selected != lastSelectedSlot;
        boolean stackChanged = !stack.isEmpty() && identity != lastStackIdentityHashCode;
        if (slotChanged || stackChanged) {
            lastSelectedSlot = selected;
            if (!stack.isEmpty()) {
                lastStackIdentityHashCode = identity;
            }
            activeDecoy = null;
            if (!stack.isEmpty()) {
                rollScramble(stack);
            }
        }
    }

    private static void rollScramble(ItemStack stack) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || DayCounter.currentDay(level) < ModConfig.scaledDay(FIRST_DAY)
                || !ModConfig.isEnabled("scrambled_names")) {
            return;
        }
        if (ThreadLocalRandom.current().nextFloat()
                >= ModConfig.chance("scrambled_names", SCRAMBLE_CHANCE)) {
            return;
        }
        activeDecoy = randomName();
    }

    /**
     * If the current hold is scrambled, returns a {@code MutableComponent}
     * carrying the random decoy name styled with the stack's rarity colour
     * (so the on-screen banner still picks up the rarity colour); otherwise
     * returns the real hover name unchanged.
     *
     * <p>Called by a redirecting mixin inside
     * {@code Gui.renderSelectedItemName}, so inventory tooltip rendering
     * never goes through this path and always displays the genuine name.
     */
    public static Component scrambleIfActive(ItemStack stack) {
        if (activeDecoy != null && !stack.isEmpty()) {
            return Component.literal(activeDecoy)
                    .withStyle(stack.getRarity().color());
        }
        return stack.getHoverName();
    }

    /**
     * Builds a random 5--10 character alphanumeric string.
     */
    private static String randomName() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int len = rng.nextInt(MIN_LENGTH, MAX_LENGTH + 1);
        char[] buf = new char[len];
        for (int i = 0; i < len; i++) {
            buf[i] = ALPHABET[rng.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }
}
