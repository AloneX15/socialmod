package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.theme.VisualText;

import com.takumistudios.socialmod.client.theme.VisualManager;
import com.takumistudios.socialmod.client.theme.ChristmasSkin;
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
    private final ChristmasSkin.Role skinRole;
    private final String skinIcon;
    private long pressedUntil;
    private StyledButton(int x, int y, int w, int h, Component text, OnPress press) { super(x, y, w, h, text, press, DEFAULT_NARRATION);skinRole=ChristmasSkin.role(text);skinIcon=ChristmasSkin.icon(text,skinRole); }
    public ChristmasSkin.Role skinRole() { return skinRole; }
    public String skinIcon() { return skinIcon; }
    @Override public void onPress(net.minecraft.client.input.InputWithModifiers input) { pressedUntil=net.minecraft.util.Util.getMillis()+140;super.onPress(input); }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean contrast = com.takumistudios.socialmod.client.ClientConfig.get().accessibility.highContrast;
        var design = VisualManager.get(); var rect = VisualManager.rect(this);
        String texture = selected && !design.buttonSelectedTexture.isEmpty() ? design.buttonSelectedTexture : !active ? design.buttonDisabledTexture : isHoveredOrFocused() ? design.buttonHoverTexture : design.buttonTexture;
        if (rect != null && !rect.texture.isEmpty()) texture = rect.texture;
        if (contrast) texture = "";
        boolean christmas=ChristmasSkin.enabled() && texture.isEmpty();
        boolean externalBackground = !christmas && !contrast && com.takumistudios.socialmod.client.compat.fancy.FancyBridge.buttonBackground(this, graphics);
        if(christmas)ChristmasSkin.button(graphics,this,skinRole,selected,net.minecraft.util.Util.getMillis()<pressedUntil);
        else if (!externalBackground && !texture.isEmpty()) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.parse(texture), getX(), getY(), getWidth(), getHeight());
        else if (!externalBackground) graphics.fill(getX(), getY(), getRight(), getBottom(), contrast ? (selected ? 0xFF303030 : 0xFF000000) : !active ? design.buttonDisabledColor : isHoveredOrFocused() ? design.buttonHoverColor : design.buttonColor);
        for (int i = 0; !christmas && !externalBackground && texture.isEmpty() && i < (contrast ? 1 : design.borderWidth); i++) graphics.outline(getX() + i, getY() + i, Math.max(1, getWidth() - i * 2), Math.max(1, getHeight() - i * 2), Ui.theme().colors().border());
        var label = getMessage().copy();
        if(design.seriesStyle.equals("dedsafio") && !com.takumistudios.socialmod.client.compat.fancy.FancyBridge.customLabel(this) && getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated) {
            String icon=switch(translated.getKey()) { case "socialmod.team.title" -> "⚑ "; case "socialmod.visual.title" -> "✦ "; case "socialmod.panel.settings" -> "☷ "; case "socialmod.panel.new_group" -> "+ "; default -> ""; };
            if(!icon.isEmpty())label=Component.literal(icon).append(label);
        }
        if (!com.takumistudios.socialmod.client.compat.fancy.FancyBridge.customLabel(this))
            label.withStyle(style -> style.withFont(new net.minecraft.network.chat.FontDescription.Resource(Identifier.parse(design.font))));
        if (rect != null) label.withStyle(style -> style.withColor(rect.textColor));
        int padding = christmas ? ChristmasSkin.labelPadding(getWidth(),getHeight()) : Math.min(design.padding, Math.max(0, (getWidth() - 8) / 2));
        int iconSize=christmas?Math.min(16,Math.max(8,getHeight()-8)):0;
        boolean compact=christmas && (getWidth()<90 || getMessage().getString().length()<2);
        var font = Minecraft.getInstance().font;
        if(compact) {
            String glyph=getMessage().getString();
            if(glyph.length()==1 && !glyph.equals("➤") && !glyph.equals("⌖") && !glyph.equals("✦"))graphics.text(font,label,getX()+(getWidth()-font.width(label))/2,getY()+(getHeight()-font.lineHeight)/2,skinRole.darkText?0xFF252019:active?0xFFFFFFFF:0xFFAAAAAA);
            else ChristmasSkin.buttonIcon(graphics,this,skinIcon,getX()+(getWidth()-iconSize)/2,getY()+(getHeight()-iconSize)/2,iconSize);
            return;
        }
        int available = Math.max(1, getWidth() - 2 * padding - (christmas ? iconSize+3 : 0)), textWidth = font.width(label);
        if(christmas && textWidth>available) { label=Component.literal(font.plainSubstrByWidth(label.getString(),Math.max(1,available-font.width("…")))+"…").withStyle(label.getStyle());textWidth=font.width(label); }
        if(christmas)ChristmasSkin.buttonIcon(graphics,this,skinIcon,getX()+padding,getY()+(getHeight()-iconSize)/2,iconSize);
        float fit = Math.max(.75f, Math.min(1, (float) available / Math.max(1, textWidth)));
        float scale = com.takumistudios.socialmod.client.compat.fancy.FancyBridge.labelScale(this) * fit;
        if (!Float.isFinite(scale) || scale <= 0) return;
        boolean shadow = com.takumistudios.socialmod.client.compat.fancy.FancyBridge.labelShadow(this);
        int color = rect == null ? christmas && skinRole.darkText ? 0xFF252019 : Ui.theme().colors().text() : rect.textColor;
        graphics.enableScissor(getX() + padding, getY(), getRight() - padding, getBottom());
        graphics.pose().pushMatrix();
        try { graphics.pose().translate(getX() + (getWidth() + (christmas ? iconSize+3 : 0)) / 2f, getY() + (getHeight() - 8 * scale) / 2f);
        graphics.pose().scale(scale, scale);
        graphics.text(font, label, -textWidth / 2, 0, color, shadow);
        } finally { graphics.pose().popMatrix();graphics.disableScissor(); }
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
        public Button build() { var button = new StyledButton(x, y, w, h, text, press); button.setTooltip(tooltip==null && ChristmasSkin.enabled()?Tooltip.create(text):tooltip); button.selected = selected; com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identify(button, "button_" + (text.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tr ? tr.getKey() : "dynamic_" + java.util.UUID.nameUUIDFromBytes(text.getString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))); return button; }
    }
}
