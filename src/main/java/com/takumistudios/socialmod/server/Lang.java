package com.takumistudios.socialmod.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.takumistudios.socialmod.SocialMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Traducciones en el servidor. Los jugadores sin el mod no tienen nuestros archivos de idioma, así que cada
 * texto se envía como {@code translatableWithFallback}: con el mod se traduce en el cliente; sin él se ve el
 * texto de respaldo, traducido aquí al idioma que el cliente anunció (o al inglés).
 */
public final class Lang {
    private static final Map<String, Map<String, String>> LANGUAGES = new HashMap<>();
    private static final String DEFAULT = "en_us";

    private Lang() {
    }

    public static void load() {
        LANGUAGES.clear();
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(SocialMod.MOD_ID);
        if (mod.isEmpty()) {
            return;
        }
        for (String code : new String[]{"en_us", "es_es", "es_mx", "es_ar", "pt_br", "fr_fr", "de_de"}) {
            mod.get().findPath("assets/socialmod/lang/" + code + ".json").ifPresent(path -> LANGUAGES.put(code, read(path)));
        }
    }

    private static Map<String, String> read(Path path) {
        Map<String, String> map = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                map.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (IOException | RuntimeException e) {
            SocialMod.LOGGER.warn("[SocialMod] No se pudo leer {}: {}", path, e.getMessage());
        }
        return map;
    }

    public static String raw(@Nullable String language, String key) {
        if (language != null) {
            String lang = language.toLowerCase(Locale.ROOT);
            Map<String, String> map = LANGUAGES.get(lang);
            if (map == null && lang.startsWith("es_")) {
                map = LANGUAGES.get("es_es");
            }
            if (map != null && map.containsKey(key)) {
                return map.get(key);
            }
        }
        Map<String, String> fallback = LANGUAGES.get(DEFAULT);
        return fallback != null ? fallback.getOrDefault(key, key) : key;
    }

    /** Texto ya traducido y con los argumentos aplicados (para payloads de texto plano). */
    public static String format(@Nullable String language, String key, Object... args) {
        String pattern = raw(language, key);
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (java.util.IllegalFormatException e) {
            return pattern;
        }
    }

    /** Texto traducible con respaldo en el idioma del jugador. */
    public static MutableComponent tr(@Nullable ServerPlayer player, String key, Object... args) {
        String language = player == null ? null : player.clientInformation().language();
        return Component.translatableWithFallback(key, raw(language, key), args);
    }

    /** Para la consola y los registros. */
    public static MutableComponent tr(String key, Object... args) {
        return Component.translatableWithFallback(key, raw(null, key), args);
    }
}
