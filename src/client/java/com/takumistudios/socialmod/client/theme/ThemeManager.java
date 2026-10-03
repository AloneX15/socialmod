package com.takumistudios.socialmod.client.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.client.ClientConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.Reader;
import java.util.Optional;

/**
 * Carga el tema {@code socialmod:themes/default.json} de los resource packs (F3+T lo recarga). Los errores se
 * registran con el archivo y se usa el tema por defecto, sin crashear.
 */
public final class ThemeManager implements ResourceManagerReloadListener {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SocialMod.MOD_ID, "themes");
    private static final Identifier FILE = Identifier.fromNamespaceAndPath(SocialMod.MOD_ID, "themes/default.json");
    private static volatile Theme current = Theme.DEFAULT;

    /** Tema activo (con el modo de alto contraste aplicado si está activado). */
    public static Theme get() {
        Theme theme = current;
        if (ClientConfig.get().accessibility.highContrast) {
            // Alto contraste: colores sólidos y sin texturas, que pueden restar legibilidad
            return new Theme(theme.layout(), theme.columns(), Theme.Colors.HIGH_CONTRAST, theme.toast(), Theme.Textures.NONE);
        }
        return theme;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Optional<Resource> resource = manager.getResource(FILE);
        if (resource.isEmpty()) {
            current = Theme.DEFAULT;
            return;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            current = Theme.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> SocialMod.LOGGER.warn("[SocialMod] Tema {} ({}) inválido: {}", FILE,
                            resource.get().sourcePackId(), error))
                    .orElse(Theme.DEFAULT);
        } catch (Exception e) {
            SocialMod.LOGGER.warn("[SocialMod] No se pudo leer el tema {}: {}", FILE, e.getMessage());
            current = Theme.DEFAULT;
        }
    }
}
