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
    private static Object read(Object instance,String name) {
        try { var field=instance.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(instance); }
        catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    static void run(ClientGameTestContext context,String conversation) {
        context.runOnClient(client->ClientNet.action(SocialAction.TEAM_CREATE,"CatalogFixture","shield;#55FF55;{\"base\":15,\"layers\":[{\"pattern\":\"minecraft:stripe_center\",\"color\":14}]}"));
        context.waitFor(client->ClientState.get().snapshot().teams.stream().anyMatch(t->t.name.equals("CatalogFixture")),100);
        String team=context.computeOnClient(client->ClientState.get().snapshot().teams.stream().filter(t->t.name.equals("CatalogFixture")).findFirst().orElseThrow().id);
        var previousMessages=context.computeOnClient(client->java.util.List.copyOf(ClientState.get().conversation(conversation).messages));
        boolean previousMore=context.computeOnClient(client->ClientState.get().conversation(conversation).hasMore);
        context.runOnClient(client->{var cache=ClientState.get().conversation(conversation);cache.hasMore=false;cache.messages.clear();
            for(int i=0;i<80;i++)cache.messages.add(new com.takumistudios.socialmod.common.net.Payloads.MessageView(20000+i,client.player.getUUID(),"Scaled","Scaled history "+i,1,false,false,java.util.List.of()));HistoryInteractionTests.invalidate();});
        context.setScreen(()->new SocialScreen(conversation));context.waitTicks(4);
        context.runOnClient(client->{
            var host=ClientCompat.currentScreen();
            for(String module:SocialComponents.MODULES) {
                String id=module.equals("search")?"socialmod_search_players":module.equals("banner")?"socialmod_edit_team_banner":"socialmod_open_"+module;
                var action=de.keksuccino.fancymenu.customization.action.ActionRegistry.getAction(id);
                if(action==null || action.getDisplayName().getString().contains("socialmod.catalog."))throw new AssertionError("Missing named navigation action: "+module);
                String destination=module.equals("chat")?conversation:java.util.List.of("group","invite").contains(module)?ClientState.get().snapshot().self.mainGroup:module.equals("profile")?ClientState.get().snapshot().self.uuid:team;
                action.execute("{\"context\":\"fixed\",\"target\":\""+destination+"\"}");
                if(ClientCompat.currentScreen()==host)throw new AssertionError("Named navigation action did not open panel: "+module);
                if(module.equals("team_management") && (!(ClientCompat.currentScreen() instanceof TeamManagementScreen screen) || !screen.selectedTeam().equals(team)))throw new AssertionError("TEAM settings action lost selected destination");
                ClientCompat.setScreen(host);
            }
            for(String id:java.util.List.of("socialmod_create_group_style","socialmod_group_style","socialmod_tag_banner")) {
                var action=de.keksuccino.fancymenu.customization.action.ActionRegistry.getAction(id);
                action.execute("{\"context\":\"fixed\",\"target\":\""+(id.equals("socialmod_group_style")?ClientState.get().snapshot().self.mainGroup:team)+"\"}");
                if(!(ClientCompat.currentScreen() instanceof TagStyleScreen || ClientCompat.currentScreen() instanceof BannerEditorScreen))throw new AssertionError("Draft navigation failed: "+id);
                ClientCompat.setScreen(host);
            }
            de.keksuccino.fancymenu.customization.action.ActionRegistry.getAction("socialmod_open_advanced").execute("{}");
            if(!(ClientCompat.currentScreen() instanceof AdvancedCustomizationScreen))throw new AssertionError("Advanced action failed");
            de.keksuccino.fancymenu.customization.action.ActionRegistry.getAction("socialmod_open_back").execute("{}");
            if(ClientCompat.currentScreen()!=host)throw new AssertionError("Back action lost parent");
            de.keksuccino.fancymenu.customization.action.ActionRegistry.getAction("socialmod_open_quick_reply").execute("{\"context\":\"fixed\",\"target\":\""+conversation+"\"}");
            if(!(ClientCompat.currentScreen() instanceof QuickReplyScreen))throw new AssertionError("Quick reply action failed");
            ClientCompat.setScreen(host);
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
            for (String module : SocialComponents.MODULES) {
                String destination=module.equals("chat")?conversation:java.util.List.of("group","invite").contains(module)?ClientState.get().snapshot().self.mainGroup:module.equals("profile")?ClientState.get().snapshot().self.uuid:team;
                var moduleForm=SocialComponents.form(host,module,"fixed",destination,"coverage");
                for(var widget:moduleForm.controls()) if(moduleForm.control(moduleForm.key(widget))!=widget) throw new AssertionError("Unselectable control in "+module);
            }
            var conv=SocialComponents.form(host,"conversations","self","","main");
            for(String id:java.util.List.of("input_socialmod.panel.search","button_socialmod.panel.new_group","button_socialmod.panel.settings","button_socialmod.search.button"))
                if(conv.controls().stream().noneMatch(widget->com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identifier(widget).equals(id)))throw new AssertionError("Missing conversation control "+id);
            var missing=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_banner_view").buildDefaultInstance(); missing.context="fixed";missing.target="missing";missing.tick();
            if(read(missing,"banner")==null)throw new AssertionError("Missing TEAM has no blank banner");
            if(!SocialComponents.teamEditError(host,"fixed","missing").equals("socialmod.team.not_found"))throw new AssertionError("Missing TEAM error not explained");
            if(!missing.mouseClicked(new MouseButtonEvent(missing.getAbsoluteX()+missing.getAbsoluteWidth()/2.0,missing.getAbsoluteY()+missing.getAbsoluteHeight()/2.0,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false) || !(ClientCompat.currentScreen() instanceof net.minecraft.client.gui.screens.AlertScreen))throw new AssertionError("Missing TEAM click not explained");
            ClientCompat.setScreen(host);
            var teamView=ClientState.get().snapshot().teams.stream().filter(t->t.id.equals(team)).findFirst().orElseThrow();
            boolean admin=ClientState.get().snapshot().teamAdmin;String leader=teamView.leader;
            try { ClientState.get().snapshot().teamAdmin=false;teamView.leader="another-player";
                if(SocialComponents.create(host,"banner",team)!=null)throw new AssertionError("Banner editor bypassed permissions");
                if(!SocialComponents.teamEditError(host,"fixed",team).equals("socialmod.team.banner_permission"))throw new AssertionError("Banner permission error not explained");
            } finally { ClientState.get().snapshot().teamAdmin=admin;teamView.leader=leader; }
            var layout=Layout.buildForScreen(host);
            var e=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_banner_view").buildDefaultInstance();
            e.context="fixed";e.target=team;e.anchorPoint=ElementAnchorPoints.TOP_LEFT;e.posOffsetX=80;e.posOffsetY=55;e.baseWidth=70;e.baseHeight=140;e.setInstanceIdentifier("catalog_banner");
            layout.serializedElements.add(e.getBuilder().serializeElementInternal(e));
            var tag=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_team_tag_view").buildDefaultInstance();tag.context="fixed";tag.target=team;tag.anchorPoint=ElementAnchorPoints.TOP_LEFT;tag.posOffsetX=80;tag.posOffsetY=230;tag.baseWidth=150;tag.baseHeight=20;tag.setInstanceIdentifier("catalog_tag");layout.serializedElements.add(tag.getBuilder().serializeElementInternal(tag));
            var data=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_data").buildDefaultInstance();data.context="fixed";data.target=team;data.anchorPoint=ElementAnchorPoints.TOP_LEFT;data.posOffsetX=80;data.posOffsetY=200;data.setInstanceIdentifier("catalog_name");layout.serializedElements.add(data.getBuilder().serializeElementInternal(data));
            var control=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_control").buildDefaultInstance();control.module="settings";control.form="shared";control.control=form.key(field);control.anchorPoint=ElementAnchorPoints.TOP_LEFT;control.posOffsetX=260;control.posOffsetY=60;control.baseWidth=220;control.setInstanceIdentifier("catalog_field");layout.serializedElements.add(control.getBuilder().serializeElementInternal(control));
            var search=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_module_search").buildDefaultInstance();search.anchorPoint=ElementAnchorPoints.TOP_LEFT;search.posOffsetX=260;search.posOffsetY=90;search.baseWidth=250;search.baseHeight=200;search.setInstanceIdentifier("catalog_search");layout.serializedElements.add(search.getBuilder().serializeElementInternal(search));
            var chat=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_module_chat").buildDefaultInstance();chat.context="fixed";chat.target=conversation;chat.form="history";chat.anchorPoint=ElementAnchorPoints.TOP_LEFT;chat.posOffsetX=520;chat.posOffsetY=60;chat.baseWidth=220;chat.baseHeight=230;chat.setInstanceIdentifier("catalog_chat");layout.serializedElements.add(chat.getBuilder().serializeElementInternal(chat));
            var players=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_module_players").buildDefaultInstance();
            players.context="fixed";players.target=ClientState.get().snapshot().self.mainGroup;players.form="players_test";players.anchorPoint=ElementAnchorPoints.TOP_LEFT;
            players.posOffsetX=10;players.posOffsetY=310;players.baseWidth=180;players.baseHeight=140;players.showBackground=false;players.setInstanceIdentifier("catalog_players");
            layout.serializedElements.add(players.getBuilder().serializeElementInternal(players));
            var wide=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_module_players").buildDefaultInstance();
            wide.context="fixed";wide.target=players.target;wide.form=players.form;wide.anchorPoint=ElementAnchorPoints.TOP_LEFT;wide.posOffsetX=420;wide.posOffsetY=310;wide.baseWidth=400;wide.baseHeight=140;wide.showBackground=false;wide.setInstanceIdentifier("catalog_players_wide");layout.serializedElements.add(wide.getBuilder().serializeElementInternal(wide));
            var conversations=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_module_conversations").buildDefaultInstance();
            conversations.anchorPoint=ElementAnchorPoints.TOP_LEFT;conversations.posOffsetX=200;conversations.posOffsetY=310;conversations.baseWidth=200;conversations.baseHeight=140;conversations.showBackground=false;conversations.setInstanceIdentifier("catalog_conversations");
            layout.serializedElements.add(conversations.getBuilder().serializeElementInternal(conversations));
            var groupButton=conv.controls().stream().filter(widget->com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identifier(widget).equals("button_socialmod.panel.new_group")).findFirst().orElseThrow();
            var group=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_control").buildDefaultInstance();group.module="conversations";group.control=conv.key(groupButton);group.anchorPoint=ElementAnchorPoints.TOP_LEFT;group.posOffsetX=80;group.posOffsetY=270;group.baseWidth=140;group.baseHeight=20;group.setInstanceIdentifier("catalog_group");layout.serializedElements.add(group.getBuilder().serializeElementInternal(group));
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
            var tag=(SocialElements.Element)layer.getElementByInstanceIdentifier("catalog_tag");tag.tick();
            var text=(net.minecraft.network.chat.Component)read(tag,"value");
            if(!text.getString().contains("CatalogFixture") || text.getSiblings().stream().noneMatch(part->part.getStyle().getColor()!=null && part.getStyle().getColor().getValue()==0x55FF55))throw new AssertionError("TEAM tag lost identity or color");
            var field=(SocialElements.Element)layer.getElementByInstanceIdentifier("catalog_field");field.tick();
            if(!field.hideOriginal)throw new AssertionError("Detached control setting lost on import");
            var shared=SocialComponents.form(ClientCompat.currentScreen(),"settings","self","","shared");
            var original=shared.controls().stream().filter(widget->widget instanceof EditBox).findFirst().orElseThrow();
            shared.render(()->{if(original.visible)throw new AssertionError("Detached original remained visible");},0);
            if(!original.visible)throw new AssertionError("Detached render permanently hid the control");
            if(!ClientCompat.currentScreen().mouseClicked(new MouseButtonEvent(field.getAbsoluteX()+3,field.getAbsoluteY()+5,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false))throw new AssertionError("Independent field lost input");
            ClientCompat.currentScreen().charTyped(new CharacterEvent('X'));
            var form=SocialComponents.form(ClientCompat.currentScreen(),"settings","self","","shared");
            if(form.controls().stream().filter(w->w instanceof EditBox).map(w->((EditBox)w).getValue()).noneMatch(t->t.contains("X")))throw new AssertionError("Independent field did not update form: focused="+ClientCompat.currentScreen().getFocused()+" native="+form.screen.getFocused()+" field="+field.getAbsoluteX()+","+field.getAbsoluteY()+" visible="+field.shouldRender()+" children="+net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(ClientCompat.currentScreen()).stream().map(w->w.getClass().getSimpleName()+":"+w.getX()+","+w.getY()+","+w.visible+","+w.active).toList());
        });
        context.runOnClient(client->{
            var host=ClientCompat.currentScreen();var layer=ScreenCustomizationLayerHandler.getLayerOfScreen(host);
            var chat=(SocialElements.Element)layer.getElementByInstanceIdentifier("catalog_chat");chat.tick();
            var form=SocialComponents.form(host,"chat","fixed",conversation,"history");
            if(!host.mouseScrolled(chat.getAbsoluteX()+chat.getAbsoluteWidth()/2.0,chat.getAbsoluteY()+chat.getAbsoluteHeight()/2.0,0,1))throw new AssertionError("Scaled chat lost wheel input");
            if(HistoryInteractionTests.number(form.screen,"chatScroll")<=0)throw new AssertionError("Scaled chat wheel did not move history");
            int[] region=form.region();double scale=chat.getAbsoluteWidth()/(double)region[2];
            double x=chat.getAbsoluteX()+(HistoryInteractionTests.number(form.screen,"chatX")+HistoryInteractionTests.number(form.screen,"chatW")-4-region[0])*scale;
            double y=chat.getAbsoluteY()+(HistoryInteractionTests.number(form.screen,"historyTop")+2-region[1])*scale;
            if(!host.mouseClicked(new MouseButtonEvent(x,y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false))throw new AssertionError("Scaled scrollbar lost click");
            host.mouseDragged(new MouseButtonEvent(x,y-5,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),0,-5);host.mouseReleased(new MouseButtonEvent(x,y-5,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)));
            if(HistoryInteractionTests.number(form.screen,"chatScroll")!=HistoryInteractionTests.number(form.screen,"historyMaximum"))throw new AssertionError("Scaled scrollbar mapping failed");
        });context.waitTicks(3);
        context.takeScreenshot("catalog_01_banner_and_controls");
        context.runOnClient(client->{
            var host=ClientCompat.currentScreen();var layer=ScreenCustomizationLayerHandler.getLayerOfScreen(host);
            var players=(SocialElements.Element)layer.getElementByInstanceIdentifier("catalog_players");
            var conversations=(SocialElements.Element)layer.getElementByInstanceIdentifier("catalog_conversations");
            if(players.showControls || conversations.showControls || players.showBackground || conversations.showBackground)throw new AssertionError("Content-only settings not restored");
            var playerForm=SocialComponents.form(host,"players","fixed",ClientState.get().snapshot().self.mainGroup,"players_test");
            if(playerForm.region()[2]<=1)throw new AssertionError("Embedded players column collapsed in narrow layout");
            var rows=players.listRows;
            if(rows.rows.size()<2)throw new AssertionError("Players module did not render group member and online self");
            var wide=(SocialElements.Element)layer.getElementByInstanceIdentifier("catalog_players_wide");
            if(players.listRows==wide.listRows || players.listRows.viewWidth!=players.getAbsoluteWidth() || wide.listRows.viewWidth!=wide.getAbsoluteWidth())throw new AssertionError("Shared form did not retain independent viewports");
            var convForm=SocialComponents.form(host,"conversations","self","","main");
            convForm.contentOnly(()-> {if(convForm.controls().stream().anyMatch(w->w.visible))throw new AssertionError("Content-only panel retained native controls");return null;});
            var background=layer.vanillaWidgetElements.stream().filter(e->"socialmod_panel_background".equals(e.getInstanceIdentifier())).findFirst().orElseThrow(()->new AssertionError("Panel background not discoverable in FancyMenu"));
            var widget=background.widgetMeta.getWidget();
            ((de.keksuccino.fancymenu.util.rendering.ui.widget.CustomizableWidget)(Object)widget).setHiddenFancyMenu(true);
            if(!com.takumistudios.socialmod.client.compat.fancy.FancyBridge.hidden(widget))throw new AssertionError("Panel background cannot be hidden");
        });context.waitTicks(3);
        context.takeScreenshot("catalog_03_content_only_transparent_background");
        context.runOnClient(client->{var element=(SocialElements.Element)ScreenCustomizationLayerHandler.getLayerOfScreen(ClientCompat.currentScreen()).getElementByInstanceIdentifier("catalog_players");element.baseWidth=90;element.baseHeight=36;});context.waitTicks(3);
        context.runOnClient(client->{
            var host=ClientCompat.currentScreen();var layer=ScreenCustomizationLayerHandler.getLayerOfScreen(host);
            var element=(SocialElements.Element)layer.getElementByInstanceIdentifier("catalog_players");var wide=(SocialElements.Element)layer.getElementByInstanceIdentifier("catalog_players_wide");
            if(element.listRows.viewWidth!=90 || element.listRows.viewHeight!=30 || wide.listRows.viewWidth!=400)throw new AssertionError("Resize did not reflow own list");
            if(!host.mouseScrolled(element.getAbsoluteX()+10,element.getAbsoluteY()+20,0,-4) || element.listRows.scroll<=0 || wide.listRows.scroll!=0)throw new AssertionError("List scrolling leaked between elements");
            host.mouseScrolled(element.getAbsoluteX()+10,element.getAbsoluteY()+20,0,4);
            var row=element.listRows.rows.getFirst();
            if(!host.mouseClicked(new MouseButtonEvent(element.getAbsoluteX()+10,element.getAbsoluteY()+element.listRows.viewY+row.y()+2,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false) || !(ClientCompat.currentScreen() instanceof ProfileScreen))throw new AssertionError("Responsive member click lost its target");
            ClientCompat.setScreen(host);element.baseWidth=180;element.baseHeight=140;
        });context.waitTicks(3);
        context.runOnClient(client->{
            var host=ClientCompat.currentScreen();
            var background=net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(host).stream().filter(w->com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identifier(w).equals("panel_background")).findFirst().orElseThrow();
            ((de.keksuccino.fancymenu.util.rendering.ui.widget.CustomizableWidget)(Object)background).setHiddenFancyMenu(false);
        });
        context.runOnClient(client->{
            var host=ClientCompat.currentScreen();var group=(SocialElements.Element)ScreenCustomizationLayerHandler.getLayerOfScreen(host).getElementByInstanceIdentifier("catalog_group");
            boolean handled=host.mouseClicked(new MouseButtonEvent(group.getAbsoluteX()+5,group.getAbsoluteY()+5,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);
            if(!handled || !(ClientCompat.currentScreen() instanceof CreateGroupScreen))throw new AssertionError("Detached New Group button lost its action: handled="+handled+" screen="+ClientCompat.currentScreen().getClass().getSimpleName()+" group="+group.getAbsoluteX()+","+group.getAbsoluteY()+" focused="+host.getFocused()+" input="+read(group,"input")+" selected="+read(group,"selectedControl")+" children="+net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(host).stream().filter(w->w.isMouseOver(group.getAbsoluteX()+5,group.getAbsoluteY()+5)).map(w->com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identifier(w)+":"+w.getX()+","+w.getY()+","+w.getWidth()+","+w.getHeight()+","+w.active+","+w.visible).toList());
        });context.runOnClient(client->ClientCompat.currentScreen().onClose());context.waitTicks(4);
        context.setScreen(()->new SocialScreen(null));context.waitTicks(8);
        context.runOnClient(client->{
            var host=ClientCompat.currentScreen();
            var layout=LayoutHandler.getAllLayouts().stream().filter(l->l.layoutFile!=null&&l.layoutFile.getName().equals("socialmod_catalog_test.txt")).findFirst().orElseThrow();
            layout.serializedElements.removeIf(data->!java.util.List.of("catalog_banner","catalog_tag").contains(data.getValue("instance_identifier")));
            var moved=(SocialElements.Element)ElementRegistry.getBuilder("socialmod_banner_view").buildDefaultInstance();
            moved.context="fixed";moved.target=team;moved.anchorPoint=ElementAnchorPoints.MID_CENTERED;moved.posOffsetX=-17;moved.posOffsetY=-host.height/2+18;moved.baseWidth=35;moved.baseHeight=63;moved.setInstanceIdentifier("catalog_banner");
            layout.serializedElements.removeIf(data->"catalog_banner".equals(data.getValue("instance_identifier")));
            layout.serializedElements.add(moved.getBuilder().serializeElementInternal(moved));
            LayoutHandler.openLayoutEditor(layout,host);
        });context.waitTicks(4);
        var closedEditor=new java.util.concurrent.atomic.AtomicReference<de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen>();
        context.runOnClient(client->{
            if(!(ClientCompat.currentScreen() instanceof de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen editor))throw new AssertionError("Layout editor not opened");
            closedEditor.set(editor);
            editor.closeEditor();
        });context.waitTicks(8);
        context.runOnClient(client->{
            var banner=(SocialElements.Element)ScreenCustomizationLayerHandler.getLayerOfScreen(ClientCompat.currentScreen()).getElementByInstanceIdentifier("catalog_banner");
            banner.context="self";
            String previousTeam=ClientState.get().snapshot().self.teamId;ClientState.get().snapshot().self.teamId=team;
            // A cached editor reference must not make a normal social screen non-interactive.
            closedEditor.get().setAsCurrentInstance();
            try {
            double mx=banner.getAbsoluteX()+banner.getAbsoluteWidth()/2.0,my=banner.getAbsoluteY()+banner.getAbsoluteHeight()/2.0;
            if(!ClientCompat.currentScreen().mouseClicked(new MouseButtonEvent(mx,my,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false) || !(ClientCompat.currentScreen() instanceof BannerEditorScreen))throw new AssertionError("Host banner click did not open editor: rectangle="+banner.getAbsoluteX()+","+banner.getAbsoluteY()+","+banner.getAbsoluteWidth()+","+banner.getAbsoluteHeight()+" hovered="+ClientCompat.currentScreen().children().stream().filter(w->w.isMouseOver(mx,my)).map(w->w.getClass().getName()).toList());
            } finally {ClientState.get().snapshot().self.teamId=previousTeam;closedEditor.get().removed();}
        }); context.runOnClient(client->ClientCompat.currentScreen().onClose()); context.waitTicks(4);
        context.runOnClient(client->{
            de.keksuccino.fancymenu.customization.action.ActionRegistry.getAction("socialmod_search_players").execute("{}");
            if(!(ClientCompat.currentScreen() instanceof PlayerSearchScreen))throw new AssertionError("Search action did not open search");
        }); context.runOnClient(client->ClientCompat.currentScreen().onClose()); context.waitTicks(4);
        context.runOnClient(client->{
            de.keksuccino.fancymenu.customization.action.ActionRegistry.getAction("socialmod_edit_team_banner").execute("{\"context\":\"fixed\",\"target\":\""+team+"\"}");
            if(!(ClientCompat.currentScreen() instanceof BannerEditorScreen))throw new AssertionError("Banner action did not open editor");
        }); context.runOnClient(client->ClientCompat.currentScreen().onClose()); context.waitTicks(4);
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
            var cache=ClientState.get().conversation(conversation);cache.messages.clear();cache.messages.addAll(previousMessages);cache.hasMore=previousMore;HistoryInteractionTests.invalidate();
        });
        context.setScreen(()->new SocialScreen(conversation));context.waitTicks(3);
    }
}
