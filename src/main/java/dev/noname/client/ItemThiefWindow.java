package dev.noname.client;

import net.minecraft.world.item.ItemStack;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTError;
import java.awt.BorderLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.io.IOException;

/**
 * Day-15+ item-thief pop-up (client side): a real window on the player's
 * desktop (not an in-game overlay) with an <i>empty</i> title — just the
 * message "i took a &lt;item name&gt; from you :)" with the stolen item's
 * name in bold. It floats on top of the game window, sits in the bottom-right
 * corner above the taskbar, closes on the X button, on "OK", or by itself
 * after a few seconds (it can keep recurring every 3-7 minutes, so it must
 * never pile up or block the game).
 *
 * <p>If the JVM's AWT is headless (some launchers force it, or the desktop
 * is Wayland-only so AWT has no display while the game itself renders fine),
 * the Swing window cannot be created; the pop-up then falls back to a real
 * native desktop dialog (zenity / kdialog / xmessage on Linux, osascript on
 * macOS, a PowerShell message box on Windows) with the same empty title and
 * text.
 */
public final class ItemThiefWindow {

    static {
        // Launchers and Wayland-only desktops often force AWT headless,
        // which kills Swing windows before they can appear. Ask for a real
        // display before any AWT class initializes; if AWT still can't make
        // windows, showNativeDialog() falls back to a native dialog.
        System.setProperty("java.awt.headless", "false");
    }

    /** Seconds before the window closes itself. */
    private static final int AUTO_CLOSE_SECONDS = 8;

    private static JFrame window;

    /** The native dialog process currently up, or {@code null}. */
    private static Process nativeDialog;

    private ItemThiefWindow() {
    }

    /**
     * Pops the "i took a &lt;item name&gt; from you :)" window up (call from
     * any thread — work happens on the Swing event thread).
     */
    public static void show(ItemStack stack) {
        String itemName = stack.getHoverName().getString();
        SwingUtilities.invokeLater(() -> createWindow(itemName));
    }

    /** Closes the window if it is up (used by {@code /noname event stopall}). */
    public static void closeNow() {
        SwingUtilities.invokeLater(ItemThiefWindow::closeWindow);
    }

    private static void createWindow(String itemName) {
        // A previous window may still be up (it auto-closes, but the player
        // might have missed it): replace it instead of stacking another.
        if (window != null && window.isDisplayable()) {
            window.dispose();
        }
        try {
            JFrame frame = new JFrame();
            frame.setTitle("");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setAlwaysOnTop(true);
            frame.setResizable(false);

            JPanel content = new JPanel(new BorderLayout(0, 14));
            content.setBorder(BorderFactory.createEmptyBorder(18, 26, 18, 26));

            JLabel message = new JLabel(
                    "<html><div style='text-align:center'>i took a <b>"
                            + escape(itemName) + "</b> from you :)</div></html>",
                    SwingConstants.CENTER);
            content.add(message, BorderLayout.CENTER);

            JButton ok = new JButton("OK");
            ok.addActionListener(event -> frame.dispose());
            JPanel bottom = new JPanel();
            bottom.add(ok);
            content.add(bottom, BorderLayout.SOUTH);

            frame.add(content);
            frame.pack();
            placeBottomRight(frame);
            frame.setVisible(true);
            window = frame;

            // Never leave a stray window behind: close it after a while even
            // if the player ignores it.
            Timer timer = new Timer(AUTO_CLOSE_SECONDS * 1000, event -> frame.dispose());
            timer.setRepeats(false);
            timer.start();
        } catch (HeadlessException | AWTError | UnsatisfiedLinkError | NoClassDefFoundError e) {
            // AWT can't make windows in this environment (headless JVM,
            // Wayland-only session, ...): fall back to a real native dialog.
            showNativeDialog(itemName);
        }
    }

    /** Opens a native desktop dialog via zenity/kdialog/xmessage/osascript. */
    private static void showNativeDialog(String itemName) {
        if (nativeDialog != null && nativeDialog.isAlive()) {
            return;
        }
        String text = "i took a " + itemName + " from you :)";
        String[][] commands = {
                {"zenity", "--info", "--title=", "--text=" + text},
                {"kdialog", "--title", "", "--msgbox", text},
                {"xmessage", "-center", text},
                {"osascript", "-e",
                        "display dialog \"" + text.replace("\"", "'") + "\" with title \"\""},
                {"powershell", "-NoProfile", "-Command",
                        "Add-Type -AssemblyName PresentationFramework; "
                                + "[System.Windows.MessageBox]::Show('"
                                + text.replace("'", "''") + "','','OK','Information')"},
        };
        for (String[] command : commands) {
            try {
                nativeDialog = new ProcessBuilder(command).start();
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
    }

    /** Positions the window in the bottom-right corner, clear of the taskbar. */
    private static void placeBottomRight(JFrame frame) {
        try {
            GraphicsDevice device = GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getDefaultScreenDevice();
            Rectangle bounds = device.getDefaultConfiguration().getBounds();
            Insets insets = Toolkit.getDefaultToolkit()
                    .getScreenInsets(device.getDefaultConfiguration());
            frame.setLocation(
                    bounds.x + bounds.width - frame.getWidth() - insets.right - 16,
                    bounds.y + bounds.height - frame.getHeight() - insets.bottom - 16);
        } catch (HeadlessException | AWTError e) {
            // No usable screen geometry — let the window manager decide.
            frame.setLocationByPlatform(true);
        }
    }

    /** Escapes a string for safe use inside the label's HTML. */
    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
