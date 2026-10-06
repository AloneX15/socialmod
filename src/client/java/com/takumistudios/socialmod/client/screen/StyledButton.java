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
    private boolean selected;
    private StyledButton(int x, int y, int w, int h, Component text, OnPress press) { super(x, y, w, h, text, press, DEFAULT_NARRATION); }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean contrast = com.takumistudios.socialmod.client.ClientConfig.get().accessibility.highContrast;
        var design = VisualManager.get(); var rect = VisualManager.rect(this);
        String texture = selected && !design.buttonSelectedTexture.isEmpty() ? design.buttonSelectedTexture : !active ? design.buttonDisabledTexture : isHoveredOrFocused() ? design.buttonHoverTexture : design.buttonTexture;
        if (rect != null && !rect.texture.isEmpty()) texture = rect.texture;
        if (contrast) texture = "";
        boolean externalBackground = !contrast && com.takumistudios.socialmod.client.compat.fancy.FancyBridge.buttonBackground(this, graphics);
        if (!externalBackground && !texture.isEmpty()) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.parse(texture), getX(), getY(), getWidth(), getHeight());
        else if (!externalBackground) graphics.fill(getX(), getY(), getRight(), getBottom(), contrast ? (selected ? 0xFF303030 : 0xFF000000) : !active ? design.buttonDisabledColor : isHoveredOrFocused() ? design.buttonHoverColor : design.buttonColor);
        for (int i = 0; !externalBackground && texture.isEmpty() && i < (contrast ? 1 : design.borderWidth); i++) graphics.outline(getX() + i, getY() + i, Math.max(1, getWidth() - i * 2), Math.max(1, getHeight() - i * 2), Ui.theme().colors().border());
        var label = getMessage().copy();
        if (!com.takumistudios.socialmod.client.compat.fancy.FancyBridge.customLabel(this))
            label.withStyle(style -> style.withFont(new net.minecraft.network.chat.FontDescription.Resource(Identifier.parse(design.font))));
        if (rect != null) label.withStyle(style -> style.withColor(rect.textColor));
        int padding = Math.min(design.padding, Math.max(0, (getWidth() - 8) / 2));
        var font = Minecraft.getInstance().font;
        int available = Math.max(1, getWidth() - 2 * padding), textWidth = font.width(label);
        float fit = Math.max(.75f, Math.min(1, (float) available / Math.max(1, textWidth)));
        float scale = com.takumistudios.socialmod.client.compat.fancy.FancyBridge.labelScale(this) * fit;
        if (!Float.isFinite(scale) || scale <= 0) return;
        boolean shadow = com.takumistudios.socialmod.client.compat.fancy.FancyBridge.labelShadow(this);
        int color = rect == null ? Ui.theme().colors().text() : rect.textColor;
        graphics.enableScissor(getX() + padding, getY(), getRight() - padding, getBottom());
        graphics.pose().pushMatrix();
        graphics.pose().translate(getX() + getWidth() / 2f, getY() + (getHeight() - 8 * scale) / 2f);
        graphics.pose().scale(scale, scale);
        graphics.text(font, label, -textWidth / 2, 0, color, shadow);
        graphics.pose().popMatrix();
        graphics.disableScissor();
    }
    public static final class Builder {
        private final Component text; private final OnPress press;
        private int x, y, w = 150, h = 20; private Tooltip tooltip; private boolean selected;
        public Builder(Component text, OnPress press) { this.text = text; this.press = press; }
        public Builder selected(boolean value) { selected = value; return this; }
        public Builder bounds(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; return this; }
        public Builder pos(int x, int y) { this.x = x; this.y = y; return this; }
        public Builder size(int w, int h) { this.w = w; this.h = h; return this; }
        public Builder width(int w) { this.w = w; return this; }
        public Builder tooltip(Tooltip tooltip) { this.tooltip = tooltip; return this; }
        public Button build() { var button = new StyledButton(x, y, w, h, text, press); button.setTooltip(tooltip); button.selected = selected; com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identify(button, "button_" + (text.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tr ? tr.getKey() : "dynamic_" + java.util.UUID.nameUUIDFromBytes(text.getString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))); return button; }
    }
}
