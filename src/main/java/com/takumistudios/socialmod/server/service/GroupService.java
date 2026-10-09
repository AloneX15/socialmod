package com.takumistudios.socialmod.server.service;

import com.takumistudios.socialmod.api.event.GroupEvents;
import com.takumistudios.socialmod.common.model.ConversationId;
import com.takumistudios.socialmod.common.model.GroupIcon;
import com.takumistudios.socialmod.common.model.GroupPermission;
import com.takumistudios.socialmod.common.model.Role;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.common.text.TextSanitizer;
import com.takumistudios.socialmod.server.PermissionBridge;
import com.takumistudios.socialmod.server.ServerApiImpl;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Grupos (clanes) persistentes y parties temporales (PLAN 5.2). Toda acción comprueba pertenencia, rol y permisos
 * del rol (configurables), límites y bloqueos. Las parties solo existen en memoria.
 */
public final class GroupService {
    public static final Pattern TAG = Pattern.compile("[A-Za-z0-9]{2,5}");
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long EVENT_WARNING_MILLIS = 5 * 60_000L;

    private final SocialServer social;
    private final NametagFallback nametags;
    private int ticks;

    public GroupService(SocialServer social) {
        this.social = social;
        this.nametags = new NametagFallback(social);
    }

    // ---------- Consultas ----------

    public Map<String, Group> all() {
        return social.storage().groups();
    }

    public @Nullable Group get(String id) {
        return id == null ? null : all().get(id);
    }

    /** Por id, nombre o etiqueta (sin distinguir mayúsculas). */
    public @Nullable Group find(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        Group byId = all().get(query.toLowerCase(Locale.ROOT));
        if (byId != null) {
            return byId;
        }
        for (Group group : all().values()) {
            if (!group.party && (group.name.equalsIgnoreCase(query) || group.tag.equalsIgnoreCase(query))) {
                return group;
            }
        }
        return null;
    }

    public List<Group> groupsOf(UUID player) {
        List<Group> result = new ArrayList<>();
        for (Group group : all().values()) {
            if (group.isMember(player) || (group.archived && group.archiveReaders.contains(player))) {
                result.add(group);
            }
        }
        return result;
    }

    public @Nullable Group partyOf(UUID player) {
        for (Group group : all().values()) {
            if (group.party && group.isMember(player)) {
                return group;
            }
        }
        return null;
    }

    /** Grupo principal (el que se muestra en el nametag y los placeholders). */
    public @Nullable Group mainGroup(UUID player) {
        PlayerRecord record = social.storage().player(player);
        if (record != null && !record.mainGroup.isEmpty()) {
            Group group = get(record.mainGroup);
            if (group != null && group.isMember(player) && !group.party) {
                return group;
            }
        }
        for (Group group : all().values()) {
            if (!group.party && group.isMember(player)) {
                return group;
            }
        }
        return null;
    }

    public boolean has(Group group, UUID player, GroupPermission permission) {
        Role role = group.roleOf(player);
        return role != null && ServerConfig.get().rolePermissions().get(role).contains(permission);
    }

    public boolean canAccess(Group group, UUID player, String channelName) {
        Role role = group.roleOf(player);
        if (group.archived) {
            Group.Channel archivedChannel = group.channel(channelName);
            Role archivedRole = group.archiveRoles.get(player);
            return archivedRole != null && archivedChannel != null && archivedRole.atLeast(archivedChannel.minRole);
        }
        Group.Channel channel = group.channel(channelName);
        return role != null && channel != null && role.atLeast(channel.minRole);
    }

    public String title(Group group, String channel) {
        return group.party ? "Party" : group.name + " #" + channel;
    }

    // ---------- Validación común ----------

    private @Nullable Group member(ServerPlayer actor, String groupId) {
        Group group = get(groupId);
        if (group == null || group.archived || !group.isMember(actor.getUUID())) {
            social.notifier().feedback(actor, false, "socialmod.group.not_member");
            return null;
        }
        return group;
    }

    private @Nullable Group withPermission(ServerPlayer actor, String groupId, GroupPermission permission) {
        Group managed = get(groupId);
        if (managed != null && managed.team && !managed.archived && PermissionBridge.isStaff(actor, PermissionBridge.TEAM_ADMIN)) return managed;
        Group group = member(actor, groupId);
        if (group != null && !has(group, actor.getUUID(), permission)) {
            social.notifier().feedback(actor, false, "socialmod.group.no_permission");
            return null;
        }
        return group;
    }

