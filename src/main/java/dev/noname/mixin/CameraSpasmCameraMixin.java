package dev.noname.mixin;

import dev.noname.client.CameraSpasmClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Whips the camera to random directions during the day-1+ spasm event. Runs
 * at the end of {@code Camera.setup}, after the rotation was applied, so the
 * pose from {@link CameraSpasmClient} wins over the default camera logic.
 * The rotation is re-applied through {@code setRotation} so the derived
 * rotation quaternion (which the renderer actually consumes) is recomputed.
 */
@Mixin(Camera.class)
public abstract class CameraSpasmCameraMixin {

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void noname$applyCameraSpasm(BlockGetter blockGetter, Entity entity, boolean bl, boolean bl2, float f, CallbackInfo ci) {
        CameraSpasmClient.CameraPose pose = new CameraSpasmClient.CameraPose();
        if (CameraSpasmClient.applyCamera(pose)) {
            this.setRotation(pose.yaw(), pose.pitch());
        }
    }
}
