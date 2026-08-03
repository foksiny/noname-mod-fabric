package dev.noname.network;

import dev.noname.Noname;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client payload to show the initial content warning overlay.
 */
public record ShowWarningPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShowWarningPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Noname.MODID, "show_warning"));

    public static final StreamCodec<FriendlyByteBuf, ShowWarningPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {},
                    buf -> new ShowWarningPayload()
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ShowWarningPayload create() {
        return new ShowWarningPayload();
    }
}