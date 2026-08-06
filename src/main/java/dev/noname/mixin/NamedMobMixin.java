package dev.noname.mixin;

import dev.noname.BloodMobHandler;
import dev.noname.DayCounter;
import dev.noname.config.ModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * From day 7 on, every non-hostile, non-ocean mob added to the server world
 * has a 4% chance of spawning with the blood-mob nametag
 * ({@link BloodMobHandler#NAMED_MOB_NAME}). The name is always visible like
 * a regular nametag ({@code setCustomNameVisible(true)}).
 * {@code ServerLevel.addFreshEntity} is the single entry every spawn path
 * routes through (natural spawning, spawners, spawn eggs, commands), so
 * hooking it covers everything.
 */
@Mixin(ServerLevel.class)
public abstract class NamedMobMixin {

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"))
    private void noname$maybeNameBloodMob(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Mob mob) || entity instanceof Monster) {
            return;
        }
        if (isOceanMob(mob)) {
            return;
        }
        ServerLevel level = (ServerLevel) (Object) this;
        if (DayCounter.currentDay(level) < ModConfig.scaledDay(7)
                || !ModConfig.isEnabled("blood_death")) {
            return;
        }
        if (level.random.nextFloat()
                >= ModConfig.chance("blood_death", BloodMobHandler.NAMED_MOB_CHANCE)) {
            return;
        }
        mob.setCustomName(Component.literal(BloodMobHandler.NAMED_MOB_NAME));
        mob.setCustomNameVisible(true);
    }

    /** {@return true if the mob is an ocean creature — fish, squid, dolphins,
     *  turtles, axolotls — so named blood mobs only ever come from land
     *  animals} */
    private static boolean isOceanMob(Mob mob) {
        MobCategory category = mob.getType().getCategory();
        return mob instanceof AbstractFish
                || mob instanceof Squid
                || mob instanceof Dolphin
                || mob instanceof Turtle
                || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.WATER_CREATURE
                || category == MobCategory.AXOLOTLS;
    }
}
