package dev.noname.network;

import dev.noname.Noname;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server payload answering the keep-inventory prompt. When
 * {@code enable} is true the server turns the keepInventory game rule on.
 */
public record KeepInventoryPromptResponsePayload(boolean enable) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<KeepInventoryPromptResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Noname.MODID, "keep_inventory_prompt_response"));

    public static final StreamCodec<FriendlyByteBuf, KeepInventoryPromptResponsePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.enable),
                    buf -> new KeepInventoryPromptResponsePayload(buf.readBoolean())
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static KeepInventoryPromptResponsePayload create(boolean enable) {
        return new KeepInventoryPromptResponsePayload(enable);
    }
}
