package com.takumistudios.socialmod.common.model;

/**
 * Rol dentro de un grupo, de mayor a menor rango. Los permisos de cada rol los decide la config del servidor
 * ({@code roles} en server.json); estos son solo los nombres y el orden.
 */
public enum Role {
    LEADER("leader", "L"),
    OFFICER("officer", "O"),
    MEMBER("member", "M"),
    RECRUIT("recruit", "R");

    private final String id;
    private final String letter;

    Role(String id, String letter) {
        this.id = id;
        this.letter = letter;
    }

    public String id() {
        return id;
    }

    /** Letra que se muestra en la lista de miembros ([L] Alex). */
    public String letter() {
        return letter;
    }

    /** {@code true} si este rol es igual o superior a {@code other}. */
    public boolean atLeast(Role other) {
        return ordinal() <= other.ordinal();
    }

    public boolean outranks(Role other) {
        return ordinal() < other.ordinal();
    }

    /** Un rango más (Recluta → Miembro → Oficial); el líder solo se cambia transfiriendo el grupo. */
    public Role promoted() {
        return this == RECRUIT ? MEMBER : this == MEMBER ? OFFICER : this;
    }

    public Role demoted() {
        return this == OFFICER ? MEMBER : this == MEMBER ? RECRUIT : this;
    }

    public static Role byId(String id) {
        for (Role role : values()) {
            if (role.id.equalsIgnoreCase(id)) {
                return role;
            }
        }
        return null;
    }

    public static Role byOrdinal(int ordinal) {
        Role[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : RECRUIT;
    }
}
