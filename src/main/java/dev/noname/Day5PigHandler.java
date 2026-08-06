package dev.noname;

import com.mojang.authlib.GameProfile;
import dev.noname.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


/**
 * From day 5 on, 1 in 3 pigs that spawn are "infected": they shake visibly,
 * glow slightly red, and continuously emit blood particles. When such a pig
 * is killed the tearing-flesh sound plays, it drops 2-3 flesh blocks, and
 * there is a 25% chance that the fake player flickers at the death spot,
 * plays the laggy1 sound, gives the killer a 1-second darkness effect, and
 * vanishes after 8 ticks (0.4 seconds).
 *
 * <p>Infection state is carried by a dedicated synced boolean on the pig
 * (see {@link InfectedPig}, applied by {@code PigInfectionMixin}), not by a
 * custom name — a synced name is unhideable in vanilla (it still renders
 * above the pig whenever the crosshair is over it). The flag syncs to
 * clients so the red tint layer can find infected pigs, survives reload via
 * the pig's NBT, and never produces a nametag. The per-tick scan is
 * registered as a server-tick callback.
 */
public final class Day5PigHandler {

    /** Chance that a pig spawned on day 5+ becomes infected — 1 in 3 (~33%). */
    public static final float INFECTED_CHANCE = 1.0F / 3.0F;

    /** Chance that killing an infected pig spawns the fake player — 25%. */
    private static final float FAKE_PLAYER_CHANCE = 0.25F;

    /** How long the fake player lingers, in ticks (8 ticks = 0.4 s). */
    private static final int FAKE_PLAYER_DURATION_TICKS = 8;

    /** Duration of the darkness effect on the killer, in ticks (20 ticks = 1 s). */
    private static final int DARKNESS_DURATION_TICKS = 20;

    /** Distance (blocks) for the shake jitter amplitude. */
    private static final double SHAKE_AMPLITUDE = 0.04D;

    /** How often (in ticks) to emit the blood particles. */
    private static final int BLOOD_PARTICLE_INTERVAL = 5;

    /** Current fake-player apparitions (one per death location that rolled the
     *  25% chance). */
    private static final Set<ServerPlayer> apparitions = new HashSet<>();

    /** Tick counter for particle emission across all pigs. */
    private static int particleTickCounter = 0;

    private Day5PigHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        if (!ModConfig.isEnabled("day5_pig")) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        if (DayCounter.currentDay(overworld) < ModConfig.scaledDay(5)) return;

        particleTickCounter++;
        boolean emitParticles = particleTickCounter % BLOOD_PARTICLE_INTERVAL == 0;

        // Make infected pigs shake and emit blood particles.
        for (Entity entity : overworld.getAllEntities()) {
            if (!(entity instanceof Pig pig)) continue;
            if (!isInfected(pig)) continue;

            if (emitParticles) {
                overworld.sendParticles(ModParticles.BLOOD_DROP,
                        pig.getX(), pig.getY() + pig.getBbHeight() * 0.5D, pig.getZ(),
                        3, 0.3D, 0.2D, 0.3D, 0.02D);
            }

            // Subtle shake
            pig.setPos(
                    pig.getX() + (pig.getRandom().nextDouble() - 0.5D) * SHAKE_AMPLITUDE,
                    pig.getY(),
                    pig.getZ() + (pig.getRandom().nextDouble() - 0.5D) * SHAKE_AMPLITUDE
            );
        }

