package dev.noname;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The "blood mobs": from day 7 on, a spawned mob has a 10% chance of
 * carrying the encoded name {@value #NAMED_MOB_NAME} ({@code NAMED_MOB_NAME}
 * is base64 for "imagine this without it's skin!" — the nametag keeps the raw
 * base64). When such a mob dies, the "tearing flesh" sound plays at its
 * position and a spray of blood drops bursts out, falls to the ground and
 * lingers there for 5 to 7 seconds.
 *
 * <p>The naming itself is applied by {@code NamedMobMixin} (server-side, on
 * entity add); this handler only reacts to the death of a named mob and the
 * dev/test hook.
 */
public final class BloodMobHandler {

    /** The nametag a blood mob carries — raw base64, kept exactly as the
     *  request specifies. */
    public static final String NAMED_MOB_NAME = "aW1hZ2luZSB0aGlzIHdpdGhvdXQgaXQncyBza2luIQ==";

    /** Chance that a spawned mob gets the name from day 7 on. */
    public static final float NAMED_MOB_CHANCE = 0.04F;

    /** How many blood drops burst out on death. */
    private static final int BLOOD_PARTICLE_COUNT = 900;

    private BloodMobHandler() {
    }

    /**
     * Reacts to the death of a named blood mob: plays the "tearing flesh"
     * sound at the death spot and bursts the blood drops. Registered against
     * {@code ServerLivingEntityEvents.AFTER_DEATH} (server-side only).
     */
    public static void onDeath(LivingEntity entity, DamageSource source) {
        if (entity.getCustomName() == null
                || !NAMED_MOB_NAME.equals(entity.getCustomName().getString())) {
            return;
        }
        spawnBloodEffects((ServerLevel) entity.level(),
                entity.getX(), entity.getY(), entity.getZ());
    }

    /**
     * Dev/test hook — play the sound and burst the blood drops at every real
     * player's position, regardless of day. Dispatched by {@code /noname
     * event play blood_death}.
     */
    public static void spawnBloodAtEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            spawnBloodEffects(player.serverLevel(),
                    player.getX(), player.getY() + 1.0D, player.getZ());
        }
    }

    /** Plays the tearing sound at every real player's own position (so it
     *  always follows them, no matter how far away the blood mob died) and
     *  sends the blood burst to every nearby client at the death spot. */
    private static void spawnBloodEffects(ServerLevel level, double x, double y, double z) {
        for (ServerPlayer player : level.players()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            // Bound to the player entity itself so the tearing sound always
            // follows each player and can never be walked away from.
            level.playSound(null, player,
                    ModSounds.TEARING_FLESH, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        level.sendParticles(ModParticles.BLOOD_DROP, x, y + 1.0D, z,
                BLOOD_PARTICLE_COUNT, 0.8D, 0.6D, 0.8D, 0.35D);
    }
}
