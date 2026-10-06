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
    static void run(ClientGameTestContext context, String conversation) {
        if (!FancyBridge.available()) throw new AssertionError("FancyMenu integration did not initialize");
        context.runOnClient(client -> { de.keksuccino.fancymenu.util.rendering.ui.pipwindow.PiPWindowHandler.INSTANCE.closeAllWindows(); de.keksuccino.fancymenu.FancyMenu.getOptions().showWelcomeScreen.setValue(false); });
        var previousRows = context.computeOnClient(client -> RowTemplates.design());
        var previousVisual = context.computeOnClient(client -> LocalSeriesDesign.get());
        int previousScale = context.computeOnClient(client -> client.options.guiScale().get());
        String previousLanguage = context.computeOnClient(client -> client.getLanguageManager().getSelected());
        try {
        context.runOnClient(client -> { client.options.guiScale().set(1); de.keksuccino.fancymenu.util.rendering.RenderingUtils.resetGuiScale(); ClientCompat.setScreen(new SocialScreen(conversation)); });
        await(context, context.computeOnClient(client -> FancyBridge.christmas(ClientCompat.currentScreen())));
        context.waitTicks(10);
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
                java.nio.file.Files.copy(zip,target.resolve("christmas-modpack.zip"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                testFreshPack(zip);
            } catch(Exception e) { throw new java.util.concurrent.CompletionException(e); }
            testExportResources();
        }));
        await(context,exported);
        await(context,context.computeOnClient(client -> FancyBridge.reset()));
        context.runOnClient(client -> {
            if (LocalSeriesDesign.get()!=null || RowTemplates.enabled("message")) throw new AssertionError("Local template reset failed");
        });
        } finally {
        context.runOnClient(client -> { for(var layout:LayoutHandler.getAllLayouts()) if(layout.layoutFile!=null && layout.layoutFile.getName().startsWith("socialmod_christmas")) layout.setEnabled(false,false); ScreenCustomization.reloadFancyMenu(); LocalSeriesDesign.clear(); client.options.guiScale().set(previousScale); de.keksuccino.fancymenu.util.rendering.RenderingUtils.resetGuiScale(); ClientCompat.setScreen(null); });
        await(context,context.computeOnClient(client -> RowTemplates.save(previousRows)));
        await(context,context.computeOnClient(client -> previousVisual==null ? LocalSeriesDesign.reset() : LocalSeriesDesign.save(previousVisual)));
        SocialModClientGameTest.language(context,previousLanguage);
        }
    }
    private static void await(ClientGameTestContext context, java.util.concurrent.CompletableFuture<?> future) {
        context.waitFor(client -> future.isDone(),200);
        try { future.join(); } catch(java.util.concurrent.CompletionException e) { throw new AssertionError("Async customization failed",e.getCause()); }
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
        String layout=java.nio.file.Files.readString(root.resolve("config/fancymenu/customization/socialmod_christmas.txt"));
        if (!rows.templates.get("message").enabled || !visual.font.equals("socialmod:christmas") || !layout.contains("identifier = socialmod_social") || !layout.contains("is_enabled = true")) throw new AssertionError("Fresh instance pack is incomplete");
    }
    private static void testExportResources() {
        try {
            var root=java.nio.file.Files.createTempDirectory("socialmod-pack-");
            var dir=root.resolve("config/fancymenu/customization"); java.nio.file.Files.createDirectories(dir);
            var asset=root.resolve("config/fancymenu/assets/snow.png"); java.nio.file.Files.createDirectories(asset.getParent()); java.nio.file.Files.write(asset,new byte[]{1,2,3});
            var layout=dir.resolve("test.txt"); java.nio.file.Files.writeString(layout,"source = [source:local]config/fancymenu/assets/snow.png\n");
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
