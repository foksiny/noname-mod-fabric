package dev.noname;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Zombie;

/**
 * Entity types registered by the mod: the "ise it" apparition
 * ({@link IseItEntity}) — a plain billboard entity with no AI, fully driven
 * by {@link IseItHandler} — and the cave stalker ({@link CaveZombie}), a
 * day-8+ smart zombie that hunts players in caves, finds its way around and
 * digs through blocks when it has to.
 */
public final class ModEntities {

    /** The "ise it" apparition, rendered as a 3-block-tall textured billboard. */
    public static final EntityType<IseItEntity> ISE_IT = EntityType.Builder
            .of(IseItEntity::new, MobCategory.MONSTER)
            .sized(1.4F, 3.0F)
            .noSummon()
            .build("ise_it");

    /** The day-8+ cave stalker: a zombie the size of a normal one. */
    public static final EntityType<CaveZombie> CAVE_ZOMBIE = EntityType.Builder
            .of(CaveZombie::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .noSummon()
            .build("cave_zombie");

    private ModEntities() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(Noname.MODID, "ise_it"),
                ISE_IT);
        Registry.register(BuiltInRegistries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(Noname.MODID, "cave_zombie"),
                CAVE_ZOMBIE);
        FabricDefaultAttributeRegistry.register(CAVE_ZOMBIE, Zombie.createAttributes());
    }
}
