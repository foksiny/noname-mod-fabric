package dev.noname.network;

import dev.noname.Noname;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server payload answering the day-10 "do you like meat" question.
 * {@code yes} is {@code true} for the "yes" button, {@code false} for "no"
 * (and for closing the window without answering — the question cannot be
 * dodged).
 */
public record MeatQuestionAnswerPayload(boolean yes) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MeatQuestionAnswerPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Noname.MODID, "meat_question_answer"));

    public static final StreamCodec<FriendlyByteBuf, MeatQuestionAnswerPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.yes),
                    buf -> new MeatQuestionAnswerPayload(buf.readBoolean())
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static MeatQuestionAnswerPayload create(boolean yes) {
        return new MeatQuestionAnswerPayload(yes);
    }
}
