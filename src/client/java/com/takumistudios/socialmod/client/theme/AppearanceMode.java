package com.takumistudios.socialmod.client.theme;

import com.takumistudios.socialmod.client.ClientConfig;
import com.takumistudios.socialmod.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** A server may enforce the base; personal preference remains intact across global switches. */
public final class AppearanceMode {
    private static String server = "";
    private static boolean previous;
    public static void join(Minecraft client) {
        var data = client.getCurrentServer();
        server = data != null ? "mp:" + data.ip.toLowerCase(java.util.Locale.ROOT)
            : client.getSingleplayerServer() != null ? "sp:" + client.getSingleplayerServer().getWorldData().getLevelName() : "";
        previous = original();
    }
    public static boolean personal() { return !server.isEmpty() && ClientConfig.get().originalServers.contains(server); }
    public static boolean original() { return ClientState.get().connected() && ClientState.get().snapshot().originalInterface || personal(); }
    public static void setPersonal(boolean value) {
        if (server.isEmpty()) return;
        if (value) ClientConfig.get().originalServers.add(server); else ClientConfig.get().originalServers.remove(server);
        com.takumistudios.socialmod.client.ClientNet.action(com.takumistudios.socialmod.common.net.SocialAction.VISUAL_PERSONAL_ORIGINAL, String.valueOf(value));
        ClientConfig.save(); refresh();
    }
    public static Component personalLabel() { return Component.translatable(personal() ? "socialmod.appearance.personal_styles" : "socialmod.appearance.personal_original"); }
    public static Component globalLabel() { return Component.translatable(ClientState.get().snapshot().originalInterface ? "socialmod.appearance.global_styles" : "socialmod.appearance.global_original"); }
    public static void refresh() {
        boolean now = original();
        com.takumistudios.socialmod.client.compat.fancy.FancyBridge.appearanceChanged();
        if (now != previous) {
            previous = now; RowTemplates.appearanceChanged();
            var screen = com.takumistudios.socialmod.client.compat.ClientCompat.currentScreen();
            if (screen instanceof com.takumistudios.socialmod.client.screen.SocialScreen || screen instanceof com.takumistudios.socialmod.client.screen.SocialChildScreen) screen.init(screen.width, screen.height);
        }
    }
    public static void disconnect() { server = ""; previous = false; com.takumistudios.socialmod.client.compat.fancy.FancyBridge.appearanceChanged(); }
    private AppearanceMode() { }
}
