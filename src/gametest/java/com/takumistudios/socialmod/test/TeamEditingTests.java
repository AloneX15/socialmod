package com.takumistudios.socialmod.test;

import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.screen.TeamManagementScreen;
import com.takumistudios.socialmod.client.screen.TagStyleScreen;
import com.takumistudios.socialmod.common.net.SocialAction;
import com.takumistudios.socialmod.server.SocialServer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Reproduces editing a local TEAM without first clicking its catalog row. */
final class TeamEditingTests {
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        String previous=context.computeOnClient(client->ClientState.get().snapshot().self.teamId);
        var fixture=new java.util.concurrent.atomic.AtomicReference<String>();
        world.getServer().runOnServer(server->{
            var social=SocialServer.get();var player=server.getPlayerList().getPlayers().getFirst();
            if(!social.teams().create(player,"EditingFixture","shield;#3366FF"))throw new AssertionError("Editing TEAM create failed");
            var team=social.teams().find("EditingFixture");
            fixture.set(team.id);
            social.teams().admin(SocialAction.TEAM_ASSIGN,player.getUUID().toString(),team.id,"test");
        });
        context.waitFor(client->ClientState.get().snapshot().teams.stream().anyMatch(t->t.id.equals(ClientState.get().snapshot().self.teamId)&&t.name.equals("EditingFixture")),100);
        context.setScreen(()->new TeamManagementScreen(null));context.waitTicks(4);
        context.runOnClient(client->{
            var screen=(TeamManagementScreen)ClientCompat.currentScreen();
            if(!screen.selectedTeam().equals(ClientState.get().snapshot().self.teamId))throw new AssertionError("Management lost local TEAM target: selected="+screen.selectedTeam()+" self="+ClientState.get().snapshot().self.teamId);
            if(net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).stream().noneMatch(w->com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identifier(w).equals("team_"+fixture.get())))throw new AssertionError("Selected TEAM hidden on another catalog page");
            var name=(net.minecraft.client.gui.components.EditBox)net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();
            if(!name.getValue().equals("EditingFixture"))throw new AssertionError("Management did not load selected TEAM name");
            name.setValue("RenamedFixture");
        });
        context.takeScreenshot("team_editing_01_local_target");
        context.clickScreenButton("socialmod.team.rename");context.clickScreenButton("gui.yes");
        context.waitFor(client->ClientState.get().snapshot().teams.stream().anyMatch(t->t.id.equals(fixture.get())&&t.name.equals("RenamedFixture")),100);
        context.clickScreenButton("socialmod.group_settings.style");
        context.runOnClient(client->{
            if(!(ClientCompat.currentScreen() instanceof TagStyleScreen))throw new AssertionError("TEAM style editor not opened");
            var hex=(net.minecraft.client.gui.components.EditBox)net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(ClientCompat.currentScreen()).stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();
            if(!hex.getValue().equals("#3366FF"))throw new AssertionError("Management lost selected TEAM color");
            hex.setValue("#AA55FF");
        });context.clickScreenButton("socialmod.group_settings.save");
        context.waitFor(client->ClientState.get().snapshot().teams.stream().anyMatch(t->t.id.equals(fixture.get())&&t.color==0xAA55FF),100);
        context.takeScreenshot("team_editing_02_saved_name_and_color");
        world.getServer().runOnServer(server->{
            var social=SocialServer.get();var player=server.getPlayerList().getPlayers().getFirst();
            social.teams().archive(fixture.get(),"test cleanup");
            if(!previous.isEmpty())social.teams().admin(SocialAction.TEAM_ASSIGN,player.getUUID().toString(),previous,"test cleanup");
        });context.waitFor(client->ClientState.get().snapshot().self.teamId.equals(previous),100);context.setScreen(()->null);
    }
    private TeamEditingTests() { }
}
