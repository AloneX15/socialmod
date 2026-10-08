package com.takumistudios.socialmod.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Independent passive frame or name plate, exposed to FancyMenu without stealing input. */
public final class BannerFrameWidget extends AbstractWidget {
    private final boolean namePlate;
    public BannerFrameWidget(int x, int y, int w, int h, Component name, String id, boolean namePlate) {
        super(x, y, w, h, name); this.namePlate = namePlate;
        com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identify(this, id);
    }
    @Override public boolean isMouseOver(double x, double y) { return false; }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) { return false; }
    @Override protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mx, int my, float partial) {
        if (!visible || com.takumistudios.socialmod.client.compat.fancy.FancyBridge.hidden(this)) return;
        boolean external = com.takumistudios.socialmod.client.compat.fancy.FancyBridge.buttonBackground(this, graphics);
        if (!external && !namePlate) Ui.panel(graphics, getX(), getY(), getRight(), getBottom());
        if (namePlate) {
            graphics.enableScissor(getX(), getY(), getRight(), getBottom());
            try { graphics.centeredText(net.minecraft.client.Minecraft.getInstance().font, com.takumistudios.socialmod.client.compat.fancy.FancyBridge.label(this), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, Ui.theme().colors().text()); }
            finally { graphics.disableScissor(); }
        }
    }
}
