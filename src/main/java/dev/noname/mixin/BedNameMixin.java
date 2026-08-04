package dev.noname.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every bed item (all 16 colours) always displays as plain "Bed", hiding
 * the colour: holding a red bed, a white bed or any other shows the very
 * same name.
 */
@Mixin(ItemStack.class)
public abstract class BedNameMixin {

    @Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
    private void noname$plainBedName(CallbackInfoReturnable<Component> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof BedBlock) {
            cir.setReturnValue(Component.literal("Bed"));
        }
    }
}
