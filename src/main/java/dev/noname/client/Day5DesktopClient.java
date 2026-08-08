package dev.noname.client;

import net.minecraft.client.Minecraft;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Locale;

/**
 * Day-5 desktop watcher. Once armed (by the server at day-5 noon via the
 * {@code day5_desktop} payload), it waits until the player actually looks at
 * their desktop — the game window loses focus, e.g. alt-tab or minimize —
 * and then writes {@code hello.txt} to the desktop. The file's content is
 * the ghost's farewell letter, base64-encoded, with the player's own PC user
 * name inserted.
 *
 * <p>The desktop directory adapts to the OS <i>and</i> to the system
 * language. On Windows the desktop is a shell folder whose filesystem name
 * follows the OS language (Рабочий стол, 桌面, Schreibtisch, …) and may be
 * redirected to OneDrive, so the real folder is asked from the shell itself
 * ({@link FileSystemView}) — that covers every language, not just a fixed
 * list, and follows redirections. macOS never localizes the path
 * ({@code ~/Desktop}), and on Linux the folder configured by the desktop
 * environment is read from the {@code XDG_DESKTOP_DIR} user dir or
 * {@code ~/.config/user-dirs.dirs}, so localized folders such as the
 * Portuguese {@code ~/Área de Trabalho} or the Chinese {@code ~/桌面} are
 * found too.
 */
public final class Day5DesktopClient {

    /** File name written onto the desktop. */
    private static final String FILE_NAME = "hello.txt";

    /** Whether the watcher is armed and waiting for the window to lose
     *  focus. */
    private static boolean armed = false;

    /** Whether the game window was focused on the previous client tick. */
    private static boolean wasActive = true;

    private Day5DesktopClient() {
    }

    public static void onClientTick(Minecraft mc) {
        if (!armed) {
            return;
        }
        boolean active = mc.isWindowActive();
        // Edge-triggered: only when the window goes from focused to
        // unfocused — the moment the player looks at the desktop.
        if (wasActive && !active) {
            armed = false;
            writeHelloFile();
        }
        wasActive = active;
    }

    /**
     * Dev/test hook / payload handler — arm the watcher now. Called by the
     * client dispatcher when the {@code day5_desktop} event arrives.
     */
    public static void start() {
        armed = true;
        wasActive = Minecraft.getInstance().isWindowActive();
    }

    /** Dev/test hook — disarm the watcher. Used by {@code /noname event
     *  stopall}. */
    public static void stopAll() {
        armed = false;
    }

    /**
     * Builds the letter with the player's PC user name, base64-encodes it
     * and writes it as {@code hello.txt} onto the desktop. A missing desktop
     * directory or a write failure is silently ignored (log only) — the
     * scare must never crash the game.
     */
    private static void writeHelloFile() {
        String userName = System.getProperty("user.name", "friend");
        String message = "Hello.\n\n"
                + "Dear " + userName + ", my name is ?????, and i'm here to say that i died.\n\n"
                + "I liked to play with you when we were kids, remember? Probably you don't even care.\n"
                + "I wanted to ask why did you let him do this to me, and why you didn't believe me.\n"
                + "Anyways, i'll just stop here. Bye.";
        String encoded = Base64.getEncoder()
                .encodeToString(message.getBytes(StandardCharsets.UTF_8));

        Path desktop = desktopDir();
        if (desktop == null) {
            return;
        }
        try {
            Files.createDirectories(desktop);
            Files.writeString(desktop.resolve(FILE_NAME), encoded, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** {@return the user's desktop directory, or {@code null} if it cannot
     *  be determined} */
    private static Path desktopDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home", "");
        if (os.contains("win")) {
            return windowsDesktopDir(home);
        }
        if (os.contains("mac")) {
            // macOS never localizes the Desktop path: /Users/<name>/Desktop.
            return Paths.get(home, "Desktop");
        }
        return linuxDesktopDir(home);
    }

    /**
     * Linux desktop resolution, in order of reliability:
     * <ol>
     *   <li>the {@code XDG_DESKTOP_DIR} env var (authoritative when a desktop
     *       environment exports it, but most do not);</li>
     *   <li>{@code ~/.config/user-dirs.dirs} — written by the desktop
     *       environment, it holds the localized folder, e.g.
     *       {@code XDG_DESKTOP_DIR="$HOME/Área de Trabalho"} (pt_BR) or
     *       {@code "$HOME/桌面"} (zh_CN);</li>
     *   <li>{@code ~/Desktop} as a last-resort guess.</li>
     * </ol>
     */
    private static Path linuxDesktopDir(String home) {
        String xdg = System.getenv("XDG_DESKTOP_DIR");
        if (xdg != null && !xdg.isEmpty()) {
            return expandHome(Paths.get(xdg), home);
        }
        Path userDirs = Paths.get(home, ".config", "user-dirs.dirs");
        if (Files.isReadable(userDirs)) {
            try {
                for (String line : Files.readAllLines(userDirs, StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (line.startsWith("XDG_DESKTOP_DIR=")) {
                        String value = line.substring("XDG_DESKTOP_DIR=".length()).trim();
                        value = value.replaceAll("^\"|\"$", "");
                        if (!value.isEmpty()) {
                            return expandHome(Paths.get(value), home);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return Paths.get(home, "Desktop");
    }

    /** Expands a leading {@code $HOME}, {@code ${HOME}} or {@code ~} in the
     *  path against the given home directory. */
    private static Path expandHome(Path path, String home) {
        String s = path.toString();
        if (s.startsWith("${HOME}")) {
            s = home + s.substring("${HOME}".length());
        } else if (s.startsWith("$HOME")) {
            s = home + s.substring("$HOME".length());
        } else if (s.equals("~") || s.startsWith("~/")) {
            s = home + s.substring(1);
        }
        return Paths.get(s);
    }

    /**
     * The Windows desktop is a shell folder whose filesystem name follows the
     * system language (Рабочий стол, 桌面, Schreibtisch, Escritorio, …) and may
     * be redirected (e.g. OneDrive). Asking the shell for the real folder via
     * {@link FileSystemView#getDefaultDirectory()} handles every language and
     * redirection; the plain {@code ~/Desktop} guess only works on English
     * systems, so it is kept as a last resort.
     */
    private static Path windowsDesktopDir(String home) {
        try {
            File desktop = FileSystemView.getFileSystemView().getDefaultDirectory();
            if (desktop != null) {
                return desktop.toPath();
            }
        } catch (Throwable ignored) {
            // java.desktop unavailable or no shell to ask — fall through.
        }
        return Paths.get(home, "Desktop");
    }
}
