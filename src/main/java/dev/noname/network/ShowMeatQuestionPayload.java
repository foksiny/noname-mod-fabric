package dev.noname.network;

import dev.noname.Noname;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client payload telling one player to open the day-10 "question"
 * desktop window: title {@code question}, text {@code do you like meat},
 * yes/no buttons. Sent individually to each player the event is asking (the
 * client answers through {@link MeatQuestionAnswerPayload}).
 */
public record ShowMeatQuestionPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShowMeatQuestionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Noname.MODID, "show_meat_question"));

    public static final StreamCodec<FriendlyByteBuf, ShowMeatQuestionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {},
                    buf -> new ShowMeatQuestionPayload()
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ShowMeatQuestionPayload create() {
        return new ShowMeatQuestionPayload();
    }
}
