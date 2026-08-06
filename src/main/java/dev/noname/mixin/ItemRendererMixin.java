package dev.noname.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.noname.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    @Inject(
        method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At("HEAD")
    )
    private void noname$modifyItemRender(
            ItemStack stack,
            ItemDisplayContext displayContext,
            boolean leftHand,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int combinedLight,
            int combinedOverlay,
            BakedModel model,
            CallbackInfo ci) {
        if (stack != null && stack.is(ModItems.INFINITE_KNIFE)) {
            // Apply translation jitter and rotation jitter to the poseStack
            long time = System.currentTimeMillis();
            long quant = time / 150; // Quantized to 150ms intervals (approx. 6.7 FPS)
            java.util.Random rand = new java.util.Random(quant);

            float scale = 1.0F;
            if (displayContext == ItemDisplayContext.GUI) {
                scale = 0.3F;
            } else if (displayContext == ItemDisplayContext.GROUND) {
                scale = 0.5F;
            }

            // Translation jitter: shift by up to +/- 0.12 blocks (scaled based on context)
            float tx = (rand.nextFloat() - 0.5F) * 0.15F * scale;
            float ty = (rand.nextFloat() - 0.5F) * 0.15F * scale;
            float tz = (rand.nextFloat() - 0.5F) * 0.15F * scale;
            poseStack.translate(tx, ty, tz);

            // Rotation jitter: rotate by up to +/- 15 degrees
            float rx = (rand.nextFloat() - 0.5F) * 20.0F * scale;
            float ry = (rand.nextFloat() - 0.5F) * 20.0F * scale;
            float rz = (rand.nextFloat() - 0.5F) * 20.0F * scale;
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rx));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(ry));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rz));
        }
    }

    @ModifyVariable(
        method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 1
    )
    private int noname$modifyOverlay(int overlay, ItemStack stack) {
        if (stack != null && stack.is(ModItems.INFINITE_KNIFE)) {
            long time = System.currentTimeMillis();
            // Blinks red every 400ms
            if ((time / 400) % 2 == 0) {
                return net.minecraft.client.renderer.texture.OverlayTexture.pack(
                        net.minecraft.client.renderer.texture.OverlayTexture.u(0.0F),
                        net.minecraft.client.renderer.texture.OverlayTexture.v(true)
                );
            }
        }
        return overlay;
    }
}