        // Remove expired apparitions.
        if (!apparitions.isEmpty()) {
            long now = server.getTickCount();
            Iterator<ServerPlayer> it = apparitions.iterator();
            while (it.hasNext()) {
                ServerPlayer apparition = it.next();
                if (now >= ((Day5Apparition) apparition).getRemoveAtTick()) {
                    apparition.discard();
                    it.remove();
                }
            }
        }
    }

    /**
     * Called when a living entity dies (from the AFTER_DEATH event). Checks
     * if it's an infected pig and, if so, plays the tearing-flesh sound,
     * drops 2-3 flesh blocks and, with a 25% chance, spawns the fake player,
     * plays laggy1, applies darkness, and schedules the vanish.
     */
    public static void onDeath(net.minecraft.world.entity.LivingEntity entity,
                               net.minecraft.world.damagesource.DamageSource source) {
        if (!ModConfig.isEnabled("day5_pig")) return;
        if (!(entity instanceof Pig pig)) return;
        if (!isInfected(pig)) return;
        ServerLevel level = (ServerLevel) pig.level();

        // Find the killer: the player who dealt the killing blow (works even
        // at long range), falling back to the nearest player within 16 blocks.
        Player killer = null;
        if (source.getEntity() instanceof Player player) {
            killer = player;
        } else if (source.getDirectEntity() instanceof Player player) {
            killer = player;
        } else {
            killer = level.getNearestPlayer(pig, 16.0D);
        }

        // Always play the flesh sound — on the killer whenever there is one,
        // so the source follows them no matter how far away the pig died and
        // can never be walked away from; without a killer it stays at the
        // death spot (pony-cork for entities like a pig that died to the
        // environment).
        if (killer != null) {
            level.playSound(null, killer,
                    ModSounds.TEARING_FLESH, SoundSource.HOSTILE, 1.0F, 1.0F);
        } else {
            level.playSound(null, pig.getX(), pig.getY(), pig.getZ(),
                    ModSounds.TEARING_FLESH, SoundSource.HOSTILE, 1.0F, 1.0F);
        }

        // Always drop 2 or 3 flesh blocks at the death spot.
        int count = 2 + level.random.nextInt(2);
        level.addFreshEntity(new ItemEntity(level, pig.getX(), pig.getY(), pig.getZ(),
                new ItemStack(ModBlocks.FLESH_BLOCK, count)));

        // 25% chance to spawn the fake player.
        if (level.random.nextFloat() >= ModConfig.chance("day5_pig", FAKE_PLAYER_CHANCE)) return;
        if (killer == null) return;

        spawnFakePlayerApparition(level, pig, killer, level.getServer());
    }

    /**
     * Dev/test hook — spawns an infected pig right in front of each real
     * player. Used by {@code /noname event play day5_pig}.
     */
    public static void spawnOneNearEachPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Vec3 inFront = player.position()
                    .add(new Vec3(-Math.sin(Math.toRadians(player.getYRot())), 0.0D,
                            Math.cos(Math.toRadians(player.getYRot()))).scale(3.0D));
            ServerLevel level = player.serverLevel();
            Pig pig = new Pig(EntityType.PIG, level);
            pig.moveTo(inFront.x, inFront.y, inFront.z, player.getYRot(), 0.0F);
            tagInfected(pig);
            level.addFreshEntity(pig);
        }
    }

    /**
     * Dev/test hook — stop all active apparitions immediately.
     */
    public static void stopAll() {
        for (ServerPlayer apparition : apparitions) {
            apparition.discard();
        }
        apparitions.clear();
    }

    /** Checks whether a pig carries the day-5 infection flag. */
    public static boolean isInfected(Pig pig) {
        return pig instanceof InfectedPig ip && ip.noname$isInfected();
    }

    /** Tags a pig as infected via its dedicated synced flag. */
    public static void tagInfected(Pig pig) {
        if (pig instanceof InfectedPig ip) {
            ip.noname$setInfected(true);
        }
    }

    private static void spawnFakePlayerApparition(ServerLevel level, Pig pig,
                                                   Player killer, MinecraftServer server) {
        GameProfile profile = new GameProfile(FakePlayerUtil.FAKE_UUID,
                FakePlayerUtil.FAKE_NAME);
        ServerPlayer apparition = new Day5Apparition(server, level, profile,
                server.getTickCount() + FAKE_PLAYER_DURATION_TICKS);
        apparition.moveTo(pig.getX(), pig.getY(), pig.getZ(), 0.0F, 0.0F);
        apparition.setInvulnerable(true);
        apparition.setSilent(true);
        apparition.noPhysics = true;
        apparition.connection = new ServerGamePacketListenerImpl(server,
                FakePlayerHandler.createDummyConnection(), apparition,
                CommonListenerCookie.createInitial(profile, false));

        level.addFreshEntity(apparition);

        // Play the laggy1 sound on the killer's own entity so it follows
        // the player, not the apparition's static spawn spot.
        level.playSound(null, killer,
                ModSounds.LAGGY1, SoundSource.AMBIENT, 1.0F, 1.0F);

        // Give the killer darkness for 1 second.
        if (killer instanceof ServerPlayer sp) {
            sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS,
                    DARKNESS_DURATION_TICKS, 0, false, false, false));
            // Also play a brief "hurt" sound to the killer for impact.
            sp.playNotifySound(SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.5F, 1.0F);
        }

        apparitions.add(apparition);
    }

    /**
     * Extension of ServerPlayer that carries the tick at which it must be
     * removed. The standard tick handler in Day5PigHandler checks this.
     */
    private static final class Day5Apparition extends ServerPlayer {
        private final long removeAtTick;

        Day5Apparition(MinecraftServer server, ServerLevel level,
                       GameProfile profile, long removeAtTick) {
            super(server, level, profile, ClientInformation.createDefault());
            this.removeAtTick = removeAtTick;
        }

        long getRemoveAtTick() {
            return removeAtTick;
        }
    }
}