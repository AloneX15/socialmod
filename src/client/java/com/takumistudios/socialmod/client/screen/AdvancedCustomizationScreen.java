package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.compat.fancy.FancyBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.*;

public final class AdvancedCustomizationScreen extends SocialChildScreen {
    private String status = "";
    private int style;
    private boolean busy;
    public AdvancedCustomizationScreen(Screen parent) { super(parent, Component.translatable("socialmod.advanced.title")); }
    @Override protected boolean rebuildOnChange() { return false; }
    @Override protected void init() {
        int w = Math.min(280,width-20), x=(width-w)/2, y=28;
        var menu=addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.screen"),b -> FancyBridge.edit(parent)).bounds(x,y,w,20).build()); menu.active=FancyBridge.available(); y+=21;
        var hud=addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.hud"),b -> FancyBridge.editHud()).bounds(x,y,w,20).build()); hud.active=FancyBridge.spiffy(); y+=21;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.rows"),b -> com.takumistudios.socialmod.client.compat.ClientCompat.setScreen(new RowTemplateScreen(this))).bounds(x,y,w,20).build()); y+=21;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.basic"),b -> com.takumistudios.socialmod.client.compat.ClientCompat.setScreen(new VisualEditorScreen(this))).bounds(x,y,w,20).build()); y+=21;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.design",Component.translatable("socialmod.advanced.design."+com.takumistudios.socialmod.common.model.SeriesTemplates.IDS.get(style))),b -> { if(!busy) { style=(style+1)%2; rebuildWidgets(); } }).bounds(x,y,w,20).build()); y+=21;
        var install=addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.install"),b -> {
            if(busy)return; busy=true; rebuildWidgets();
            status=Component.translatable("socialmod.advanced.exporting").getString();
            FancyBridge.install(parent,com.takumistudios.socialmod.common.model.SeriesTemplates.IDS.get(style)).whenComplete((v,e) -> minecraft.execute(() -> { busy=false; status=e==null?Component.translatable("socialmod.advanced.saved").getString():Component.translatable("socialmod.advanced.failed").getString(); rebuildWidgets(); }));
        }).bounds(x,y,w,20).build()); install.active=!busy && FancyBridge.available(); y+=21;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.series.title"),b -> com.takumistudios.socialmod.client.compat.ClientCompat.setScreen(new SeriesManagerScreen(this))).bounds(x,y,w,20).build()); y+=21;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.reset"),b -> {
            status=Component.translatable("socialmod.advanced.exporting").getString();
            if(busy)return; busy=true; rebuildWidgets();
            FancyBridge.reset().whenComplete((v,e) -> minecraft.execute(() -> { busy=false; status=e==null?Component.translatable("socialmod.advanced.saved").getString():Component.translatable("socialmod.advanced.failed").getString(); rebuildWidgets(); }));
        }).bounds(x,y,w,20).build());
        addRenderableWidget(Ui.button(Component.translatable("gui.back"),b -> onClose()).bounds(x,height-25,w,20).build());
        if(busy) for(var widget:net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(this)) widget.active=false;
    }
    @Override public void onClose() { if(!busy) super.onClose(); }
    @Override protected void drawContent(GuiGraphicsExtractor graphics,int mx,int my) {
        Ui.title(graphics,font,title,width/2,10);
        var state = status.isEmpty() ? Component.translatable("socialmod.advanced.integrations", Component.translatable(FancyBridge.available()?"gui.yes":"gui.no"), Component.translatable(FancyBridge.spiffy()?"gui.yes":"gui.no")).getString() : status;
        graphics.centeredText(font,Component.literal(Ui.trim(font,state,width-20)),width/2,height-45,Ui.theme().colors().text());
    }
}
