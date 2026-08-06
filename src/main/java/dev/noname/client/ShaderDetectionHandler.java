package dev.noname.client;

import java.lang.reflect.Method;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side watchdog that detects Iris shader packs and forbids them.
 *
 * <p>Noname's entire visual identity is built on its own VHS post-chain
 * ({@link dev.noname.mixin.VhsGlassesMixin}) layered on top of vanilla's
 * fog / light / colour mixins. An external Iris shader pack overrides both
 * halves — it replaces the vanilla render pipeline wholesale — which
 * dismantles the intended atmosphere and breaks the custom effect chain.
 * So the mod tells the player that shaders are not supported and quietly
 * turns them off through Iris' own public API.
 *
 * <p>Iris is never a hard dependency: its API is reached reflectively, so the
 * mod still loads and runs perfectly when Iris is absent. The watchdog is a
 * no-op in that case.
 */
public final class ShaderDetectionHandler {

    /** Mod id Iris registers itself under with the Fabric loader. */
    private static final String IRIS_MOD_ID = "iris";

    /** How often (in client ticks) the shader state is re-checked while in a
     *  world. Five seconds is often enough to react to a player flipping a
     *  shader on mid-session, and rare enough to be free. */
    private static final int RECHECK_INTERVAL_TICKS = 20 * 5;

    private static final Logger LOGGER = LoggerFactory.getLogger("Noname/ShaderDetection");

    private static boolean irisPresent;
    private static boolean irisChecked;

    private static boolean warnedThisSession;
    private static int recheckTicks;

    private static Class<?> irisApiClass;
    private static Object irisApiInstance;
    private static Method apiIsShaderPackInUse;
    private static Method apiGetConfig;
    private static Method configAreShadersEnabled;
    private static Method configSetShadersEnabledAndApply;

    private ShaderDetectionHandler() {
    }

    public static void onClientTick(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            warnedThisSession = false;
            recheckTicks = 0;
            return;
        }

        if (!checkIrisPresent() || !bindIrisApi()) {
            return;
        }
        if (--recheckTicks > 0) {
            return;
        }
        recheckTicks = RECHECK_INTERVAL_TICKS;

        if (shadersCurrentlyActive()) {
            forceDisableAndWarn(mc);
        }
    }

    private static boolean checkIrisPresent() {
        if (!irisChecked) {
            irisChecked = true;
            irisPresent = FabricLoader.getInstance().isModLoaded(IRIS_MOD_ID);
        }
        return irisPresent;
    }

    private static boolean bindIrisApi() {
        if (irisApiClass == null) {
            try {
                ClassLoader loader = ShaderDetectionHandler.class.getClassLoader();
                irisApiClass = Class.forName(
                        "net.irisshaders.iris.api.v0.IrisApi", false, loader);
                Method getInstance = irisApiClass.getMethod("getInstance");
                irisApiInstance = getInstance.invoke(null);
                apiIsShaderPackInUse = irisApiClass.getMethod("isShaderPackInUse");
                apiGetConfig = irisApiClass.getMethod("getConfig");
                Class<?> cfgClass = Class.forName(
                        "net.irisshaders.iris.api.v0.IrisApiConfig", false, loader);
                configAreShadersEnabled = cfgClass.getMethod("areShadersEnabled");
                configSetShadersEnabledAndApply = cfgClass.getMethod(
                        "setShadersEnabledAndApply", boolean.class);
            } catch (Exception e) {
                LOGGER.warn("Iris present but its API is unavailable; "
                        + "cannot inspect or disable shader packs: {}", e.toString());
                irisApiClass = null;
                return false;
            }
        }
        return irisApiInstance != null;
    }

    private static boolean shadersCurrentlyActive() {
        try {
            boolean packInUse = (boolean) apiIsShaderPackInUse.invoke(irisApiInstance);
            Object config = apiGetConfig.invoke(irisApiInstance);
            boolean enabled = (boolean) configAreShadersEnabled.invoke(config);
            return packInUse || enabled;
        } catch (Exception e) {
            LOGGER.warn("Failed to query Iris shader state: {}", e.toString());
            return false;
        }
    }

    private static void forceDisableAndWarn(Minecraft mc) {
        boolean disabled = disableShadersSoft();
        if (!warnedThisSession) {
            warnedThisSession = true;
            mc.player.sendSystemMessage(Component.literal(
                    "[Noname] Shader pack detected and auto-disabled.")
                    .withStyle(ChatFormatting.RED));
            mc.player.sendSystemMessage(Component.literal(
                    "[Noname] This mod relies on its own visual effects; "
                            + "external shaders break the intended experience.")
                    .withStyle(ChatFormatting.GOLD));
        } else if (!disabled) {
            mc.player.sendSystemMessage(Component.literal(
                    "[Noname] Shader still enabled — please disable shaders "
                            + "in the Iris video settings screen.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static boolean disableShadersSoft() {
        try {
            Object config = apiGetConfig.invoke(irisApiInstance);
            configSetShadersEnabledAndApply.invoke(config, false);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Could not auto-disable Iris shaders: {}", e.toString());
            return false;
        }
    }
}
