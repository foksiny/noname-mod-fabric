package dev.noname;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Particle types added by Noname. Registered straight into the vanilla
 * {@link BuiltInRegistries#PARTICLE_TYPE} registry at mod init so the server
 * can send them through {@code ServerLevel.sendParticles} and the client can
 * look up a {@code ParticleProvider} for them.
 */
public final class ModParticles {

    /**
     * "Blood drop" — a falling drop of blood, spawned when a named blood mob
     * dies. Rendered by {@code dev.noname.client.BloodDropParticle}.
     */
    public static final SimpleParticleType BLOOD_DROP = FabricParticleTypes.simple(false);

    private ModParticles() {
    }

    /** Registers every particle type into the particle-type registry. */
    public static void register() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath(Noname.MODID, "blood_drop"), BLOOD_DROP);
    }
}
