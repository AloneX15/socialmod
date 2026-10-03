package com.takumistudios.socialmod.server.service;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.integration.VoiceBridge;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Chat de voz del grupo o la party (PLAN 12) a través de {@link VoiceBridge}. Comprueba módulo y pertenencia; el
 * mod de voz solo recibe peticiones válidas. Cualquier error del mod de voz se registra y no rompe SocialMod.
 */
public final class VoiceService {
    private final SocialServer social;

    public VoiceService(SocialServer social) {
        this.social = social;
    }

    public boolean available() {
        return ServerConfig.get().modules.voice && VoiceBridge.get() != null;
    }

    public @Nullable String currentGroup(UUID player) {
        VoiceBridge.Backend backend = VoiceBridge.get();
        if (backend == null) {
            return null;
        }
        try {
            return backend.currentGroup(player);
        } catch (RuntimeException | LinkageError e) {
            SocialMod.warnOnce("voice_state", "Error consultando el chat de voz", e);
            return null;
        }
    }

    public boolean join(ServerPlayer player, String groupId) {
        VoiceBridge.Backend backend = VoiceBridge.get();
        if (!ServerConfig.get().modules.voice || backend == null) {
            social.notifier().feedback(player, false, "socialmod.voice.unavailable");
            return false;
        }
        Group group = social.groups().get(groupId);
        if (group == null || !group.isMember(player.getUUID())) {
            social.notifier().feedback(player, false, "socialmod.group.not_member");
            return false;
        }
        try {
            if (!backend.join(player, group)) {
                social.notifier().feedback(player, false, "socialmod.voice.no_mod");
                return false;
            }
        } catch (RuntimeException | LinkageError e) {
            SocialMod.warnOnce("voice_join", "Error entrando al grupo de voz", e);
            return false;
        }
        social.notifier().feedback(player, true, "socialmod.voice.joined", group.party ? "Party" : group.name);
        social.snapshots().send(player);
        return true;
    }

    public void leave(ServerPlayer player) {
        VoiceBridge.Backend backend = VoiceBridge.get();
        if (backend == null) {
            return;
        }
        try {
            backend.leave(player);
        } catch (RuntimeException | LinkageError e) {
            SocialMod.warnOnce("voice_leave", "Error saliendo del grupo de voz", e);
        }
        social.notifier().feedback(player, true, "socialmod.voice.left");
        social.snapshots().send(player);
    }

    /** Al salir o ser expulsado de un grupo: si estaba en su chat de voz, sale. */
    public void onLeftGroup(UUID player, Group group) {
        if (!group.id.equals(currentGroup(player))) {
            return;
        }
        ServerPlayer online = social.online(player);
        VoiceBridge.Backend backend = VoiceBridge.get();
        if (online != null && backend != null) {
            try {
                backend.leave(online);
            } catch (RuntimeException | LinkageError e) {
                SocialMod.warnOnce("voice_leave", "Error saliendo del grupo de voz", e);
            }
        }
    }

    public void onDisband(Group group) {
        VoiceBridge.Backend backend = VoiceBridge.get();
        if (backend != null) {
            try {
                backend.groupRemoved(group);
            } catch (RuntimeException | LinkageError e) {
                SocialMod.warnOnce("voice_remove", "Error cerrando el grupo de voz", e);
            }
        }
    }
}
