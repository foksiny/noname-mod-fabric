package dev.noname;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registry of Noname's custom items. Every item registers into the vanilla
 * item registry here (creative-tab entries are optional).
 */
public final class ModItems {

    /**
     * The Knife: given by the fake player on day 4 when the player answers
     * "no". One-hit-kills anything it hits and immediately breaks the moment
     * it kills something, so it can only ever be used once.
     */
    public static final Item KNIFE = new KnifeItem(new Item.Properties().stacksTo(1));

    /**
     * The ".". An item with no texture at all — the game renders it as the
     * purple-and-black missing texture on purpose. While held in the main
     * hand it vanishes after 3 seconds and, 20% of the time, the "he is
     * here" event starts ({@link HeIsHereHandler}).
     */
    public static final Item DOT = new DotItem(new Item.Properties().stacksTo(1));

    private ModItems() {
    }

    /** Registers every item (plus its creative-tab entry). */
    public static void register() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Noname.MODID, "knife");
        Registry.register(BuiltInRegistries.ITEM, id, KNIFE);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT)
                .register(entries -> entries.accept(KNIFE));

        ResourceLocation dotId = ResourceLocation.fromNamespaceAndPath(Noname.MODID, "dot");
        Registry.register(BuiltInRegistries.ITEM, dotId, DOT);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(DOT));
    }

    /**
     * The knife's tier: absurd attack damage (any hit kills anything short of
     * creative god-mode). The one-use behaviour lives in
     * {@link KnifeItem#hurtEnemy} — the knife breaks the instant it kills.
     */
    private static final class KnifeTier implements Tier {

        @Override
        public int getUses() {
            return 1;
        }

        @Override
        public float getSpeed() {
            return 5.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 1000.0F;
        }

        @Override
        public int getEnchantmentValue() {
            return 0;
        }

        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        }

        @Override
        public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
            return net.minecraft.world.item.crafting.Ingredient.EMPTY;
        }
    }

    /**
     * The Knife item: deals a guaranteed instant kill on any successful hit
     * (regardless of the attack-cooldown damage scaling) and breaks
     * immediately after the killing blow, so it can only be used once.
     */
    private static final class KnifeItem extends SwordItem {

        KnifeItem(Item.Properties properties) {
            super(new KnifeTier(), properties);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            if (attacker instanceof Player player) {
                // Guarantee the kill: swing at any attack strength, armour or
                // enchantment, the target dies in one hit.
                if (target.isAlive()) {
                    target.hurt(player.damageSources().playerAttack(player), 100000.0F);
                }
                if (target.isDeadOrDying()) {
                    // The knife breaks the instant it kills something.
                    stack.shrink(1);
                    player.level().playSound(null, player,
                            SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
                }
            }
            return true;
        }
    }

    /**
     * The "." item: deliberately has no model file, so it renders as the
     * purple-and-black missing texture. It counts how long it is held in the
     * main hand and, after 3 seconds, it disappears from the hand — then
     * there is a 50% chance that the "he is here" secret event starts.
     */
    private static final class DotItem extends Item {

        /** How long the "." must be held before it disappears, in ticks. */
        private static final int HELD_TICKS = 20 * 3;

        /** Probability that the disappearance triggers the secret event. */
        private static final float EVENT_CHANCE = 0.50F;

        /** Player UUID -> ticks the "." has been held in the main hand. */
        private static final java.util.Map<java.util.UUID, Integer> heldTicks =
                new java.util.HashMap<>();

        DotItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity,
                                  int slot, boolean selected) {
            if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
                return;
            }
            // Only counts while actually held in the main hand; anywhere else
            // (inventory, offhand) resets the timer.
            if (!selected || player.getMainHandItem() != stack) {
                heldTicks.remove(player.getUUID());
                return;
            }
            int ticks = heldTicks.merge(player.getUUID(), 1, Integer::sum);
            if (ticks < HELD_TICKS) {
                return;
            }
            heldTicks.remove(player.getUUID());
            // It disappears after 3 seconds...
            stack.shrink(1);
            // ...and 50% of the time something much worse happens.
            if (level.getRandom().nextFloat() < EVENT_CHANCE) {
                HeIsHereHandler.start(player.serverLevel().getServer(), player);
            }
        }
    }
}
