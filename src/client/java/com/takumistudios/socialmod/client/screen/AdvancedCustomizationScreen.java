package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.compat.fancy.FancyBridge;
import com.takumistudios.socialmod.client.theme.AppearanceMode;
import com.takumistudios.socialmod.common.net.SocialAction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Editor entry points; profiles are managed by FancyMenu itself. */
public final class AdvancedCustomizationScreen extends SocialChildScreen {
    public AdvancedCustomizationScreen(Screen parent) { super(parent, Component.translatable("socialmod.advanced.title")); }
    @Override protected void init() {
        int w = Math.min(300, width - 20), x = (width - w) / 2, y = 28;
        var menu = addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.screen"), b -> FancyBridge.edit(parent)).bounds(x,y,w,20).build()); menu.active = FancyBridge.available(); y += 23;
        var hud = addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.hud"), b -> FancyBridge.editHud()).bounds(x,y,w,20).build()); hud.active = FancyBridge.spiffy(); y += 23;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.rows"), b -> ClientCompat.setScreen(new RowTemplateScreen(this))).bounds(x,y,w,20).build()); y += 23;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.basic"), b -> ClientCompat.setScreen(new VisualEditorScreen(parent))).bounds(x,y,w,20).build()); y += 23;
        addRenderableWidget(Ui.button(AppearanceMode.personalLabel(), b -> { AppearanceMode.setPersonal(!AppearanceMode.personal()); rebuildWidgets(); }).bounds(x,y,w,20).build()); y += 23;
        if (ClientState.get().snapshot().visualAdmin) addRenderableWidget(Ui.button(AppearanceMode.globalLabel(), b -> ClientNet.action(SocialAction.VISUAL_ORIGINAL, String.valueOf(!ClientState.get().snapshot().originalInterface))).bounds(x,y,w,20).build());
        addRenderableWidget(Ui.button(Component.translatable("gui.back"), b -> onClose()).bounds(x,height-25,w,20).build());
    }
    @Override protected void drawContent(GuiGraphicsExtractor graphics, int mx, int my) {
        Ui.title(graphics,font,title,width/2,10);
        var status = Component.translatable("socialmod.advanced.integrations", Component.translatable(FancyBridge.installed()?"gui.yes":"gui.no"), Component.translatable(FancyBridge.spiffyInstalled()?"gui.yes":"gui.no"));
        graphics.centeredText(font,status,width/2,height-43,Ui.theme().colors().muted());
    }
}