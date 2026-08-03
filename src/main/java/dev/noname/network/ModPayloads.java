package dev.noname.network;

import dev.noname.NonameEvents;
import dev.noname.client.WarningOverlay;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

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
        PayloadTypeRegistry.playC2S().register(WarningAcknowledgedPayload.TYPE, WarningAcknowledgedPayload.STREAM_CODEC);
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
    }

    /** Registers the receiving handlers (server side only). */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(WarningAcknowledgedPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.getServer().execute(() -> {
                var data = player.level().getServer().overworld().getDataStorage().computeIfAbsent(
                        dev.noname.NonameSavedData.factory(), dev.noname.NonameSavedData.ID);
                data.markWarningShown();
            });
        });
    }

    /** Sends the show-warning payload to a specific player (server side). */
    public static void sendShowWarning(ServerPlayer player) {
        ServerPlayNetworking.send(player, ShowWarningPayload.create());
    }

    /** Sends the warning-acknowledged payload to the server (client side). */
    public static void sendWarningAcknowledged() {
        ClientPlayNetworking.send(WarningAcknowledgedPayload.create());
    }
}
