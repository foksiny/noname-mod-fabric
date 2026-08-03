package dev.noname.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A single blood drop, spawned when a named blood mob dies. Each drop pops
 * out of the burst with a random little spray velocity, is pulled down by
 * gravity, and — once it reaches the ground — stays there until its 5 to 7
 * second lifetime (randomized per drop) runs out, fading away in the last
 * second.
 */
public final class BloodDropParticle extends TextureSheetParticle {

    /** Lifetime range in ticks: 5 seconds (100 ticks) to 7 seconds (140). */
    private static final int MIN_LIFETIME = 20 * 5;
    private static final int MAX_LIFETIME = 20 * 7;

    /** How long before despawning the drop fades out, in ticks (1 s). */
    private static final int FADE_TICKS = 20;

    private BloodDropParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.setSprite(sprites.get(level.random));
        this.lifetime = MIN_LIFETIME + level.random.nextInt(MAX_LIFETIME - MIN_LIFETIME + 1);
        this.gravity = 0.06F;
        this.quadSize = 0.08F + level.random.nextFloat() * 0.05F;
        // Random spray: mostly sideways with a small pop upwards.
        this.xd = (level.random.nextDouble() - 0.5) * 0.4;
        this.yd = level.random.nextDouble() * 0.25 + 0.05;
        this.zd = (level.random.nextDouble() - 0.5) * 0.4;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        // Fade out in the last second.
        if (this.age > this.lifetime - FADE_TICKS) {
            this.setAlpha(Math.max(0.0F,
                    1.0F - (this.age - (this.lifetime - FADE_TICKS)) / (float) FADE_TICKS));
        }

        if (this.onGround) {
            return;   // landed: just age in place until it fades
        }

        this.yd -= this.gravity;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.9D;
        this.zd *= 0.9D;

        // Land on the first solid block below and stay there.
        BlockPos below = BlockPos.containing(this.x, this.y - 0.1D, this.z);
        if (this.yd < 0.0D && !this.level.getBlockState(below).isAir()) {
            this.y = below.getY() + 1.0D - 0.05D;
            this.xd = 0.0D;
            this.yd = 0.0D;
            this.zd = 0.0D;
            this.onGround = true;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Provider registered against {@link dev.noname.ModParticles#BLOOD_DROP}. */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new BloodDropParticle(level, x, y, z, this.sprites);
        }
    }
}
