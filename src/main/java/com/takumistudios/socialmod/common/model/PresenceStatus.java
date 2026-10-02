package com.takumistudios.socialmod.common.model;

/**
 * Estado de presencia. Cada estado tiene un símbolo propio además del color (PLAN 7.3: no depender solo del color).
 * {@link #OFFLINE} nunca lo elige el jugador: es lo que ven los demás cuando está desconectado o invisible.
 */
public enum PresenceStatus {
    ONLINE("online", "●", 0xFF55FF55),
    AWAY("away", "◐", 0xFFFFAA00),
    DND("dnd", "⊘", 0xFFFF5555),
    INVISIBLE("invisible", "○", 0xFF808080),
    OFFLINE("offline", "○", 0xFF808080);

    private final String id;
    private final String symbol;
    private final int color;

    PresenceStatus(String id, String symbol, int color) {
        this.id = id;
        this.symbol = symbol;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public String symbol() {
        return symbol;
    }

    public int color() {
        return color;
    }

    /** Estados que el jugador puede elegir. */
    public boolean selectable() {
        return this != OFFLINE;
    }

    public static PresenceStatus byId(String id) {
        for (PresenceStatus status : values()) {
            if (status.id.equalsIgnoreCase(id)) {
                return status;
            }
        }
        return ONLINE;
    }

    public static PresenceStatus byOrdinal(int ordinal) {
        PresenceStatus[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : OFFLINE;
    }
}
