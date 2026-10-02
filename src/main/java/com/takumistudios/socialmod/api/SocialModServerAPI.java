package com.takumistudios.socialmod.api;

import com.takumistudios.socialmod.server.ServerApiImpl;
import com.takumistudios.socialmod.server.SocialServer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * API de servidor de SocialMod (PLAN 16). Se obtiene con {@link #get()}. Las consultas devuelven valores vacíos
 * si el servidor aún no ha arrancado. Usar solo desde el hilo principal del servidor.
 */
public interface SocialModServerAPI {
    static SocialModServerAPI get() {
        return ServerApiImpl.INSTANCE;
    }

    Optional<GroupInfo> getMainGroup(UUID player);

    Collection<GroupInfo> getGroups(UUID player);

    boolean areFriends(UUID a, UUID b);

    boolean isBlocked(UUID blocker, UUID target);

    void sendSystemNotification(ServerPlayer player, Notification notification);

    void registerStatusProvider(Identifier id, StatusProvider provider);

    void registerMessageFilter(MessageFilter filter);

    /** {@code true} mientras haya un servidor con SocialMod en marcha. */
    default boolean isAvailable() {
        return SocialServer.get() != null;
    }
}
