package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Pantalla secundaria del panel (perfil, ajustes, grupo...). Al cerrarla se vuelve al panel. Se redibuja sola cuando
 * llega un estado nuevo del servidor.
 */
public abstract class SocialChildScreen extends Screen {
    protected final @Nullable Screen parent;
    private int lastVersion = -1;
    private net.minecraft.client.gui.components.AbstractWidget content;

    protected SocialChildScreen(@Nullable Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    @Override protected void init() {
        content = null;
        if (!com.takumistudios.socialmod.client.compat.fancy.FancyBridge.available() || getClass().getSimpleName().startsWith("Visual") || this instanceof AdvancedCustomizationScreen || this instanceof RowTemplateScreen) return;
        content = new net.minecraft.client.gui.components.AbstractWidget(0,0,width,height,title) {
            @Override public boolean isMouseOver(double x,double y) { return false; }
            @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick) { return false; }
            @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial) {
                graphics.pose().pushMatrix(); graphics.pose().translate(getX(),getY()); graphics.pose().scale(getWidth()/(float)Math.max(1,SocialChildScreen.this.width),getHeight()/(float)Math.max(1,SocialChildScreen.this.height));
                try { if (com.takumistudios.socialmod.client.compat.fancy.FancyBridge.available()) drawContent(graphics,mouseX,mouseY); } finally { graphics.pose().popMatrix(); }
            }
            @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) { defaultButtonNarrationText(out); }
        };
        com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identify(content,"screen_content"); addRenderableWidget(content);
    }

    @Override
    public void onClose() {
        ClientCompat.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ClientState state = ClientState.get();
        if (!state.connected()) {
            ClientCompat.setScreen(null);
            return;
        }
        if (lastVersion != state.version()) {
            boolean first = lastVersion == -1;
            lastVersion = state.version();
            if (!first && rebuildOnChange()) {
                saveDrafts();
                rebuildWidgets();
            }
        }
    }

    /** ¿Rehacer los widgets cuando cambia el estado? (los textos escritos se conservan con {@link #saveDrafts()}). */
    protected boolean rebuildOnChange() {
        return true;
    }

    protected void saveDrafts() {
    }

    protected int panelLeft(int panelWidth) {
        return (this.width - panelWidth) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Ui.background(graphics, this.width, this.height);
        if (content == null || !com.takumistudios.socialmod.client.compat.fancy.FancyBridge.available()) drawContent(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    protected abstract void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY);
}
