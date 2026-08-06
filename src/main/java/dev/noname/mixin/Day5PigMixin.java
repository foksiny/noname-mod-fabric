package dev.noname.mixin;

import dev.noname.Day5PigHandler;
import dev.noname.DayCounter;
import dev.noname.config.ModConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * From day 5 on, 1 in 3 pigs added to the server world are marked as
 * "infected" ({@link dev.noname.InfectedPig}, applied by
 * {@code PigInfectionMixin}) via a dedicated synced boolean flag — they will
 * shake, glow red, and emit blood particles. The flag has no nametag
 * side-effect (a synced custom name would still be drawn above the pig when
 * the crosshair is over it); it syncs to clients so both server logic and
 * the client-side red tint layer can recognise infected pigs.
 * {@code ServerLevel.addFreshEntity} is the single entry every spawn path
 * routes through.
 */
@Mixin(ServerLevel.class)
public abstract class Day5PigMixin {

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"))
    private void noname$maybeInfectPig(Entity entity,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Pig pig)) {
            return;
        }
        // Skip pigs already flagged as infected.
        if (Day5PigHandler.isInfected(pig)) {
            return;
        }
        ServerLevel level = (ServerLevel) (Object) this;
        if (DayCounter.currentDay(level) < ModConfig.scaledDay(5)
                || !ModConfig.isEnabled("day5_pig")) {
            return;
        }
        if (level.random.nextFloat()
                >= ModConfig.chance("day5_pig", Day5PigHandler.INFECTED_CHANCE)) {
            return;
        }
        Day5PigHandler.tagInfected(pig);
    }
}