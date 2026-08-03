package dev.noname;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Registry of Noname's custom blocks. Every block registers its block, its
 * {@link BlockItem} and its creative-tab entry here (drop tables are shipped
 * as datapack JSON in {@code data/noname/loot_table/}).
 */
public final class ModBlocks {

    /**
     * The Flesh Block: soft (breaks in ~1 s by hand), sticky (slime-block
     * behaviour without the bounce) and the building material of the day-8+
     * flesh trees.
     */
    public static final Block FLESH_BLOCK = new FleshBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED)
            .strength(0.7F)
            .sound(SoundType.SLIME_BLOCK)
            .friction(0.8F));

    /**
     * The Blood Flesh Block: a redder, meaner flesh block that can only be
     * found in the day-11+ chests. Takes 5 s to break by hand and 2 s with a
     * pickaxe or an axe, throws blood particles and deals 1 damage every
     * second to everything within 5 blocks while placed.
     */
    public static final Block BLOOD_FLESH_BLOCK = new BloodFleshBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED)
            .strength(0.7F)
            .sound(SoundType.SLIME_BLOCK)
            .friction(0.8F));

    private ModBlocks() {
    }

    /** Registers every block (plus item and creative-tab entry). */
    public static void register() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Noname.MODID, "flesh_block");
        Registry.register(BuiltInRegistries.BLOCK, id, FLESH_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(FLESH_BLOCK, new Item.Properties()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS)
                .register(entries -> entries.accept(FLESH_BLOCK));

        ResourceLocation bloodId = ResourceLocation.fromNamespaceAndPath(Noname.MODID, "blood_flesh_block");
        Registry.register(BuiltInRegistries.BLOCK, bloodId, BLOOD_FLESH_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, bloodId,
                new BlockItem(BLOOD_FLESH_BLOCK, new Item.Properties()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS)
                .register(entries -> entries.accept(BLOOD_FLESH_BLOCK));
    }
}
