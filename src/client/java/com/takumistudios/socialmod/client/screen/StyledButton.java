package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.theme.VisualText;

import com.takumistudios.socialmod.client.theme.VisualManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Native button behavior with resource-pack and design-controlled presentation. */
public final class StyledButton extends Button {
    private StyledButton(int x, int y, int w, int h, Component text, OnPress press) { super(x, y, w, h, text, press, DEFAULT_NARRATION); }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var design = VisualManager.get(); var rect = VisualManager.rect(this);
        String texture = !active ? design.buttonDisabledTexture : isHoveredOrFocused() ? design.buttonHoverTexture : design.buttonTexture;
        if (rect != null && !rect.texture.isEmpty()) texture = rect.texture;
        if (!texture.isEmpty()) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.parse(texture), getX(), getY(), getWidth(), getHeight());
        else graphics.fill(getX(), getY(), getRight(), getBottom(), !active ? design.buttonDisabledColor : isHoveredOrFocused() ? design.buttonHoverColor : design.buttonColor);
        for (int i = 0; i < design.borderWidth; i++) graphics.outline(getX() + i, getY() + i, Math.max(1, getWidth() - i * 2), Math.max(1, getHeight() - i * 2), Ui.theme().colors().border());
        Component label = getMessage().copy().withStyle(style -> (rect == null ? style : style.withColor(rect.textColor)).withFont(new net.minecraft.network.chat.FontDescription.Resource(Identifier.parse(design.font))));
        int padding = Math.min(design.padding, Math.max(0, (getWidth() - 8) / 2));
        graphics.enableScissor(getX() + padding, getY(), getRight() - padding, getBottom());
        VisualText.centeredText(graphics, Minecraft.getInstance().font, label, getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, rect == null ? Ui.theme().colors().text() : rect.textColor);
        graphics.disableScissor();
    }
    public static final class Builder {
        private final Component text; private final OnPress press;
        private int x, y, w = 150, h = 20; private Tooltip tooltip;
        public Builder(Component text, OnPress press) { this.text = text; this.press = press; }
        public Builder bounds(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; return this; }
        public Builder pos(int x, int y) { this.x = x; this.y = y; return this; }
        public Builder size(int w, int h) { this.w = w; this.h = h; return this; }
        public Builder width(int w) { this.w = w; return this; }
        public Builder tooltip(Tooltip tooltip) { this.tooltip = tooltip; return this; }
        public Button build() { var button = new StyledButton(x, y, w, h, text, press); button.setTooltip(tooltip); return button; }
    }
}
