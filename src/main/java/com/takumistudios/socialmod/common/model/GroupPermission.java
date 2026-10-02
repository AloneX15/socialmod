package com.takumistudios.socialmod.common.model;

/** Acciones de gestión de un grupo que se pueden conceder por rol (PLAN 5.2). */
public enum GroupPermission {
    INVITE("invite"),
    KICK("kick"),
    MANAGE_CHANNELS("manage_channels"),
    PIN("pin"),
    EDIT_INFO("edit_info"),
    MANAGE_ROLES("manage_roles"),
    MANAGE_EVENTS("manage_events");

    private final String id;

    GroupPermission(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static GroupPermission byId(String id) {
        for (GroupPermission permission : values()) {
            if (permission.id.equalsIgnoreCase(id)) {
                return permission;
            }
        }
        return null;
    }
}
