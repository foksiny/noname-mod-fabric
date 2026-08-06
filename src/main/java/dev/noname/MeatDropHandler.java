package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;

/**
 * From day 8 on, every killed (non-player) entity — passive mobs, monsters,
 * anything that counts as a {@link LivingEntity} — has a 5% chance to drop one
 * {@link MeatItem} at the spot where it died. The drop is a real
 * {@code ItemEntity} so it obeys despawn, pickup and physics like any other
 * loot.
 *
 * <p>Registered against {@code ServerLivingEntityEvents.AFTER_DEATH}, so this
 * runs once per entity death, server-side only.
 */
public final class MeatDropHandler {

    /** Probability that a single kill drops a meat item (from day 8 on). */
    private static final float DROP_CHANCE = 0.05F;

    private MeatDropHandler() {
    }

    /**
     * Reacts to every entity death: from day 8 on, with a 5% chance, drops
     * one meat item where the entity died. Players (including the fake
     * player) never drop meat.
     */
    public static void onDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof Player) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (DayCounter.currentDay(level) < ModConfig.scaledDay(8)
                || !ModConfig.isEnabled("meat_drops")) {
            return;
        }
        // 5% chance per kill — independent of how or who killed the entity.
        if (level.getRandom().nextFloat() >= ModConfig.chance("meat_drops", DROP_CHANCE)) {
            return;
        }
        entity.spawnAtLocation(new ItemStack(ModItems.MEAT));
    }
}
