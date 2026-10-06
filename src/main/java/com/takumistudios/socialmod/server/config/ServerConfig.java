package com.takumistudios.socialmod.server.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.common.model.GroupPermission;
import com.takumistudios.socialmod.common.model.Role;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reglas del servidor en {@code config/socialmod/server.json} (PLAN 15). Se recarga con {@code /socialmod reload}.
 * Si el archivo está corrupto se usan los valores por defecto y se avisa (nunca crashea).
 */
public final class ServerConfig {
    public static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile ServerConfig instance = new ServerConfig().sanitize();

    public int configVersion = CURRENT_VERSION;

    /** Módulos que se pueden desactivar por separado (PLAN 2.4). */
    public Modules modules = new Modules();
    public Storage storage = new Storage();
    public Chat chat = new Chat();
    public Limits limits = new Limits();
    public Presence presence = new Presence();
    public AntiSpam antiSpam = new AntiSpam();
    public Filter filter = new Filter();
    public Moderation moderation = new Moderation();
    public Formats formats = new Formats();
    /** Permisos de cada rol de grupo: {@code "officer": ["invite", "kick", ...]}. */
    public Map<String, List<String>> roles = defaultRoles();
    public List<String> defaultChannels = new ArrayList<>(List.of("general"));
    public Nametags nametags = new Nametags();
    public int maxTeams = 8;
    public Integrations integrations = new Integrations();

    public static final class Modules {
        public boolean privateMessages = true;
        public boolean groups = true;
        public boolean parties = true;
        public boolean friends = true;
        public boolean presence = true;
        public boolean mailbox = true;
        public boolean sharing = true;
        public boolean mentions = true;
        public boolean placeholders = true;
        /** Vida de los compañeros de party en el HUD de los clientes con el mod. */
        public boolean partyHud = true;
        /** Ping en el mundo para la party (tecla G por defecto en el cliente). */
        public boolean ping = true;
        /** Grupos de voz por grupo/party con Simple Voice Chat (si está instalado en el servidor). */
        public boolean voice = true;
    }

    public static final class Storage {
        /**
         * {@code file} (por defecto), {@code h2}, {@code mysql} o {@code mariadb}. Los de base de datos necesitan el driver
         * JDBC en {@code config/socialmod/drivers/}; si falta o no conecta, se avisa y se usa {@code file}.
         */
        public String backend = "file";
        /** URL JDBC. Vacía con {@code h2}: archivo {@code <mundo>/socialmod/socialmod-h2}. Ej.: {@code jdbc:mysql://localhost:3306/minecraft}. */
        public String jdbcUrl = "";
        public String user = "";
        public String password = "";
        public String tablePrefix = "socialmod_";
        public int retentionDays = 30;
        public int maxMessagesPerConversation = 500;
        /** Segundos entre escrituras agrupadas a disco. */
        public int flushIntervalSeconds = 5;
        /** Conversaciones que se mantienen en memoria. */
        public int cachedConversations = 256;
    }

    public static final class Chat {
        public int maxMessageLength = 256;
        public int editWindowSeconds = 120;
        public boolean allowLinks = true;
        public boolean typingIndicator = true;
        public boolean readReceipts = true;
        public int historyPageSize = 50;
        public int maxStatusLength = 48;
        public int maxNoteLength = 128;
    }

    public static final class Limits {
        public int maxGroupsPerPlayer = 3;
        public int maxMembersPerGroup = 50;
        public int maxChannelsPerGroup = 8;
        public int maxFriends = 200;
        public int maxPartySize = 8;
        public int maxPendingRequests = 50;
        public int maxEventsPerGroup = 10;
        public int packetsPerSecond = 20;
        public int packetBurst = 40;
    }

    public static final class Presence {
        public int afkMinutes = 5;
        /** Cada cuántos ticks se envían los cambios de presencia agrupados (5 ticks = 250 ms). */
        public int batchTicks = 5;
    }

    public static final class AntiSpam {
        public boolean enabled = true;
        public int maxMessages = 5;
        public int windowSeconds = 4;
        public int maxRepeats = 3;
        public int repeatWindowSeconds = 30;
        public int strikesToMute = 3;
        public int autoMuteSeconds = 60;
    }