    private String newId() {
        String id;
        do {
            StringBuilder builder = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            id = builder.toString();
        } while (all().containsKey(id));
        return id;
    }

    // ---------- Crear, invitar, unirse, salir ----------

    public @Nullable Group create(ServerPlayer actor, String rawName, String rawTag) {
        return create(actor, rawName, rawTag, null, null);
    }

    /** Con icono y color opcionales (pantalla de crear grupo); los valores no válidos se ignoran. */
    public @Nullable Group create(ServerPlayer actor, String rawName, String rawTag, @Nullable String rawIcon, @Nullable String rawColor) {
        ServerConfig config = ServerConfig.get();
        if (!config.modules.groups) {
            social.notifier().feedback(actor, false, "socialmod.error.module_disabled");
            return null;
        }
        if (!PermissionBridge.allows(actor, PermissionBridge.GROUP_CREATE)) {
            social.notifier().feedback(actor, false, "socialmod.error.no_permission");
            return null;
        }
        String name = TextSanitizer.cleanName(rawName, 24);
        String tag = rawTag.trim();
        if (name.length() < 3) {
            social.notifier().feedback(actor, false, "socialmod.group.bad_name");
            return null;
        }
        if (!TAG.matcher(tag).matches()) {
            social.notifier().feedback(actor, false, "socialmod.group.bad_tag");
            return null;
        }
        for (Group other : all().values()) {
            if (!other.party && (other.name.equalsIgnoreCase(name) || other.tag.equalsIgnoreCase(tag))) {
                social.notifier().feedback(actor, false, "socialmod.group.taken");
                return null;
            }
        }
        int max = PermissionBridge.limit(actor, PermissionBridge.LIMIT_GROUPS, config.limits.maxGroupsPerPlayer);
        long owned = groupsOf(actor.getUUID()).stream().filter(g -> !g.party && !g.team).count();
        if (owned >= max) {
            social.notifier().feedback(actor, false, "socialmod.error.limit_groups", max);
            return null;
        }
        Group group = new Group();
        group.id = newId();
        group.name = name;
        group.tag = tag.toUpperCase(Locale.ROOT);
        group.created = System.currentTimeMillis();
        group.color = 0x55FF55;
        Integer chosenColor = rawColor == null || rawColor.isBlank() ? null : parseColor(rawColor);
        if (chosenColor != null) {
            group.color = chosenColor;
        }
        GroupIcon chosenIcon = rawIcon == null ? null : GroupIcon.byId(rawIcon);
        if (chosenIcon != null) {
            group.icon = chosenIcon.id();
        }
        group.members.put(actor.getUUID(), Role.LEADER);
        group.memberNames.put(actor.getUUID(), social.record(actor).name);
        for (String channel : config.defaultChannels) {
            group.channels.add(new Group.Channel(channel, Role.RECRUIT));
        }
        group.normalize();
        all().put(group.id, group);
        PlayerRecord record = social.record(actor);
        if (record.mainGroup.isEmpty()) {
            record.mainGroup = group.id;
            social.storage().markPlayersDirty();
        }
        social.storage().markGroupsDirty();
        social.storage().audit("GROUP_CREATE " + record.name + " " + group.id + " '" + group.name + "' [" + group.tag + "]");
        social.notifier().feedback(actor, true, "socialmod.group.created", group.name, group.tag);
        GroupEvents.CREATED.invoker().onGroup(ServerApiImpl.info(group));
        refresh(group);
        nametags.update(actor.getUUID());
        return group;
    }

