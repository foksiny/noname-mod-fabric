package dev.noname.mixin;

import dev.noname.RecentMobTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.WorldGenRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Worldgen-time twin of {@link RecentMobGateMixin}: structure templates
 * placing post-1.8 mobs during chunk generation (bastions, trial chambers,
 * desert pyramid cats-in-waiting...) go through {@code WorldGenRegion}'s own
 * spawn method, so they need their own gate.
 */
@Mixin(WorldGenRegion.class)
public abstract class RecentMobWorldGenGateMixin {

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void noname$blockRecentWorldGenMobs(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Mob mob && RecentMobTracker.isRemoved(mob.getType())) {
            cir.setReturnValue(false);
        }
    }
}