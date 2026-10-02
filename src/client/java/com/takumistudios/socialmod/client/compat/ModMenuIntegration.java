package com.takumistudios.socialmod.client.compat;

import com.takumistudios.socialmod.client.screen.SettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Pantalla de configuración en ModMenu (PLAN 12). Solo se carga si ModMenu está instalado. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
