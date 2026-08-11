package dev.noname.network;

import dev.noname.NonameEvents;
import dev.noname.Day10MeatQuestionHandler;
import dev.noname.client.BloodyNightClient;
import dev.noname.client.ItemThiefWindow;
import dev.noname.client.MeatQuestionWindow;
import dev.noname.client.KeepInventoryPromptOverlay;
import dev.noname.client.WarningOverlay;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

/**
 * Registers the mod's custom payloads. Types are registered on both sides
 * ({@link #registerCommon}); client-only and server-only receivers are
 * registered by {@link #registerClient} and {@link #registerServer}
 * from the respective entrypoints.
 */
public final class ModPayloads {

    private ModPayloads() {
    }

    /** Registers the payload types so both sides can read them. */
    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(NonameEventPayload.TYPE, NonameEventPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ShowWarningPayload.TYPE, ShowWarningPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ShowKeepInventoryPromptPayload.TYPE, ShowKeepInventoryPromptPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(BloodyNightPayload.TYPE, BloodyNightPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ItemThiefPayload.TYPE, ItemThiefPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ShowMeatQuestionPayload.TYPE, ShowMeatQuestionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(WarningAcknowledgedPayload.TYPE, WarningAcknowledgedPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(KeepInventoryPromptResponsePayload.TYPE, KeepInventoryPromptResponsePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(MeatQuestionAnswerPayload.TYPE, MeatQuestionAnswerPayload.STREAM_CODEC);
    }

    /** Registers the receiving handlers (client side only). */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(NonameEventPayload.TYPE, (payload, context) -> {
            Minecraft mc = context.client();
            mc.execute(() -> NonameEvents.handleClientEvent(payload.eventName()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ShowWarningPayload.TYPE, (payload, context) -> {
            Minecraft mc = context.client();
            mc.execute(WarningOverlay::show);
        });

        ClientPlayNetworking.registerGlobalReceiver(ShowKeepInventoryPromptPayload.TYPE, (payload, context) -> {
            Minecraft mc = context.client();
            mc.execute(KeepInventoryPromptOverlay::show);
        });

        ClientPlayNetworking.registerGlobalReceiver(BloodyNightPayload.TYPE, (payload, context) -> {
            Minecraft mc = context.client();
            mc.execute(() -> BloodyNightClient.set(payload.bloody()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ItemThiefPayload.TYPE, (payload, context) -> {
            Minecraft mc = context.client();
            mc.execute(() -> ItemThiefWindow.show(payload.stack()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ShowMeatQuestionPayload.TYPE, (payload, context) -> {
            Minecraft mc = context.client();
            mc.execute(MeatQuestionWindow::show);
        });
    }

    /** Registers the receiving handlers (server side only). */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(WarningAcknowledgedPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.getServer().execute(() -> {
                var data = player.level().getServer().overworld().getDataStorage().computeIfAbsent(
                        dev.noname.NonameSavedData.factory(), dev.noname.NonameSavedData.ID);
                data.markWarningShown();
                dev.noname.JoinMessageHandler.maybeSendKeepInventoryPrompt(player, data);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(KeepInventoryPromptResponsePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.getServer().execute(() -> {
                var data = player.level().getServer().overworld().getDataStorage().computeIfAbsent(
                        dev.noname.NonameSavedData.factory(), dev.noname.NonameSavedData.ID);
                data.markKeepInventoryPromptShown();
                if (payload.enable()) {
                    player.level().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, player.getServer());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(MeatQuestionAnswerPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.getServer().execute(() -> Day10MeatQuestionHandler.onAnswer(player, payload.yes()));
        });
    }

    /** Sends the show-warning payload to a specific player (server side). */
    public static void sendShowWarning(ServerPlayer player) {
        ServerPlayNetworking.send(player, ShowWarningPayload.create());
    }

    /** Sends the keep-inventory prompt payload to a specific player (server side). */
    public static void sendShowKeepInventoryPrompt(ServerPlayer player) {
        ServerPlayNetworking.send(player, ShowKeepInventoryPromptPayload.create());
    }

    /** Sends the day-10 "do you like meat" question payload to a specific
     *  player (server side). */
    public static void sendShowMeatQuestion(ServerPlayer player) {
        ServerPlayNetworking.send(player, ShowMeatQuestionPayload.create());
    }

    /** Broadcasts the current Bloody-Night state to every player (server side). */
    public static void sendBloodyNight(MinecraftServer server, boolean bloody) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, BloodyNightPayload.create(bloody));
        }
    }

    /** Sends the warning-acknowledged payload to the server (client side). */
    public static void sendWarningAcknowledged() {
        ClientPlayNetworking.send(WarningAcknowledgedPayload.create());
    }

    /** Sends the keep-inventory prompt answer to the server (client side). */
    public static void sendKeepInventoryPromptResponse(boolean enable) {
        ClientPlayNetworking.send(KeepInventoryPromptResponsePayload.create(enable));
    }
}
