package dev.noname.mixin;

import dev.noname.DoorAmbushHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Day-15+ door ambush trigger: every time a player right-clicks a CLOSED
 * door (i.e. opens it), the server rolls the 5% ambush chance. The mixin
 * only observes the attempt at the server-side use-item-on-block packet
 * handler (the same authoritative choke point as {@link BedUseMixin}); the
 * door itself still opens normally.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class DoorAmbushMixin {

    @Inject(method = "handleUseItemOn", at = @At("HEAD"))
    private void noname$doorAmbush(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
        ServerLevel level = player.serverLevel();
        if (packet.getHitResult().getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = packet.getHitResult().getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock door) || door.isOpen(state)) {
            return;
        }
        DoorAmbushHandler.onDoorOpenAttempt(player);
    }
}
