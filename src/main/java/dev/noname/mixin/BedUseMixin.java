package dev.noname.mixin;

import dev.noname.DayCounter;
import dev.noname.IseItHandler;
import dev.noname.config.ModConfig;
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
 * Sleeping in a bed is blocked on day 2, and while an "ise it" apparition
 * is active for the player: right-clicking a bed is cancelled on the server
 * and the player gets the error message "An error occurred while trying to
 * call Bed.sleep() function" (key {@code error.unable.sleep}). Every other
 * situation the player can sleep as usual.
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
        // Day 2 blocks sleeping (when enabled), and so does an active
        // "ise it" apparition.
        boolean day2Block = day == ModConfig.scaledDay(2)
                && ModConfig.isEnabled("sleep_block");
        if (!day2Block && !IseItHandler.isActiveFor(player)) {
            return;
        }

        player.displayClientMessage(Component.translatable("error.unable.sleep"), false);
        ci.cancel();
    }
}
