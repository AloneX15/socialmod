package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.theme.VisualManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Native editing, cursor and narration with optional resource-pack field styling. */
public final class StyledEditBox extends EditBox {
    public StyledEditBox(Font font, int x, int y, int w, int h, Component label) {
        super(font, x, y, w, h, label);
        com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identify(this, "input_" + (label.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tr ? tr.getKey() : "input"));
        addFormatter((text, cursor) -> Component.literal(text).withStyle(style -> style.withFont(new FontDescription.Resource(Identifier.parse(VisualManager.get().font)))).getVisualOrderText());
    }
    @Override public void setHint(Component hint) { super.setHint(hint.copy().withStyle(style -> style.withFont(new FontDescription.Resource(Identifier.parse(VisualManager.get().font))))); }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var design = VisualManager.get();
        String texture = isFocused() && !design.inputFocusTexture.isEmpty() ? design.inputFocusTexture : design.inputTexture;
        boolean contrast = com.takumistudios.socialmod.client.ClientConfig.get().accessibility.highContrast;
        if (contrast) texture = "";
        boolean external = !contrast && com.takumistudios.socialmod.client.compat.fancy.FancyBridge.buttonBackground(this,graphics);
        if (texture.isEmpty() && !external) { super.extractWidgetRenderState(graphics, mouseX, mouseY, delta); return; }
        boolean bordered = isBordered();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        if (!external) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.parse(texture), x, y, w, h);
        graphics.enableScissor(x+4,y,x+w-4,y+h);
        try {
            // Keep the native bordered field's four-pixel content inset and mouse/cursor geometry.
            setBordered(false); setRectangle(Math.max(1, w - 8), h, x + 4, y);
            super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
        } finally { setRectangle(w, h, x, y); setBordered(bordered); graphics.disableScissor(); }
    }
}
