package com.takumistudios.socialmod.client;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.common.model.PresenceStatus;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.common.net.SnapshotDto;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caché local del estado social que envía el servidor (que es la fuente de verdad). Se borra al desconectarse.
 * Las pantallas comprueban {@link #version()} para saber si deben redibujarse.
 */
public final class ClientState {
    private static final Gson GSON = new Gson();
    private static final ClientState INSTANCE = new ClientState();
    private static final int MAX_CACHED_MESSAGES = 300;

    private Payloads.@Nullable HelloS2C hello;
    private SnapshotDto snapshot = new SnapshotDto();
    private final Map<UUID, Payloads.PresenceEntry> presence = new HashMap<>();
    private final Map<String, ConversationCache> conversations = new LinkedHashMap<>();
    /** Últimas conversaciones con mensajes recibidos (para Tab en Quick-Reply). */
    private final List<String> replyTargets = new ArrayList<>();
    private @Nullable String activeConversation;
    private int version;

    public static final class ConversationCache {
        public String title = "";
        public final List<Payloads.MessageView> messages = new ArrayList<>();
        public boolean hasMore = true;
        public boolean requested;
        /** Jugadores escribiendo: uuid → hasta cuándo se muestra. */
        public final Map<UUID, Typing> typing = new HashMap<>();
        /** Hasta qué id leyó el otro participante (privados). */
        public long readUpTo;

        public record Typing(String name, long until) {
        }

        public Payloads.@Nullable MessageView last() {
            return messages.isEmpty() ? null : messages.getLast();
        }
    }

    public static ClientState get() {
        return INSTANCE;
    }

    public int version() {
        return version;
    }

    private void changed() {
        version++;
    }

    public void reset() {
        hello = null;
        snapshot = new SnapshotDto();
        presence.clear();
        conversations.clear();
        replyTargets.clear();
        tags.clear();
        activeConversation = null;
        changed();
    }

    // ---------- Servidor ----------

    public void onHello(Payloads.HelloS2C payload) {
        hello = payload;
        changed();
    }

    /** {@code true} si el servidor tiene SocialMod con un protocolo compatible: se activa la UI completa. */
    public boolean connected() {
        return hello != null && hello.protocol() == Payloads.PROTOCOL_VERSION;
    }

    public Payloads.@Nullable HelloS2C hello() {
        return hello;
    }

    public void onSnapshot(String json) {
        try {
            SnapshotDto parsed = GSON.fromJson(json, SnapshotDto.class);
            if (parsed != null) {
                snapshot = parsed;
                for (SnapshotDto.ConversationView view : parsed.conversations) {
                    conversation(view.id).title = view.title;
                }
                changed();
            }
        } catch (JsonParseException e) {
            SocialMod.warnOnce("snapshot_parse", "Estado social inválido recibido del servidor", e);
        }
    }

    public SnapshotDto snapshot() {
        return snapshot;
    }

    public @Nullable UUID selfId() {
        try {
            return snapshot.self.uuid.isEmpty() ? null : UUID.fromString(snapshot.self.uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------- Presencia ----------

    public void onPresence(List<Payloads.PresenceEntry> entries) {
        for (Payloads.PresenceEntry entry : entries) {
            presence.put(entry.player(), entry);
            for (SnapshotDto.Friend friend : snapshot.friends) {
                if (friend.uuid.equals(entry.player().toString())) {
                    friend.status = PresenceStatus.byOrdinal(entry.status()).id();
                    friend.customStatus = entry.customStatus();
                    friend.dimension = entry.dimension();
                }
            }
            for (SnapshotDto.GroupView group : snapshot.groups) {
                for (SnapshotDto.Member member : group.members) {
                    if (member.uuid.equals(entry.player().toString())) {
                        PresenceStatus status = PresenceStatus.byOrdinal(entry.status());
                        member.status = status.id();
                        member.online = status != PresenceStatus.OFFLINE;
                    }
                }
            }
        }
        changed();
    }

    public PresenceStatus statusOf(UUID player) {
        Payloads.PresenceEntry entry = presence.get(player);
        return entry == null ? PresenceStatus.OFFLINE : PresenceStatus.byOrdinal(entry.status());
    }

    public Payloads.@Nullable PresenceEntry presenceOf(UUID player) {
        return presence.get(player);
    }

    // ---------- Conversaciones ----------

    public ConversationCache conversation(String id) {
        return conversations.computeIfAbsent(id, k -> new ConversationCache());
    }

    public @Nullable ConversationCache existing(String id) {
        return conversations.get(id);
    }

    public void onMessages(Payloads.MessagesS2C payload) {
        ConversationCache cache = conversation(payload.conversation());
        if (!payload.title().isEmpty()) {
            cache.title = payload.title();
        }
        switch (payload.mode()) {
            case LIVE -> {
                for (Payloads.MessageView message : payload.messages()) {
                    if (cache.messages.isEmpty() || cache.messages.getLast().id() < message.id()) {
                        cache.messages.add(message);
                    }
                    cache.typing.remove(message.sender());
                    UUID self = selfId();
                    if (self != null && !self.equals(message.sender())) {
                        replyTargets.remove(payload.conversation());
                        replyTargets.addFirst(payload.conversation());
                        while (replyTargets.size() > 5) {
                            replyTargets.removeLast();
                        }
                        if (!payload.conversation().equals(activeConversation)) {
                            bumpUnread(payload.conversation(), cache.title, message);
                        }
                    }
                    touchConversation(payload.conversation(), cache.title, message);
                }
            }
            case HISTORY -> {
                cache.requested = true;
                cache.hasMore = payload.hasMore();
                List<Payloads.MessageView> merged = new ArrayList<>(payload.messages());
                long newest = merged.isEmpty() ? Long.MIN_VALUE : merged.getLast().id();
                for (Payloads.MessageView existing : cache.messages) {
                    if (existing.id() > newest) {
                        merged.add(existing);
                    }
                }
                cache.messages.clear();
                cache.messages.addAll(merged);
            }
            case UPDATE -> {
                for (Payloads.MessageView message : payload.messages()) {
                    for (int i = 0; i < cache.messages.size(); i++) {
                        if (cache.messages.get(i).id() == message.id()) {
                            cache.messages.set(i, message);
                        }
                    }
                }
            }
        }
        while (cache.messages.size() > MAX_CACHED_MESSAGES) {
            cache.messages.removeFirst();
            cache.hasMore = true;
        }
        changed();
    }

    private void bumpUnread(String id, String title, Payloads.MessageView message) {
        SnapshotDto.ConversationView view = findView(id, title);
        view.unread++;
    }

    private void touchConversation(String id, String title, Payloads.MessageView message) {
        SnapshotDto.ConversationView view = findView(id, title);
        view.preview = message.senderName() + ": " + com.takumistudios.socialmod.common.text.MessageFormatter.preview(message.text(), 40);
        view.lastTime = message.time();
        snapshot.conversations.remove(view);
        snapshot.conversations.addFirst(view);
    }

    private SnapshotDto.ConversationView findView(String id, String title) {
        for (SnapshotDto.ConversationView view : snapshot.conversations) {
            if (view.id.equals(id)) {
                return view;
            }
        }
        SnapshotDto.ConversationView view = new SnapshotDto.ConversationView();
        view.id = id;
        view.title = title;
        snapshot.conversations.addFirst(view);
        return view;
    }

    public void onSignal(Payloads.SignalS2C payload) {
        ConversationCache cache = conversation(payload.conversation());
        if (payload.signal() == Payloads.Signal.TYPING) {
            cache.typing.put(payload.player(), new ConversationCache.Typing(payload.name(), System.currentTimeMillis() + 4000));
        } else {
            cache.readUpTo = Math.max(cache.readUpTo, payload.value());
        }
        changed();
    }

    /** Marca como leída una conversación abierta. Devuelve {@code true} si tenía mensajes sin leer. */
    public boolean markRead(String id) {
        boolean hadUnread = false;
        for (SnapshotDto.ConversationView view : snapshot.conversations) {
            if (view.id.equals(id) && view.unread > 0) {
                view.unread = 0;
                hadUnread = true;
            }
        }
        if (hadUnread) {
            changed();
        }
        return hadUnread;
    }

    public long lastMessageId(String id) {
        ConversationCache cache = conversations.get(id);
        Payloads.MessageView last = cache == null ? null : cache.last();
        return last == null ? 0 : last.id();
    }

    public int unreadOf(String id) {
        for (SnapshotDto.ConversationView view : snapshot.conversations) {
            if (view.id.equals(id)) {
                return view.unread;
            }
        }
        return 0;
    }

    public int totalUnread() {
        int total = 0;
        for (SnapshotDto.ConversationView view : snapshot.conversations) {
            total += view.unread;
        }
        return total;
    }

    public void setActiveConversation(@Nullable String id) {
        activeConversation = id;
    }

    public @Nullable String activeConversation() {
        return activeConversation;
    }

    public List<String> replyTargets() {
        return replyTargets;
    }

    public String titleOf(String conversation) {
        ConversationCache cache = conversations.get(conversation);
        if (cache != null && !cache.title.isEmpty()) {
            return cache.title;
        }
        for (SnapshotDto.ConversationView view : snapshot.conversations) {
            if (view.id.equals(conversation)) {
                return view.title;
            }
        }
        return conversation;
    }

    public SnapshotDto.@Nullable GroupView group(String id) {
        for (SnapshotDto.GroupView group : snapshot.groups) {
            if (group.id.equals(id)) {
                return group;
            }
        }
        return null;
    }

    public SnapshotDto.@Nullable GroupView mainGroup() {
        SnapshotDto.GroupView main = group(snapshot.self.mainGroup);
        if (main != null) {
            return main;
        }
        for (SnapshotDto.GroupView group : snapshot.groups) {
            if (!group.party) {
                return group;
            }
        }
        return null;
    }

    /** Etiqueta del grupo principal de un jugador visible para mí (para nametags). */
    public SnapshotDto.@Nullable GroupView sharedMainGroupOf(UUID player) {
        String id = player.toString();
        for (SnapshotDto.GroupView group : snapshot.groups) {
            if (group.party) {
                continue;
            }
            for (SnapshotDto.Member member : group.members) {
                if (member.uuid.equals(id)) {
                    return group;
                }
            }
        }
        return null;
    }

    // ---------- Etiquetas de grupo (nametags) ----------

    private final Map<UUID, Payloads.TagEntry> tags = new HashMap<>();

    public void onTags(Payloads.TagsS2C payload) {
        if (payload.full()) {
            tags.clear();
        }
        for (Payloads.TagEntry entry : payload.entries()) {
            if (entry.tag().isEmpty()) {
                tags.remove(entry.player());
            } else {
                tags.put(entry.player(), entry);
            }
        }
    }

    public Payloads.@Nullable TagEntry tagOf(UUID player) {
        return tags.get(player);
    }

    public boolean isFriend(UUID player) {
        String id = player.toString();
        return snapshot.friends.stream().anyMatch(f -> f.uuid.equals(id));
    }

    public boolean isBlocked(UUID player) {
        String id = player.toString();
        return snapshot.blocked.stream().anyMatch(f -> f.uuid.equals(id));
    }

    public boolean hasOutgoingRequest(UUID player) {
        String id = player.toString();
        return snapshot.outgoing.stream().anyMatch(f -> f.uuid.equals(id));
    }
}