    public @Nullable Group createParty(ServerPlayer actor) {
        if (!ServerConfig.get().modules.parties) {
            social.notifier().feedback(actor, false, "socialmod.error.module_disabled");
            return null;
        }
        if (!PermissionBridge.allows(actor, PermissionBridge.PARTY_CREATE)) {
            social.notifier().feedback(actor, false, "socialmod.error.no_permission");
            return null;
        }
        Group existing = partyOf(actor.getUUID());
        if (existing != null) {
            social.notifier().feedback(actor, false, "socialmod.party.already");
            return existing;
        }
        Group party = new Group();
        party.id = newId();
        party.party = true;
        party.name = social.record(actor).name;
        party.tag = "P";
        party.color = 0x5555FF;
        party.icon = "none";
        party.created = System.currentTimeMillis();
        party.members.put(actor.getUUID(), Role.LEADER);
        party.memberNames.put(actor.getUUID(), social.record(actor).name);
        party.channels.add(new Group.Channel("party", Role.RECRUIT));
        all().put(party.id, party);
        social.notifier().feedback(actor, true, "socialmod.party.created");
        GroupEvents.CREATED.invoker().onGroup(ServerApiImpl.info(party));
        refresh(party);
        return party;
    }

    public boolean invite(ServerPlayer actor, String groupId, String targetName) {
        Group group = withPermission(actor, groupId, GroupPermission.INVITE);
        if (group != null && group.team) return false;
        if (group == null) return false;
        PlayerRecord target = social.friends().resolve(targetName);
        if (target == null) {
            social.notifier().feedback(actor, false, "socialmod.error.unknown_player", targetName);
            return false;
        }
        if (group.isMember(target.id)) {
            social.notifier().feedback(actor, false, "socialmod.group.already_member", target.name);
            return false;
        }
        int max = group.party ? ServerConfig.get().limits.maxPartySize : ServerConfig.get().limits.maxMembersPerGroup;
        if (group.members.size() + group.invited.size() >= max + 10 || group.members.size() >= max) {
            social.notifier().feedback(actor, false, "socialmod.group.full");
            return false;
        }
        if (group.party && social.online(target.id) == null) {
            social.notifier().feedback(actor, false, "socialmod.error.offline", target.name);
            return false;
        }
        social.notifier().feedback(actor, true, "socialmod.group.invited", target.name);
        // Bloqueo: la invitación se descarta en silencio (no se revela el bloqueo)
        if (social.friends().blockedEitherWay(actor.getUUID(), target.id)) {
            return true;
        }
        group.invited.add(target.id);
        target.groupInvites.add(group.id);
        social.storage().markPlayersDirty();
        social.storage().markGroupsDirty();
        ServerPlayer online = social.online(target.id);
        if (online != null) {
            social.notifier().notify(online, Payloads.NotifyKind.INVITE, actor.getUUID(),
                    group.party ? "socialmod.notify.party_invite" : "socialmod.notify.group_invite",
                    new Object[]{social.record(actor).name, group.name}, "", "");
            if (!social.hasMod(online.getUUID())) {
                online.sendSystemMessage(Component.translatableWithFallback("socialmod.group.invite_hint",
                        "Use /g accept %s to join", group.party ? "party" : group.tag).withStyle(ChatFormatting.GRAY));
            }
            social.snapshots().send(online);
        }
        return true;
    }

    public boolean accept(ServerPlayer actor, String query) {
        PlayerRecord record = social.record(actor);
        Group group = "party".equalsIgnoreCase(query) ? pendingParty(record) : find(query);
        if (group != null && group.team) return false;
        if (group == null || !group.invited.contains(actor.getUUID())) {
            social.notifier().feedback(actor, false, "socialmod.group.no_invite");
            return false;
        }
        int max = group.party ? ServerConfig.get().limits.maxPartySize : ServerConfig.get().limits.maxMembersPerGroup;
        if (group.members.size() >= max) {
            social.notifier().feedback(actor, false, "socialmod.group.full");
            return false;
        }
        if (group.party) {
            Group current = partyOf(actor.getUUID());
            if (current != null) {
                leave(actor, current.id);
            }
        } else {
            int maxGroups = PermissionBridge.limit(actor, PermissionBridge.LIMIT_GROUPS, ServerConfig.get().limits.maxGroupsPerPlayer);
            long count = groupsOf(actor.getUUID()).stream().filter(g -> !g.party && !g.team).count();
            if (count >= maxGroups) {
                social.notifier().feedback(actor, false, "socialmod.error.limit_groups", maxGroups);
                return false;
            }
        }
        group.invited.remove(actor.getUUID());
        record.groupInvites.remove(group.id);
        group.members.put(actor.getUUID(), group.party ? Role.MEMBER : Role.RECRUIT);
        group.memberNames.put(actor.getUUID(), record.name);
        if (!group.party && record.mainGroup.isEmpty()) {
            record.mainGroup = group.id;
        }
        social.storage().markPlayersDirty();
        social.storage().markGroupsDirty();
        social.storage().audit("GROUP_JOIN " + record.name + " " + group.id);
        GroupEvents.MEMBER_JOINED.invoker().onMemberChanged(ServerApiImpl.info(group), actor.getUUID());
        announce(group, "socialmod.group.joined", record.name);
        refresh(group);
        nametags.update(actor.getUUID());
        return true;
    }

