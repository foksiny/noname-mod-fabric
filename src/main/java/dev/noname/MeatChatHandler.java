package dev.noname;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Lets a player summon a meat item by typing "meat" in chat. Any chat
 * message that is exactly "meat" (punctuation stripped, case-insensitive)
 * gives one {@link dev.noname.ModItems#MEAT} item to the sender: into the
 * inventory if it fits, dropped at their feet otherwise.
 *
 * <p>Registered against {@code ServerMessageEvents.ALLOW_CHAT_MESSAGE} — the
 * message itself always passes through; it is only observed here.
 */
public final class MeatChatHandler {

    private MeatChatHandler() {
    }

    /**
     * Watches the player's chat messages and hands out a meat item whenever
     * the normalized text is exactly "meat".
     */
    public static boolean onChatMessage(PlayerChatMessage message, ServerPlayer player,
                                        ChatType.Bound params) {
        String text = normalize(message.decoratedContent().getString());
        if (text.equals("meat")) {
            ItemStack meat = new ItemStack(ModItems.MEAT);
            if (!player.getInventory().add(meat)) {
                player.drop(meat, false);
            }
        }
        return true;
    }

    /** Strips every non-letter/non-digit character, lowercases and collapses
     *  whitespace, so "MEAT!", " meat " and "meat." all match cleanly. */
    private static String normalize(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ') {
                sb.append(c);
            }
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }
}