    public static final class Filter {
        public boolean enabled = false;
        /** {@code censor} o {@code block}. */
        public String mode = "censor";
        public List<String> words = new ArrayList<>();
        public List<String> regex = new ArrayList<>();
    }

    public static final class Moderation {
        /** Si es true, el staff con permiso puede leer los privados en directo. Se avisa en la UI de todos. */
        public boolean spyEnabled = false;
        public boolean reportsEnabled = true;
        /** Mensajes de contexto que se adjuntan a un reporte (antes y después). */
        public int reportContext = 5;
        public boolean allowDataExport = true;
        public boolean allowDataDelete = true;
        public boolean auditLog = true;
    }

    /**
     * Formatos para jugadores sin el mod (PLAN 10). Variables: {sender}, {receiver}, {group}, {tag}, {channel}, {message}.
     * Los colores usan nombres de {@code ChatFormatting} (gold, gray, aqua...).
     */
    public static final class Formats {
        public String directIn = "[{sender} → me] {message}";
        public String directOut = "[me → {receiver}] {message}";
        public String group = "[{tag}#{channel}] {sender}: {message}";
        public String party = "[Party] {sender}: {message}";
        public String directColor = "light_purple";
        public String groupColor = "aqua";
        public String partyColor = "blue";
        /** {@code actionbar} o {@code chat}: dónde reciben las notificaciones los jugadores sin el mod. */
        public String vanillaNotifications = "actionbar";
    }

    /** Integraciones con otros mods (solo actúan si el mod está instalado). */
    public static final class Integrations {
        /**
         * Open Parties and Claims: {@code off} (por defecto), {@code to_claims} (el grupo manda: sus miembros entran
         * en la party del líder) o {@code both} (también quien entra o sale de la party entra o sale del grupo).
         * Solo afecta a los grupos que el líder enlaza con {@code /g claims link}.
         */
        public String claimsSync = "off";
    }

    public static final class Nametags {
        /** Fallback con teams del scoreboard para clientes sin el mod. Desactivado: choca con TAB y similares (PLAN 11). */
        public boolean scoreboardFallback = false;
    }

    public static ServerConfig get() {
        return instance;
    }

