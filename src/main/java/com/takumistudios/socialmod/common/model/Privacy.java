package com.takumistudios.socialmod.common.model;

/** Quién puede escribirme o ver mi estado (PLAN 5.3). */
public enum Privacy {
    EVERYONE("everyone"),
    FRIENDS("friends"),
    NOBODY("nobody");

    private final String id;

    Privacy(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Privacy next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Privacy byId(String id) {
        for (Privacy privacy : values()) {
            if (privacy.id.equalsIgnoreCase(id)) {
                return privacy;
            }
        }
        return EVERYONE;
    }
}
