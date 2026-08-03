package dev.noname.client;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.AWTError;
import java.awt.Color;
import java.awt.Font;
import java.awt.HeadlessException;
import java.io.File;
import java.io.IOException;

/**
 * The day-4 75% pop-up: a real window on the player's desktop (not an
 * in-game overlay) that pops up saying "help me" — a black window with a big
 * red "help me" in the middle, titled "why". It floats on top of the game
 * window, plays the morse-code wav once in the background and closes when
 * the player dismisses it (X button).
 *
 * <p>If the JVM's AWT is headless (some launchers force it, or the desktop
 * is Wayland-only so AWT has no display while the game itself renders fine),
 * the Swing window cannot be created; the pop-up then falls back to a real
 * native desktop dialog (zenity / kdialog / xmessage on Linux, osascript on
 * macOS, a PowerShell message box on Windows) with the same title, text and
 * background sound.
 */
public final class Day4HelpPopup {

    static {
        // Launchers and Wayland-only desktops often force AWT headless,
        // which kills Swing windows before they can appear. Ask for a real
        // display before any AWT class initializes; if AWT still can't make
        // windows, showNativeDialog() falls back to a native dialog.
        System.setProperty("java.awt.headless", "false");
    }

    /** The wav played in the background while the window is up. */
    private static final String MORSE_WAV = "/home/foksiny/Downloads/morse.wav";

    /** Native dialogs tried in order when AWT can't create windows: GNOME's
     *  zenity, KDE's kdialog and the X11 fallback xmessage on Linux,
     *  osascript on macOS, and a PowerShell message box on Windows. Tools
     *  that aren't installed simply fail to start and the next one is tried. */
    private static final String[][] NATIVE_DIALOGS = {
            {"zenity", "--warning", "--title=why", "--text=help me"},
            {"kdialog", "--title", "why", "--msgbox", "help me"},
            {"xmessage", "-center", "help me"},
            {"osascript", "-e", "display dialog \"help me\" with title \"why\""},
            {"powershell", "-NoProfile", "-Command",
                    "Add-Type -AssemblyName PresentationFramework; "
                            + "[System.Windows.MessageBox]::Show('help me','why','OK','Warning')"},
    };

    private static JFrame window;

    private static Clip clip;

    /** The native dialog process currently up, or {@code null}. */
    private static Process nativeDialog;

    private Day4HelpPopup() {
    }

    /** Pops the window up (call from any thread — work happens on the Swing
     *  event thread). */
    public static void showNow() {
        SwingUtilities.invokeLater(Day4HelpPopup::createWindow);
    }

    /** Closes the window (and the morse) if either is up. */
    public static void closeNow() {
        SwingUtilities.invokeLater(Day4HelpPopup::closeWindow);
    }

    private static void createWindow() {
        // Already showing: don't stack a second window.
        if (window != null && window.isDisplayable()) {
            return;
        }
        try {
            JFrame frame = new JFrame();
            frame.setTitle("why");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setAlwaysOnTop(true);
            frame.setResizable(false);

            // Whatever path closes the window (X button, stopall), the morse
            // stops with it.
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent event) {
                    stopBackgroundSound();
                }
            });

            JLabel label = new JLabel("help me", SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(Color.BLACK);
            label.setForeground(Color.RED);
            label.setFont(new Font("SansSerif", Font.BOLD, 48));
            frame.add(label);

            frame.setSize(400, 200);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            window = frame;
            startBackgroundSound();
        } catch (HeadlessException | AWTError | UnsatisfiedLinkError | NoClassDefFoundError e) {
            // AWT can't make windows in this environment (headless JVM,
            // Wayland-only session, ...): fall back to a real native dialog.
            showNativeDialog();
        }
    }

    /** Opens a native desktop dialog via zenity/kdialog/xmessage/osascript and
     *  plays the morse while it is up. */
    private static void showNativeDialog() {
        if (nativeDialog != null && nativeDialog.isAlive()) {
            return;
        }
        for (String[] command : NATIVE_DIALOGS) {
            try {
                Process process = new ProcessBuilder(command).start();
                nativeDialog = process;
                startBackgroundSound();
                // Keep the morse only while the dialog is up.
                new Thread(() -> {
                    try {
                        process.waitFor();
                    } catch (InterruptedException ignored) {
                    }
                    stopBackgroundSound();
                }, "noname-popup-waiter").start();
                return;
            } catch (IOException e) {
                // Tool not installed — try the next one.
            }
        }
    }

    /** Closes whichever popup is up (Swing window or native dialog). */
    private static void closeWindow() {
        if (window != null) {
            window.dispose();
            window = null;
        }
        if (nativeDialog != null && nativeDialog.isAlive()) {
            nativeDialog.destroy();
            nativeDialog = null;
        }
        stopBackgroundSound();
    }

    /** Starts the morse wav playing once in the background. */
    private static void startBackgroundSound() {
        stopBackgroundSound();
        try {
            File file = new File(MORSE_WAV);
            if (!file.isFile()) {
                return;
            }
            AudioInputStream stream = AudioSystem.getAudioInputStream(file);
            Clip newClip = AudioSystem.getClip();
            newClip.open(stream);
            newClip.start();
            clip = newClip;
        } catch (Exception e) {
            // Never let audio problems take the popup (or the game) down.
        }
    }

    /** Stops and frees the looping morse clip. */
    private static void stopBackgroundSound() {
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
    }
}
