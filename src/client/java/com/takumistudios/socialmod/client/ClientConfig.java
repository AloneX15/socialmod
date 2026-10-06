package com.takumistudios.socialmod.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.takumistudios.socialmod.SocialMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Preferencias del jugador en {@code config/socialmod/client.json} (PLAN 15). Los modpacks pueden incluir
 * {@code client-defaults.json}: se aplica como valores por defecto sin pisar lo que el jugador haya cambiado.
 */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static ClientConfig instance = new ClientConfig();

    public enum Corner {
        TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT;

        public boolean right() {
            return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }

        public boolean bottom() {
            return this == BOTTOM_RIGHT || this == BOTTOM_LEFT;
        }

        public Corner next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public Toasts toasts = new Toasts();
    public Sounds sounds = new Sounds();
    public Hud hud = new Hud();
    public Nametags nametags = new Nametags();
    public Accessibility accessibility = new Accessibility();
    public Panel panel = new Panel();
    public Ping ping = new Ping();
    public Maps maps = new Maps();
    public Cache cache = new Cache();

    public static final class Ping {
        public boolean enabled = true;
        public boolean sound = true;
    }

    /** Integración con mapas (Xaero's World Map / Minimap, JourneyMap). */
    public static final class Maps {
        /** Crea un waypoint al hacer doble clic en unas coordenadas compartidas (si hay un mapa compatible). */
        public boolean waypoints = true;
        /** Primera vez con un minimapa instalado: mueve toasts y HUD para no taparlo. */
        public boolean autoMargins = true;
        public boolean autoMarginsApplied = false;
    }

    /** Caché local por servidor para abrir el panel al instante (PLAN 4.2). */
    public static final class Cache {
        public boolean enabled = true;
    }

    public static final class Toasts {
        public boolean enabled = true;
        public Corner position = Corner.TOP_RIGHT;
        /** Margen extra desde el borde (para no tapar minimapas). */
        public int marginX = 4;
        public int marginY = 4;
        public int durationSeconds = 6;
        public boolean animations = true;
        public int maxVisible = 3;
        public boolean groupBySender = true;
        public boolean showGroupMessages = true;
        /** Modo no molestar inteligente: oculta en combate y con una pantalla abierta, y resume al terminar. */
        public boolean smartDnd = true;
    }

    public static final class Sounds {
        public boolean enabled = true;
        /** Volumen 0–100. */
        public int volume = 70;
        public boolean privateMessages = true;
        public boolean mentions = true;
        public boolean invites = true;
        public boolean events = true;
        public boolean groupMessages = false;
    }

    public static final class Hud {
        public boolean enabled = true;
        public Corner position = Corner.BOTTOM_RIGHT;
        public int offsetX = 4;
        public int offsetY = 4;
        /** Escala en porcentaje (50–200). */
        public int scale = 100;
        /** Vida de los miembros de la party conectados (PLAN 5.2). */
        public boolean partyHealth = true;
    }

    public static final class Nametags {
        public boolean showGroupTags = true;
        /** Etiqueta en una línea debajo del nombre (como el marcador "belowName" vanilla); si no, delante. */
        public boolean belowName = true;
        public boolean showIcon = true;
        public boolean showRole = true;
    }

    public static final class Accessibility {
        /** Lee los toasts con el Narrador de Minecraft si está activo. */
        public boolean narrateToasts = true;
        public boolean highContrast = false;
        public boolean reducedMotion = false;
    }

    public static final class Panel {
        /** Ancho reservado a la derecha (minimapas u otros HUD). */
        public int reservedRight = 0;
        public int reservedTop = 0;
        /** Escala propia del texto del panel en porcentaje (75–150), aparte de la GUI Scale. */
        public int textScale = 100;
        /** Conversaciones de canal silenciadas en este cliente (sin toasts, sonidos ni contador en el HUD). */
        public java.util.List<String> mutedChannels = new java.util.ArrayList<>();
    }

    public static ClientConfig get() {
        return instance;
    }

    private static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve(SocialMod.MOD_ID);
    }

    public static void load() {
        Path path = directory().resolve("client.json");
        Path defaults = directory().resolve("client-defaults.json");
        JsonObject merged = GSON.toJsonTree(new ClientConfig()).getAsJsonObject();
        boolean ok = true;
        try {
            if (Files.exists(defaults)) {
                merge(merged, read(defaults));
            }
            if (Files.exists(path)) {
                merge(merged, read(path));
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            ok = false;
            SocialMod.LOGGER.warn("[SocialMod] Config de cliente inválida ({}); se usan los valores por defecto", e.getMessage());
        }
        ClientConfig loaded;
        try {
            loaded = GSON.fromJson(merged, ClientConfig.class);
        } catch (JsonParseException e) {
            loaded = new ClientConfig();
            ok = false;
        }
        instance = loaded == null ? new ClientConfig() : loaded.sanitize();
        if (ok) {
            save();
        }
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /** Copia recursivamente {@code source} sobre {@code target}. */
    private static void merge(JsonObject target, JsonObject source) {
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            JsonElement existing = target.get(entry.getKey());
            if (existing instanceof JsonObject targetObject && entry.getValue() instanceof JsonObject sourceObject) {
                merge(targetObject, sourceObject);
            } else {
                target.add(entry.getKey(), entry.getValue());
            }
        }
    }

    public static void save() {
        Path path = directory().resolve("client.json");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            SocialMod.LOGGER.warn("[SocialMod] No se pudo guardar {}: {}", path, e.getMessage());
        }
    }

    private ClientConfig sanitize() {
        if (toasts == null) toasts = new Toasts();
        if (sounds == null) sounds = new Sounds();
        if (hud == null) hud = new Hud();
        if (nametags == null) nametags = new Nametags();
        if (accessibility == null) accessibility = new Accessibility();
        if (panel == null) panel = new Panel();
        if (ping == null) ping = new Ping();
        if (maps == null) maps = new Maps();
        if (cache == null) cache = new Cache();
        if (panel.mutedChannels == null) panel.mutedChannels = new java.util.ArrayList<>();
        panel.textScale = clamp(panel.textScale, 75, 150);
        if (toasts.position == null) toasts.position = Corner.TOP_RIGHT;
        if (hud.position == null) hud.position = Corner.BOTTOM_RIGHT;
        toasts.durationSeconds = clamp(toasts.durationSeconds, 3, 15);
        toasts.maxVisible = clamp(toasts.maxVisible, 1, 8);
        toasts.marginX = clamp(toasts.marginX, 0, 400);
        toasts.marginY = clamp(toasts.marginY, 0, 400);
        sounds.volume = clamp(sounds.volume, 0, 100);
        hud.scale = clamp(hud.scale, 50, 200);
        hud.offsetX = clamp(hud.offsetX, 0, 400);
        hud.offsetY = clamp(hud.offsetY, 0, 400);
        panel.reservedRight = clamp(panel.reservedRight, 0, 300);
        panel.reservedTop = clamp(panel.reservedTop, 0, 200);
        return this;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static String cornerKey(Corner corner) {
        return "socialmod.corner." + corner.name().toLowerCase(Locale.ROOT);
    }
}
