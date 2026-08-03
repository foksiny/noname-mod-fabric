package dev.noname.mixin;

import dev.noname.DayCounter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.BedBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sleeping in a bed is blocked only on day 2: right-clicking a bed is
 * cancelled on the server and the player gets the error message
 * "An error occurred while trying to call Bed.sleep() function"
 * (key {@code error.unable.sleep}). Every other day the player can sleep
 * as usual.
 *
 * <p>The server-side use-item-on-block packet handler is the authoritative
 * choke point for every bed right-click (the equivalent of NeoForge's
 * cancelled {@code PlayerInteractEvent.RightClickBlock}).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class BedUseMixin {

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void noname$blockBedUse(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
        ServerLevel level = player.serverLevel();

        BlockPos pos = packet.getHitResult().getBlockPos();
        if (!(level.getBlockState(pos).getBlock() instanceof BedBlock)) {
            return;
        }

        long day = DayCounter.currentDay(level);
        // Only day 2 blocks sleeping; every other day the player can sleep.
        if (day != 2) {
            return;
        }

        player.displayClientMessage(Component.translatable("error.unable.sleep"), false);
        ci.cancel();
    }
}
