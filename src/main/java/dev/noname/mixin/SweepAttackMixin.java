package dev.noname.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Removes the sword sweep attack. In {@code Player.attack} the sweep only
 * happens when the main-hand item is a {@code SwordItem}, so the single
 * {@code getItemInHand} call that guards it is redirected to an empty stack —
 * no sweep damage, no sweep sound, no sweep particle.
 */
@Mixin(Player.class)
public abstract class SweepAttackMixin {

    @Redirect(method = "attack",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack noname$noSweep(Player instance, InteractionHand hand) {
        return ItemStack.EMPTY;
    }
}