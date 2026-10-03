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

    protected SocialChildScreen(@Nullable Screen parent, Component title) {
        super(title);
        this.parent = parent;
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
        drawContent(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    protected abstract void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY);
}
