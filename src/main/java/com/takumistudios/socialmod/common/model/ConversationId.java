package com.takumistudios.socialmod.common.model;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identificador de conversación, en texto para poder usarlo como clave y nombre de archivo:
 * <ul>
 *     <li>{@code dm:<uuidA>:<uuidB>}: privado entre dos jugadores (UUIDs ordenados, así A→B y B→A son la misma).</li>
 *     <li>{@code g:<groupId>:<canal>}: canal de un grupo o de una party.</li>
 * </ul>
 */
public record ConversationId(Kind kind, UUID a, UUID b, String groupId, String channel) {
    public static final int MAX_LENGTH = 96;
    public static final Pattern GROUP_ID = Pattern.compile("[a-z0-9]{4,16}");
    public static final Pattern CHANNEL = Pattern.compile("[a-z0-9_-]{1,16}");

    public enum Kind {
        DIRECT, GROUP
    }

    public static ConversationId direct(UUID first, UUID second) {
        return first.compareTo(second) <= 0
                ? new ConversationId(Kind.DIRECT, first, second, null, null)
                : new ConversationId(Kind.DIRECT, second, first, null, null);
    }

    public static ConversationId group(String groupId, String channel) {
        return new ConversationId(Kind.GROUP, null, null, groupId, channel.toLowerCase(Locale.ROOT));
    }

    public boolean isDirect() {
        return kind == Kind.DIRECT;
    }

    /** En un privado, el otro participante. */
    public UUID other(UUID self) {
        return self.equals(a) ? b : a;
    }

    public boolean involves(UUID player) {
        return isDirect() && (player.equals(a) || player.equals(b));
    }

    /** Clave de texto. Es estable: se usa como nombre de archivo y como clave de mapas. */
    public String key() {
        return isDirect() ? "dm:" + a + ":" + b : "g:" + groupId + ":" + channel;
    }

    /** Nombre de archivo seguro en cualquier sistema operativo. */
    public String fileName() {
        return key().replace(':', '_');
    }

    /** Lee una clave; devuelve {@code null} si está mal formada (nunca lanza excepción con datos del cliente). */
    public static ConversationId parse(String key) {
        if (key == null || key.length() > MAX_LENGTH) {
            return null;
        }
        String[] parts = key.split(":", -1);
        try {
            if (parts.length == 3 && parts[0].equals("dm")) {
                return direct(UUID.fromString(parts[1]), UUID.fromString(parts[2]));
            }
            if (parts.length == 3 && parts[0].equals("g") && GROUP_ID.matcher(parts[1]).matches() && CHANNEL.matcher(parts[2]).matches()) {
                return group(parts[1], parts[2]);
            }
        } catch (IllegalArgumentException ignored) {
            // UUID mal formado
        }
        return null;
    }

    @Override
    public String toString() {
        return key();
    }
}
