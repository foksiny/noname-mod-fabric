package dev.noname.mixin;

import dev.noname.DayCounter;
import dev.noname.config.ModConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks the hostile mobs that structure templates place during chunk
 * generation (dungeon features, pillager outposts, ...): WorldGenRegion has
 * its own {@code addFreshEntity}. Same gate as
 * {@link HostileSpawnGateMixin} — day 1 onward, no hostile mobs enter newly
 * generated chunks either.
 */
@Mixin(net.minecraft.server.level.WorldGenRegion.class)
public abstract class HostileWorldGenGateMixin {

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void noname$blockWorldGenHostiles(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        if (mob.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        // The first day of the world stays normal; from day 1 on, no hostile
        // mobs are placed in newly generated chunks.
        if (DayCounter.currentDay((LevelAccessor) this) < ModConfig.scaledDay(1)
                || !ModConfig.isEnabled("hostile_stop")) {
            return;
        }
        cir.setReturnValue(false);
    }
}
