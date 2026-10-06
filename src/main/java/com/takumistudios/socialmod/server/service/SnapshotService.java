package com.takumistudios.socialmod.server.service;

import com.takumistudios.socialmod.common.model.ConversationId;
import com.takumistudios.socialmod.common.model.GroupPermission;
import com.takumistudios.socialmod.common.model.PresenceStatus;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.common.net.SnapshotDto;
import com.takumistudios.socialmod.common.text.MessageFormatter;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.ChatMessage;
import com.takumistudios.socialmod.server.data.Conversation;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import com.takumistudios.socialmod.server.storage.SocialStorage;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Estado social completo para los clientes con el mod. Los envíos se agrupan: varias modificaciones en el mismo tick
 * producen un solo paquete por jugador al final del tick.
 */
public final class SnapshotService {
    private final SocialServer social;
    private final Set<UUID> pending = new LinkedHashSet<>();

    public SnapshotService(SocialServer social) {
        this.social = social;
    }

    /** Programa el envío para el final del tick. */
    public void send(ServerPlayer player) {
        if (social.hasMod(player.getUUID())) {
            pending.add(player.getUUID());
        }
    }

    public void sendLater(ServerPlayer player) {
        send(player);
    }

    public void tick() {
        if (pending.isEmpty()) {
            return;
        }
        Set<UUID> batch = new LinkedHashSet<>(pending);
        pending.clear();
        for (UUID id : batch) {
            ServerPlayer player = social.online(id);
            if (player != null) {
                sendNow(player);
            }
        }
    }

    public void sendNow(ServerPlayer player) {
        if (!social.hasMod(player.getUUID())) {
            return;
        }
        String json = SocialStorage.gson().toJson(build(player));
        social.send(player, new Payloads.SnapshotS2C(json));
    }

