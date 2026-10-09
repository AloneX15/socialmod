package com.takumistudios.socialmod.test;

import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.compat.fancy.FancyBridge;
import com.takumistudios.socialmod.client.screen.*;
import com.takumistudios.socialmod.client.theme.*;
import de.keksuccino.fancymenu.customization.ScreenCustomization;
import de.keksuccino.fancymenu.customization.layer.ScreenCustomizationLayerHandler;
import de.keksuccino.fancymenu.customization.layout.LayoutHandler;
import de.keksuccino.fancymenu.customization.element.anchor.ElementAnchorPoints;
import de.keksuccino.fancymenu.util.rendering.ui.widget.UniqueWidget;
import de.keksuccino.fancymenu.util.rendering.ui.widget.CustomizableWidget;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.*;

/** Loaded only in the optional editor test profile. */
final class FancyIntegrationTests {
    static void prepare(ClientGameTestContext context) {
        context.runOnClient(client -> { de.keksuccino.fancymenu.util.rendering.ui.pipwindow.PiPWindowHandler.INSTANCE.closeAllWindows(); de.keksuccino.fancymenu.FancyMenu.getOptions().showWelcomeScreen.setValue(false); });
    }
    static void run(ClientGameTestContext context, String conversation) {
        if (!FancyBridge.available()) throw new AssertionError("FancyMenu integration did not initialize");
        context.runOnClient(client -> { de.keksuccino.fancymenu.util.rendering.ui.pipwindow.PiPWindowHandler.INSTANCE.closeAllWindows(); de.keksuccino.fancymenu.FancyMenu.getOptions().showWelcomeScreen.setValue(false); });
        var previousRows = context.computeOnClient(client -> RowTemplates.design());
        var previousVisual = context.computeOnClient(client -> LocalSeriesDesign.get());
        int previousScale = context.computeOnClient(client -> client.options.guiScale().get());
        String previousLanguage = context.computeOnClient(client -> client.getLanguageManager().getSelected());
        try {
        context.runOnClient(client -> { client.options.guiScale().set(1); de.keksuccino.fancymenu.util.rendering.RenderingUtils.resetGuiScale(); ClientCompat.setScreen(new SocialScreen(conversation)); });
        SocialCatalogTests.run(context, conversation);
        compactControls(context, conversation);
        await(context, context.computeOnClient(client -> FancyBridge.install(ClientCompat.currentScreen(),"clean")));
        context.runOnClient(client -> VisualManager.preview(com.takumistudios.socialmod.common.model.SeriesTemplates.visual("clean")));
        context.waitTicks(10);
        RowEditorRegressionTests.run(context);
        for (String language : new String[]{"es_es","en_us"}) {
            SocialModClientGameTest.language(context,language);
            context.runOnClient(client -> ClientCompat.setScreen(new SocialScreen(conversation))); context.waitTicks(6);
            context.runOnClient(client -> {
                var screen = ClientCompat.currentScreen(); var layer = ScreenCustomizationLayerHandler.getLayerOfScreen(screen);
                if (layer == null || layer.cachedScreenWidgetMetas.stream().noneMatch(m -> "socialmod_block_chat".equals(m.getUniversalIdentifier()))) throw new AssertionError("Chat block not discovered by FancyMenu");
                var ids = new java.util.HashSet<String>();
                for(var widget : net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen)) { var id=((UniqueWidget)(Object)widget).getWidgetIdentifierFancyMenu(); if(id!=null && !ids.add(id)) throw new AssertionError("Duplicate widget identifier: "+id); }
            });
            context.takeScreenshot("advanced_"+language+"_01_panel");
            context.runOnClient(client -> ClientCompat.setScreen(new AdvancedCustomizationScreen(ClientCompat.currentScreen()))); context.waitTicks(2);
            context.takeScreenshot("advanced_"+language+"_02_hub");
            context.clickScreenButton("socialmod.advanced.rows"); context.waitTicks(2); context.takeScreenshot("advanced_"+language+"_03_rows");
            context.runOnClient(client -> { client.options.guiScale().set(2); de.keksuccino.fancymenu.util.rendering.RenderingUtils.resetGuiScale(); }); context.waitTicks(2);
            context.runOnClient(client -> {
                var screen = ClientCompat.currentScreen(); var widgets=net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen);
                for (var a : widgets) {
                    if (a.getX()<0 || a.getY()<0 || a.getRight()>screen.width || a.getBottom()>screen.height) throw new AssertionError("Row editor control outside small GUI: "+a.getMessage().getString());
                    for (var b:widgets) if(a!=b && a.getX()<b.getRight() && a.getRight()>b.getX() && a.getY()<b.getBottom() && a.getBottom()>b.getY()) throw new AssertionError("Overlapping row editor controls: "+a.getMessage().getString()+" / "+b.getMessage().getString());
                }
            });
            context.takeScreenshot("advanced_"+language+"_07_small_rows");
            context.runOnClient(client -> { client.options.guiScale().set(1); de.keksuccino.fancymenu.util.rendering.RenderingUtils.resetGuiScale(); }); context.waitTicks(2);
            context.clickScreenButton("gui.back"); context.clickScreenButton("socialmod.advanced.screen"); context.waitTicks(8); context.takeScreenshot("advanced_"+language+"_04_fancymenu");
            context.runOnClient(client -> { var editor=de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen.getCurrentInstance(); if(editor==null) throw new AssertionError("Editor not opened"); editor.closeEditor(); }); context.waitTicks(4);
            if(FancyBridge.spiffy()) {
                context.runOnClient(client -> FancyBridge.editHud()); context.waitTicks(8); context.takeScreenshot("advanced_"+language+"_05_spiffyhud");
                context.runOnClient(client -> { var editor=de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen.getCurrentInstance(); if(editor==null) throw new AssertionError("HUD editor not opened"); editor.closeEditor(); }); context.waitTicks(3);
                context.runOnClient(client -> ClientCompat.setScreen(null)); context.waitTicks(3);
                context.runOnClient(client -> {
                    for (String kind : java.util.List.of("social","party","pings","toasts")) if(!FancyBridge.replacesHud(kind)) throw new AssertionError("HUD replacement not active: "+kind);
                });
                context.takeScreenshot("advanced_"+language+"_06_hud");
                SpiffyIntegrationTests.assertRendered(context);
            }
        }
        context.runOnClient(client -> ClientCompat.setScreen(new SocialScreen(conversation))); context.waitTicks(5);
        context.runOnClient(client -> {
            var screen=ClientCompat.currentScreen(); var layer=ScreenCustomizationLayerHandler.getLayerOfScreen(screen);
            var element=layer.vanillaWidgetElements.stream().filter(e -> "socialmod_block_players".equals(e.getInstanceIdentifier())).findFirst().orElseThrow();
            element.anchorPoint=ElementAnchorPoints.TOP_LEFT; element.posOffsetX=20; element.posOffsetY=90; element.baseWidth=160; element.baseHeight=220; element.updateWidgetPosition(); element.updateWidgetSize();
        }); context.waitTicks(4);
        context.runOnClient(client -> {
            var screen=(SocialScreen)ClientCompat.currentScreen();
            var block=net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).stream().filter(w -> "socialmod_block_players".equals(((UniqueWidget)(Object)w).getWidgetIdentifierFancyMenu())).findFirst().orElseThrow();
            if(block.getX()!=20 || block.getY()!=90 || block.getWidth()!=160) throw new AssertionError("Moved widget bounds ignored");
            boolean clicked=screen.mouseClicked(new MouseButtonEvent(55,110,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);
            if(!clicked || !(ClientCompat.currentScreen() instanceof ProfileScreen)) throw new AssertionError("Moved list hit testing did not open profile: clicked="+clicked+", screen="+ClientCompat.currentScreen().getClass().getSimpleName()+", children="+screen.children().stream().map(c->c.getClass().getSimpleName()).toList()+", rows="+rowsInfo(screen));
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            var profile = ClientCompat.currentScreen();
            var back = net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(profile).stream().filter(w -> "socialmod_button_gui.back".equals(((UniqueWidget)(Object)w).getWidgetIdentifierFancyMenu())).findFirst().orElseThrow();
            boolean handled = profile.mouseClicked(new MouseButtonEvent(back.getX()+5,back.getY()+5,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);
            if (!handled || !(ClientCompat.currentScreen() instanceof SocialScreen)) throw new AssertionError("Passive screen content blocked Back: handled="+handled+", screen="+ClientCompat.currentScreen().getClass().getSimpleName()+", back="+back.getX()+","+back.getY()+","+back.active+","+back.visible+", hovered="+profile.children().stream().filter(c->c.isMouseOver(back.getX()+5,back.getY()+5)).map(c->c.getClass().getSimpleName()).toList());
        });
        context.runOnClient(client -> ClientCompat.setScreen(new SocialScreen(conversation))); context.waitTicks(3);
        context.runOnClient(client -> {
            var screen=(SocialScreen)ClientCompat.currentScreen();
            var block=net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).stream().filter(w -> "socialmod_block_players".equals(((UniqueWidget)(Object)w).getWidgetIdentifierFancyMenu())).findFirst().orElseThrow();
            ((CustomizableWidget)(Object)block).setHiddenFancyMenu(true);
            if(screen.mouseScrolled(block.getX()+5,block.getY()+30,0,1)) throw new AssertionError("Hidden list consumed scroll");
        });
        var exported=context.computeOnClient(client -> java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                var zip=SeriesPackFiles.export(client.gameDirectory.toPath()); var target=java.nio.file.Path.of("advanced-export"); java.nio.file.Files.createDirectories(target);
                java.nio.file.Files.copy(zip,target.resolve("clean-modpack.zip"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                testFreshPack(zip);
            } catch(Exception e) { throw new java.util.concurrent.CompletionException(e); }
            testExportResources();
        }));
        await(context,exported);
        for(String style:com.takumistudios.socialmod.common.model.SeriesTemplates.IDS) {
            context.runOnClient(client -> ClientCompat.setScreen(new SocialScreen(conversation)));
            await(context,context.computeOnClient(client -> FancyBridge.install(ClientCompat.currentScreen(),style)));
            context.runOnClient(client -> VisualManager.preview(com.takumistudios.socialmod.common.model.SeriesTemplates.visual(style)));
            if(style.equals("clean"))context.takeScreenshot("neutral_style");
            for(String language:new String[]{"es_es","en_us"}) {
                SocialModClientGameTest.language(context,language);
                context.runOnClient(client -> ClientCompat.setScreen(new SocialScreen(conversation))); context.waitTicks(6);
                context.runOnClient(client -> {
                    var visual=LocalSeriesDesign.get();
                    if(!visual.transparentWorld || !visual.seriesStyle.equals(style) || FancyBridge.background(ClientCompat.currentScreen())) throw new AssertionError("Series background obscures world");
                    var theme=Ui.theme(); if(theme.textures().background().isPresent() || theme.colors().background()!=0 || (theme.colors().panel()>>>24)>=255) throw new AssertionError("Opaque series theme");
                });
                context.takeScreenshot("series_"+style+"_"+language+"_panel");
                context.runOnClient(client -> ClientCompat.setScreen(null)); context.waitTicks(5);
                context.takeScreenshot("series_"+style+"_"+language+"_hud");
                if(FancyBridge.spiffy()) SpiffyIntegrationTests.assertRendered(context);
            }
            var pack=context.computeOnClient(client -> { ClientCompat.setScreen(new SocialScreen(conversation)); return FancyBridge.template(ClientCompat.currentScreen(),style); });
            await(context,java.util.concurrent.CompletableFuture.runAsync(() -> {
                try { com.takumistudios.socialmod.common.model.SeriesPack.write(java.nio.file.Path.of("advanced-export",style+"-"+net.minecraft.SharedConstants.getCurrentVersion().id()+".zip"),pack); }
                catch(java.io.IOException e) { throw new java.util.concurrent.CompletionException(e); }
            }));
        }
        await(context,context.computeOnClient(client -> FancyBridge.reset()));
        context.runOnClient(client -> {
            if (LocalSeriesDesign.get()==null || !RowTemplates.enabled("message")) throw new AssertionError("Profile restoration did not preserve previous design");
        });
        } finally {
        context.runOnClient(client -> { for(var layout:LayoutHandler.getAllLayouts()) if(layout.layoutFile!=null && layout.layoutFile.getName().startsWith("socialmod_clean")) layout.setEnabled(false,false); ScreenCustomization.reloadFancyMenu(); LocalSeriesDesign.clear(); client.options.guiScale().set(previousScale); de.keksuccino.fancymenu.util.rendering.RenderingUtils.resetGuiScale(); ClientCompat.setScreen(null); });
        await(context,context.computeOnClient(client -> RowTemplates.save(previousRows)));
        await(context,context.computeOnClient(client -> previousVisual==null ? LocalSeriesDesign.reset() : LocalSeriesDesign.save(previousVisual)));
        context.runOnClient(client -> { VisualManager.preview(null); VisualManager.accept(com.takumistudios.socialmod.client.ClientState.get().snapshot().visual); });
        SocialModClientGameTest.language(context,previousLanguage);
        }
    }
    private static void await(ClientGameTestContext context, java.util.concurrent.CompletableFuture<?> future) {
        context.waitFor(client -> future.isDone(),200);
        try { future.join(); } catch(java.util.concurrent.CompletionException e) { throw new AssertionError("Async customization failed",e.getCause()); }
    }
    private static void compactControls(ClientGameTestContext context, String conversation) {
        context.setScreen(() -> new SocialScreen(conversation));
        context.runOnClient(client -> FancyBridge.edit(ClientCompat.currentScreen())); context.waitTicks(5);
        context.runOnClient(client -> de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen.getCurrentInstance().closeEditor()); context.waitTicks(5);
        context.runOnClient(client -> {
            var screen = ClientCompat.currentScreen(); var layer = ScreenCustomizationLayerHandler.getLayerOfScreen(screen);
            var group = layer.vanillaWidgetElements.stream().filter(e -> "socialmod_button_socialmod.panel.new_group".equals(e.getInstanceIdentifier())).findFirst().orElseThrow();
            group.anchorPoint = ElementAnchorPoints.TOP_LEFT; group.posOffsetX = 20; group.posOffsetY = 45; group.baseWidth = group.baseHeight = 22; group.updateWidgetPosition(); group.updateWidgetSize();
            var source = group.getPropertySource(); source.label = ""; source.hoverLabel = ""; group.updateWidgetLabels();
            try {
                var path = client.gameDirectory.toPath().resolve("config/fancymenu/assets/socialmod-test-icon.png"); java.nio.file.Files.createDirectories(path.getParent());
                try (var input = FancyIntegrationTests.class.getResourceAsStream("/assets/socialmod/icon.png")) { java.nio.file.Files.write(path, input.readAllBytes()); }
                source.iconTextureNormal = de.keksuccino.fancymenu.util.resource.ResourceSupplier.image("[source:local]config/fancymenu/assets/socialmod-test-icon.png");
                source.iconTextureHover = source.iconTextureNormal;
            } catch (java.io.IOException e) { throw new AssertionError(e); }
            var search = layer.vanillaWidgetElements.stream().filter(e -> "socialmod_input_socialmod.panel.search".equals(e.getInstanceIdentifier())).findFirst().orElseThrow(); search.setHidden(true); search.updateWidgetVisibility();
            var find = layer.vanillaWidgetElements.stream().filter(e -> "socialmod_button_socialmod.search.button".equals(e.getInstanceIdentifier())).findFirst().orElseThrow();
            find.anchorPoint = ElementAnchorPoints.TOP_LEFT; find.posOffsetX = 46; find.posOffsetY = 45; find.baseWidth = find.baseHeight = 22; find.updateWidgetPosition(); find.updateWidgetSize();
        }); context.waitTicks(10); context.takeScreenshot("banner_07_fancy_compact_buttons");
        context.runOnClient(client -> {
            var screen = ClientCompat.currentScreen();
            boolean clicked = screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(25, 50, new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            if (!clicked || !(ClientCompat.currentScreen() instanceof CreateGroupScreen)) throw new AssertionError("Compact icon button lost action: handled=" + clicked + " screen=" + ClientCompat.currentScreen().getClass().getSimpleName() + " controls=" + net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).stream().filter(w -> w.isMouseOver(25,50)).map(w -> ((de.keksuccino.fancymenu.util.rendering.ui.widget.UniqueWidget)(Object)w).getWidgetIdentifierFancyMenu() + ":" + w.getX() + "," + w.getY() + "," + w.getWidth() + "," + w.visible + "," + w.active + "," + FancyBridge.hidden(w)).toList());
        });
        context.setScreen(() -> new SocialScreen(conversation)); context.waitTicks(4);
        context.runOnClient(client -> {
            var screen = ClientCompat.currentScreen();
            var layer = ScreenCustomizationLayerHandler.getLayerOfScreen(screen);
            var find = layer.vanillaWidgetElements.stream().filter(e -> "socialmod_button_socialmod.search.button".equals(e.getInstanceIdentifier())).findFirst().orElseThrow();
            find.anchorPoint = ElementAnchorPoints.TOP_LEFT; find.posOffsetX = 46; find.posOffsetY = 45; find.baseWidth = find.baseHeight = 22; find.updateWidgetPosition(); find.updateWidgetSize();
            boolean clicked = screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(51, 50, new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            if (!clicked || !(ClientCompat.currentScreen() instanceof PlayerSearchScreen)) throw new AssertionError("Icon search did not open universal search: " + ClientCompat.currentScreen().getClass().getSimpleName());
        });
        context.setScreen(() -> new SocialScreen(conversation));
        context.runOnClient(client -> {
            com.takumistudios.socialmod.client.theme.AppearanceMode.setPersonal(true);
            if (ScreenCustomization.isCustomizationEnabledForScreen(ClientCompat.currentScreen())) throw new AssertionError("FancyMenu still active in original mode");
            var group = net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(ClientCompat.currentScreen()).stream().filter(w -> w.getMessage().getString().contains(net.minecraft.network.chat.Component.translatable("socialmod.panel.new_group").getString())).findFirst().orElseThrow();
            if (group.getWidth() == 22) throw new AssertionError("Original mode retained compact FancyMenu bounds");
            com.takumistudios.socialmod.client.theme.AppearanceMode.setPersonal(false);
        }); context.waitTicks(4);
    }
    private static void testFreshPack(java.nio.file.Path exported) throws java.io.IOException {
        var root=java.nio.file.Files.createTempDirectory("socialmod-clean-instance-");
        try(var zip=new java.util.zip.ZipFile(exported.toFile())) {
            for(var entries=zip.entries();entries.hasMoreElements();) {
                var entry=entries.nextElement(); var target=root.resolve(entry.getName()).normalize();
                if(!target.startsWith(root)) throw new AssertionError("Unsafe exported entry");
                java.nio.file.Files.createDirectories(target.getParent());
                try(var input=zip.getInputStream(entry)) { java.nio.file.Files.copy(input,target); }
            }
        }
        var rows=com.takumistudios.socialmod.common.model.RowDesign.parse(java.nio.file.Files.readString(root.resolve("config/socialmod/integration/rows.json")));
        var visual=com.takumistudios.socialmod.common.model.VisualDesign.parse(java.nio.file.Files.readString(root.resolve("config/socialmod/integration/visual.json")));
        String layout=java.nio.file.Files.readString(root.resolve("config/fancymenu/customization/socialmod_clean.txt"));
        if (!rows.templates.get("message").enabled || !visual.transparentWorld || !layout.contains("identifier = socialmod_social") || !layout.contains("is_enabled = true")) throw new AssertionError("Fresh instance pack is incomplete");
    }
    private static void testExportResources() {
        try {
            var root=java.nio.file.Files.createTempDirectory("socialmod-pack-");
            var dir=root.resolve("config/fancymenu/customization"); java.nio.file.Files.createDirectories(dir);
            var asset=root.resolve("config/fancymenu/assets/snow.png"); java.nio.file.Files.createDirectories(asset.getParent()); java.nio.file.Files.write(asset,new byte[]{1,2,3});
            var layout=dir.resolve("socialmod_test.txt"); java.nio.file.Files.writeString(layout,"source = [source:local]config/fancymenu/assets/snow.png\n");
            try(var zip=new java.util.zip.ZipFile(SeriesPackFiles.export(root).toFile())) { if(zip.getEntry("config/fancymenu/assets/snow.png")==null) throw new AssertionError("Referenced pack asset omitted"); }
            java.nio.file.Files.writeString(layout,"source = [source:local]../secret.png\n");
            boolean rejected=false; try { SeriesPackFiles.export(root); } catch(java.io.IOException expected) { rejected=true; }
            if(!rejected) throw new AssertionError("External pack resource accepted");
        } catch(java.io.IOException e) { throw new AssertionError(e); }
    }
    private static String rowsInfo(SocialScreen screen) {
        try { var field=SocialScreen.class.getDeclaredField("right"); field.setAccessible(true); var rows=(Ui.RowList)field.get(screen); return rows.viewX+","+rows.viewY+","+rows.viewWidth+","+rows.viewHeight+" "+rows.rows; }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
