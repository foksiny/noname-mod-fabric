package dev.noname.client;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileSystemView;
import java.awt.AWTError;
import java.awt.Color;
import java.awt.Font;
import java.awt.HeadlessException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Day-8 smile event (client side): a real window on the player's desktop
 * (not an in-game overlay) with an <i>empty</i> title, showing a big white
 * "{@code :)}" on black, that floats on top of the game window and closes
 * when the player dismisses it (X button). At the same moment a text file
 * named {@code " .txt"} (no title, just the extension with a leading space
 * so no system hides it as a dotfile) is written onto the desktop, its
 * content "{@code ?em fo kniht uoy od tahw}".
 *
 * <p>Triggered once by the server at the day 7 → 8 transition via the
 * {@code day8_smile} payload.
 *
 * <p>If the JVM's AWT is headless (some launchers force it, or the desktop
 * is Wayland-only so AWT has no display while the game itself renders fine),
 * the Swing window cannot be created; the pop-up then falls back to a real
 * native desktop dialog (zenity / kdialog / xmessage on Linux, osascript on
 * macOS, a PowerShell message box on Windows) with the same empty title and
 * text.
 */
public final class Day8SmileClient {

    static {
        // Launchers and Wayland-only desktops often force AWT headless,
        // which kills Swing windows before they can appear. Ask for a real
        // display before any AWT class initializes; if AWT still can't make
        // windows, showNativeDialog() falls back to a native dialog.
        System.setProperty("java.awt.headless", "false");
    }

    /** File name written onto the desktop — no title, just the extension,
     *  prefixed with a space so the leading character is not a dot: dotfiles
     *  are hidden on Linux and macOS, and this way every system's file
     *  manager shows it. */
    private static final String FILE_NAME = " .txt";

    /** The file's content — "what do you think of me?" reversed. */
    private static final String FILE_CONTENT = "?em fo kniht uoy od tahw";

    /** Native dialogs tried in order when AWT can't create windows: GNOME's
     *  zenity, KDE's kdialog and the X11 fallback xmessage on Linux,
     *  osascript on macOS, and a PowerShell message box on Windows. Tools
     *  that aren't installed simply fail to start and the next one is tried. */
    private static final String[][] NATIVE_DIALOGS = {
            {"zenity", "--info", "--title=", "--text=:)"},
            {"kdialog", "--title", "", "--msgbox", ":)"},
            {"xmessage", "-center", ":)"},
            {"osascript", "-e", "display dialog \":)\" with title \"\""},
            {"powershell", "-NoProfile", "-Command",
                    "Add-Type -AssemblyName PresentationFramework; "
                            + "[System.Windows.MessageBox]::Show(':)','','OK','Information')"},
    };

    private static JFrame window;

    /** The native dialog process currently up, or {@code null}. */
    private static Process nativeDialog;

    private Day8SmileClient() {
    }

    /**
     * Payload handler — pop the smile window and write the desktop file now.
     * Called by the client dispatcher when the {@code day8_smile} event
     * arrives.
     */
    public static void start() {
        showNow();
        writeFile();
    }

    /** Pops the window up (call from any thread — work happens on the Swing
     *  event thread). */
    public static void showNow() {
        SwingUtilities.invokeLater(Day8SmileClient::createWindow);
    }

    /** Closes the window if it is up. */
    public static void closeWindow() {
        SwingUtilities.invokeLater(Day8SmileClient::closeWindowNow);
    }

    private static void createWindow() {
        // Already showing: don't stack a second window.
        if (window != null && window.isDisplayable()) {
            return;
        }
        try {
            JFrame frame = new JFrame();
            frame.setTitle("");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setAlwaysOnTop(true);
            frame.setResizable(false);

            JLabel label = new JLabel(":)", SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(Color.BLACK);
            label.setForeground(Color.WHITE);
            label.setFont(new Font("SansSerif", Font.BOLD, 48));
            frame.add(label);

            frame.setSize(200, 150);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            window = frame;
        } catch (HeadlessException | AWTError | UnsatisfiedLinkError | NoClassDefFoundError e) {
            // AWT can't make windows in this environment (headless JVM,
            // Wayland-only session, ...): fall back to a real native dialog.
            showNativeDialog();
        }
    }

    /** Opens a native desktop dialog via zenity/kdialog/xmessage/osascript. */
    private static void showNativeDialog() {
        if (nativeDialog != null && nativeDialog.isAlive()) {
            return;
        }
        for (String[] command : NATIVE_DIALOGS) {
            try {
                nativeDialog = new ProcessBuilder(command).start();
                return;
            } catch (IOException e) {
                // Tool not installed — try the next one.
            }
        }
    }

    /** Closes whichever popup is up (Swing window or native dialog). */
    private static void closeWindowNow() {
        if (window != null) {
            window.dispose();
            window = null;
        }
        if (nativeDialog != null && nativeDialog.isAlive()) {
            nativeDialog.destroy();
            nativeDialog = null;
        }
    }

    /**
     * Writes "{@code ?em fo kniht uoy od tahw}" as {@code " .txt"} onto the
     * desktop. A missing desktop directory or a write failure is silently
     * ignored (log only) — the scare must never crash the game.
     */
    private static void writeFile() {
        Path desktop = desktopDir();
        if (desktop == null) {
            return;
        }
        try {
            Files.createDirectories(desktop);
            Files.writeString(desktop.resolve(FILE_NAME), FILE_CONTENT, StandardCharsets.UTF_8);
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
