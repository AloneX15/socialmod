package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.compat.fancy.FancyBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** The native panel background is independently editable and hideable in FancyMenu. */
public final class SocialBackgroundWidget extends AbstractWidget {
    private final boolean full;
    public SocialBackgroundWidget(int x, int y, int width, int height, boolean full) {
        super(x, y, width, height, Component.translatable("socialmod.advanced.block.background"));
        this.full = full; FancyBridge.identify(this, "panel_background");
    }
    @Override public boolean isMouseOver(double x, double y) { return false; }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean twice) { return false; }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mx, int my, float partial) {
        if (!visible || FancyBridge.hidden(this)) return;
        if (!FancyBridge.buttonBackground(this, graphics)) return;
        if (full && getX() == 0 && getY() == 0 && getWidth() == graphics.guiWidth() && getHeight() == graphics.guiHeight())
            Ui.background(graphics, getWidth(), getHeight());
        else Ui.panel(graphics, getX(), getY(), getX() + getWidth(), getY() + getHeight());
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