    private @Nullable Group pendingParty(PlayerRecord record) {
        for (String id : record.groupInvites) {
            Group group = get(id);
            if (group != null && group.party) {
                return group;
            }
        }
        return null;
    }

    public boolean decline(ServerPlayer actor, String query) {
        PlayerRecord record = social.record(actor);
        Group group = "party".equalsIgnoreCase(query) ? pendingParty(record) : find(query);
        if (group != null && group.team) return false;
        if (group == null || !group.invited.remove(actor.getUUID())) {
            record.groupInvites.remove(query);
            social.notifier().feedback(actor, false, "socialmod.group.no_invite");
            return false;
        }
        record.groupInvites.remove(group.id);
        social.storage().markPlayersDirty();
        social.storage().markGroupsDirty();
        social.notifier().feedback(actor, true, "socialmod.group.declined", group.name);
        social.snapshots().send(actor);
        return true;
    }

    public boolean leave(ServerPlayer actor, String groupId) {
        Group group = member(actor, groupId);
        if (group != null && group.team) return false;
        if (group == null) return false;
        removeMember(group, actor.getUUID(), "socialmod.group.left");
        social.notifier().feedback(actor, true, "socialmod.group.you_left", group.name);
        social.snapshots().send(actor);
        return true;
    }

    /** Saca a un miembro. Si era el líder, el grupo pasa al de mayor rango; si queda vacío, se disuelve. */
    public void removeMember(Group group, UUID player, String announceKey) {
        Role role = group.members.remove(player);
        String name = group.memberNames.remove(player);
        if (role == null) {
            return;
        }
        PlayerRecord record = social.storage().player(player);
        if (record != null && record.mainGroup.equals(group.id)) {
            record.mainGroup = "";
            social.storage().markPlayersDirty();
        }
        GroupEvents.MEMBER_LEFT.invoker().onMemberChanged(ServerApiImpl.info(group), player);
        social.voice().onLeftGroup(player, group);
        if (group.members.isEmpty()) {
            disbandInternal(group, null);
            return;
        }
        if (role == Role.LEADER) {
            UUID heir = group.members.entrySet().stream()
                    .min((a, b) -> Integer.compare(a.getValue().rank(), b.getValue().rank()))
                    .map(Map.Entry::getKey).orElse(null);
            if (heir != null) {
                group.members.put(heir, Role.LEADER);
                announce(group, "socialmod.group.new_leader", group.memberNames.getOrDefault(heir, "?"));
                nametags.update(heir);
            }
        }
        social.storage().markGroupsDirty();
        social.storage().audit("GROUP_LEAVE " + name + " " + group.id);
        announce(group, announceKey, name == null ? "?" : name);
        refresh(group);
        nametags.update(player);
    }

    /** Alguien entró al grupo desde fuera de SocialMod (sincronización con claims): mismos avisos que al aceptar. */
    public void afterExternalJoin(Group group, UUID player, String name) {
        PlayerRecord record = social.storage().player(player);
        if (record != null && record.mainGroup.isEmpty()) {
            record.mainGroup = group.id;
            social.storage().markPlayersDirty();
        }
        social.storage().markGroupsDirty();
        social.storage().audit("GROUP_JOIN " + name + " " + group.id + " (claims)");
        GroupEvents.MEMBER_JOINED.invoker().onMemberChanged(ServerApiImpl.info(group), player);
        announce(group, "socialmod.group.joined", name);
        refresh(group);
        nametags.update(player);
    }