    public static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve(SocialMod.MOD_ID);
    }

    public static Path path() {
        return directory().resolve("server.json");
    }

    /** Carga (o crea) el archivo. Devuelve un mensaje de error o {@code null}. */
    public static String load() {
        Path path = path();
        ServerConfig loaded = new ServerConfig();
        String error = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                ServerConfig parsed = GSON.fromJson(reader, ServerConfig.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (IOException | JsonParseException e) {
                error = "No se pudo leer " + path + ": " + e.getMessage() + ". Se usan los valores por defecto.";
                SocialMod.LOGGER.warn(error);
            }
        }
        loaded.sanitize();
        instance = loaded;
        if (error == null) {
            save(loaded, path);
        }
        return error;
    }

    /** Solo para pruebas. */
    public static void set(ServerConfig config) {
        instance = config.sanitize();
    }

    public static void persist(java.util.concurrent.Executor executor) {
        String json = GSON.toJson(instance);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            Path path = FabricLoader.getInstance().getConfigDir().resolve("socialmod/server.json");
            try {
                Files.createDirectories(path.getParent());
                Path tmp = Files.createTempFile(path.getParent(), "server-", ".tmp");
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) { SocialMod.LOGGER.error("No se pudo guardar la configuracion del servidor", e); }
        }, executor);
    }

    private static void save(ServerConfig config, Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            SocialMod.LOGGER.warn("No se pudo guardar {}: {}", path, e.getMessage());
        }
    }

    public ServerConfig sanitize() {
        if (modules == null) modules = new Modules();
        if (storage == null) storage = new Storage();
        if (chat == null) chat = new Chat();
        if (limits == null) limits = new Limits();
        if (presence == null) presence = new Presence();
        if (antiSpam == null) antiSpam = new AntiSpam();
        if (filter == null) filter = new Filter();
        if (moderation == null) moderation = new Moderation();
        if (formats == null) formats = new Formats();
        maxTeams = Math.max(1, Math.min(1000, maxTeams));
        if (nametags == null) nametags = new Nametags();
        if (integrations == null) integrations = new Integrations();
        if (integrations.claimsSync == null || !List.of("off", "to_claims", "both").contains(integrations.claimsSync)) integrations.claimsSync = "off";
        if (roles == null) roles = defaultRoles();
        if (defaultChannels == null || defaultChannels.isEmpty()) defaultChannels = new ArrayList<>(List.of("general"));
        if (filter.words == null) filter.words = new ArrayList<>();
        if (filter.regex == null) filter.regex = new ArrayList<>();
        if (storage.backend == null) storage.backend = "file";
        storage.backend = storage.backend.toLowerCase(Locale.ROOT);
        storage.retentionDays = clamp(storage.retentionDays, 0, 3650);
        storage.maxMessagesPerConversation = clamp(storage.maxMessagesPerConversation, 10, 100_000);
        storage.flushIntervalSeconds = clamp(storage.flushIntervalSeconds, 1, 300);
        storage.cachedConversations = clamp(storage.cachedConversations, 16, 100_000);
        chat.maxMessageLength = clamp(chat.maxMessageLength, 16, 1024);
        chat.editWindowSeconds = clamp(chat.editWindowSeconds, 0, 3600);
        chat.historyPageSize = clamp(chat.historyPageSize, 10, 100);
        chat.maxStatusLength = clamp(chat.maxStatusLength, 0, 64);
        chat.maxNoteLength = clamp(chat.maxNoteLength, 0, 256);
        limits.maxGroupsPerPlayer = clamp(limits.maxGroupsPerPlayer, 0, 100);
        limits.maxMembersPerGroup = clamp(limits.maxMembersPerGroup, 2, 10_000);
        limits.maxChannelsPerGroup = clamp(limits.maxChannelsPerGroup, 1, 64);
        limits.maxFriends = clamp(limits.maxFriends, 0, 10_000);
        limits.maxPartySize = clamp(limits.maxPartySize, 2, 100);
        limits.maxPendingRequests = clamp(limits.maxPendingRequests, 1, 1000);
        limits.maxEventsPerGroup = clamp(limits.maxEventsPerGroup, 0, 100);
        limits.packetsPerSecond = clamp(limits.packetsPerSecond, 1, 1000);
        limits.packetBurst = clamp(limits.packetBurst, limits.packetsPerSecond, 2000);
        presence.afkMinutes = clamp(presence.afkMinutes, 0, 1440);
        presence.batchTicks = clamp(presence.batchTicks, 1, 100);
        antiSpam.maxMessages = clamp(antiSpam.maxMessages, 0, 1000);
        antiSpam.windowSeconds = clamp(antiSpam.windowSeconds, 1, 600);
        antiSpam.maxRepeats = clamp(antiSpam.maxRepeats, 0, 100);
        antiSpam.repeatWindowSeconds = clamp(antiSpam.repeatWindowSeconds, 1, 3600);
        antiSpam.strikesToMute = clamp(antiSpam.strikesToMute, 0, 100);
        antiSpam.autoMuteSeconds = clamp(antiSpam.autoMuteSeconds, 1, 86_400);
        moderation.reportContext = clamp(moderation.reportContext, 0, 50);
        defaultChannels.replaceAll(c -> c == null ? "general" : c.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", ""));
        defaultChannels.removeIf(String::isEmpty);
        if (defaultChannels.isEmpty()) defaultChannels.add("general");
        return this;
    }

    /** Permisos efectivos de cada rol. El líder siempre los tiene todos. */
    public Map<Role, java.util.Set<GroupPermission>> rolePermissions() {
        Map<Role, java.util.Set<GroupPermission>> result = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            java.util.Set<GroupPermission> set = java.util.EnumSet.noneOf(GroupPermission.class);
            if (role == Role.LEADER) {
                set.addAll(java.util.EnumSet.allOf(GroupPermission.class));
            } else {
                List<String> ids = roles.get(role.id());
                if (ids != null) {
                    for (String id : ids) {
                        GroupPermission permission = GroupPermission.byId(id);
                        if (permission != null) {
                            set.add(permission);
                        }
                    }
                }
            }
            result.put(role, set);
        }
        return result;
    }

    private static Map<String, List<String>> defaultRoles() {
        Map<String, List<String>> roles = new LinkedHashMap<>();
        roles.put("officer", new ArrayList<>(List.of("invite", "kick", "manage_channels", "pin", "edit_info", "manage_events")));
        roles.put("member", new ArrayList<>(List.of("invite")));
        roles.put("recruit", new ArrayList<>());
        return roles;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
