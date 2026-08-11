package dev.noname.network;

import dev.noname.Noname;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client payload carrying whether the current night is a Bloody
 * Night (day 15+). Sent whenever the state changes and replayed to joining
 * players; the client uses it to tint the fog a dark red.
 */
public record BloodyNightPayload(boolean bloody) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BloodyNightPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Noname.MODID, "bloody_night"));

    public static final StreamCodec<FriendlyByteBuf, BloodyNightPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.bloody()),
                    buf -> new BloodyNightPayload(buf.readBoolean())
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static BloodyNightPayload create(boolean bloody) {
        return new BloodyNightPayload(bloody);
    }
}
