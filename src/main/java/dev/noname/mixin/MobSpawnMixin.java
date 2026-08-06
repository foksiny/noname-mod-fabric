package dev.noname.mixin;

import dev.noname.DayCounter;
import dev.noname.config.ModConfig;
import dev.noname.HostileSpawnTracker;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks hostile mobs that were spawned deliberately (spawn egg or
 * {@code /summon}), or on the first day, so {@link HostileSpawnGateMixin}
 * lets them through. Every other hostile mob is blocked at
 * {@code ServerLevel.addFreshEntity} — cancelling {@code finalizeSpawn} would
 * not work: in 1.21.1 the callers (NaturalSpawner, BaseSpawner, ...) never
 * look at its return value and add the mob to the world anyway.
 */
@Mixin(Mob.class)
public abstract class MobSpawnMixin {

    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void noname$markDeliberateSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            SpawnGroupData spawnGroupData,
            CallbackInfoReturnable<SpawnGroupData> cir) {
        // Only hostile mobs are gated; the mark is ignored for everything else.
        Mob self = (Mob) (Object) this;
        if (self.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        // Nothing to mark when the hostile-mob block is switched off.
        if (!ModConfig.isEnabled("hostile_stop")) {
            return;
        }
        HostileSpawnTracker.markDeliberate(self,
                spawnType == MobSpawnType.SPAWN_EGG
                        || spawnType == MobSpawnType.COMMAND
                        || DayCounter.currentDay(level) < ModConfig.scaledDay(1));
    }
}
