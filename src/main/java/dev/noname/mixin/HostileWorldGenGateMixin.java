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
 * Blocks the mobs that structure templates place during chunk generation
 * (dungeon features, pillager outposts, ...): WorldGenRegion has its own
 * {@code addFreshEntity}. Same gates as {@link HostileSpawnGateMixin} — day
 * 1 onward no hostile mobs, day 9 onward no natural mobs at all, enter newly
 * generated chunks.
 */
@Mixin(net.minecraft.server.level.WorldGenRegion.class)
public abstract class HostileWorldGenGateMixin {

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void noname$blockWorldGenMobs(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        long day = DayCounter.currentDay((LevelAccessor) this);
        if (mob.getType().getCategory() == MobCategory.MONSTER) {
            // The first day of the world stays normal; from day 1 on, no
            // hostile mobs are placed in newly generated chunks.
            if (day < ModConfig.scaledDay(1) || !ModConfig.isEnabled("hostile_stop")) {
                return;
            }
        } else {
            // From day 9 on, newly generated chunks carry no natural mobs
            // either.
            if (day < ModConfig.scaledDay(9) || !ModConfig.isEnabled("natural_spawn_stop")) {
                return;
            }
        }
        cir.setReturnValue(false);
    }
}
