package dev.noname.mixin;

import dev.noname.client.Day10LookClient;
import dev.noname.client.Day10LookClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swings the camera behind the player during the day-10+ lag event. Runs at
 * the end of {@code Camera.setup}, after the rotation was applied, so the
 * pose from {@link Day10LookClient} wins over the default camera logic.
 */
@Mixin(Camera.class)
public abstract class Day10LookCameraMixin {

    @Shadow
    private float yRot;

    @Shadow
    private float xRot;

    @Inject(method = "setup", at = @At("TAIL"))
    private void noname$applyDay10Look(BlockGetter blockGetter, Entity entity, boolean bl, boolean bl2, float f, CallbackInfo ci) {
        Day10LookClient.CameraPose pose = new Day10LookClient.CameraPose();
        if (Day10LookClient.applyCamera(pose)) {
            this.yRot = pose.yaw();
            this.xRot = pose.pitch();
        }
    }
}
