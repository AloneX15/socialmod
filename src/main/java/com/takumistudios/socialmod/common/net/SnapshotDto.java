package com.takumistudios.socialmod.common.net;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado social completo de un jugador que el servidor envía al cliente con el mod: al conectarse y cuando cambian
 * sus amistades o grupos (cambios poco frecuentes). Los cambios de presencia van aparte, por deltas.
 * Viaja como JSON dentro de un payload (solo S→C; el servidor nunca acepta JSON del cliente).
 */
public final class SnapshotDto {
    public Self self = new Self();
    public List<Friend> friends = new ArrayList<>();
    public List<NameRef> incoming = new ArrayList<>();
    public List<NameRef> outgoing = new ArrayList<>();
    public List<NameRef> blocked = new ArrayList<>();
    public List<GroupView> groups = new ArrayList<>();
    public List<Invite> groupInvites = new ArrayList<>();
    public List<ConversationView> conversations = new ArrayList<>();

    public static final class Self {
        public String uuid = "";
        public String name = "";
        public String status = "online";
        public String customStatus = "";
        public String whoCanMessage = "everyone";
        public String whoSeesStatus = "everyone";
        public boolean showDimension;
        public boolean readReceipts = true;
        public boolean typingIndicator = true;
        public String mainGroup = "";
        public long mutedUntil;
    }

    public static final class NameRef {
        public String uuid;
        public String name;

        public NameRef() {
        }

        public NameRef(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    public static final class Friend {
        public String uuid;
        public String name;
        public boolean favorite;
        public String note = "";
        public String status = "offline";
        public String customStatus = "";
        public String dimension = "";
        public long lastSeen;
    }

    public static final class Member {
        public String uuid;
        public String name;
        public String role;
        public boolean online;
        public String status = "offline";
    }

    public static final class ChannelView {
        public String name;
        public String minRole;
        public boolean canWrite;
    }

    public static final class EventView {
        public String id;
        public String title;
        public long startsAt;
    }

    public static final class GroupView {
        public String id;
        public String name;
        public String tag;
        public int color;
        public String icon;
        public String description = "";
        public String motd = "";
        public String pinned = "";
        public boolean party;
        public String myRole;
        public List<String> myPermissions = new ArrayList<>();
        public List<Member> members = new ArrayList<>();
        public List<ChannelView> channels = new ArrayList<>();
        public List<EventView> events = new ArrayList<>();
    }

    public static final class Invite {
        public String groupId;
        public String groupName;
        public boolean party;
    }

    public static final class ConversationView {
        public String id;
        public String title;
        public int unread;
        public String preview = "";
        public long lastTime;
    }
}
