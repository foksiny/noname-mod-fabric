package dev.noname.client;

import dev.noname.config.ModConfig;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of the per-source "old VHS" low-pass filters created by {@link
 * dev.noname.mixin.ChannelMixin}.
 *
 * <p>An OpenAL source has exactly one direct filter slot
 * ({@code AL_DIRECT_FILTER}), so whoever writes it last owns it. Sound Physics
 * Remastered also writes that slot on every sound it processes (both at
 * play-time and periodically while a moving sound is playing), which wipes the
 * VHS low-pass away. Its reverb, however, is carried on the separate
 * {@code AL_AUXILIARY_SEND_FILTER} path, so re-attaching only the direct
 * filter is safe: SPR keeps echoing/occluding the wet signal while the dry
 * signal stays muffled like an old camcorder.
 *
 * <p>This side table maps each OpenAL source to the filter handle it was given
 * at channel creation, and hands that handle back out so a compat mixin can
 * re-attach it right after SPR touches the source. Callers are always the
 * client audio threads, so a plain map is enough.
 */
public final class VhsFilterManager {

    private static final Map<Integer, Integer> FILTER_BY_SOURCE = new HashMap<>();

    private VhsFilterManager() {
    }

    /**
     * Records the VHS filter attached to a freshly created source.
     */
    public static void register(int source, int filter) {
        FILTER_BY_SOURCE.put(source, filter);
    }

    /**
     * Forgets a source that is being destroyed.
     */
    public static void unregister(int source) {
        FILTER_BY_SOURCE.remove(source);
    }

    /**
     * Re-attaches the VHS filter to the given OpenAL source. No-op for sources
     * that don't have one (e.g. voice-chat sources that never went through a
     * vanilla audio channel).
     *
     * <p>When the VHS effect is disabled, the filter slot is cleared instead:
     * vanilla reuses channels, so an old filtered channel would otherwise keep
     * muffling new sounds.
     */
    public static void apply(int source) {
        if (!ModConfig.isEnabled("vhs_effect")) {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, 0);
            return;
        }
        Integer filter = FILTER_BY_SOURCE.get(source);
        if (filter != null) {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
        }
    }
}