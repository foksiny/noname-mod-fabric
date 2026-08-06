package dev.noname;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

/**
 * The "Meat" item.
 *
 * <p>From day 8 on, any killed non-player entity has a 5% chance to drop one
 * "Meat" item (see {@link MeatDropHandler}). It behaves like a normal piece
 * of food: eat it to restore a little hunger and saturation. A "Meat" item
 * combined with a {@code Knife} in a crafting grid yields an
 * {@link dev.noname.ModItems#INFINITE_KNIFE Infinite Knife} — the same
 * one-hit-kill weapon as the {@code Knife} but unbreakable. Texture:
 * {@code noname:item/meat}.
 */
public final class MeatItem extends Item {

    private MeatItem(Item.Properties properties) {
        super(properties);
    }

    /** Creates the singleton meat item instance. */
    static MeatItem create() {
        return new MeatItem(new Item.Properties()
                .stacksTo(64)
                .food(new FoodProperties.Builder()
                        .nutrition(3)
                        .saturationModifier(0.6F)
                        .build()));
    }
}
