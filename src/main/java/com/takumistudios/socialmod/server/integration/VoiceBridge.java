package com.takumistudios.socialmod.server.integration;

import com.takumistudios.socialmod.server.data.Group;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Puente con un mod de voz (PLAN 12). No usa ninguna clase del mod de voz: la implementación la registra el plugin
 * ({@link VoiceChatPlugin}, cargado por Simple Voice Chat solo si está instalado), así SocialMod funciona sin él.
 */
public final class VoiceBridge {
    public interface Backend {
        /** Mete al jugador en el grupo de voz del grupo/party. {@code false} si el jugador no tiene el mod de voz. */
        boolean join(ServerPlayer player, Group group);

        void leave(ServerPlayer player);

        /** Id del grupo de SocialMod cuyo grupo de voz ocupa el jugador, o {@code null}. */
        @Nullable String currentGroup(UUID player);

        /** El grupo de SocialMod desapareció: se cierra su grupo de voz. */
        void groupRemoved(Group group);
    }

    private static volatile @Nullable Backend backend;

    private VoiceBridge() {
    }

    public static @Nullable Backend get() {
        return backend;
    }

    public static void set(@Nullable Backend value) {
        backend = value;
    }
}
