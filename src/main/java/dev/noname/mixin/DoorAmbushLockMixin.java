package dev.noname.mixin;

import dev.noname.DoorAmbushHandler;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-side lockdown during the day-15+ door ambush. The client-side
 * mixins block the input at the source (mouse, keyboard, movement), but
 * held keys or a modified client could still send gameplay packets; this
 * mixin drops every one of them at the authoritative choke point while the
 * victim is ambushed — attacks, block breaking, item use, inventory
 * clicks, hotbar changes, jumping, sneaking, sprinting, vehicle input,
 * chat... nothing gets through for the 5 seconds. {@code /noname event
 * stopall} and console commands are unaffected (they are not player
 * packets).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class DoorAmbushLockMixin {

    /** {@return whether the packet sender is currently ambushed} */
    private static boolean noname$ambushLocked(ServerGamePacketListenerImpl handler) {
        return DoorAmbushHandler.isAmbushed(handler.player);
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void noname$lockUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (noname$ambushLocked((ServerGamePacketListenerImpl) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void noname$lockUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (noname$ambushLocked((ServerGamePacketListenerImpl) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void noname$lockPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (noname$ambushLocked((ServerGamePacketListenerImpl) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerCommand", at = @At("HEAD"), cancellable = true)
    private void noname$lockPlayerCommand(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
        if (noname$ambushLocked((ServerGamePacketListenerImpl) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void noname$lockInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (noname$ambushLocked((ServerGamePacketListenerImpl) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void noname$lockContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (noname$ambushLocked((ServerGamePacketListenerImpl) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerButtonClick", at = @At("HEAD"), cancellable = true)
    private void noname$lockContainerButtonClick(ServerboundContainerButtonClickPacket packet, CallbackInfo ci) {
        if (noname$ambushLocked((ServerGamePacketListenerImpl) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void noname$lockSetCarriedItem(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (noname$ambushLocked((ServerGamePacketListenerImpl) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerInput", at = @At("HEAD"), cancellable = true)
    private void noname$lockPlayerInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        if (noname$ambushLocked((ServerGamePacketListenerImpl) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true)
    private void noname$lockAnimate(ServerboundSwingPacket packet, CallbackInfo ci) {
        if (noname$ambushLocked((ServerGamePacketListenerImpl) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void noname$lockChat(ServerboundChatPacket packet, CallbackInfo ci) {
        if (noname$ambushLocked((ServerGamePacketListenerImpl) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void noname$lockSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        if (noname$ambushLocked((ServerGamePacketListenerImpl) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void noname$lockPlayerAbilities(ServerboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        if (noname$ambushLocked((ServerGamePacketListenerImpl) (Object) this)) {
            ci.cancel();
        }
    }
}
