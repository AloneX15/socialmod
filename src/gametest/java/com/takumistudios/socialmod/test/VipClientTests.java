package com.takumistudios.socialmod.test;

import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.screen.*;
import com.takumistudios.socialmod.common.model.Role;
import com.takumistudios.socialmod.common.net.SocialAction;
import com.takumistudios.socialmod.server.SocialServer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

final class VipClientTests {
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        String previous=context.computeOnClient(client->ClientState.get().snapshot().self.teamId);
        var teamId=new java.util.concurrent.atomic.AtomicReference<String>();
        var target=java.util.UUID.randomUUID();var other=java.util.UUID.randomUUID();
        world.getServer().runOnServer(server->{
            var social=SocialServer.get();var player=server.getPlayerList().getPlayers().getFirst();
            if(!social.teams().create(player,"VipClientFixture","shield;#55FF55"))throw new AssertionError("VIP UI fixture create failed");
            var team=social.teams().find("VipClientFixture");teamId.set(team.id);
            social.teams().admin(SocialAction.TEAM_ASSIGN,player.getUUID().toString(),team.id,"test");
            team.members.put(player.getUUID(),Role.LEADER);
            social.storage().getOrCreate(target,"VipMember",System.currentTimeMillis());social.storage().getOrCreate(other,"OrdinaryMember",System.currentTimeMillis());
            social.teams().admin(SocialAction.TEAM_ASSIGN,target.toString(),team.id,"test");social.teams().admin(SocialAction.TEAM_ASSIGN,other.toString(),team.id,"test");
            social.snapshots().send(player);
        });
        context.waitFor(client->ClientState.get().snapshot().self.teamId.equals(teamId.get()) && ClientState.get().group(teamId.get())!=null && ClientState.get().group(teamId.get()).members.size()==3,100);
        String conversation=context.computeOnClient(client->"g:"+teamId.get()+":"+ClientState.get().group(teamId.get()).channels.getFirst().name);
        context.setScreen(()->new SocialScreen(conversation));context.waitTicks(3);
        context.setScreen(()->new ProfileScreen(ClientCompat.currentScreen(),target,"VipMember"));context.waitTicks(3);
        context.clickScreenButton("socialmod.profile.grant_vip");
        context.waitFor(client->ClientState.get().group(teamId.get()).members.stream().anyMatch(m->m.uuid.equals(target.toString()) && m.role.equals("vip")),100);
        context.takeScreenshot("vip_01_grant_and_revoke_control");
        context.runOnClient(client->{
            var state=ClientState.get().snapshot();var group=ClientState.get().group(teamId.get());var team=state.teams.stream().filter(t->t.id.equals(teamId.get())).findFirst().orElseThrow();
            boolean admin=state.teamAdmin;String role=group.myRole,leader=team.leader;
            try {
                state.teamAdmin=false;group.myRole="vip";team.leader=target.toString();
                if(SocialComponents.create(ClientCompat.currentScreen(),"banner",team.id)==null)throw new AssertionError("VIP banner screen denied");
                if(SocialComponents.create(ClientCompat.currentScreen(),"tag",team.id)!=null)throw new AssertionError("VIP obtained tag/color editor");
                if(!SocialComponents.teamBannerError(ClientCompat.currentScreen(),"fixed",team.id).isEmpty())throw new AssertionError("VIP Fancy banner action denied");
            } finally {state.teamAdmin=admin;group.myRole=role;team.leader=leader;}
        });
        context.runOnClient(client->ClientCompat.currentScreen().onClose());context.waitTicks(3);
        context.takeScreenshot("vip_02_crown_heart_member_icons");
        int guiScale=context.computeOnClient(client->client.options.guiScale().get());
        context.runOnClient(client->{client.options.guiScale().set(2);if(com.takumistudios.socialmod.client.compat.fancy.FancyBridge.installed())de.keksuccino.fancymenu.util.rendering.RenderingUtils.resetGuiScale();});
        context.setScreen(()->{var panel=new SocialScreen(conversation);panel.prepareComponent("players");return panel;});context.waitTicks(3);
        context.takeScreenshot("vip_03_icons_small_gui");
        context.runOnClient(client->{client.options.guiScale().set(guiScale);if(com.takumistudios.socialmod.client.compat.fancy.FancyBridge.installed())de.keksuccino.fancymenu.util.rendering.RenderingUtils.resetGuiScale();});
        context.setScreen(()->new SocialScreen(conversation));context.waitTicks(3);
        context.setScreen(()->new ProfileScreen(ClientCompat.currentScreen(),target,"VipMember"));context.waitTicks(3);
        context.clickScreenButton("socialmod.profile.revoke_vip");
        context.waitFor(client->ClientState.get().group(teamId.get()).members.stream().anyMatch(m->m.uuid.equals(target.toString()) && m.role.equals("member")),100);
        world.getServer().runOnServer(server->{var social=SocialServer.get();var player=server.getPlayerList().getPlayers().getFirst();social.teams().archive(teamId.get(),"test cleanup");if(!previous.isEmpty())social.teams().admin(SocialAction.TEAM_ASSIGN,player.getUUID().toString(),previous,"test cleanup");});
        context.waitFor(client->ClientState.get().snapshot().self.teamId.equals(previous),100);
        context.setScreen(()->null);
    }
    private VipClientTests() { }
}
