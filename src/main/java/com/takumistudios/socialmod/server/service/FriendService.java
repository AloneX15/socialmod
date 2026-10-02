package com.takumistudios.socialmod.server.service;

import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.common.text.TextSanitizer;
import com.takumistudios.socialmod.server.PermissionBridge;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Amigos, solicitudes, favoritos, notas y bloqueos (PLAN 5.3). Bloquear se aplica en el servidor: el bloqueado no
 * puede enviarte privados, invitarte, mencionarte ni ver tu estado, y se rompe la amistad.
 */
public final class FriendService {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final SocialServer social;

    public FriendService(SocialServer social) {
        this.social = social;
    }

    /** Busca un jugador conocido por nombre o UUID (sin peticiones a Mojang). */
    public @Nullable PlayerRecord resolve(String nameOrUuid) {
        if (nameOrUuid == null || nameOrUuid.isEmpty()) {
            return null;
        }
        if (nameOrUuid.length() == 36) {
            try {
                return social.storage().player(UUID.fromString(nameOrUuid));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return NAME.matcher(nameOrUuid).matches() ? social.storage().findByName(nameOrUuid) : null;
    }

    private boolean enabled(ServerPlayer actor) {
        if (!ServerConfig.get().modules.friends) {
            social.notifier().feedback(actor, false, "socialmod.error.module_disabled");
            return false;
        }
        return true;
    }

    private @Nullable PlayerRecord target(ServerPlayer actor, String name) {
        PlayerRecord target = resolve(name);
        if (target == null) {
            social.notifier().feedback(actor, false, "socialmod.error.unknown_player", name);
            return null;
        }
        if (target.id.equals(actor.getUUID())) {
            social.notifier().feedback(actor, false, "socialmod.error.self");
            return null;
        }
        return target;
    }

    public boolean request(ServerPlayer actor, String name) {
        if (!enabled(actor)) return false;
        PlayerRecord target = target(actor, name);
        if (target == null) return false;
        PlayerRecord self = social.record(actor);
        if (self.friends.contains(target.id)) {
            social.notifier().feedback(actor, false, "socialmod.friend.already", target.name);
            return false;
        }
        if (self.blocked.contains(target.id)) {
            social.notifier().feedback(actor, false, "socialmod.error.you_blocked", target.name);
            return false;
        }
        // Si el otro ya nos lo pidió, aceptar directamente
        if (self.incomingRequests.contains(target.id)) {
            return accept(actor, target.name);
        }
        int maxFriends = PermissionBridge.limit(actor, PermissionBridge.LIMIT_FRIENDS, ServerConfig.get().limits.maxFriends);
        if (self.friends.size() >= maxFriends) {
            social.notifier().feedback(actor, false, "socialmod.error.limit_friends", maxFriends);
            return false;
        }
        if (self.outgoingRequests.size() >= ServerConfig.get().limits.maxPendingRequests) {
            social.notifier().feedback(actor, false, "socialmod.error.too_many_requests");
            return false;
        }
        // Un jugador que te bloqueó no recibe nada; el que envía ve el mismo mensaje (no se revela el bloqueo)
        self.outgoingRequests.add(target.id);
        if (!target.blocked.contains(actor.getUUID()) && target.incomingRequests.size() < ServerConfig.get().limits.maxPendingRequests) {
            target.incomingRequests.add(actor.getUUID());
            ServerPlayer online = social.online(target.id);
            if (online != null) {
                social.notifier().notify(online, Payloads.NotifyKind.FRIEND, actor.getUUID(), "socialmod.notify.friend_request",
                        new Object[]{self.name}, "", "");
                social.snapshots().send(online);
            }
        }
        social.storage().markPlayersDirty();
        social.notifier().feedback(actor, true, "socialmod.friend.requested", target.name);
        social.snapshots().send(actor);
        return true;
    }

    public boolean accept(ServerPlayer actor, String name) {
        if (!enabled(actor)) return false;
        PlayerRecord target = target(actor, name);
        if (target == null) return false;
        PlayerRecord self = social.record(actor);
        if (!self.incomingRequests.remove(target.id)) {
            social.notifier().feedback(actor, false, "socialmod.friend.no_request", target.name);
            return false;
        }
        target.outgoingRequests.remove(actor.getUUID());
        self.outgoingRequests.remove(target.id);
        target.incomingRequests.remove(actor.getUUID());
        self.friends.add(target.id);
        target.friends.add(actor.getUUID());
        social.storage().markPlayersDirty();
        social.storage().audit("FRIEND " + self.name + " <-> " + target.name);
        social.notifier().feedback(actor, true, "socialmod.friend.added", target.name);
        ServerPlayer online = social.online(target.id);
        if (online != null) {
            social.notifier().notify(online, Payloads.NotifyKind.FRIEND, actor.getUUID(), "socialmod.notify.friend_accepted",
                    new Object[]{self.name}, "", "");
        }
        refresh(actor.getUUID(), target.id);
        return true;
    }

    public boolean deny(ServerPlayer actor, String name) {
        PlayerRecord target = target(actor, name);
        if (target == null) return false;
        PlayerRecord self = social.record(actor);
        boolean removed = self.incomingRequests.remove(target.id);
        target.outgoingRequests.remove(actor.getUUID());
        // Cancelar una solicitud propia también pasa por aquí
        removed |= self.outgoingRequests.remove(target.id);
        target.incomingRequests.remove(actor.getUUID());
        if (!removed) {
            social.notifier().feedback(actor, false, "socialmod.friend.no_request", target.name);
            return false;
        }
        social.storage().markPlayersDirty();
        social.notifier().feedback(actor, true, "socialmod.friend.denied", target.name);
        refresh(actor.getUUID(), target.id);
        return true;
    }

    public boolean remove(ServerPlayer actor, String name) {
        PlayerRecord target = target(actor, name);
        if (target == null) return false;
        PlayerRecord self = social.record(actor);
        if (!self.friends.remove(target.id)) {
            social.notifier().feedback(actor, false, "socialmod.friend.not_friends", target.name);
            return false;
        }
        target.friends.remove(actor.getUUID());
        self.favorites.remove(target.id);
        target.favorites.remove(actor.getUUID());
        social.storage().markPlayersDirty();
        social.notifier().feedback(actor, true, "socialmod.friend.removed", target.name);
        refresh(actor.getUUID(), target.id);
        return true;
    }

    public boolean toggleFavorite(ServerPlayer actor, String name) {
        PlayerRecord target = target(actor, name);
        if (target == null) return false;
        PlayerRecord self = social.record(actor);
        if (!self.friends.contains(target.id)) {
            social.notifier().feedback(actor, false, "socialmod.friend.not_friends", target.name);
            return false;
        }
        if (!self.favorites.remove(target.id)) {
            self.favorites.add(target.id);
        }
        social.storage().markPlayersDirty();
        social.snapshots().send(actor);
        return true;
    }

    public boolean setNote(ServerPlayer actor, String name, String note) {
        PlayerRecord target = target(actor, name);
        if (target == null) return false;
        PlayerRecord self = social.record(actor);
        String clean = TextSanitizer.clean(note, ServerConfig.get().chat.maxNoteLength);
        if (clean.isEmpty()) {
            self.notes.remove(target.id);
        } else {
            self.notes.put(target.id, clean);
        }
        social.storage().markPlayersDirty();
        social.snapshots().send(actor);
        return true;
    }

    public boolean block(ServerPlayer actor, String name) {
        PlayerRecord target = target(actor, name);
        if (target == null) return false;
        PlayerRecord self = social.record(actor);
        if (!self.blocked.add(target.id)) {
            social.notifier().feedback(actor, false, "socialmod.block.already", target.name);
            return false;
        }
        self.friends.remove(target.id);
        target.friends.remove(actor.getUUID());
        self.favorites.remove(target.id);
        target.favorites.remove(actor.getUUID());
        self.incomingRequests.remove(target.id);
        self.outgoingRequests.remove(target.id);
        target.incomingRequests.remove(actor.getUUID());
        target.outgoingRequests.remove(actor.getUUID());
        social.storage().markPlayersDirty();
        social.storage().audit("BLOCK " + self.name + " -> " + target.name);
        social.notifier().feedback(actor, true, "socialmod.block.done", target.name);
        refresh(actor.getUUID(), target.id);
        social.presence().markChanged(actor.getUUID());
        return true;
    }

    public boolean unblock(ServerPlayer actor, String name) {
        PlayerRecord target = target(actor, name);
        if (target == null) return false;
        PlayerRecord self = social.record(actor);
        if (!self.blocked.remove(target.id)) {
            social.notifier().feedback(actor, false, "socialmod.block.not_blocked", target.name);
            return false;
        }
        social.storage().markPlayersDirty();
        social.notifier().feedback(actor, true, "socialmod.block.undone", target.name);
        social.snapshots().send(actor);
        social.presence().markChanged(actor.getUUID());
        return true;
    }

    public boolean areFriends(UUID a, UUID b) {
        PlayerRecord record = social.storage().player(a);
        return record != null && record.friends.contains(b);
    }

    /** {@code true} si alguno de los dos bloqueó al otro. */
    public boolean blockedEitherWay(UUID a, UUID b) {
        PlayerRecord ra = social.storage().player(a);
        PlayerRecord rb = social.storage().player(b);
        return (ra != null && ra.blocked.contains(b)) || (rb != null && rb.blocked.contains(a));
    }

    private void refresh(UUID a, UUID b) {
        for (UUID id : new UUID[]{a, b}) {
            ServerPlayer player = social.online(id);
            if (player != null) {
                social.snapshots().send(player);
            }
        }
        social.presence().markChanged(a);
        social.presence().markChanged(b);
    }
}
