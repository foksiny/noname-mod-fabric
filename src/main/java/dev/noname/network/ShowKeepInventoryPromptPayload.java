package dev.noname.network;

import dev.noname.Noname;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client payload asking the player whether the keepInventory
 * game rule should be enabled for a better experience.
 */
public record ShowKeepInventoryPromptPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShowKeepInventoryPromptPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Noname.MODID, "show_keep_inventory_prompt"));

    public static final StreamCodec<FriendlyByteBuf, ShowKeepInventoryPromptPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {},
                    buf -> new ShowKeepInventoryPromptPayload()
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ShowKeepInventoryPromptPayload create() {
        return new ShowKeepInventoryPromptPayload();
    }
}
