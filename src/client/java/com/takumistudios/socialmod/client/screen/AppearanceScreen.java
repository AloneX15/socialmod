package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.theme.AppearanceMode;
import com.takumistudios.socialmod.common.net.SocialAction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Available without editor integrations or staff permissions. */
public final class AppearanceScreen extends SocialChildScreen {
    public AppearanceScreen(Screen parent) { super(parent, Component.translatable("socialmod.appearance.title")); }
    @Override protected void init() {
        int w = Math.min(300, width - 20), x = (width - w) / 2, y = 38;
        addRenderableWidget(Ui.button(AppearanceMode.personalLabel(), b -> { AppearanceMode.setPersonal(!AppearanceMode.personal()); rebuildWidgets(); }).bounds(x, y, w, 20).build()); y += 24;
        if (ClientState.get().snapshot().visualAdmin) {
            addRenderableWidget(Ui.button(AppearanceMode.globalLabel(), b -> ClientNet.action(SocialAction.VISUAL_ORIGINAL, String.valueOf(!ClientState.get().snapshot().originalInterface))).bounds(x, y, w, 20).build()); y += 24;
            addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.basic"), b -> com.takumistudios.socialmod.client.compat.ClientCompat.setScreen(new VisualEditorScreen(this))).bounds(x, y, w, 20).build());
        }
        addRenderableWidget(Ui.button(Component.translatable("gui.back"), b -> onClose()).bounds(x, height - 26, w, 20).build());
    }
    @Override protected void drawContent(GuiGraphicsExtractor graphics, int mx, int my) {
        Ui.title(graphics, font, title, width / 2, 10);
        if (ClientState.get().snapshot().originalInterface) graphics.centeredText(font, Component.translatable("socialmod.appearance.enforced"), width / 2, height - 44, Ui.theme().colors().muted());
    }
}