    public SnapshotDto build(ServerPlayer player) {
        UUID self = player.getUUID();
        PlayerRecord record = social.record(player);
        SnapshotDto dto = new SnapshotDto();

        dto.teamAdmin = com.takumistudios.socialmod.server.PermissionBridge.isStaff(player, com.takumistudios.socialmod.server.PermissionBridge.TEAM_ADMIN);
        dto.visualAdmin = com.takumistudios.socialmod.server.PermissionBridge.isStaff(player, "admin.visuals");
        dto.maxTeams = ServerConfig.get().maxTeams;
        dto.visual = social.visuals().design();
        dto.self.teamId = record.teamId; dto.self.teamChosen = record.teamChosen;
        for (Group team : social.teams().all()) {
            if (team.archived && !dto.teamAdmin) continue;
            SnapshotDto.TeamView view = new SnapshotDto.TeamView();
            view.id = team.id; view.name = team.name; view.color = team.color; view.icon = team.icon;
            view.archived = team.archived; view.members = team.members.size(); dto.teams.add(view);
        }
        dto.self.uuid = self.toString();
        dto.self.name = record.name;
        dto.self.status = record.status.id();
        dto.self.customStatus = record.customStatus;
        dto.self.whoCanMessage = record.whoCanMessage.id();
        dto.self.whoSeesStatus = record.whoSeesStatus.id();
        dto.self.showDimension = record.showDimension;
        dto.self.readReceipts = record.readReceipts;
        dto.self.typingIndicator = record.typingIndicator;
        Group main = social.groups().mainGroup(self);
        dto.self.mainGroup = main == null ? "" : main.id;
        dto.self.mutedUntil = record.isMuted(System.currentTimeMillis()) ? record.mutedUntil : 0;

        for (UUID friendId : record.friends) {
            PlayerRecord friend = social.storage().player(friendId);
            if (friend == null) {
                continue;
            }
            SnapshotDto.Friend view = new SnapshotDto.Friend();
            view.uuid = friendId.toString();
            view.name = friend.name;
            view.favorite = record.favorites.contains(friendId);
            view.note = record.notes.getOrDefault(friendId, "");
            Payloads.PresenceEntry presence = social.presence().entryFor(self, friendId);
            view.status = PresenceStatus.byOrdinal(presence.status()).id();
            view.customStatus = presence.customStatus();
            view.dimension = presence.dimension();
            view.lastSeen = social.presence().canSeeDetails(self, friendId) ? friend.lastSeen : 0;
            dto.friends.add(view);
        }
        record.incomingRequests.forEach(id -> dto.incoming.add(new SnapshotDto.NameRef(id.toString(), social.storage().nameOf(id))));
        record.outgoingRequests.forEach(id -> dto.outgoing.add(new SnapshotDto.NameRef(id.toString(), social.storage().nameOf(id))));
        record.blocked.forEach(id -> dto.blocked.add(new SnapshotDto.NameRef(id.toString(), social.storage().nameOf(id))));

        var permissions = ServerConfig.get().rolePermissions();
        for (Group group : social.groups().groupsOf(self)) {
            SnapshotDto.GroupView view = new SnapshotDto.GroupView();
            view.id = group.id;
            view.name = group.name;
            view.tag = group.tag;
            view.color = group.color;
            view.icon = group.icon;
            view.description = group.description;
            view.motd = group.motd;
            view.pinned = group.pinned;
            view.party = group.party;
            view.myRole = group.archived ? "member" : group.roleOf(self).id();
            for (GroupPermission permission : permissions.get(group.archived ? com.takumistudios.socialmod.common.model.Role.MEMBER : group.roleOf(self))) {
                if (!group.archived && (!group.team || !java.util.Set.of("invite", "kick", "manage_roles", "edit_info").contains(permission.id()))) view.myPermissions.add(permission.id());
            }
            group.members.forEach((id, role) -> {
                SnapshotDto.Member member = new SnapshotDto.Member();
                member.uuid = id.toString();
                member.name = group.memberNames.getOrDefault(id, social.storage().nameOf(id));
                member.role = role.id();
                PresenceStatus status = social.presence().visibleStatus(self, id);
                member.online = social.online(id) != null && status != PresenceStatus.OFFLINE;
                member.status = status.id();
                view.members.add(member);
            });
            for (Group.Channel channel : group.channels) {
                if (!social.groups().canAccess(group, self, channel.name)) {
                    continue;
                }
                SnapshotDto.ChannelView channelView = new SnapshotDto.ChannelView();
                channelView.name = channel.name;
                channelView.minRole = channel.minRole.id();
                channelView.canWrite = !group.archived;
                view.channels.add(channelView);
            }
            for (Group.GroupEvent event : group.events) {
                SnapshotDto.EventView eventView = new SnapshotDto.EventView();
                eventView.id = event.id;
                eventView.title = event.title;
                eventView.startsAt = event.startsAt;
                view.events.add(eventView);
            }
            dto.groups.add(view);
        }
        for (String groupId : record.groupInvites) {
            Group group = social.groups().get(groupId);
            if (group != null && group.invited.contains(self)) {
                SnapshotDto.Invite invite = new SnapshotDto.Invite();
                invite.groupId = group.id;
                invite.groupName = group.party ? "Party (" + group.name + ")" : group.name;
                invite.party = group.party;
                dto.groupInvites.add(invite);
            }
        }

        // Conversaciones: recientes + con mensajes sin leer
        Set<String> keys = new LinkedHashSet<>(record.recent);
        keys.addAll(record.unread.keySet());
        for (String key : keys) {
            ConversationId conversation = ConversationId.parse(key);
            if (conversation == null || !social.chat().canRead(self, conversation)) {
                continue;
            }
            SnapshotDto.ConversationView view = new SnapshotDto.ConversationView();
            view.id = key;
            view.title = social.chat().titleFor(self, conversation);
            view.unread = record.unread.getOrDefault(key, 0);
            Conversation cached = social.storage().cachedConversation(key);
            ChatMessage last = cached == null ? null : cached.last();
            if (last != null && !last.deleted) {
                view.preview = last.senderName + ": " + MessageFormatter.preview(last.text, social.chat().toView(last).attachments(), 40);
                view.lastTime = last.time;
            }
            dto.conversations.add(view);
        }
        dto.voice = social.voice().available();
        String voiceGroup = dto.voice ? social.voice().currentGroup(player.getUUID()) : null;
        dto.voiceGroup = voiceGroup == null ? "" : voiceGroup;
        return dto;
    }
}
