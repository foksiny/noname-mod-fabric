package dev.noname.mixin;

import dev.noname.DayCounter;
import dev.noname.HostileSpawnTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The real hostile-mob block: every hostile mob that reaches
 * {@code ServerLevel.addFreshEntity} is refused from day 1 onward (the first
 * day of the world is completely normal). This is the funnel every spawn path
 * ends in — natural night spawns, mob spawner blocks, structure templates,
 * raids, zombie sieges, phantoms, wardens — so they all stop. Spawn eggs and
 * {@code /summon} are marked by {@link MobSpawnMixin} and still work.
 *
 * <p>Cancelling {@code Mob.finalizeSpawn} does <em>not</em> prevent a spawn in
 * 1.21.1 — none of the callers check its return value — so the block lives
 * here instead. Worldgen-time spawns are handled by
 * {@link HostileWorldGenGateMixin}; villagers/iron golems by
 * {@link VillageMixin}.
 */
@Mixin(net.minecraft.server.level.ServerLevel.class)
public abstract class HostileSpawnGateMixin {

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void noname$blockHostileSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        // Only hostile (MONSTER category) mobs are blocked.
        if (mob.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        // The first day stays normal; from day 1 on, no hostile spawns.
        if (DayCounter.currentDay((LevelAccessor) this) < 1) {
            return;
        }
        // Deliberate spawns (spawn egg, /summon) still work.
        if (HostileSpawnTracker.isDeliberateSpawn(mob)) {
            return;
        }
        cir.setReturnValue(false);
    }
}
