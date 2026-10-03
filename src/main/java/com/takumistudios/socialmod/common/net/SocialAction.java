package com.takumistudios.socialmod.common.net;

/**
 * Acciones sociales que el cliente puede pedir con {@code ActionC2S}. Cada una se valida en el servidor
 * (permisos, bloqueos, límites, pertenencia al grupo). Los argumentos son texto con tope de longitud.
 * El mismo código se usa desde los comandos, así que el cliente no tiene más poder que un jugador vanilla.
 */
public enum SocialAction {
    // Amigos y bloqueos: a = nombre o uuid del jugador
    FRIEND_REQUEST, FRIEND_ACCEPT, FRIEND_DENY, FRIEND_REMOVE, FRIEND_FAVORITE, FRIEND_NOTE,
    BLOCK, UNBLOCK,
    // Estado y privacidad: a = valor
    SET_STATUS, SET_CUSTOM_STATUS, SET_PRIVACY_MESSAGES, SET_PRIVACY_STATUS, SET_SHOW_DIMENSION,
    SET_READ_RECEIPTS, SET_TYPING_INDICATOR,
    // Grupos: a = id del grupo, b = argumento
    GROUP_CREATE, GROUP_INVITE, GROUP_ACCEPT, GROUP_DECLINE, GROUP_LEAVE, GROUP_KICK, GROUP_PROMOTE, GROUP_DEMOTE,
    GROUP_TRANSFER, GROUP_SET_MAIN, GROUP_SET_MOTD, GROUP_SET_DESCRIPTION, GROUP_SET_COLOR, GROUP_SET_TAG,
    GROUP_CHANNEL_CREATE, GROUP_CHANNEL_DELETE, GROUP_PIN, GROUP_DISBAND, GROUP_EVENT_CREATE, GROUP_EVENT_DELETE,
    // Parties
    PARTY_CREATE, PARTY_INVITE,
    // Panel abierto/cerrado: suscripción a la presencia de todos (PLAN 4.3)
    PANEL_OPEN, PANEL_CLOSE,
    // Moderación: a = conversación, b = id del mensaje
    REPORT_MESSAGE,
    // Datos: exportar mis datos
    DATA_EXPORT,
    // Añadidas en el protocolo 2 (al final para no mover los ordinales anteriores)
    GROUP_SET_ICON,
    // Chat de voz (Simple Voice Chat): a = id del grupo
    VOICE_JOIN, VOICE_LEAVE;

    public static SocialAction byOrdinal(int ordinal) {
        SocialAction[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
