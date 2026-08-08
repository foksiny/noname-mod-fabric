package dev.noname.client.config;

import dev.noname.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * The "Noname" configuration screen, opened from the button in the top-left
 * corner of the vanilla Options screen.
 *
 * <p>Three sections:
 * <ul>
 *  <li><b>Mod speed</b> slider — 6 levels, level 3 is the default pacing.
 *       Lower levels make every day-gated event happen earlier (level 1:
 *       moon fully infected at day 5, ghost joins at day 1), higher levels
 *       push them later (level 6: moon at day 20, ghost at day 6).</li>
 *   <li><b>Visual features</b> — always-on ambience toggles (the VHS screen
 *       filter and overlay), kept apart from the events because they are not
 *       random events and have no chance setting.</li>
 *   <li><b>Events</b> — a scrollable list, one row per mod event. Each row
 *       has a checkbox to turn the event on or off and a slider to set that
 *       event's own chance (5%-200%, 100% = the event's built-in base
 *       probability).</li>
 *   <li><b>Back to default</b> — restores every event's chance to 100%.</li>
 * </ul>
 *
 * <p>Every change is persisted to {@code config/noname.json} immediately.
 */
public final class NonameConfigScreen extends Screen {

    private static final int SLIDER_WIDTH = 260;
    private static final int SLIDER_HEIGHT = 20;
    private static final int ROW_SLIDER_WIDTH = 100;
    private static final int LIST_ITEM_HEIGHT = 20;
    private static final int FEATURE_ROW_HEIGHT = 22;

    /** Width taken by the checkbox box (17 px) plus its label padding (4 px).
     *  Subtracted from the checkbox width to get the space left for text. */
    private static final int CHECKBOX_TEXT_OFFSET = 21;

    /** One pixel per row keeps the scrollbar from overlapping the slider. */
    private static final int ROW_SCROLLBAR_GAP = 10;

    private static final Component DONE = Component.literal("Done");
    private static final Component BACK_TO_DEFAULT = Component.literal("Back to default");
    private static final Component RESET = Component.literal("Reset all");
    private static final Component FEATURES_LABEL = Component.literal("Visual features");

    /** Events list top Y, in screen coords. Leaves room above for the
     *  speed slider and the visual-feature checkboxes. */
    private static final int EVENT_LIST_Y = 94;

    private final Screen lastScreen;

    public NonameConfigScreen(Screen lastScreen) {
        super(Component.literal("Noname Settings"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        this.addRenderableWidget(new SpeedSlider(centerX - SLIDER_WIDTH / 2, 30,
                SLIDER_WIDTH, SLIDER_HEIGHT));

        int featureY = 62;
        for (Map.Entry<String, String> entry : ModConfig.VISUAL_FEATURES.entrySet()) {
            this.addRenderableWidget(Checkbox.builder(
                            fitLabel(entry.getValue(), this.font,
                                    this.width - 40 - CHECKBOX_TEXT_OFFSET),
                            this.font)
                    .pos(20, featureY)
                    .selected(ModConfig.isEnabled(entry.getKey()))
                    .maxWidth(this.width - 40)
                    .onValueChange((box, value) -> ModConfig.setEnabled(entry.getKey(), value))
                    .build());
            featureY += FEATURE_ROW_HEIGHT;
        }

        EventList list = new EventList(this.minecraft, this.width - 40,
                this.height - 52 - EVENT_LIST_Y, EVENT_LIST_Y);
        list.setPosition(20, EVENT_LIST_Y);
        for (Map.Entry<String, String> entry : ModConfig.EVENTS.entrySet()) {
            String key = entry.getKey();
            int checkboxMaxWidth = list.rowWidth() - ROW_SLIDER_WIDTH - 24;
            list.addEvent(new EventEntry(key,
                    fitLabel(entry.getValue(), this.font,
                            checkboxMaxWidth - CHECKBOX_TEXT_OFFSET),
                    ModConfig.isEnabled(key), ModConfig.getEventChance(key),
                    checkboxMaxWidth));
        }
        this.addRenderableWidget(list);

        this.addRenderableWidget(Button.builder(DONE, button -> this.onClose())
                .bounds(centerX - 154, this.height - 32, 100, SLIDER_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(BACK_TO_DEFAULT, button -> {
            ModConfig.resetChances();
            this.rebuild();
        }).bounds(centerX - 50, this.height - 32, 100, SLIDER_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(RESET, button -> {
            ModConfig.reset();
            this.rebuild();
        }).bounds(centerX + 54, this.height - 32, 100, SLIDER_HEIGHT)
                .build());
    }

    /** Reopens the screen so every widget reflects the current config. */
    private void rebuild() {
        this.minecraft.setScreen(new NonameConfigScreen(lastScreen));
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        gui.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        gui.drawString(this.font, FEATURES_LABEL, 20, 54, 0xFFA0A0A0);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(lastScreen);
    }

    /** The mod-speed slider: 6 discrete levels, level 3 = default pacing. */
    private static final class SpeedSlider extends AbstractSliderButton {

        SpeedSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), speedValue());
            updateMessage();
        }

        private static double speedValue() {
            return (ModConfig.getSpeedLevel() - ModConfig.MIN_SPEED_LEVEL)
                    / (double) (ModConfig.MAX_SPEED_LEVEL - ModConfig.MIN_SPEED_LEVEL);
        }

        @Override
        protected void updateMessage() {
            int level = ModConfig.getSpeedLevel();
            String pace;
            if (level < 3) {
                pace = "fast";
            } else if (level > 3) {
                pace = "slow";
            } else {
                pace = "normal";
            }
            this.setMessage(Component.literal("Mod speed: level " + level + " (" + pace + ")"));
        }

        @Override
        protected void applyValue() {
            int level = ModConfig.MIN_SPEED_LEVEL + (int) Math.round(
                    this.value * (ModConfig.MAX_SPEED_LEVEL - ModConfig.MIN_SPEED_LEVEL));
            ModConfig.setSpeedLevel(level);
            // Snap the knob onto the level.
            this.value = (level - ModConfig.MIN_SPEED_LEVEL)
                    / (double) (ModConfig.MAX_SPEED_LEVEL - ModConfig.MIN_SPEED_LEVEL);
        }
    }

    /**
     * Truncates a plain-text label so it fits in {@code maxTextWidth} pixels
     * on a single line, appending an ellipsis when it had to be cut. Long
     * event/feature names would otherwise wrap inside the checkbox and make
     * it taller than its fixed row, overlapping the slider and the rows
     * around it.
     */
    private static Component fitLabel(String label, Font font, int maxTextWidth) {
        String text = font.plainSubstrByWidth(label, maxTextWidth);
        if (!text.equals(label)) {
            String ellipsis = "\u2026";
            text = font.plainSubstrByWidth(label,
                    Math.max(0, maxTextWidth - font.width(ellipsis))) + ellipsis;
        }
        return Component.literal(text);
    }

    /** Scrollable list of per-event rows. */
    private static final class EventList extends ContainerObjectSelectionList<EventEntry> {

        EventList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, LIST_ITEM_HEIGHT);
        }

