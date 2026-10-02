package com.takumistudios.socialmod.api;

import java.util.Map;
import java.util.UUID;

/**
 * Vista de solo lectura de un grupo para otros mods (p. ej. mods de protección que dan permisos por grupo).
 *
 * @param members jugador → rol ({@code leader}, {@code officer}, {@code member}, {@code recruit})
 */
public record GroupInfo(String id, String name, String tag, int color, boolean party, Map<UUID, String> members) {
}
