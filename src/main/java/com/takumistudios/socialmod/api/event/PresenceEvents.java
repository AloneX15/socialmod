package com.takumistudios.socialmod.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.UUID;

/** Eventos de presencia. Estados: online, away, dnd, invisible u offline. */
public final class PresenceEvents {
    public static final Event<StatusChanged> STATUS_CHANGED = EventFactory.createArrayBacked(StatusChanged.class,
            listeners -> (player, oldStatus, newStatus) -> {
                for (StatusChanged listener : listeners) {
                    listener.onStatusChanged(player, oldStatus, newStatus);
                }
            });

    private PresenceEvents() {
    }

    @FunctionalInterface
    public interface StatusChanged {
        void onStatusChanged(UUID player, String oldStatus, String newStatus);
    }
}