        void addEvent(EventEntry entry) {
            this.addEntry(entry);
        }

        int rowWidth() {
            return this.getRowWidth();
        }

        /** Rows span the full list width instead of the default 220 px, so
         *  labels and the chance slider have room and never collide. */
        @Override
        public int getRowWidth() {
            return this.width - ROW_SCROLLBAR_GAP;
        }
    }

    /** One row: an event's checkbox and its individual chance slider. */
    private static final class EventEntry extends ContainerObjectSelectionList.Entry<EventEntry> {

        private final Checkbox checkbox;
        private final EventChanceSlider slider;

        EventEntry(String eventKey, Component message, boolean selected,
                   float chance, int checkboxMaxWidth) {
            this.checkbox = Checkbox.builder(message,
                            Minecraft.getInstance().font)
                    .pos(0, 0)
                    .selected(selected)
                    .maxWidth(checkboxMaxWidth)
                    .onValueChange((box, value) -> ModConfig.setEnabled(eventKey, value))
                    .build();
            this.slider = new EventChanceSlider(eventKey, chance);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(checkbox, slider);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(checkbox, slider);
        }

        @Override
        public void render(GuiGraphics gui, int index, int top, int left, int width,
                           int height, int mouseX, int mouseY, boolean hovering,
                           float partialTick) {
            this.checkbox.setX(left);
            this.checkbox.setY(top + (height - checkbox.getHeight()) / 2);
            this.slider.setX(left + width - ROW_SLIDER_WIDTH);
            this.slider.setY(top + (height - SLIDER_HEIGHT) / 2);
            this.checkbox.render(gui, mouseX, mouseY, partialTick);
            this.slider.render(gui, mouseX, mouseY, partialTick);
        }
    }

    /** The chance slider of a single event: 5% to 200%, default 100%. */
    private static final class EventChanceSlider extends AbstractSliderButton {

        private final String eventKey;

        EventChanceSlider(String eventKey, float chance) {
            super(0, 0, ROW_SLIDER_WIDTH, SLIDER_HEIGHT, Component.empty(),
                    sliderValue(chance));
            this.eventKey = eventKey;
            updateMessage();
        }

        private static double sliderValue(float chance) {
            return (chance - ModConfig.MIN_EVENT_CHANCE)
                    / (ModConfig.MAX_EVENT_CHANCE - ModConfig.MIN_EVENT_CHANCE);
        }

        @Override
        protected void updateMessage() {
            int percent = Math.round(ModConfig.getEventChance(eventKey) * 100.0F);
            this.setMessage(Component.literal(percent + "%"));
        }

        @Override
        protected void applyValue() {
            float chance = ModConfig.MIN_EVENT_CHANCE + (float) this.value
                    * (ModConfig.MAX_EVENT_CHANCE - ModConfig.MIN_EVENT_CHANCE);
            ModConfig.setEventChance(eventKey, Math.round(chance * 20.0F) / 20.0F);
        }
    }
}
