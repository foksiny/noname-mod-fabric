package dev.noname.client;

import dev.noname.ModParticles;
import dev.noname.config.ModConfig;
import dev.noname.network.ModPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Client entrypoint: registers every client-only behaviour — the day-2
 * creep timing, the cave-ambience ticker, the creepy-bass render-distance
 * stinger, the day-5 "i can't stop doing it" flash, the "why don't you like
 * it? :(" HUD overlay, the menu button restrictions, the incoming event
 * payload handler and the re-install of the ghost's procedural flesh skin
 * after a resource reload.
 */
public final class NonameClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        NonameClientState.load();

        // The first time the player enters a world, the main menu's "Quit
        // Game" button is locked away forever.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                NonameClientState.lockQuitForever());

        ClientTickEvents.START_CLIENT_TICK.register(CaveSoundHandler::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(Day2CreepHandler::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(Day5FlashHandler::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(Day6Handler::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(CreepyBassStingerHandler::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(Day8SkyHandler::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(HeIsHereClient::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(TapeMotorHandler::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(ScrambledItemNameHandler::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(ShaderDetectionHandler::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(Day10WhisperHandler::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(CameraSpasmClient::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(StalkerDarknessHandler::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(StalkerStaticHandler::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(DoorAmbushClient::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(Day5DesktopClient::onClientTick);
        ClientTickEvents.START_CLIENT_TICK.register(BloodyNightClient::onClientTick);

        HudRenderCallback.EVENT.register(Day2CreepOverlay::onHudRender);
        HudRenderCallback.EVENT.register(Day5FlashOverlay::onHudRender);
        HudRenderCallback.EVENT.register(Day6Overlay::onHudRender);
        HudRenderCallback.EVENT.register(VhsOverlay::onHudRender);
        HudRenderCallback.EVENT.register(Day8SkyOverlay::onHudRender);
        HudRenderCallback.EVENT.register(HeIsHereOverlay::onHudRender);
        HudRenderCallback.EVENT.register(VersionOverlay::onHudRender);
        HudRenderCallback.EVENT.register(DayCounterOverlay::onHudRender);
        HudRenderCallback.EVENT.register(Day10WhisperOverlay::onHudRender);
        HudRenderCallback.EVENT.register(StalkerDarknessOverlay::onHudRender);
        HudRenderCallback.EVENT.register(StalkerStaticOverlay::onHudRender);
        ScreenEvents.AFTER_INIT.register(ButtonRestrictionsHandler::onScreenInit);

        // Blood drops from dying named mobs.
        ParticleFactoryRegistry.getInstance()
                .register(ModParticles.BLOOD_DROP, BloodDropParticle.Provider::new);

        // The "ise it" apparition: a flat billboard entity.
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                dev.noname.ModEntities.ISE_IT, IseItRenderer::new);

        // The cave stalker: a vanilla-style zombie renderer.
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                dev.noname.ModEntities.CAVE_ZOMBIE,
                ctx -> new net.minecraft.client.renderer.entity.ZombieRenderer(ctx));

        // Stitch alpha chest textures into vanilla atlas format
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new ChestTextureStitcher());

        // F3+T (client resource reload) closes every registered texture, so
        // the synthesized ghost skin would fall back to the packed PNG; put
        // the runtime-generated texture back right after the reload.
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return ResourceLocation.fromNamespaceAndPath(
                                dev.noname.Noname.MODID, "flesh_skin_reload");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        FakeSkin.reinstall();
                        NullSkin.reinstall();
                        VhsOverlay.reinstall();
                        HeIsHereOverlay.reinstall();
                    }
                });

        ModPayloads.registerClient();
    }
}
