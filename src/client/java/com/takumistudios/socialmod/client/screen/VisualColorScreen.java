package com.takumistudios.socialmod.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;

/** RGBA picker for theme colors, including transparent backgrounds. */
public final class VisualColorScreen extends SocialChildScreen {
    private final int[] channels = new int[4];
    private final IntConsumer save;
    public VisualColorScreen(Screen parent, int color, IntConsumer save) {
        super(parent, Component.translatable("socialmod.visual.color")); this.save = save;
        channels[0] = color >>> 16 & 255; channels[1] = color >>> 8 & 255; channels[2] = color & 255; channels[3] = color >>> 24;
    }
    private int color() { return channels[3] << 24 | channels[0] << 16 | channels[1] << 8 | channels[2]; }
    @Override protected boolean rebuildOnChange() { return false; }
    @Override protected void init() {
        int w = Math.min(220, width - 16), x = (width - w) / 2, y = Math.max(42, height / 2 - 48);
        for (int i = 0; i < 4; i++) {
            int index = i; String key = new String[]{"red", "green", "blue", "alpha"}[i];
            addRenderableWidget(new AbstractSliderButton(x, y + i * 22, w, 20, Component.translatable("socialmod.visual." + key, channels[i]), channels[i] / 255.0) {
                @Override protected void updateMessage() { setMessage(Component.translatable("socialmod.visual." + key, (int) Math.round(value * 255))); }
                @Override protected void applyValue() { channels[index] = (int) Math.round(value * 255); }
            });
        }
        addRenderableWidget(Ui.button(Component.translatable("gui.done"), b -> { save.accept(color()); onClose(); }).bounds(x, y + 92, w / 2 - 2, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("gui.cancel"), b -> onClose()).bounds(x + w / 2 + 2, y + 92, w / 2 - 2, 20).build());
    }
    @Override protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Ui.title(graphics, font, title, width / 2, 8);
        graphics.fill(width / 2 - 30, 24, width / 2 + 30, 38, 0xFFFFFFFF);
        graphics.fill(width / 2 - 30, 24, width / 2 + 30, 38, color());
    }
}
