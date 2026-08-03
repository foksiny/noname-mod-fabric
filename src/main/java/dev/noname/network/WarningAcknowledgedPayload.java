package dev.noname.network;

import dev.noname.Noname;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server payload acknowledging the warning has been seen.
 */
public record WarningAcknowledgedPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WarningAcknowledgedPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Noname.MODID, "warning_acknowledged"));

    public static final StreamCodec<FriendlyByteBuf, WarningAcknowledgedPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {},
                    buf -> new WarningAcknowledgedPayload()
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static WarningAcknowledgedPayload create() {
        return new WarningAcknowledgedPayload();
    }
}