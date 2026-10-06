package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.compat.fancy.FancyBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A discoverable native widget whose final bounds drive content and hit testing. */
public final class SocialBlockWidget extends AbstractWidget {
    private final SocialScreen owner;
    private final String kind;
    public SocialBlockWidget(SocialScreen owner, String kind, int x, int y, int w, int h) {
        super(x, y, w, h, Component.translatable("socialmod.advanced.block." + kind));
        this.owner = owner; this.kind = kind;
        FancyBridge.identify(this, "block_" + kind);
    }
    @Override public boolean isMouseOver(double x, double y) { return false; }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) { return false; }
    public String kind() { return kind; }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        if (visible && FancyBridge.available()) owner.drawBlock(kind, graphics, mouseX, mouseY, getX(), getY(), getWidth(), getHeight());
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
