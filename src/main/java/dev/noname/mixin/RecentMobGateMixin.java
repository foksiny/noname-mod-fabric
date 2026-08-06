package dev.noname.mixin;

import dev.noname.RecentMobTracker;
import dev.noname.config.ModConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refuses the "recent mobs" (see {@link RecentMobTracker}) at the server's
 * main spawn funnel — {@code ServerLevel.addFreshEntity}. Works side-by-side
 * with {@link HostileSpawnGateMixin}: that one blocks day-gated MONSTER-category
 * mobs, this one blocks the removed post-1.8 types unconditionally. Both
 * obey the {@code hostile_stop} toggle.
 */
@Mixin(ServerLevel.class)
public abstract class RecentMobGateMixin {

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void noname$blockRecentMobs(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Mob mob && RecentMobTracker.isRemoved(mob.getType())
                && ModConfig.isEnabled("hostile_stop")) {
            cir.setReturnValue(false);
        }
    }
}