package dev.noname.client;

import dev.noname.network.ModPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A full-screen overlay shown on first join warning about flashing lights,
 * loud noises, and the slow escalation of events.
 */
public final class WarningOverlay extends Screen {

    private static final Component TITLE = Component.translatable("noname.warning.title");
    private static final Component[] LINES = {
            Component.translatable("noname.warning.line1"),
            Component.translatable("noname.warning.line2"),
            Component.translatable("noname.warning.line3"),
            Component.translatable("noname.warning.line4"),
    };
    private static final Component BUTTON_TEXT = Component.translatable("noname.warning.understand");

    private WarningOverlay() {
        super(TITLE);
    }

    public static void show() {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new WarningOverlay());
        });
    }

    @Override
    protected void init() {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonX = (width - buttonWidth) / 2;
        int buttonY = height / 2 + 80;

        addRenderableWidget(Button.builder(BUTTON_TEXT, button -> {
            onClose();
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // Render standard screen background (handles vignette, etc.)
        this.renderBackground(gui, mouseX, mouseY, partialTick);

        // Dark semi-transparent overlay on top
        gui.fill(0, 0, width, height, 0xCC000000);

        super.render(gui, mouseX, mouseY, partialTick);

        // Title - render AFTER widgets for crisp text
        int titleX = (gui.guiWidth() - font.width(TITLE)) / 2;
        int titleY = gui.guiHeight() / 4 - 40;
        gui.drawString(font, TITLE, titleX, titleY, 0xFFFF5555, false);

        // Warning lines
        int lineY = gui.guiHeight() / 4 + 20;
        for (Component line : LINES) {
            int lineX = (gui.guiWidth() - font.width(line)) / 2;
            gui.drawString(font, line, lineX, lineY, 0xFFFFFFFF, false);
            lineY += font.lineHeight + 8;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // Send acknowledgment to server; the keep-inventory prompt follows.
        ModPayloads.sendWarningAcknowledged();
        Minecraft.getInstance().setScreen(null);
    }
}