package dev.noname.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.noname.IseItEntity;
import dev.noname.Noname;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Renders the "ise it" apparition as a flat billboard: the {@code ise_it}
 * PNG drawn as a camera-facing quad, 3 blocks tall (and as wide as the
 * image's aspect ratio). The billboard gets a stuttery per-frame shake on
 * top of the server-side glitch teleports, so the apparition always looks
 * laggy and glitchy.
 */
public class IseItRenderer extends EntityRenderer<IseItEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Noname.MODID,
                    "textures/entity/ise_it.png");

    /** The apparition is 3 blocks tall. */
    private static final float HEIGHT = 3.0F;

    /** Width from the image's aspect ratio (102x233). */
    private static final float WIDTH = HEIGHT * 102.0F / 233.0F;

    /** How hard the per-frame shake wobbles (stutter buckets). */
    private static final double SHAKE_AMPLITUDE = 0.10D;

    public IseItRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(IseItEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(IseItEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        // Stuttery shake: quantize time into half-second buckets so the
        // offset jumps around instead of flowing smoothly.
        int bucket = (int) ((entity.tickCount + partialTick) * 2.0F);
        double sx = (hash01(entity.getId(), bucket * 3 + 1) - 0.5D) * SHAKE_AMPLITUDE;
        double sy = (hash01(entity.getId(), bucket * 3 + 2) - 0.5D) * SHAKE_AMPLITUDE * 0.6D;
        double sz = (hash01(entity.getId(), bucket * 3 + 3) - 0.5D) * SHAKE_AMPLITUDE;
        poseStack.translate(sx, sy, sz);

        // Billboards always face the camera (the same rotation vanilla uses
        // for name tags).
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        // The quad, anchored at the feet, 3 blocks tall.
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        float hw = WIDTH / 2.0F;
        consumer.addVertex(pose, -hw, 0.0F, 0.0F)
                .setUv(0.0F, 1.0F).setColor(255, 255, 255, 255).setLight(LightTexture.FULL_BRIGHT)
                .setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, -hw, HEIGHT, 0.0F)
                .setUv(0.0F, 0.0F).setColor(255, 255, 255, 255).setLight(LightTexture.FULL_BRIGHT)
                .setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, hw, HEIGHT, 0.0F)
                .setUv(1.0F, 0.0F).setColor(255, 255, 255, 255).setLight(LightTexture.FULL_BRIGHT)
                .setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, hw, 0.0F, 0.0F)
                .setUv(1.0F, 1.0F).setColor(255, 255, 255, 255).setLight(LightTexture.FULL_BRIGHT)
                .setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0.0F, 0.0F, 1.0F);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** {@return a deterministic pseudo-random value in {@code [0, 1)} from
     *  an integer key} — stable per frame bucket, so the shake stutters. */
    private static double hash01(int a, int b) {
        long h = a * 374761393L + b * 668265263L + 1274126177L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0D;
    }
}
