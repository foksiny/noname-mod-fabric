package dev.noname;

import dev.noname.config.ModConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Day-14+ random "lightning" death: every 4-9 minutes spent on day 14 or
 * later, every still-alive survival/adventure player rolls a 15% chance to be
 * struck dead by {@code err.noname}, a fake "lightning bolt" attacker whose
 * display name threads straight through the mod's own {@code
 * death.attack.noname.err_noname.player} death message ("... died while
 * trying to run from err.noname" — matching the user's description: the chat
 * says the player died by something called {@code err.noname}, and the death
 * message says they "died while trying to run from a lightning bolt").
 *
 * <p>The death is staged so it leaves the world exactly as it was: a snapshot
 * of the victim's full inventory (main + armor + offhand + selected hotbar
 * slot) and their experience (level, progress, total) is taken <em>before</em>
 * the killing blow, the inventory and XP are then zeroed on the dying player
 * so nothing is dropped on the ground (vanilla's {@code dropEquipment()} and
 * {@code dropExperience()} both see an empty player), and the snapshot is
 * restored on respawn through {@link
 * net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN}.
 * The respawned player is then teleported straight back to the exact
 * coordinates and dimension where they died — so, keepInventory on or off,
 * it looks as though the death never happened at all.
 *
 * <p>Creative and spectators are skipped (the killing blow would fail and the
 * pre-death inventory clear would be wasted), and so is the mod's own fake
 * player ({@link FakePlayerUtil#FAKE_UUID}) since it is never a real victim.
 *
 * <p>Like all the other day-gated handlers the roll cadence, day gate and
 * probability honour {@link ModConfig}: {@link ModConfig#scaledDay(long)}
 * shifts the start day with the speed level and {@link
 * ModConfig#chance(String, float)} scales the base 15%.
 */
public final class Day14DeathHandler {

    /** Roll cadence: 4-9 minutes (4800-10800 ticks). */
    private static final int MIN_ROLL_TICKS = 20 * 60 * 4;
    private static final int MAX_ROLL_TICKS = 20 * 60 * 9;

    /** Probability that a roll actually kills the player — 15%. */
    private static final float EVENT_CHANCE = 0.15F;

    /** Damage of the killing blow — enough to end anything (no totem will
     *  save them, since the inventory — and thus any held totem — is cleared
     *  first). */
    private static final float KILL_DAMAGE = Float.MAX_VALUE;

    /** The display name of the fake lightning attacker, woven into the
     *  mod's custom death message as the {@code %2$s} the player was
     *  "trying to run from". */
    private static final String ATTACKER_NAME = "err.noname";

    /** Player UUID -> ticks until that player's next roll. */
    private static final Map<UUID, Integer> ticksUntilRoll = new HashMap<>();

    /** Player UUID -> pending death snapshot to restore on respawn. */
    private static final Map<UUID, DeathSnapshot> pending = new HashMap<>();

    private Day14DeathHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long day = DayCounter.currentDay(overworld);

        // Drop timers and pending snapshots for players no longer around so
        // they don't pile up forever.
        pending.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);

        if (day < ModConfig.scaledDay(14) || !ModConfig.isEnabled("day14_death")) {
            ticksUntilRoll.clear();
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            // Only real, living, mortal players are eligible: creative and
            // spectator are invulnerable, and a dead player has nothing to
            // roll for.
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
                continue;
            }
            int remaining = ticksUntilRoll.getOrDefault(player.getUUID(), MIN_ROLL_TICKS);
            if (remaining > 1) {
                ticksUntilRoll.put(player.getUUID(), remaining - 1);
                continue;
            }
            ticksUntilRoll.put(player.getUUID(), MIN_ROLL_TICKS
                    + overworld.getRandom().nextInt(MAX_ROLL_TICKS - MIN_ROLL_TICKS + 1));
            if (overworld.getRandom().nextFloat()
                    < ModConfig.chance("day14_death", EVENT_CHANCE)) {
                triggerForPlayer(player);
            }
        }
    }

    /**
     * Dev/test hook — strike every online real player right now, bypassing
     * the day gate and the roll. Dispatched by {@code /noname event play
     * day14_death}.
     */
    public static void triggerForAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
                continue;
            }
            triggerForPlayer(player);
        }
    }

    /** Cancels every armed timer and pending snapshot. Used by
     *  {@code /noname event stopall}. */
    public static void stopAll() {
        ticksUntilRoll.clear();
        pending.clear();
    }

    /**
     * Fabric {@link
     * net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents#AFTER_RESPAWN}
     * hook: when a player respawns from a death this handler scheduled, give
     * them back the exact inventory and XP they had the instant before the
     * killing blow and drop them back at the death coordinates, so the death
     * leaves no trace.
     *
     * @param oldPlayer the dead player (pre-respawn), kept around for cleanup
     * @param newPlayer the freshly respawned player to restore to
     * @param alive     {@code false} for a respawn from death; {@code true}
     *                  for an End-portal cross, which this handler ignores
     */
    public static void onRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        if (alive) {
            return;
        }
        DeathSnapshot snapshot = pending.remove(newPlayer.getUUID());
        if (snapshot == null) {
            return;
        }
        snapshot.restore(newPlayer);
    }

    // ------------------------------------------------------------------
    // The kill

    /** Stages and delivers the killing blow for one player. */
    private static void triggerForPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        // 1) Snapshot everything that must survive the death.
        DeathSnapshot snapshot = new DeathSnapshot(player);
        pending.put(player.getUUID(), snapshot);

        // 2) Strip the dying player of inventory and XP *before* the killing
        //    blow so vanilla's death-time drop routines (Inventory.dropAll /
        //    the experience-orb award) have nothing to scatter. This is what
        //    makes "the items never dropped" true even with keepInventory off.
        player.getInventory().clearContent();
        player.experienceLevel = 0;
        player.totalExperience = 0;
        player.experienceProgress = 0.0F;

        // 3) Build the fake lightning-bolt attacker whose name threads into
        //    the mod's custom "died while trying to run from err.noname"
        //    death message. It is never added to the world; it only needs a
        //    display name for DamageSource.getLocalizedDeathMessage.
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
            bolt.setCustomName(Component.literal(ATTACKER_NAME));
            bolt.setCustomNameVisible(false);
        }
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,
                        ResourceLocation.fromNamespaceAndPath(Noname.MODID, "err_noname")));
        DamageSource source = new DamageSource(type, bolt, bolt);

        // 4) The killing blow itself.
        player.hurt(source, KILL_DAMAGE);

        // Discard the throwaway bolt entity now that vanilla has finished
        // capturing the death message (the death message is materialised at
        // the kill, not lazily on respawn).
        if (bolt != null) {
            bolt.discard();
        }
    }

    // ------------------------------------------------------------------
    // The snapshot

    /**
     * A complete, restorable copy of one dying player's "carried" state: full
     * main inventory, armor, offhand, the selected hotbar slot, total
     * experience (level, progress, points) and the exact death location and
     * dimension. Item stacks are deep-copied with {@link ItemStack#copy()},
     * which preserves their count, components and all NBT-backed data, so what
     * comes back is byte-for-byte what went in.
     */
    private static final class DeathSnapshot {
        private final NonNullList<ItemStack> items;
        private final NonNullList<ItemStack> armor;
        private final NonNullList<ItemStack> offhand;
        private final int selected;
        private final int experienceLevel;
        private final int totalExperience;
        private final float experienceProgress;
        private final ResourceKey<Level> dimension;
        private final double x;
        private final double y;
        private final double z;
        private final float yRot;
        private final float xRot;

        DeathSnapshot(ServerPlayer player) {
            Inventory inv = player.getInventory();
            this.items = copyList(inv.items);
            this.armor = copyList(inv.armor);
            this.offhand = copyList(inv.offhand);
            this.selected = inv.selected;
            this.experienceLevel = player.experienceLevel;
            this.totalExperience = player.totalExperience;
            this.experienceProgress = player.experienceProgress;
            this.dimension = player.level().dimension();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.yRot = player.getYRot();
            this.xRot = player.getXRot();
        }

        private static NonNullList<ItemStack> copyList(NonNullList<ItemStack> list) {
            NonNullList<ItemStack> copy = NonNullList.withSize(list.size(), ItemStack.EMPTY);
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = list.get(i);
                if (!stack.isEmpty()) {
                    copy.set(i, stack.copy());
                }
            }
            return copy;
        }

        /** Pushes the snapshot back onto {@code newPlayer}: inventory and XP
         *  first, then a cross-dimensional teleport to the death location so
         *  the player wakes up exactly where they fell. */
        void restore(ServerPlayer newPlayer) {
            MinecraftServer server = newPlayer.getServer();
            if (server == null) {
                return;
            }

            // Inventory: wipe whatever vanilla's respawn handed the new
            // player (empty, if keepInventory was off; the copied set, if it
            // was on — which we cleared anyway — or the default set), then
            // slot the snapshot back in place.
            Inventory inv = newPlayer.getInventory();
            inv.clearContent();
            replaceInto(inv.items, this.items);
            replaceInto(inv.armor, this.armor);
            replaceInto(inv.offhand, this.offhand);
            inv.selected = this.selected;
            inv.setChanged();

            // Experience.
            newPlayer.experienceLevel = this.experienceLevel;
            newPlayer.totalExperience = this.totalExperience;
            newPlayer.experienceProgress = this.experienceProgress;

            // Position: drop them back where they died. teleportTo handles a
            // dimension switch, chunk loading and the client-side reset.
            ServerLevel target = server.getLevel(this.dimension);
            if (target != null) {
                newPlayer.teleportTo(target, this.x, this.y, this.z, this.yRot, this.xRot);
            }
        }

        private static void replaceInto(NonNullList<ItemStack> dest, NonNullList<ItemStack> src) {
            for (int i = 0; i < src.size() && i < dest.size(); i++) {
                ItemStack stack = src.get(i);
                dest.set(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
        }
    }
}
