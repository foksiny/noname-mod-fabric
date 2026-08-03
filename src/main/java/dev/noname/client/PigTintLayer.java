package dev.noname.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.noname.Day5PigHandler;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Pig;

/**
 * Renders a translucent red overlay over infected day-5+ pigs so they look
 * "a little red-ish". The infection marker is a dedicated synced boolean
 * flag ({@link dev.noname.InfectedPig}, applied by {@code PigInfectionMixin})
 * which syncs to clients via the normal entity data; the layer is added to
 * the pig renderer by {@code PigRendererTintMixin}.
 */
public final class PigTintLayer extends RenderLayer<Pig, PigModel<Pig>> {

    private static final ResourceLocation PIG_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/pig/pig.png");

    /** ~35% alpha pure red — a subtle bloody tint over the normal skin. */
    private static final int TINT_COLOR = 0x59FF0000;

    public PigTintLayer(PigRenderer renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, Pig pig, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        if (!Day5PigHandler.isInfected(pig)) {
            return;
        }
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(PIG_TEXTURE));
        this.getParentModel().renderToBuffer(poseStack, consumer, packedLight,
                OverlayTexture.NO_OVERLAY, TINT_COLOR);
    }
}
