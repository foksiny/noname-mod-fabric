package dev.noname.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-side funnel for every right-click: anything held in the off hand is
 * refused (no placing blocks, no using items from there) and shields can't
 * be raised from either hand — {@code useItem} never starts the shield's
 * block, {@code useItemOn} never lets a shield interact with the world.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class GameModeOffhandBlockMixin {

    private static boolean noname$blocked(ItemStack stack, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND) {
            return true;
        }
        return stack.getItem() == Items.SHIELD;
    }

    @Inject(method = "useItem(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true)
    private void noname$noUse(ServerPlayer player, Level level, ItemStack stack,
                              InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (noname$blocked(stack, hand)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true)
    private void noname$noUseOn(ServerPlayer player, Level level, ItemStack stack,
                                InteractionHand hand, BlockHitResult hit,
                                CallbackInfoReturnable<InteractionResult> cir) {
        if (noname$blocked(stack, hand)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}