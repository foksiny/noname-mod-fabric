package dev.noname.mixin;

import dev.noname.client.ScrambledItemNameHandler;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * From day 1 onwards, every time the player switches to a different hotbar
 * slot there is a 10% chance that the on-HUD "currently held item" banner
 * displays a string of 5--10 random characters in place of the stack's real
 * name. The decoy is rolled per fresh hold in
 * {@link ScrambledItemNameHandler#onClientTick} and consulted here, where the
 * banner's only {@code ItemStack.getHoverName()} call lives. So:
 * <ul>
 *   <li>hovering the same stack in an inventory screen still reveals the real
 *       name (the tooltip path is never touched);</li>
 *   <li>selecting the slot again later performs a brand-new roll, so on
 *       average the held item reads normally next time it is held.</li>
 * </ul>
 */
@Mixin(Gui.class)
public abstract class ScrambledItemNameMixin {

    @Redirect(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
    private Component noname$scrambleHeldItemName(ItemStack stack) {
        return ScrambledItemNameHandler.scrambleIfActive(stack);
    }
}