    /** {@code /g claims link|unlink}: solo el líder, y solo si la sincronización está activada en el servidor. */
    public boolean setClaimsLink(ServerPlayer actor, String groupId, boolean link) {
        Group group = member(actor, groupId);
        if (group == null || group.party || group.team) return false;
        if (group.roleOf(actor.getUUID()) != Role.LEADER) {
            social.notifier().feedback(actor, false, "socialmod.group.no_permission");
            return false;
        }
        if (!social.claims().available() || ServerConfig.get().integrations.claimsSync.equals("off")) {
            social.notifier().feedback(actor, false, "socialmod.claims.unavailable");
            return false;
        }
        group.claimsLink = link;
        social.storage().markGroupsDirty();
        social.storage().audit("CLAIMS_LINK " + social.record(actor).name + " " + group.id + " " + link);
        social.notifier().feedback(actor, true, link ? "socialmod.claims.linked" : "socialmod.claims.unlinked", group.name);
        if (link) {
            social.claims().syncAll();
        }
        return true;
    }

    public boolean kick(ServerPlayer actor, String groupId, String targetName) {
        Group group = withPermission(actor, groupId, GroupPermission.KICK);
        if (group != null && group.team) return false;
        if (group == null) return false;
        PlayerRecord target = social.friends().resolve(targetName);
        if (target == null || !group.isMember(target.id)) {
            social.notifier().feedback(actor, false, "socialmod.group.not_in_group", targetName);
            return false;
        }
        if (!group.roleOf(actor.getUUID()).outranks(group.roleOf(target.id))) {
            social.notifier().feedback(actor, false, "socialmod.group.rank_too_low");
            return false;
        }
        removeMember(group, target.id, "socialmod.group.kicked");
        ServerPlayer online = social.online(target.id);
        if (online != null) {
            social.notifier().feedback(online, false, "socialmod.group.you_were_kicked", group.name);
            social.snapshots().send(online);
        }
        return true;
    }

    public boolean changeRole(ServerPlayer actor, String groupId, String targetName, boolean promote) {
        Group group = withPermission(actor, groupId, GroupPermission.MANAGE_ROLES);
        if (group != null && group.team) return false;
        if (group == null) return false;
        PlayerRecord target = social.friends().resolve(targetName);
        if (target == null || !group.isMember(target.id) || group.party) {
            social.notifier().feedback(actor, false, "socialmod.group.not_in_group", targetName);
            return false;
        }
        Role actorRole = group.roleOf(actor.getUUID());
        Role current = group.roleOf(target.id);
        Role next = promote ? current.promoted() : current.demoted();
        // Nadie puede dar un rango igual o superior al suyo, salvo el líder
        if (!actorRole.outranks(current) || (actorRole != Role.LEADER && !actorRole.outranks(next)) || next == current) {
            social.notifier().feedback(actor, false, "socialmod.group.rank_too_low");
            return false;
        }
        group.members.put(target.id, next);
        social.storage().markGroupsDirty();
        social.storage().audit("GROUP_ROLE " + social.record(actor).name + " " + target.name + " " + current.id() + "->" + next.id() + " " + group.id);
        announce(group, "socialmod.group.role_changed", target.name, next.id());
        nametags.update(target.id);
        refresh(group);
        return true;
    }

    public boolean transfer(ServerPlayer actor, String groupId, String targetName) {
        Group group = member(actor, groupId);
        if (group != null && group.team) return false;
        if (group == null) return false;
        if (group.roleOf(actor.getUUID()) != Role.LEADER) {
            social.notifier().feedback(actor, false, "socialmod.group.no_permission");
            return false;
        }
        PlayerRecord target = social.friends().resolve(targetName);
        if (target == null || !group.isMember(target.id) || target.id.equals(actor.getUUID())) {
            social.notifier().feedback(actor, false, "socialmod.group.not_in_group", targetName);
            return false;
        }
        group.members.put(actor.getUUID(), group.party ? Role.MEMBER : Role.OFFICER);
        group.members.put(target.id, Role.LEADER);
        social.storage().markGroupsDirty();
        social.storage().audit("GROUP_TRANSFER " + social.record(actor).name + " -> " + target.name + " " + group.id);
        announce(group, "socialmod.group.new_leader", target.name);
        nametags.update(actor.getUUID());
        nametags.update(target.id);
        refresh(group);
        return true;
    }

