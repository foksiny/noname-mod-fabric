package dev.noname.mixin;

import dev.noname.DayCounter;
import dev.noname.config.ModConfig;
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
 * The real spawn block funnel every spawn path ends in
 * ({@code ServerLevel.addFreshEntity}):
 * <ul>
 *   <li>hostile mobs (MONSTER category) are refused from day 1 onward (the
 *       first day of the world is completely normal) — natural night spawns,
 *       mob spawner blocks, structure templates, raids, zombie sieges,
 *       phantoms, wardens;</li>
 *   <li>from day 9 onward <em>every</em> other mob that is not a deliberate
 *       spawn is refused too — the world's natural animals stop appearing,
 *       so the day-9+ loot piles are the player's only source of food.</li>
 * </ul>
 *
 * <p>Spawn eggs and {@code /summon} are marked by {@link MobSpawnMixin}
 * (which also marks the mod's own event mobs) and still work.
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
    private void noname$blockNaturalSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        long day = DayCounter.currentDay((LevelAccessor) this);
        if (mob.getType().getCategory() == MobCategory.MONSTER) {
            // Hostile mobs: the first day stays normal; from day 1 on, no
            // non-deliberate hostile spawns.
            if (day < ModConfig.scaledDay(1) || !ModConfig.isEnabled("hostile_stop")) {
                return;
            }
        } else {
            // Everything else: normal until day 9; from day 9 on, no
            // non-deliberate spawns at all.
            if (day < ModConfig.scaledDay(9) || !ModConfig.isEnabled("natural_spawn_stop")) {
                return;
            }
        }
        // Deliberate spawns (spawn egg, /summon, the mod's own event mobs)
        // still work.
        if (HostileSpawnTracker.isDeliberateSpawn(mob)) {
            return;
        }
        cir.setReturnValue(false);
    }
}
