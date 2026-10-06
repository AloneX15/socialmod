package com.takumistudios.socialmod.server.data;

import com.takumistudios.socialmod.common.model.PresenceStatus;
import com.takumistudios.socialmod.common.model.Privacy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Datos sociales persistentes de un jugador (servidor como fuente de verdad, PLAN 4.2).
 * Se serializa con Gson: todos los campos deben tener un valor por defecto y {@link #normalize()}
 * repara los que falten en archivos antiguos o editados a mano.
 */
public final class PlayerRecord {
    public UUID id;
    public String name = "";
    public long firstSeen;
    public long lastSeen;

    // --- Presencia ---
    /** Estado elegido por el jugador (AFK se calcula aparte y no se guarda). */
    public PresenceStatus status = PresenceStatus.ONLINE;
    public String customStatus = "";

    // --- Privacidad ---
    public Privacy whoCanMessage = Privacy.EVERYONE;
    public Privacy whoSeesStatus = Privacy.EVERYONE;
    public boolean showDimension = false;
    public boolean readReceipts = true;
    public boolean typingIndicator = true;

    // --- Amigos y bloqueos ---
    public Set<UUID> friends = new LinkedHashSet<>();
    public Set<UUID> favorites = new HashSet<>();
    public Map<UUID, String> notes = new HashMap<>();
    public Set<UUID> blocked = new LinkedHashSet<>();
    public Set<UUID> incomingRequests = new LinkedHashSet<>();
    public Set<UUID> outgoingRequests = new LinkedHashSet<>();

    // --- Grupos ---
    public String mainGroup = "";
    public String teamId = "";
    public boolean teamChosen;
    public Set<String> groupInvites = new LinkedHashSet<>();

    // --- Buzón ---
    /** Mensajes sin leer por conversación (clave de {@code ConversationId}). */
    public Map<String, Integer> unread = new HashMap<>();
    /** Último interlocutor de un privado, para {@code /r}. */
    public UUID lastDirectPartner;
    /** Conversaciones recientes (la más reciente primero), para la columna de conversaciones. */
    public java.util.List<String> recent = new java.util.ArrayList<>();

    // --- Moderación ---
    public long mutedUntil;
    public String muteReason = "";

    public PlayerRecord() {
    }

    public PlayerRecord(UUID id, String name, long now) {
        this.id = id;
        this.name = name;
        this.firstSeen = now;
        this.lastSeen = now;
    }

    public boolean isMuted(long now) {
        return mutedUntil > now;
    }

    /** Marca una conversación como la más reciente (máximo 30). */
    public void touch(String conversation) {
        recent.remove(conversation);
        recent.addFirst(conversation);
        while (recent.size() > 30) {
            recent.removeLast();
        }
    }

    public int totalUnread() {
        int total = 0;
        for (int count : unread.values()) {
            total += count;
        }
        return total;
    }

    /** Repara campos nulos tras leer de disco. */
    public PlayerRecord normalize() {
        if (name == null) name = "";
        if (status == null || !status.selectable()) status = PresenceStatus.ONLINE;
        if (customStatus == null) customStatus = "";
        if (whoCanMessage == null) whoCanMessage = Privacy.EVERYONE;
        if (whoSeesStatus == null) whoSeesStatus = Privacy.EVERYONE;
        if (friends == null) friends = new LinkedHashSet<>();
        if (favorites == null) favorites = new HashSet<>();
        if (notes == null) notes = new HashMap<>();
        if (blocked == null) blocked = new LinkedHashSet<>();
        if (incomingRequests == null) incomingRequests = new LinkedHashSet<>();
        if (outgoingRequests == null) outgoingRequests = new LinkedHashSet<>();
        if (mainGroup == null) mainGroup = "";
        if (teamId == null) teamId = "";
        if (groupInvites == null) groupInvites = new LinkedHashSet<>();
        if (unread == null) unread = new HashMap<>();
        unread.values().removeIf(java.util.Objects::isNull);
        if (recent == null) recent = new java.util.ArrayList<>();
        recent.removeIf(java.util.Objects::isNull);
        if (muteReason == null) muteReason = "";
        return this;
    }
}
