package dev.noname.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Gives {@link dev.noname.FakePlayerHandler} access to the private
 * {@code channel} field of {@link Connection}, so a dummy netty channel can be
 * installed on a fake player's connection. Using an accessor instead of raw
 * reflection keeps the field name remapped correctly by Loom (in the released
 * jar the field is the intermediary {@code field_11651}, not {@code channel}).
 */
@Mixin(Connection.class)
public interface ConnectionAccessor {

    @Accessor("channel")
    void noname$setChannel(Channel channel);
}
