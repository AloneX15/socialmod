package com.takumistudios.socialmod.server.data;

import com.takumistudios.socialmod.common.model.Role;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Grupo persistente (clan) o party temporal (PLAN 5.2). Las parties viven solo en memoria y desaparecen
 * cuando se quedan sin miembros conectados.
 */
public final class Group {
    public String id;
    public String name = "";
    /** Etiqueta corta (2–5 caracteres) que se muestra junto al nombre. */
    public String tag = "";
    public int color = 0x55FF55;
    /** Icono de la lista de {@code assets/socialmod/textures/gui/icons/}. */
    public String icon = "shield";
    public String description = "";
    public String motd = "";
    public String pinned = "";
    public long created;
    public boolean party;
    /** Managed TEAM identity and its linked chat share a persistent id. */
    public boolean team;
    public boolean archived;
    public Set<UUID> archiveReaders = new LinkedHashSet<>();
    public Map<UUID, Role> archiveRoles = new LinkedHashMap<>();
    public Map<UUID, Role> members = new LinkedHashMap<>();
    /** Nombres de los miembros (para mostrarlos sin estar conectados). */
    public Map<UUID, String> memberNames = new LinkedHashMap<>();
    public List<Channel> channels = new ArrayList<>();
    public List<GroupEvent> events = new ArrayList<>();
    public Set<UUID> invited = new LinkedHashSet<>();
    /** Sincronizar con la party de Open Parties and Claims del líder ({@code /g claims link}). */
    public boolean claimsLink;
    /** Miembros que SocialMod añadió a la party de claims (solo esos se quitan al salir del grupo). */
    public Set<UUID> claimsSynced = new LinkedHashSet<>();

    public static final class Channel {
        public String name;
        /** Rol mínimo para leer y escribir. */
        public Role minRole = Role.RECRUIT;

        public Channel() {
        }

        public Channel(String name, Role minRole) {
            this.name = name;
            this.minRole = minRole;
        }
    }

    public static final class GroupEvent {
        public String id;
        public String title = "";
        public long startsAt;
        public UUID createdBy;
        public boolean warned;
        public boolean started;

        public GroupEvent() {
        }

        public GroupEvent(String id, String title, long startsAt, UUID createdBy) {
            this.id = id;
            this.title = title;
            this.startsAt = startsAt;
            this.createdBy = createdBy;
        }
    }

    public Role roleOf(UUID player) {
        return members.get(player);
    }

    public boolean isMember(UUID player) {
        return members.containsKey(player);
    }

    public UUID leader() {
        for (Map.Entry<UUID, Role> entry : members.entrySet()) {
            if (entry.getValue() == Role.LEADER) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Channel channel(String name) {
        for (Channel channel : channels) {
            if (channel.name.equalsIgnoreCase(name)) {
                return channel;
            }
        }
        return null;
    }

    public String defaultChannel() {
        return channels.isEmpty() ? "general" : channels.getFirst().name;
    }

    public Group normalize() {
        if (archiveRoles == null) archiveRoles = new LinkedHashMap<>();
        if (archiveReaders == null) archiveReaders = new LinkedHashSet<>();
        if (name == null) name = "";
        if (tag == null) tag = "";
        if (icon == null) icon = "shield";
        if (description == null) description = "";
        if (motd == null) motd = "";
        if (pinned == null) pinned = "";
        if (members == null) members = new LinkedHashMap<>();
        members.values().removeIf(java.util.Objects::isNull);
        if (memberNames == null) memberNames = new LinkedHashMap<>();
        if (channels == null) channels = new ArrayList<>();
        channels.removeIf(c -> c == null || c.name == null);
        channels.forEach(c -> {
            if (c.minRole == null) c.minRole = Role.RECRUIT;
        });
        if (channels.isEmpty()) channels.add(new Channel("general", Role.RECRUIT));
        if (events == null) events = new ArrayList<>();
        events.removeIf(e -> e == null || e.id == null);
        if (invited == null) invited = new LinkedHashSet<>();
        if (claimsSynced == null) claimsSynced = new LinkedHashSet<>();
        return this;
    }
}
