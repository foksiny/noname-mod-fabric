package dev.noname;

import dev.noname.network.ModPayloads;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameRules;

/**
 * Shows a yellow "{@code <player> joined the game}" message when a player
 * joins the world — in singleplayer and multiplayer alike.
 *
 * <p>Vanilla only broadcasts the join message to players who are already in
 * the world (the joining player never sees it, and in singleplayer there is
 * nobody else, so it is never shown at all). We therefore send the message
 * to the joining player directly; other players in multiplayer still get
 * their regular copy from vanilla, so nothing is shown twice.
 *
 * <p>Also sends the content warning overlay on first join.
 */
public final class JoinMessageHandler {

    private JoinMessageHandler() {
    }

    public static void onPlayerJoin(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) {
        ServerPlayer player = handler.player;
        player.sendSystemMessage(
                Component.translatable("multiplayer.player.joined", player.getDisplayName())
                        .withStyle(ChatFormatting.YELLOW)
        );

        // Check if warning has been shown for this world
        var data = server.overworld().getDataStorage().computeIfAbsent(
                NonameSavedData.factory(), NonameSavedData.ID);
        if (!data.isWarningShown()) {
            // The keep-inventory prompt follows once the warning is acknowledged.
            ModPayloads.sendShowWarning(player);
        } else {
            maybeSendKeepInventoryPrompt(player, data);
        }
    }

    /** Sends the keep-inventory prompt if it hasn't been asked before and
     *  keepInventory is currently off. */
    public static void maybeSendKeepInventoryPrompt(ServerPlayer player, NonameSavedData data) {
        if (!data.isKeepInventoryPromptShown()
                && !player.server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).get()) {
            ModPayloads.sendShowKeepInventoryPrompt(player);
        }
    }
}
