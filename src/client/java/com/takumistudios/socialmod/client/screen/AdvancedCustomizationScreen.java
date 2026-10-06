package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.compat.fancy.FancyBridge;
import com.takumistudios.socialmod.client.theme.SeriesPackFiles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.*;

public final class AdvancedCustomizationScreen extends SocialChildScreen {
    private String status = "";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r,"SocialMod-Series-IO"); t.setDaemon(true); return t; });
    public AdvancedCustomizationScreen(Screen parent) { super(parent, Component.translatable("socialmod.advanced.title")); }
    @Override protected boolean rebuildOnChange() { return false; }
    @Override protected void init() {
        int w = Math.min(280,width-20), x=(width-w)/2, y=30;
        var menu=addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.screen"),b -> FancyBridge.edit(parent)).bounds(x,y,w,20).build()); menu.active=FancyBridge.available(); y+=23;
        var hud=addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.hud"),b -> FancyBridge.editHud()).bounds(x,y,w,20).build()); hud.active=FancyBridge.spiffy(); y+=23;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.rows"),b -> com.takumistudios.socialmod.client.compat.ClientCompat.setScreen(new RowTemplateScreen(this))).bounds(x,y,w,20).build()); y+=23;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.basic"),b -> com.takumistudios.socialmod.client.compat.ClientCompat.setScreen(new VisualEditorScreen(this))).bounds(x,y,w,20).build()); y+=23;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.christmas"),b -> {
            status=Component.translatable("socialmod.advanced.exporting").getString();
            FancyBridge.christmas(parent).whenComplete((v,e) -> minecraft.execute(() -> status=e==null?Component.translatable("socialmod.advanced.saved").getString():Component.translatable("socialmod.advanced.failed").getString()));
        }).bounds(x,y,w,20).build()); y+=23;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.export"),b -> {
            status=Component.translatable("socialmod.advanced.exporting").getString();
            CompletableFuture.supplyAsync(() -> { try { return SeriesPackFiles.export(minecraft.gameDirectory.toPath()).toString(); } catch(Exception e) { throw new CompletionException(e); } },IO).whenComplete((path,error) -> minecraft.execute(() -> status=error==null?path:(error.getCause()==null?error.getMessage():error.getCause().getMessage())));
        }).bounds(x,y,w,20).build()); y+=23;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.reset"),b -> {
            status=Component.translatable("socialmod.advanced.exporting").getString();
            FancyBridge.reset().whenComplete((v,e) -> minecraft.execute(() -> status=e==null?Component.translatable("socialmod.advanced.saved").getString():Component.translatable("socialmod.advanced.failed").getString()));
        }).bounds(x,y,w,20).build());
        addRenderableWidget(Ui.button(Component.translatable("gui.back"),b -> onClose()).bounds(x,height-25,w,20).build());
    }
    @Override protected void drawContent(GuiGraphicsExtractor graphics,int mx,int my) {
        Ui.title(graphics,font,title,width/2,10);
        var state = status.isEmpty() ? Component.translatable("socialmod.advanced.integrations", Component.translatable(FancyBridge.available()?"gui.yes":"gui.no"), Component.translatable(FancyBridge.spiffy()?"gui.yes":"gui.no")).getString() : status;
        graphics.centeredText(font,Component.literal(Ui.trim(font,state,width-20)),width/2,height-45,Ui.theme().colors().text());
    }
}
