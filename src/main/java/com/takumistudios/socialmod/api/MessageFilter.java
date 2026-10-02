package com.takumistudios.socialmod.api;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Filtro externo de mensajes (PLAN 9: hook para filtros externos). Se aplica después del filtro de palabras
 * de SocialMod. Debe ser rápido: se ejecuta en el hilo principal por cada mensaje.
 */
@FunctionalInterface
public interface MessageFilter {
    /** Devuelve el texto (posiblemente modificado) o {@code null} para rechazar el mensaje. */
    @Nullable String filter(ServerPlayer sender, String conversation, String text);
}
