package com.takumistudios.socialmod.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** Capa mínima para las diferencias de la API de cliente entre versiones de Minecraft. */
public final class ClientCompat {
    private ClientCompat() {
    }

    public static @Nullable Screen currentScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=26.2 {
        return minecraft.gui.screen();
        //?} else {
        /*return minecraft.screen;
        *///?}
    }

    public static void setScreen(@Nullable Screen screen) {
        if (screen != null) screen = com.takumistudios.socialmod.client.screen.SocialComponents.owner(screen);
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=26.2 {
        minecraft.gui.setScreen(screen);
        //?} else {
        /*minecraft.setScreen(screen);
        *///?}
    }

    /** F1: el HUD está oculto. */
    public static boolean hudHidden() {
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=26.2 {
        return minecraft.gui.hud.isHidden();
        //?} else {
        /*return minecraft.options.hideGui;
        *///?}
    }
}
