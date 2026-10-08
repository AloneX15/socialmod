package com.takumistudios.socialmod.test;

import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.compat.fancy.SocialElements;
import com.takumistudios.socialmod.client.screen.*;
import com.takumistudios.socialmod.client.theme.AppearanceMode;
import com.takumistudios.socialmod.common.net.SocialAction;
import de.keksuccino.fancymenu.customization.ScreenCustomization;
import de.keksuccino.fancymenu.customization.element.*;
import de.keksuccino.fancymenu.customization.element.anchor.ElementAnchorPoints;
import de.keksuccino.fancymenu.customization.layer.ScreenCustomizationLayerHandler;
import de.keksuccino.fancymenu.customization.layout.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.*;

/** Exercises the actual FancyMenu registry, saved layouts and shared native form controls. */
final class SocialCatalogTests {
    static void run(ClientGameTestContext context,String conversation) {
        context.runOnClient(client->ClientNet.action(SocialAction.TEAM_CREATE,"CatalogFixture","shield;#55FF55;{\"base\":15,\"layers\":[{\"pattern\":\"minecraft:stripe_center\",\"color\":14}]}"));
        context.waitFor(client->ClientState.get().snapshot().teams.stream().anyMatch(t->t.name.equals("CatalogFixture")),100);
        String team=context.computeOnClient(client->ClientState.get().snapshot().teams.stream().filter(t->t.name.equals("CatalogFixture")).findFirst().orElseThrow().id);
        context.setScreen(()->new SocialScreen(conversation));context.waitTicks(4);
        context.runOnClient(client->{
            var host=ClientCompat.currentScreen();
            for(String module:SocialComponents.MODULES) if(ElementRegistry.getBuilder("socialmod_module_"+module)==null)throw new AssertionError("Missing catalog module "+module);
            for(String module:SocialComponents.MODULES) {
                String destination=module.equals("chat")?conversation:java.util.List.of("group","invite").contains(module)?ClientState.get().snapshot().self.mainGroup:module.equals("profile")?ClientState.get().snapshot().self.uuid:team;
                var nativeForm=SocialComponents.form(host,module,"fixed",destination,"coverage");
                if(nativeForm==null)throw new AssertionError("Native catalog form unavailable: "+module);
                nativeForm.tick(0);
            }
            if(SocialComponents.create(host,"chat","dm:malformed")!=null)throw new AssertionError("Malformed chat destination accepted");
            var malformed=new SerializedElement();malformed.putProperty("social_target","x".repeat(181));
            var safe=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_banner_view").deserializeElement(malformed);
            if(!safe.target.isEmpty())throw new AssertionError("Oversized target not bounded");
            var form=SocialComponents.form(host,"settings","self","","shared");
            var same=SocialComponents.form(host,"settings","self","","shared");
            var separate=SocialComponents.form(host,"settings","self","","separate");
            if(form!=same||form==separate)throw new AssertionError("Form namespace isolation failed");
            var field=(EditBox)form.controls().stream().filter(w->w instanceof EditBox).findFirst().orElseThrow();field.setValue("Catalog draft");
            if(!((EditBox)same.controls().stream().filter(w->w instanceof EditBox).findFirst().orElseThrow()).getValue().equals("Catalog draft"))throw new AssertionError("Shared draft lost");
            if(((EditBox)separate.controls().stream().filter(w->w instanceof EditBox).findFirst().orElseThrow()).getValue().equals("Catalog draft"))throw new AssertionError("Draft leaked");
            var banner=SocialComponents.form(host,"banner","fixed",team,"draft");
            if(banner==null)throw new AssertionError("TEAM leader banner form unavailable");
            if(SocialComponents.form(host,"banner","fixed","missing","draft")!=null)throw new AssertionError("Invalid target enabled");
            if(SocialComponents.form(host,"banner","fixed",team,"draft")==banner)throw new AssertionError("Target change kept old draft");
            var layout=Layout.buildForScreen(host);
            var e=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_banner_view").buildDefaultInstance();
            e.context="fixed";e.target=team;e.anchorPoint=ElementAnchorPoints.TOP_LEFT;e.posOffsetX=80;e.posOffsetY=55;e.baseWidth=70;e.baseHeight=140;e.setInstanceIdentifier("catalog_banner");
            layout.serializedElements.add(e.getBuilder().serializeElementInternal(e));
            var data=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_data").buildDefaultInstance();data.context="fixed";data.target=team;data.anchorPoint=ElementAnchorPoints.TOP_LEFT;data.posOffsetX=80;data.posOffsetY=200;data.setInstanceIdentifier("catalog_name");layout.serializedElements.add(data.getBuilder().serializeElementInternal(data));
            var control=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_control").buildDefaultInstance();control.module="settings";control.form="shared";control.control=form.key(field);control.anchorPoint=ElementAnchorPoints.TOP_LEFT;control.posOffsetX=260;control.posOffsetY=60;control.baseWidth=220;control.setInstanceIdentifier("catalog_field");layout.serializedElements.add(control.getBuilder().serializeElementInternal(control));
            var search=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_module_search").buildDefaultInstance();search.anchorPoint=ElementAnchorPoints.TOP_LEFT;search.posOffsetX=260;search.posOffsetY=90;search.baseWidth=250;search.baseHeight=200;search.setInstanceIdentifier("catalog_search");layout.serializedElements.add(search.getBuilder().serializeElementInternal(search));
            var path=client.gameDirectory.toPath().resolve("config/fancymenu/customization/socialmod_catalog_test.txt");
            layout.setEnabled(true,false);
            if(!LayoutHandler.saveLayoutToFile(layout,path.toString()))throw new AssertionError("Layout save failed");
            ScreenCustomization.setCustomizationForScreenEnabled(host,true);ScreenCustomization.reloadFancyMenu();
        });
        context.setScreen(()->new SocialScreen(conversation));context.waitTicks(15);
        context.runOnClient(client->{
            var layer=ScreenCustomizationLayerHandler.getLayerOfScreen(ClientCompat.currentScreen());
            var banner=(SocialElements.Element)layer.getElementByInstanceIdentifier("catalog_banner");
            if(banner==null||!banner.target.equals(team)||!banner.context.equals("fixed")||banner.baseWidth!=70)throw new AssertionError("Saved context/size lost");
            var field=(SocialElements.Element)layer.getElementByInstanceIdentifier("catalog_field");field.tick();
            if(!ClientCompat.currentScreen().mouseClicked(new MouseButtonEvent(field.getAbsoluteX()+3,field.getAbsoluteY()+5,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false))throw new AssertionError("Independent field lost input");
            ClientCompat.currentScreen().charTyped(new CharacterEvent('X'));
            var form=SocialComponents.form(ClientCompat.currentScreen(),"settings","self","","shared");
            if(form.controls().stream().filter(w->w instanceof EditBox).map(w->((EditBox)w).getValue()).noneMatch(t->t.contains("X")))throw new AssertionError("Independent field did not update form: focused="+ClientCompat.currentScreen().getFocused()+" native="+form.screen.getFocused()+" field="+field.getAbsoluteX()+","+field.getAbsoluteY()+" visible="+field.shouldRender()+" children="+net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(ClientCompat.currentScreen()).stream().map(w->w.getClass().getSimpleName()+":"+w.getX()+","+w.getY()+","+w.visible+","+w.active).toList());
        });
        context.takeScreenshot("catalog_01_banner_and_controls");
        context.runOnClient(client->{
            de.keksuccino.fancymenu.customization.action.ActionRegistry.getAction("socialmod_open_module").execute("{\"module\":\"teams\",\"context\":\"fixed\",\"target\":\""+team+"\"}");
            if(!(ClientCompat.currentScreen() instanceof TeamScreen screen) || !screen.selectedTeam().equals(team))throw new AssertionError("Native FancyMenu action lost destination");
        });
        context.clickScreenButton("gui.back");context.waitTicks(4);
        context.runOnClient(client->AppearanceMode.setPersonal(true));context.waitTicks(4);
        context.runOnClient(client->{if(ScreenCustomization.isCustomizationEnabledForScreen(ClientCompat.currentScreen()))throw new AssertionError("Original mode still customized");});
        context.takeScreenshot("catalog_02_original");
        context.runOnClient(client->AppearanceMode.setPersonal(false));context.waitTicks(6);
        context.runOnClient(client->{
            if(ScreenCustomizationLayerHandler.getLayerOfScreen(ClientCompat.currentScreen()).getElementByInstanceIdentifier("catalog_banner")==null)throw new AssertionError("Style reactivation lost catalog element");
            for(var layout:LayoutHandler.getAllLayouts())if(layout.layoutFile!=null&&layout.layoutFile.getName().equals("socialmod_catalog_test.txt"))layout.setEnabled(false,false);
            SocialComponents.clear();ScreenCustomization.reloadFancyMenu();ClientNet.action(SocialAction.TEAM_ARCHIVE,team,"");
        });
        context.setScreen(()->new SocialScreen(conversation));context.waitTicks(3);
    }
}
