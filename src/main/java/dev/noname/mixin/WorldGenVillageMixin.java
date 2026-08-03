package dev.noname.mixin;

import dev.noname.DayCounter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks the villagers that village structures generate during chunk
 * generation: {@code WorldGenRegion.addFreshEntity} is the path structure
 * templates use to place their villager entities into the world. Same gate as
 * {@link VillageMixin} — day 1 onward, structure villagers never spawn.
 */
@Mixin(net.minecraft.server.level.WorldGenRegion.class)
public abstract class WorldGenVillageMixin {

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void noname$blockStructureVillagers(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Villager || entity instanceof IronGolem)) {
            return;
        }

        if (DayCounter.currentDay((LevelAccessor) this) < 1) {
            return;
        }

        cir.setReturnValue(false);
    }
}
