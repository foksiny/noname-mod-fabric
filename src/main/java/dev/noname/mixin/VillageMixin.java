package dev.noname.mixin;

import dev.noname.DayCounter;
import dev.noname.config.ModConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Villages have no villagers or iron golems from day 1 onward (the first day
 * of the world stays normal): new {@link Villager} and {@link IronGolem}
 * entities are prevented from entering the world from the second day on.
 *
 * <p>This mixin covers every <em>runtime</em> spawn path — villager breeding,
 * curing zombie villagers and player-built iron golems all add their entity
 * through {@code ServerLevel.addFreshEntity}. Villagers already saved to disk
 * in existing worlds are kept (chunk loads go through the entity manager,
 * not {@code addFreshEntity}). Village-structure villagers generated during
 * worldgen are handled by {@link WorldGenVillageMixin}.
 */
@Mixin(net.minecraft.server.level.ServerLevel.class)
public abstract class VillageMixin {

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void noname$blockVillagers(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Villager || entity instanceof IronGolem)) {
            return;
        }

        // Server-side only; the first day stays normal, from day 1 on it stops.
        if (DayCounter.currentDay((LevelAccessor) this) < ModConfig.scaledDay(1)
                || !ModConfig.isEnabled("village_removal")) {
            return;
        }

        cir.setReturnValue(false);
    }
}
