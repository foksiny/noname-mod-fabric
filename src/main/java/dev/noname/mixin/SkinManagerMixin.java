package dev.noname.mixin;

import com.mojang.authlib.GameProfile;
import dev.noname.client.FakeSkin;
import dev.noname.client.NullSkin;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the Noname fake players their custom skins (from the mod's assets)
 * instead of the default skin. {@link SkinManager#getOrLoad} is the single
 * lookup used by both the player renderer and the tab-list head icons, so
 * hooking it covers everything:
 * <ul>
 *   <li>the day-3 ghost ({@link FakeSkin#get()}) — the procedural flesh skin,</li>
 *   <li>the day-2 {@code null} visitor ({@link NullSkin#get()}) — a flat
 *       opaque-black skin, shown in the tab-list head icon for the ~3
 *       seconds the {@code null} entry is in the player list.</li>
 * </ul>
 */
@Mixin(SkinManager.class)
public abstract class SkinManagerMixin {

    @Inject(method = "getOrLoad", at = @At("HEAD"), cancellable = true)
    private void noname$ghostSkin(GameProfile profile, CallbackInfoReturnable<CompletableFuture<PlayerSkin>> cir) {
        if (FakeSkin.isGhostProfile(profile)) {
            cir.setReturnValue(CompletableFuture.completedFuture(FakeSkin.get()));
        } else if (NullSkin.isNullProfile(profile)) {
            cir.setReturnValue(CompletableFuture.completedFuture(NullSkin.get()));
        }
    }
}