    public boolean setMain(ServerPlayer actor, String groupId) {
        Group group = member(actor, groupId);
        if (group == null || group.party || group.team) return false;
        social.record(actor).mainGroup = group.id;
        social.storage().markPlayersDirty();
        social.notifier().feedback(actor, true, "socialmod.group.main_set", group.name);
        social.snapshots().send(actor);
        social.presence().markChanged(actor.getUUID());
        nametags.update(actor.getUUID());
        return true;
    }

    // ---------- Información del grupo ----------

    public boolean setText(ServerPlayer actor, String groupId, String field, String value) {
        GroupPermission permission = field.equals("pinned") ? GroupPermission.PIN : GroupPermission.EDIT_INFO;
        Group group = withPermission(actor, groupId, permission);
        if (group != null && group.team && (field.equals("color") || field.equals("tag") || field.equals("icon"))) return false;
        if (group == null) return false;
        switch (field) {
            case "motd" -> group.motd = TextSanitizer.clean(value, 128);
            case "description" -> group.description = TextSanitizer.clean(value, 256);
            case "pinned" -> group.pinned = TextSanitizer.clean(value, 200);
            case "tag" -> {
                String tag = value.trim();
                if (!TAG.matcher(tag).matches() || group.party) {
                    social.notifier().feedback(actor, false, "socialmod.group.bad_tag");
                    return false;
                }
                for (Group other : all().values()) {
                    if (other != group && !other.party && other.tag.equalsIgnoreCase(tag)) {
                        social.notifier().feedback(actor, false, "socialmod.group.taken");
                        return false;
                    }
                }
                group.tag = tag.toUpperCase(Locale.ROOT);
                group.members.keySet().forEach(nametags::update);
            }
            case "color" -> {
                Integer color = parseColor(value);
                if (color == null) {
                    social.notifier().feedback(actor, false, "socialmod.group.bad_color");
                    return false;
                }
                group.color = color;
                group.members.keySet().forEach(nametags::update);
            }
            case "icon" -> {
                GroupIcon icon = GroupIcon.byId(value);
                if (icon == null) {
                    social.notifier().feedback(actor, false, "socialmod.group.bad_icon");
                    return false;
                }
                group.icon = icon.id();
                group.members.keySet().forEach(nametags::update);
            }
            default -> {
                return false;
            }
        }
        social.storage().markGroupsDirty();
        social.storage().audit("GROUP_EDIT " + social.record(actor).name + " " + group.id + " " + field);
        if (field.equals("motd") && !group.motd.isEmpty()) {
            announce(group, "socialmod.group.motd", group.motd);
        } else if (field.equals("pinned") && !group.pinned.isEmpty()) {
            announce(group, "socialmod.group.pinned", group.pinned);
        }
        refresh(group);
        return true;
    }

