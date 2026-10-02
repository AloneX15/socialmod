package com.takumistudios.socialmod.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Proveedor de actividad para el estado de un jugador (p. ej. "En una mazmorra"). Se usa cuando el jugador no
 * tiene un estado personalizado. Se consulta como mucho una vez por segundo por jugador.
 */
@FunctionalInterface
public interface StatusProvider {
    Optional<String> activity(ServerPlayer player);
}
