package dev.noname.client;

import dev.noname.network.ModPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A full-screen prompt shown on first join when the keepInventory game rule
 * is off, recommending it for a better experience in this mod.
 */
public final class KeepInventoryPromptOverlay extends Screen {

    private static final Component TITLE = Component.translatable("noname.keepinventory.title");
    private static final Component LINE = Component.translatable("noname.keepinventory.line");
    private static final Component YES_TEXT = Component.translatable("noname.keepinventory.yes");
    private static final Component NO_TEXT = Component.translatable("noname.keepinventory.no");

    private KeepInventoryPromptOverlay() {
        super(TITLE);
    }

    public static void show() {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new KeepInventoryPromptOverlay());
        });
    }

    @Override
    protected void init() {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonX = (width - buttonWidth) / 2;
        int buttonY = height / 2 + 40;

        addRenderableWidget(Button.builder(YES_TEXT, button -> {
            ModPayloads.sendKeepInventoryPromptResponse(true);
            onClose();
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());

        addRenderableWidget(Button.builder(NO_TEXT, button -> {
            ModPayloads.sendKeepInventoryPromptResponse(false);
            onClose();
        }).bounds(buttonX, buttonY + buttonHeight + 6, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui, mouseX, mouseY, partialTick);

        // Dark semi-transparent overlay on top
        gui.fill(0, 0, width, height, 0xCC000000);

        super.render(gui, mouseX, mouseY, partialTick);

        // Title - render AFTER widgets for crisp text
        int titleX = (gui.guiWidth() - font.width(TITLE)) / 2;
        int titleY = gui.guiHeight() / 4 - 40;
        gui.drawString(font, TITLE, titleX, titleY, 0xFFFFAA55, false);

        // Explanation line
        int lineX = (gui.guiWidth() - font.width(LINE)) / 2;
        int lineY = gui.guiHeight() / 4 + 20;
        gui.drawString(font, LINE, lineX, lineY, 0xFFFFFFFF, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
