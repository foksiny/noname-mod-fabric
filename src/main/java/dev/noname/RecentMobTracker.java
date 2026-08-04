package dev.noname;

import net.minecraft.world.entity.EntityType;

import java.util.Set;

/**
 * The set of mobs added to Minecraft after the 1.8 era. Anything in this set
 * is refused by {@code RecentMobGateMixin} / {@code RecentMobWorldGenGateMixin}
 * no matter how it tries to enter the world (natural spawn, structure
 * template, spawn egg, {@code /summon}) and no matter what day it is — the
 * world is supposed to feel like the game stopped updating a long time ago.
 */
public final class RecentMobTracker {

    private RecentMobTracker() {
    }

    private static final Set<EntityType<?>> REMOVED = Set.of(
            // 1.9 Combat Update
            EntityType.SHULKER,
            // 1.10 Frostburn Update
            EntityType.POLAR_BEAR,
            // 1.11 Exploration Update
            EntityType.LLAMA, EntityType.VEX, EntityType.EVOKER, EntityType.VINDICATOR,
            // 1.12 World of Color Update
            EntityType.PARROT,
            // 1.13 Update Aquatic
            EntityType.DROWNED, EntityType.PHANTOM, EntityType.TURTLE,
            EntityType.DOLPHIN, EntityType.COD, EntityType.SALMON,
            EntityType.PUFFERFISH, EntityType.TROPICAL_FISH,
            // 1.14 Village & Pillage
            EntityType.PANDA, EntityType.FOX, EntityType.PILLAGER,
            EntityType.RAVAGER, EntityType.WANDERING_TRADER, EntityType.TRADER_LLAMA,
            // 1.15 Buzzy Bees
            EntityType.BEE,
            // 1.16 Nether Update
            EntityType.PIGLIN, EntityType.HOGLIN, EntityType.ZOGLIN,
            EntityType.STRIDER, EntityType.PIGLIN_BRUTE,
            // 1.17 Caves & Cliffs
            EntityType.AXOLOTL, EntityType.GLOW_SQUID, EntityType.GOAT,
            // 1.18 Caves & Cliffs II
            EntityType.ALLAY,
            // 1.19 The Wild Update
            EntityType.FROG, EntityType.TADPOLE, EntityType.WARDEN,
            // 1.20 Trails & Tales
            EntityType.SNIFFER, EntityType.CAMEL,
            // 1.20.5 Armored Paws
            EntityType.ARMADILLO,
            // 1.21 Tricky Trials
            EntityType.BOGGED, EntityType.BREEZE);

    /** True if the entity type belongs to the removed "recent mobs" set. */
    public static boolean isRemoved(EntityType<?> type) {
        return REMOVED.contains(type);
    }
}
