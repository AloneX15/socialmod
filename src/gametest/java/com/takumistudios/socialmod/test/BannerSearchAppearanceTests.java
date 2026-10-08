package com.takumistudios.socialmod.test;

import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.compat.fancy.FancyBridge;
import com.takumistudios.socialmod.client.screen.*;
import com.takumistudios.socialmod.client.theme.AppearanceMode;
import com.takumistudios.socialmod.client.theme.VisualManager;
import com.takumistudios.socialmod.common.model.TeamBanner;
import com.takumistudios.socialmod.common.model.VisualDesign;
import com.takumistudios.socialmod.common.net.SocialAction;
import com.takumistudios.socialmod.server.SocialServer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import java.util.UUID;

final class BannerSearchAppearanceTests {
    static void run(ClientGameTestContext context, TestSingleplayerContext world, String conversation) {
        UUID offline = UUID.randomUUID();
        var previous = context.computeOnClient(client -> ClientState.get().snapshot().visual.copy());
        world.getServer().runOnServer(server -> {
            SocialServer.get().storage().getOrCreate(offline, "ZSearchOffline", System.currentTimeMillis());
            SocialServer.get().snapshots().send(server.getPlayerList().getPlayers().getFirst());
        });
        context.waitFor(client -> ClientState.get().snapshot().visualAdmin, 100);
        var draft = new TeamBanner(); draft.base = 15;
        draft.layers.add(new TeamBanner.Layer("minecraft:stripe_center", 14));
        draft.layers.add(new TeamBanner.Layer("minecraft:border", 0));
        var saved = new java.util.concurrent.atomic.AtomicReference<TeamBanner>();
        context.setScreen(() -> new BannerEditorScreen(new TeamScreen(null), draft, saved::set)); context.waitTicks(5);
        context.takeScreenshot("banner_01_editor");
        context.clickScreenButton("socialmod.banner.add"); context.clickScreenButton("gui.cancel");
        if (saved.get() != null || draft.layers.size() != 2) throw new AssertionError("Cancelled banner draft escaped");
        context.setScreen(() -> new BannerEditorScreen(new TeamScreen(null), draft, saved::set));
        context.clickScreenButton("socialmod.banner.add"); context.clickScreenButton("socialmod.group_settings.save");
        if (saved.get() == null || saved.get().layers.size() != 3) throw new AssertionError("Banner Save failed");
        context.setScreen(() -> new TagStyleScreen(new TeamScreen(null), "TEAM", 0x55FF55, "shield", "", (rgb, icon) -> {}, draft, value -> {})); context.waitTicks(4);
        context.takeScreenshot("banner_02_tag_preview");
        context.setScreen(() -> new PlayerSearchScreen(new SocialScreen(conversation)));
        context.runOnClient(client -> ((net.minecraft.client.gui.components.EditBox)net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(ClientCompat.currentScreen()).stream().filter(w -> w instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow()).setValue("ZSearch"));
        context.waitFor(client -> net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(ClientCompat.currentScreen()).stream().anyMatch(w -> w.getMessage().getString().contains("ZSearchOffline")), 200);
        context.takeScreenshot("banner_03_player_search");
        context.runOnClient(client -> {
            var button = (net.minecraft.client.gui.components.Button)net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(ClientCompat.currentScreen()).stream().filter(w -> w.getMessage().getString().contains("ZSearchOffline")).findFirst().orElseThrow();
            button.onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0, new net.minecraft.client.input.MouseButtonInfo(0, 0)));
            if (!(ClientCompat.currentScreen() instanceof SocialScreen panel) || !panel.selectedConversation().contains(offline.toString())) throw new AssertionError("Search did not open direct chat");
        }); context.waitTicks(5); context.takeScreenshot("banner_04_direct_chat");
        var changed = previous.copy(); changed.buttonColor = 0xFF123456;
        context.runOnClient(client -> ClientNet.action(SocialAction.VISUAL_PUBLISH, "", VisualDesign.GSON.toJson(changed)));
        context.waitFor(client -> ClientState.get().snapshot().visual.buttonColor == changed.buttonColor, 100);
        context.runOnClient(client -> {
            if (VisualManager.get().buttonColor != changed.buttonColor) throw new AssertionError("Basic design hidden by local integration design");
            AppearanceMode.setPersonal(true);
            if (VisualManager.get().buttonColor != new VisualDesign().buttonColor || FancyBridge.available()) throw new AssertionError("Personal base did not disable styles");
        }); context.waitTicks(3); context.takeScreenshot("banner_05_personal_base");
        context.runOnClient(client -> { AppearanceMode.setPersonal(false); ClientNet.action(SocialAction.VISUAL_ORIGINAL, "true"); });
        context.waitFor(client -> ClientState.get().snapshot().originalInterface, 100);
        context.runOnClient(client -> {
            if (!AppearanceMode.original() || FancyBridge.available() || VisualManager.get().buttonColor == changed.buttonColor) throw new AssertionError("Global base not enforced");
            if (ClientState.get().snapshot().visual.buttonColor != changed.buttonColor) throw new AssertionError("Global base destroyed design");
        }); context.takeScreenshot("banner_06_global_base");
        context.runOnClient(client -> ClientNet.action(SocialAction.VISUAL_ORIGINAL, "false"));
        context.waitFor(client -> !ClientState.get().snapshot().originalInterface, 100);
        context.runOnClient(client -> {
            if (VisualManager.get().buttonColor != changed.buttonColor) throw new AssertionError("Styles not restored");
            ClientNet.action(SocialAction.VISUAL_PUBLISH, "", VisualDesign.GSON.toJson(previous));
        });
        context.waitFor(client -> ClientState.get().snapshot().visual.buttonColor == previous.buttonColor, 100);
        world.getServer().runOnServer(server -> SocialServer.get().storage().removePlayer(offline));
        context.setScreen(() -> null);
    }
    private BannerSearchAppearanceTests() { }
}
