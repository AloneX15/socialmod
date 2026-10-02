package com.takumistudios.socialmod.api;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Notificación de sistema que otro mod puede mostrar con {@link SocialModServerAPI#sendSystemNotification}.
 * Con SocialMod en el cliente se muestra como toast; sin él, en la action bar o el chat.
 *
 * @param source jugador relacionado (se muestra su cabeza), o {@code null}
 */
public record Notification(Component title, Component body, @Nullable UUID source) {
    public static Notification of(Component title, Component body) {
        return new Notification(title, body, null);
    }
}