    static @Nullable Integer parseColor(String value) {
        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.matches("[0-9A-Fa-f]{6}")) {
            return Integer.parseInt(hex, 16);
        }
        return net.minecraft.network.chat.TextColor.parseColor(value.trim().toLowerCase(Locale.ROOT)).result()
                .map(net.minecraft.network.chat.TextColor::getValue).orElse(null);
    }

    public boolean createChannel(ServerPlayer actor, String groupId, String rawName, String minRole) {
        Group group = withPermission(actor, groupId, GroupPermission.MANAGE_CHANNELS);
        if (group == null || group.party || group.team) return false;
        String name = rawName.trim().toLowerCase(Locale.ROOT);
        if (!ConversationId.CHANNEL.matcher(name).matches()) {
            social.notifier().feedback(actor, false, "socialmod.group.bad_channel");
            return false;
        }
        if (group.channel(name) != null) {
            social.notifier().feedback(actor, false, "socialmod.group.channel_exists");
            return false;
        }
        if (group.channels.size() >= ServerConfig.get().limits.maxChannelsPerGroup) {
            social.notifier().feedback(actor, false, "socialmod.group.channel_limit");
            return false;
        }
        Role role = minRole == null || minRole.isEmpty() ? Role.RECRUIT : Role.byId(minRole);
        if (role == Role.VIP) return false;
        if (role == null) role = Role.RECRUIT;
        group.channels.add(new Group.Channel(name, role));
        social.storage().markGroupsDirty();
        social.storage().audit("CHANNEL_CREATE " + social.record(actor).name + " " + group.id + " #" + name);
        social.notifier().feedback(actor, true, "socialmod.group.channel_created", name);
        refresh(group);
        return true;
    }

    public boolean deleteChannel(ServerPlayer actor, String groupId, String name) {
        Group group = withPermission(actor, groupId, GroupPermission.MANAGE_CHANNELS);
        if (group == null || group.party || group.team) return false;
        Group.Channel channel = group.channel(name);
        if (channel == null || group.channels.size() <= 1) {
            social.notifier().feedback(actor, false, "socialmod.group.channel_cannot_delete");
            return false;
        }
        group.channels.remove(channel);
        social.storage().deleteConversation(ConversationId.group(group.id, channel.name).key());
        social.storage().markGroupsDirty();
        social.storage().audit("CHANNEL_DELETE " + social.record(actor).name + " " + group.id + " #" + channel.name);
        social.notifier().feedback(actor, true, "socialmod.group.channel_deleted", channel.name);
        refresh(group);
        return true;
    }

    public boolean disband(ServerPlayer actor, String groupId) {
        Group group = member(actor, groupId);
        if (group != null && group.team) return false;
        if (group == null) return false;
        if (group.roleOf(actor.getUUID()) != Role.LEADER) {
            social.notifier().feedback(actor, false, "socialmod.group.no_permission");
            return false;
        }
        disbandInternal(group, social.record(actor).name);
        return true;
    }

    /** Disuelve un grupo (también desde moderación). Borra su historial. */
    public void disbandInternal(Group group, @Nullable String actor) {
        if (group.team) { social.teams().archive(group.id, actor == null ? "system" : actor); return; }
        all().remove(group.id);
        announce(group, "socialmod.group.disbanded", group.name);
        for (UUID member : group.members.keySet()) {
            PlayerRecord record = social.storage().player(member);
            if (record != null && record.mainGroup.equals(group.id)) {
                record.mainGroup = "";
            }
        }
        for (UUID invited : group.invited) {
            PlayerRecord record = social.storage().player(invited);
            if (record != null) {
                record.groupInvites.remove(group.id);
            }
        }
        if (!group.party) {
            for (Group.Channel channel : group.channels) {
                social.storage().deleteConversation(ConversationId.group(group.id, channel.name).key());
            }
        }
        social.storage().markPlayersDirty();
        social.storage().markGroupsDirty();
        social.storage().audit("GROUP_DISBAND " + (actor == null ? "-" : actor) + " " + group.id + " '" + group.name + "'");
        GroupEvents.DISBANDED.invoker().onGroup(ServerApiImpl.info(group));
        social.voice().onDisband(group);
        for (UUID member : group.members.keySet()) {
            ServerPlayer online = social.online(member);
            if (online != null) {
                social.snapshots().send(online);
            }
            nametags.update(member);
        }
        group.members.clear();
    }

    // ---------- Eventos del grupo ----------

    public boolean createEvent(ServerPlayer actor, String groupId, int minutesFromNow, String rawTitle) {
        Group group = withPermission(actor, groupId, GroupPermission.MANAGE_EVENTS);
        if (group == null) return false;
        String title = TextSanitizer.clean(rawTitle, 64);
        if (title.isEmpty() || minutesFromNow < 1 || minutesFromNow > 60 * 24 * 60) {
            social.notifier().feedback(actor, false, "socialmod.event.invalid");
            return false;
        }
        if (group.events.size() >= ServerConfig.get().limits.maxEventsPerGroup) {
            social.notifier().feedback(actor, false, "socialmod.event.limit");
            return false;
        }
        long startsAt = System.currentTimeMillis() + minutesFromNow * 60_000L;
        group.events.add(new Group.GroupEvent(newId(), title, startsAt, actor.getUUID()));
        group.events.sort((a, b) -> Long.compare(a.startsAt, b.startsAt));
        social.storage().markGroupsDirty();
        announce(group, "socialmod.event.created", title, minutesFromNow);
        refresh(group);
        return true;
    }

    public boolean deleteEvent(ServerPlayer actor, String groupId, String eventId) {
        Group group = withPermission(actor, groupId, GroupPermission.MANAGE_EVENTS);
        if (group == null) return false;
        boolean removed = group.events.removeIf(e -> e.id.equals(eventId));
        if (removed) {
            social.storage().markGroupsDirty();
            refresh(group);
        }
        return removed;
    }

    // ---------- Ciclo de vida ----------

    public void restoreAfterLoad() {
        // Invitaciones a grupos que ya no existen
        for (PlayerRecord record : social.storage().players()) {
            record.groupInvites.removeIf(id -> get(id) == null);
        }
    }

    public void onJoin(ServerPlayer player) {
        for (Group group : groupsOf(player.getUUID())) {
            group.memberNames.put(player.getUUID(), social.record(player).name);
            if (!group.motd.isEmpty()) {
                player.sendSystemMessage(Component.literal("[" + group.tag + "] ").withColor(group.color)
                        .append(Component.literal(group.motd).withStyle(ChatFormatting.WHITE)));
            }
            for (UUID member : group.members.keySet()) {
                if (!member.equals(player.getUUID())) {
                    ServerPlayer online = social.online(member);
                    if (online != null) {
                        social.snapshots().sendLater(online);
                    }
                }
            }
        }
        nametags.update(player.getUUID());
    }

    public void onLeave(ServerPlayer player) {
        UUID id = player.getUUID();
        Group party = partyOf(id);
        if (party != null) {
            // La party desaparece cuando no queda ningún miembro conectado
            social.server().execute(() -> {
                boolean anyOnline = party.members.keySet().stream().anyMatch(m -> social.online(m) != null);
                if (!anyOnline && all().containsKey(party.id)) {
                    disbandInternal(party, null);
                } else if (all().containsKey(party.id)) {
                    removeMember(party, id, "socialmod.group.left");
                }
            });
        }
        for (Group group : groupsOf(id)) {
            for (UUID member : group.members.keySet()) {
                ServerPlayer online = social.online(member);
                if (online != null && !member.equals(id)) {
                    social.snapshots().sendLater(online);
                }
            }
        }
    }

    public void tick() {
        if (++ticks % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Group group : List.copyOf(all().values())) {
            boolean changed = false;
            for (Group.GroupEvent event : group.events) {
                if (!event.warned && now >= event.startsAt - EVENT_WARNING_MILLIS && now < event.startsAt) {
                    event.warned = true;
                    changed = true;
                    notifyMembers(group, "socialmod.event.soon", event.title, Math.max(1, (event.startsAt - now) / 60_000));
                }
                if (!event.started && now >= event.startsAt) {
                    event.started = true;
                    event.warned = true;
                    changed = true;
                    notifyMembers(group, "socialmod.event.now", event.title, 0);
                }
            }
            // Los eventos se borran una hora después de empezar
            changed |= group.events.removeIf(e -> e.started && now - e.startsAt > 3_600_000L);
            if (changed) {
                social.storage().markGroupsDirty();
                refresh(group);
            }
        }
    }

    private void notifyMembers(Group group, String key, String title, long minutes) {
        for (UUID member : group.members.keySet()) {
            ServerPlayer online = social.online(member);
            if (online != null) {
                social.notifier().notify(online, Payloads.NotifyKind.EVENT, null, key, new Object[]{title, minutes}, group.name,
                        ConversationId.group(group.id, group.defaultChannel()).key());
            }
        }
    }

    /** Mensaje de sistema a los miembros conectados (entradas, salidas, cambios de rol...). */
    private void announce(Group group, String key, Object... args) {
        for (UUID member : group.members.keySet()) {
            ServerPlayer online = social.online(member);
            if (online != null) {
                online.sendSystemMessage(Component.literal("[" + (group.party ? "Party" : group.tag) + "] ").withColor(group.color)
                        .append(com.takumistudios.socialmod.server.Lang.tr(online, key, args).withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    /** Envía el estado actualizado a los miembros e invitados conectados y sincroniza la presencia entre ellos. */
    public void refresh(Group group) {
        List<UUID> targets = new ArrayList<>(group.members.keySet());
        targets.addAll(group.invited);
        for (UUID id : targets) {
            ServerPlayer online = social.online(id);
            if (online != null) {
                social.snapshots().send(online);
            }
        }
        for (UUID member : group.members.keySet()) {
            social.presence().markChanged(member);
        }
    }

    public NametagFallback nametags() {
        return nametags;
    }
}
