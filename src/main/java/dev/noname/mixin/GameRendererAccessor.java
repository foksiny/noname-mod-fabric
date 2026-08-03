package dev.noname.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor that exposes the private post-effect plumbing of {@link
 * GameRenderer} to {@link VhsGlassesMixin}, so the mod can install and tear
 * down its own full-screen shader chain exactly the way vanilla's
 * creeper/spider/enderman screen filters do.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Invoker("loadEffect")
    void noname$loadEffect(ResourceLocation effect);

    @Invoker("shutdownEffect")
    void noname$shutdownEffect();
}