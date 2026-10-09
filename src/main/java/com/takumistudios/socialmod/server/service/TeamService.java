package com.takumistudios.socialmod.server.service;

import com.takumistudios.socialmod.common.model.GroupIcon;
import com.takumistudios.socialmod.common.model.Role;
import com.takumistudios.socialmod.common.net.SocialAction;
import com.takumistudios.socialmod.common.text.TextSanitizer;
import com.takumistudios.socialmod.server.PermissionBridge;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.UUID;

/** Server-thread-only TEAM transactions. The managed group is also the durable TEAM identity. */
public final class TeamService {
    private final SocialServer social;
    public TeamService(SocialServer social) { this.social = social; }
    public List<Group> all() {
        return social.groups().all().values().stream().filter(g -> g.team)
                .sorted(java.util.Comparator.comparing(g -> g.name)).toList();
    }
    public Group find(String query) {
        return all().stream().filter(g -> g.id.equals(query) || g.name.equalsIgnoreCase(query)).findFirst().orElse(null);
    }
    public Group of(UUID player) {
        PlayerRecord record = social.storage().player(player);
        Group team = record == null ? null : social.groups().get(record.teamId);
        return team != null && team.team && !team.archived && team.isMember(player) ? team : null;
    }
    public static boolean mayChoose(PlayerRecord record) { return !record.teamChosen && record.teamId.isEmpty(); }
    public boolean create(ServerPlayer player, String rawName, String style) {
        boolean admin = PermissionBridge.isStaff(player, PermissionBridge.TEAM_ADMIN);
        PlayerRecord record = social.record(player);
        if (!admin && !mayChoose(record)) return fail(player, "locked");
        if (!admin && !PermissionBridge.allows(player, PermissionBridge.TEAM_CREATE)) return fail(player, "create_permission");
        if (!ServerConfig.get().modules.groups) return fail(player, "disabled");
        String name = TextSanitizer.cleanName(rawName, 49);
        if (TextSanitizer.length(name) < 3 || TextSanitizer.length(name) > 48) return fail(player, "name_length");
        if (find(name) != null) return fail(player, "name_exists");
        if (all().stream().filter(g -> !g.archived).count() >= ServerConfig.get().maxTeams) return fail(player, "limit");
        String[] parts = style.split(";", -1);
        GroupIcon icon = GroupIcon.byId(parts.length > 0 ? parts[0] : "shield");
        Integer color = GroupService.parseColor(parts.length > 1 ? parts[1] : "#55FF55");
        if (icon == null) return fail(player, "bad_icon");
        if (color == null) return fail(player, "bad_color");
        com.takumistudios.socialmod.common.model.TeamBanner banner;
        try { banner = parts.length == 3 ? com.takumistudios.socialmod.common.model.TeamBanner.parse(parts[2]) : new com.takumistudios.socialmod.common.model.TeamBanner(); validateBanner(banner); }
        catch (RuntimeException e) { return fail(player, "bad_banner"); }
        if (parts.length > 3) return fail(player, "bad_style");
        Group team = new Group();
        do { team.id = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); team.tag = team.id.substring(0, 5).toUpperCase(java.util.Locale.ROOT); }
        while (social.groups().all().containsKey(team.id) || social.groups().all().values().stream().anyMatch(g -> g.tag.equalsIgnoreCase(team.tag)));
        team.team = true; team.name = name; team.tag = team.id.substring(0, 5).toUpperCase(java.util.Locale.ROOT);
        team.icon = icon.id(); team.color = color; team.created = System.currentTimeMillis(); team.normalize();
        team.banner = banner;
        social.groups().all().put(team.id, team);
        if (!admin || mayChoose(record)) { assign(record, team); team.members.put(record.id, Role.LEADER); }
        changed("CREATE " + record.name + " " + team.id);
        return true;
    }
    public boolean choose(ServerPlayer player, String id) {
        Group team = find(id); PlayerRecord record = social.record(player);
        if (!mayChoose(record)) return fail(player, "locked");
        if (id.isBlank()) return fail(player, "select_team");
        if (team == null) return fail(player, "not_found");
        if (team.archived) return fail(player, "archived");
        assign(record, team); changed("CHOOSE " + record.name + " " + team.id); return true;
    }
    private void assign(PlayerRecord record, Group team) {
        Group old = of(record.id);
        if (old != null && old == team) return;
        ServerPlayer online = social.online(record.id);
        if (online != null && old != null && old.id.equals(social.voice().currentGroup(record.id))) social.voice().leave(online);
        if (old != null) {
            old.members.remove(record.id); old.memberNames.remove(record.id);
            if (!old.members.isEmpty() && old.leader() == null) old.members.put(old.members.keySet().iterator().next(), Role.LEADER);
        }
        record.teamId = team == null ? "" : team.id; record.teamChosen = team != null;
        if (team != null) {
            team.members.put(record.id, team.members.isEmpty() ? Role.LEADER : Role.MEMBER); team.memberNames.put(record.id, record.name);
            record.touch(com.takumistudios.socialmod.common.model.ConversationId.group(team.id, team.defaultChannel()).key());
        }
        social.groups().nametags().update(record.id);
    }
    public boolean archive(String id, String actor) {
        Group team = find(id); if (team == null || team.archived) return false;
        team.archiveReaders.clear(); team.archiveReaders.addAll(team.members.keySet());
        team.archiveRoles.clear(); team.archiveRoles.putAll(team.members);
        for (PlayerRecord record : social.storage().players()) if (record.teamId.equals(team.id)) assign(record, null);
        team.members.clear(); team.memberNames.clear(); team.archived = true;
        changed("ARCHIVE " + actor + " " + team.id); return true;
    }
    public record AdminResult(boolean success, String errorKey) {
        private static AdminResult ok() { return new AdminResult(true, ""); }
        private static AdminResult failure(String reason) { return new AdminResult(false, "socialmod.team." + reason); }
    }
    public boolean admin(SocialAction action, String a, String b, String actor) {
        return adminResult(action, a, b, actor).success();
    }
    /** Same transaction for GUI and commands, with a precise rejection reason. */
    public AdminResult adminResult(SocialAction action, String a, String b, String actor) {
        Group team = find(a);
        if (action != SocialAction.TEAM_ASSIGN && action != SocialAction.TEAM_RESET) {
            if (a.isBlank()) return AdminResult.failure("select_team");
            if (team == null) return AdminResult.failure("not_found");
        }
        switch (action) {
            case TEAM_ARCHIVE -> {
                if (team.archived) return AdminResult.failure("already_archived");
                return archive(a, actor) ? AdminResult.ok() : AdminResult.failure("not_found");
            }
            case TEAM_RESTORE -> {
                if (!team.archived) return AdminResult.failure("not_archived");
                if (all().stream().filter(g -> !g.archived).count() >= ServerConfig.get().maxTeams) return AdminResult.failure("limit");
                team.archived = false;
            }
            case TEAM_STYLE -> {
                String[] style = b.split(";", -1);
                if (style.length != 2) return AdminResult.failure("bad_style");
                GroupIcon icon = GroupIcon.byId(style[0]); Integer color = GroupService.parseColor(style[1]);
                if (icon == null) return AdminResult.failure("bad_icon");
                if (color == null) return AdminResult.failure("bad_color");
                team.icon = icon.id(); team.color = color; team.members.keySet().forEach(social.groups().nametags()::update);
            }
            case TEAM_RENAME -> {
                String name = TextSanitizer.cleanName(b, 49);
                Group other = find(name);
                if (TextSanitizer.length(name) < 3 || TextSanitizer.length(name) > 48) return AdminResult.failure("name_length");
                if (other != null && other != team) return AdminResult.failure("name_exists");
                team.name = name; team.members.keySet().forEach(social.groups().nametags()::update);
            }
            case TEAM_ASSIGN, TEAM_RESET -> {
                if (a.isBlank()) return AdminResult.failure("select_player");
                PlayerRecord record = social.storage().findByName(a.trim());
                if (record == null) { try { record = social.storage().player(UUID.fromString(a.trim())); } catch (IllegalArgumentException e) { return AdminResult.failure("player_not_found"); } }
                Group target = action == SocialAction.TEAM_RESET ? null : find(b);
                if (record == null) return AdminResult.failure("player_not_found");
                if (action == SocialAction.TEAM_ASSIGN) {
                    if (b.isBlank()) return AdminResult.failure("select_team");
                    if (target == null) return AdminResult.failure("not_found");
                    if (target.archived) return AdminResult.failure("archived");
                }
                assign(record, target);
            }
            default -> { return AdminResult.failure("invalid_action"); }
        }
        changed(action + " " + actor + " " + a); return AdminResult.ok();
    }
    private boolean fail(ServerPlayer player, String reason) {
        social.notifier().feedback(player, false, "socialmod.team." + reason); return false;
    }
    private void validateBanner(com.takumistudios.socialmod.common.model.TeamBanner banner) {
        banner.validate();
        var registry = social.server().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN);
        for (var layer : banner.layers) if (registry.getOptional(net.minecraft.resources.Identifier.parse(layer.pattern())).isEmpty()) throw new IllegalArgumentException("Unknown pattern");
    }
    public boolean banner(ServerPlayer player, String id, String json) {
        Group team = find(id);
        if (id.isBlank()) return fail(player, "select_team");
        if (team == null) return fail(player, "not_found");
        if (team.archived) return fail(player, "archived");
        if (!(PermissionBridge.isStaff(player, PermissionBridge.TEAM_ADMIN) || player.getUUID().equals(team.leader()))) return fail(player, "banner_permission");
        try { var value = com.takumistudios.socialmod.common.model.TeamBanner.parse(json); validateBanner(value); team.banner = value; }
        catch (RuntimeException e) { return fail(player, "bad_banner"); }
        changed("BANNER " + player.getGameProfile().name() + " " + team.id); return true;
    }
    private void changed(String event) {
        social.storage().markPlayersDirty(); social.storage().markGroupsDirty(); social.storage().audit("TEAM_" + event);
        for (ServerPlayer player : social.server().getPlayerList().getPlayers()) social.snapshots().send(player);
    }
}
