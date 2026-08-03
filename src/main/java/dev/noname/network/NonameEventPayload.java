package dev.noname.network;

import dev.noname.Noname;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One small client-bound payload: tell the receiving client which Noname
 * "event" to play locally (or to stop everything). Carries a single event
 * name (or {@link #STOPALL}) so the client-side dispatcher in
 * {@link dev.noname.NonameEvents} can route it to the matching handler.
 *
 * <p>Only sent server → client by the {@code /noname event} command, hence
 * the single client-bound direction registered in {@link ModPayloads}.
 */
public record NonameEventPayload(String eventName) implements CustomPacketPayload {

    /** Special event name meaning "stop every running Noname client-side
     *  effect" — handled by the client dispatcher directly. */
    public static final String STOPALL = "stopall";

    public static final CustomPacketPayload.Type<NonameEventPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Noname.MODID, "event"));

    /** The on-wire codec: just the event-name UTF string. */
    public static final StreamCodec<FriendlyByteBuf, NonameEventPayload> STREAM_CODEC =
            StreamCodec.of(NonameEventPayload::encode, NonameEventPayload::decode);

    private static void encode(FriendlyByteBuf buf, NonameEventPayload payload) {
        buf.writeUtf(payload.eventName);
    }

    private static NonameEventPayload decode(FriendlyByteBuf buf) {
        return new NonameEventPayload(buf.readUtf());
    }

    /** {@return a play-event payload for the given event name} */
    public static NonameEventPayload play(String eventName) {
        return new NonameEventPayload(eventName);
    }

    /** {@return the canonical stop-all payload} */
    public static NonameEventPayload stopAll() {
        return new NonameEventPayload(STOPALL);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
