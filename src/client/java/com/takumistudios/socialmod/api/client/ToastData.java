package com.takumistudios.socialmod.api.client;

import com.takumistudios.socialmod.common.net.Payloads;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Datos de un toast de SocialMod. Otros mods pueden mostrar uno con {@link SocialModClientAPI#showToast}.
 *
 * @param source       jugador cuya cabeza se muestra (o null para un icono genérico)
 * @param conversation conversación que abre Quick-Reply al pulsar la tecla (vacío si no aplica)
 */
public record ToastData(Payloads.NotifyKind kind, @Nullable UUID source, String title, String body, String conversation) {
    public static ToastData system(String title, String body) {
        return new ToastData(Payloads.NotifyKind.SYSTEM, null, title, body, "");
    }
}
