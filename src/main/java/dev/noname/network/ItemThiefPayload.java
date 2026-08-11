package dev.noname.network;

import dev.noname.Noname;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Server-to-client payload carrying the item a player just had stolen from
 * them (day-15+ item thief). The client shows a real desktop window naming
 * the item ("i took a &lt;item name&gt; from you :)").
 */
public record ItemThiefPayload(ItemStack stack) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ItemThiefPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Noname.MODID, "item_thief"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemThiefPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    ItemThiefPayload::stack,
                    ItemThiefPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ItemThiefPayload create(ItemStack stack) {
        return new ItemThiefPayload(stack);
    }
}
