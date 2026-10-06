package com.takumistudios.socialmod.client.screen;

import net.minecraft.client.gui.screens.Screen;

/** Dedicated staff screen; server permissions remain authoritative for every action. */
public final class TeamManagementScreen extends TeamScreen {
    public TeamManagementScreen(Screen parent) { super(parent, true); }
}
