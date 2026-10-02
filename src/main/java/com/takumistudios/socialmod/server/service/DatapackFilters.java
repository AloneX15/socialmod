package com.takumistudios.socialmod.server.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.takumistudios.socialmod.SocialMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Listas de palabras del filtro aportadas por datapacks (PLAN 15: el servidor decide con config y datapack):
 * {@code data/<namespace>/socialmod/filters/*.json} con {@code {"words": ["..."]}}. Se recargan con {@code /reload}.
 */
public final class DatapackFilters implements ResourceManagerReloadListener {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SocialMod.MOD_ID, "filters");
    private static volatile List<String> words = List.of();

    public static List<String> words() {
        return words;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        List<String> loaded = new ArrayList<>();
        Map<Identifier, Resource> resources = manager.listResources("socialmod/filters", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                if (json instanceof JsonObject object && object.get("words") instanceof JsonArray array) {
                    array.forEach(word -> {
                        if (word.isJsonPrimitive()) {
                            loaded.add(word.getAsString());
                        }
                    });
                }
            } catch (Exception e) {
                SocialMod.LOGGER.warn("[SocialMod] Lista de filtro inválida {}: {}", entry.getKey(), e.getMessage());
            }
        }
        words = List.copyOf(loaded);
        com.takumistudios.socialmod.server.SocialServer social = com.takumistudios.socialmod.server.SocialServer.get();
        if (social != null) {
            social.server().execute(() -> social.chat().reconfigure());
        }
    }
}
