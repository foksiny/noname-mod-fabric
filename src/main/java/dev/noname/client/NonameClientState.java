package dev.noname.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Hidden, client-only progress of the Noname mod, persisted as JSON in the
 * config directory ({@code config/noname-state.json}). Unlike the
 * user-facing {@link dev.noname.config.ModConfig}, these are irreversible
 * once set — the player is never offered a way back.
 *
 * <p>Currently tracks whether the player has joined a world at least once.
 * The first time that happens the main menu's "Quit Game" button is disabled
 * forever, even across restarts and other worlds.
 */
public final class NonameClientState {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path statePath;
    private static boolean quitLocked;

    private NonameClientState() {
    }

    /** Loads the state file (creating it lazily on first write). */
    public static void load() {
        if (statePath == null) {
            statePath = FabricLoader.getInstance()
                    .getConfigDir().resolve("noname-state.json");
        }
        if (!Files.exists(statePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(statePath)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null) {
                quitLocked = data.quitLocked;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** {@return true if the main menu's "Quit Game" button is locked away} */
    public static boolean isQuitLocked() {
        return quitLocked;
    }

    /** Locks the "Quit Game" button forever (no-op if already locked). */
    public static void lockQuitForever() {
        if (!quitLocked) {
            quitLocked = true;
            save();
        }
    }

    private static void save() {
        if (statePath == null) {
            statePath = FabricLoader.getInstance()
                    .getConfigDir().resolve("noname-state.json");
        }
        Data data = new Data();
        data.quitLocked = quitLocked;
        try {
            Files.createDirectories(statePath.getParent());
            try (Writer writer = Files.newBufferedWriter(statePath)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Mirror of the persisted JSON. */
    private static final class Data {
        boolean quitLocked;
    }
}
