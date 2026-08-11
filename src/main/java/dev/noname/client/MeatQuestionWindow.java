package dev.noname.client;

import dev.noname.network.MeatQuestionAnswerPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.AWTError;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.HeadlessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Day-10 "question" pop-up (client side): a real window on the player's
 * desktop (not an in-game overlay) titled {@code question}, asking
 * {@code do you like meat} with a {@code yes} and a {@code no} button. It
 * floats on top of the game window and stays up until answered — unlike the
 * item-thief pop-up it never closes itself, because the server is waiting
 * for the answer.
 *
 * <p>Only an explicit "yes" counts as yes; the "no" button, the X button and
 * any other way of closing the window all count as "no" (the question cannot
 * be dodged). The answer is sent to the server through
 * {@link MeatQuestionAnswerPayload}; the server decides the consequences
 * (half a heart of damage, or halved max hearts plus a kick).
 *
 * <p>If the JVM's AWT is headless (some launchers force it, or the desktop
 * is Wayland-only so AWT has no display while the game itself renders fine),
 * the Swing window cannot be created; the pop-up then falls back to a real
 * native desktop dialog (zenity / kdialog / xmessage on Linux, osascript on
 * macOS, a PowerShell message box on Windows) with the same title, text and
 * buttons.
 */
public final class MeatQuestionWindow {

    static {
        // Launchers and Wayland-only desktops often force AWT headless,
        // which kills Swing windows before they can appear. Ask for a real
        // display before any AWT class initializes; if AWT still can't make
        // windows, showNativeDialog() falls back to a native dialog.
        System.setProperty("java.awt.headless", "false");
    }

    private static JFrame window;

    /** The native dialog process currently up, or {@code null}. */
    private static Process nativeDialog;

    /** Whether an answer was already sent for the current question. */
    private static boolean answered = false;

    /** True while stopall closes the window: a forced close must not count
     *  as an answer. */
    private static boolean suppressAnswer = false;

    private MeatQuestionWindow() {
    }

    /**
     * Pops the "question" window up (call from any thread — work happens on
     * the Swing event thread).
     */
    public static void show() {
        SwingUtilities.invokeLater(MeatQuestionWindow::createWindow);
    }

    /** Closes the window without answering (used by
     *  {@code /noname event stopall}). */
    public static void closeNow() {
        SwingUtilities.invokeLater(() -> {
            suppressAnswer = true;
            closeWindow();
        });
    }

    private static void createWindow() {
        // A previous window may still be up (the same question is already
        // being asked): never stack or replace it, or the old window's close
        // would count as an answer.
        if (window != null && window.isDisplayable()) {
            return;
        }
        answered = false;
        suppressAnswer = false;
        try {
            JFrame frame = new JFrame();
            frame.setTitle("question");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setAlwaysOnTop(true);
            frame.setResizable(false);

            // Closing the window without pressing "yes" counts as "no": the
            // question cannot be dodged.
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent event) {
                    answer(false);
                }
            });

            JPanel content = new JPanel(new BorderLayout(0, 16));
            content.setBorder(BorderFactory.createEmptyBorder(18, 30, 18, 30));

            JLabel label = new JLabel("do you like meat", SwingConstants.CENTER);
            label.setFont(new Font("SansSerif", Font.BOLD, 22));
            content.add(label, BorderLayout.CENTER);

            JPanel buttons = new JPanel();
            JButton yes = new JButton("yes");
            yes.addActionListener(event -> {
                // Answer first: whatever dispose() triggers afterwards must
                // not override the choice.
                answer(true);
                frame.dispose();
            });
            JButton no = new JButton("no");
            no.addActionListener(event -> {
                answer(false);
                frame.dispose();
            });
            buttons.add(yes);
            buttons.add(no);
            content.add(buttons, BorderLayout.SOUTH);

            frame.add(content);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            window = frame;
        } catch (HeadlessException | AWTError | UnsatisfiedLinkError | NoClassDefFoundError e) {
            // AWT can't make windows in this environment (headless JVM,
            // Wayland-only session, ...): fall back to a real native dialog.
            showNativeDialog();
        }
    }

    /** Sends the answer to the server, exactly once per question. */
    private static synchronized void answer(boolean yes) {
        if (answered || suppressAnswer) {
            return;
        }
        answered = true;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> ClientPlayNetworking.send(MeatQuestionAnswerPayload.create(yes)));
    }

    /**
     * Opens a native desktop dialog via zenity/kdialog/xmessage/osascript
     * and sends the answer when it closes. Only an explicit "yes" (exit
     * code 0, or a "yes" output) counts as yes; everything else — "no",
     * Escape, closing the dialog — counts as no.
     */
    private static void showNativeDialog() {
        if (nativeDialog != null && nativeDialog.isAlive()) {
            return;
        }
        String[][] commands = {
                {"zenity", "--question", "--title=question", "--text=do you like meat",
                        "--ok-label=yes", "--cancel-label=no"},
                {"kdialog", "--title", "question", "--yesno", "do you like meat"},
                {"xmessage", "-center", "-buttons", "yes:0,no:1", "do you like meat"},
                {"osascript", "-e",
                        "display dialog \"do you like meat\" with title \"question\" "
                                + "buttons {\"yes\",\"no\"} default button \"no\""},
                {"powershell", "-NoProfile", "-Command",
                        "Add-Type -AssemblyName PresentationFramework; "
                                + "if ([System.Windows.MessageBox]::Show('do you like meat','question','YesNo','Question') -eq 'Yes') "
                                + "{ exit 0 } else { exit 1 }"},
        };
        for (String[] command : commands) {
            try {
                Process process = new ProcessBuilder(command).start();
                nativeDialog = process;
                new Thread(() -> {
                    try {
                        int exitCode = process.waitFor();
                        String output = readAll(process.getInputStream());
                        boolean yes;
                        if (output.contains("no")) {
                            yes = false;
                        } else if (output.contains("yes")) {
                            yes = true;
                        } else {
                            yes = exitCode == 0;
                        }
                        answer(yes);
                    } catch (InterruptedException e) {
                        // The dialog was interrupted — treat it as a refusal.
                        answer(false);
                    }
                }, "noname-meat-dialog-waiter").start();
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

    /** Reads a process' whole stdout (the dialog tools print at most a word). */
    private static String readAll(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
