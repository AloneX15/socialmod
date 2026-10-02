package com.takumistudios.socialmod.api.event;

import com.takumistudios.socialmod.api.GroupInfo;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.UUID;

/** Eventos de grupos y parties. */
public final class GroupEvents {
    public static final Event<GroupChanged> CREATED = EventFactory.createArrayBacked(GroupChanged.class,
            listeners -> group -> {
                for (GroupChanged listener : listeners) {
                    listener.onGroup(group);
                }
            });

    public static final Event<GroupChanged> DISBANDED = EventFactory.createArrayBacked(GroupChanged.class,
            listeners -> group -> {
                for (GroupChanged listener : listeners) {
                    listener.onGroup(group);
                }
            });

    public static final Event<MemberChanged> MEMBER_JOINED = EventFactory.createArrayBacked(MemberChanged.class,
            listeners -> (group, player) -> {
                for (MemberChanged listener : listeners) {
                    listener.onMemberChanged(group, player);
                }
            });

    public static final Event<MemberChanged> MEMBER_LEFT = EventFactory.createArrayBacked(MemberChanged.class,
            listeners -> (group, player) -> {
                for (MemberChanged listener : listeners) {
                    listener.onMemberChanged(group, player);
                }
            });

    private GroupEvents() {
    }

    @FunctionalInterface
    public interface GroupChanged {
        void onGroup(GroupInfo group);
    }

    @FunctionalInterface
    public interface MemberChanged {
        void onMemberChanged(GroupInfo group, UUID player);
    }
}
