package com.takumistudios.socialmod.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.takumistudios.socialmod.common.model.ConversationId;
import com.takumistudios.socialmod.common.model.PresenceStatus;
import com.takumistudios.socialmod.common.model.Privacy;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.common.text.TextSanitizer;
import com.takumistudios.socialmod.server.Lang;
import com.takumistudios.socialmod.server.PermissionBridge;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Comandos (PLAN 10): todo lo que hace la UI se puede hacer sin el mod en el cliente, así que los jugadores vanilla
 * y de Bedrock (Geyser) tienen la misma funcionalidad. Los comandos de staff exigen permisos (LuckPerms u OP 2).
 */
public final class SocialCommands {
    private static final SuggestionProvider<CommandSourceStack> ONLINE = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerList().getPlayers().stream()
                    .map(p -> p.getGameProfile().name()), builder);
    private static final SuggestionProvider<CommandSourceStack> MY_GROUPS = (context, builder) -> {
        SocialServer social = SocialServer.get();
        ServerPlayer player = context.getSource().getPlayer();
        if (social == null || player == null) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(social.groups().groupsOf(player.getUUID()).stream()
                .filter(g -> !g.party).map(g -> g.tag), builder);
    };
    private static final SuggestionProvider<CommandSourceStack> MAIN_CHANNELS = (context, builder) -> {
        SocialServer social = SocialServer.get();
        ServerPlayer player = context.getSource().getPlayer();
        Group group = social == null || player == null ? null : social.groups().mainGroup(player.getUUID());
        return group == null ? builder.buildFuture() : SharedSuggestionProvider.suggest(group.channels.stream().map(c -> c.name), builder);
    };

    private SocialCommands() {
    }

    @FunctionalInterface
    private interface PlayerAction {
        int run(SocialServer social, ServerPlayer player, CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, PlayerAction action) throws CommandSyntaxException {
        SocialServer social = SocialServer.get();
        if (social == null) {
            return 0;
        }
        return action.run(social, ctx.getSource().getPlayerOrException(), ctx);
    }

    private static String str(CommandContext<CommandSourceStack> ctx, String name) {
        return StringArgumentType.getString(ctx, name);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        registerMessaging(dispatcher);
        registerGroups(dispatcher);
        registerTeams(dispatcher);
        registerParty(dispatcher);
        registerFriends(dispatcher);
        registerStatus(dispatcher);
        registerAdmin(dispatcher);
    }

    // =====================================================================
    // Mensajes privados
    // =====================================================================

    private static void registerTeams(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("socialteam");
        root.then(Commands.literal("list").executes(ctx -> {
            SocialServer social = SocialServer.get(); if (social == null) return 0;
            for (Group team : social.teams().all()) if (!team.archived)
                ctx.getSource().sendSuccess(() -> Component.literal(team.id + " : " + team.name), false);
            return 1;
        }));
        root.then(Commands.literal("choose").then(Commands.argument("team", StringArgumentType.word())
                .then(Commands.literal("confirm").executes(ctx -> run(ctx, (social, player, c) ->
                        social.teams().choose(player, StringArgumentType.getString(c, "team")) ? 1 : 0)))));
        root.then(Commands.literal("create").then(Commands.literal("confirm")
                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> run(ctx, (social, player, c) ->
                        social.teams().create(player, StringArgumentType.getString(c, "name"), "shield;#55FF55") ? 1 : 0)))));
        for (String op : List.of("assign", "reset", "archive", "restore", "rename", "style")) {
            var command = Commands.literal(op).requires(PermissionBridge.require(PermissionBridge.TEAM_ADMIN, PermissionLevel.GAMEMASTERS));
            var target = Commands.argument("target", StringArgumentType.word());
            if (op.equals("assign") || op.equals("rename") || op.equals("style")) {
                target.then(Commands.argument("value", StringArgumentType.greedyString()).executes(ctx -> teamAdmin(ctx, op, StringArgumentType.getString(ctx, "value"))));
            } else target.executes(ctx -> teamAdmin(ctx, op, ""));
            root.then(command.then(target));
        }
        root.then(Commands.literal("limit").requires(PermissionBridge.require(PermissionBridge.TEAM_ADMIN, PermissionLevel.GAMEMASTERS))
                .then(Commands.argument("maximum", IntegerArgumentType.integer(1, 1000)).executes(ctx -> {
                    SocialServer social = SocialServer.get(); if (social == null) return 0;
                    ServerConfig.get().maxTeams = IntegerArgumentType.getInteger(ctx, "maximum"); social.visuals().persistConfig();
                    social.server().getPlayerList().getPlayers().forEach(social.snapshots()::send); return 1;
                })));
        dispatcher.register(root);
    }

    private static int teamAdmin(CommandContext<CommandSourceStack> ctx, String op, String value) {
        SocialServer social = SocialServer.get(); if (social == null) return 0;
        try {
            boolean ok = social.teams().admin(com.takumistudios.socialmod.common.net.SocialAction.valueOf("TEAM_" + op.toUpperCase(java.util.Locale.ROOT)),
                    StringArgumentType.getString(ctx, "target"), value, ctx.getSource().getTextName());
            if (!ok) ctx.getSource().sendFailure(Lang.tr("socialmod.team.invalid"));
            return ok ? 1 : 0;
        } catch (RuntimeException e) {
            com.takumistudios.socialmod.SocialMod.warnOnce("team_command", "Error procesando TEAM", e); return 0;
        }
    }

    private static void registerMessaging(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : new String[]{"pm", "dm"}) {
            dispatcher.register(Commands.literal(name)
                    .then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> run(ctx, (social, player, c) -> {
                                        PlayerRecord target = social.friends().resolve(str(c, "player"));
                                        if (target == null) {
                                            social.notifier().feedback(player, false, "socialmod.error.unknown_player", str(c, "player"));
                                            return 0;
                                        }
                                        return social.chat().send(player, "dm:" + target.id, str(c, "message")) ? 1 : 0;
                                    })))));
        }
        dispatcher.register(Commands.literal("r")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> run(ctx, (social, player, c) -> {
                            UUID partner = social.record(player).lastDirectPartner;
                            if (partner == null) {
                                social.notifier().feedback(player, false, "socialmod.error.no_reply");
                                return 0;
                            }
                            return social.chat().send(player, "dm:" + partner, str(c, "message")) ? 1 : 0;
                        }))));
    }

    // =====================================================================
    // Grupos
    // =====================================================================

    private static @Nullable Group main(SocialServer social, ServerPlayer player) {
        Group group = social.groups().mainGroup(player.getUUID());
        if (group == null) {
            social.notifier().feedback(player, false, "socialmod.group.none");
        }
        return group;
    }

    private static int withMain(CommandContext<CommandSourceStack> ctx, GroupAction action) throws CommandSyntaxException {
        return run(ctx, (social, player, c) -> {
            Group group = main(social, player);
            return group == null ? 0 : (action.run(social, player, group, c) ? 1 : 0);
        });
    }

    @FunctionalInterface
    private interface GroupAction {
        boolean run(SocialServer social, ServerPlayer player, Group group, CommandContext<CommandSourceStack> ctx);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> groupTarget(String literal, GroupTargetAction action) {
        return Commands.literal(literal).then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                .executes(ctx -> withMain(ctx, (social, player, group, c) -> action.run(social, player, group, str(c, "player")))));
    }

    @FunctionalInterface
    private interface GroupTargetAction {
        boolean run(SocialServer social, ServerPlayer player, Group group, String target);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> groupText(String literal, String field) {
        return Commands.literal(literal).then(Commands.argument("text", StringArgumentType.greedyString())
                .executes(ctx -> withMain(ctx, (social, player, group, c) -> social.groups().setText(player, group.id, field, str(c, "text")))));
    }

    private static void registerGroups(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("g")
                .then(Commands.literal("create")
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> run(ctx, (social, player, c) ->
                                                social.groups().create(player, str(c, "name"), str(c, "tag")) != null ? 1 : 0)))))
                .then(groupTarget("invite", (social, player, group, target) -> social.groups().invite(player, group.id, target)))
                .then(Commands.literal("accept").then(Commands.argument("group", StringArgumentType.word())
                        .executes(ctx -> run(ctx, (social, player, c) -> social.groups().accept(player, str(c, "group")) ? 1 : 0))))
                .then(Commands.literal("join").then(Commands.argument("group", StringArgumentType.word())
                        .executes(ctx -> run(ctx, (social, player, c) -> social.groups().accept(player, str(c, "group")) ? 1 : 0))))
                .then(Commands.literal("decline").then(Commands.argument("group", StringArgumentType.word())
                        .executes(ctx -> run(ctx, (social, player, c) -> social.groups().decline(player, str(c, "group")) ? 1 : 0))))
                .then(Commands.literal("leave")
                        .executes(ctx -> withMain(ctx, (social, player, group, c) -> social.groups().leave(player, group.id)))
                        .then(Commands.argument("group", StringArgumentType.word()).suggests(MY_GROUPS)
                                .executes(ctx -> run(ctx, (social, player, c) -> {
                                    Group group = social.groups().find(str(c, "group"));
                                    return group != null && social.groups().leave(player, group.id) ? 1 : 0;
                                }))))
                .then(groupTarget("kick", (social, player, group, target) -> social.groups().kick(player, group.id, target)))
                .then(groupTarget("promote", (social, player, group, target) -> social.groups().changeRole(player, group.id, target, true)))
                .then(groupTarget("demote", (social, player, group, target) -> social.groups().changeRole(player, group.id, target, false)))
                .then(groupTarget("transfer", (social, player, group, target) -> social.groups().transfer(player, group.id, target)))
                .then(Commands.literal("main").then(Commands.argument("group", StringArgumentType.word()).suggests(MY_GROUPS)
                        .executes(ctx -> run(ctx, (social, player, c) -> {
                            Group group = social.groups().find(str(c, "group"));
                            return group != null && social.groups().setMain(player, group.id) ? 1 : 0;
                        }))))
                .then(groupText("motd", "motd"))
                .then(groupText("description", "description"))
                .then(groupText("pin", "pinned"))
                .then(Commands.literal("tag").then(Commands.argument("tag", StringArgumentType.word())
                        .executes(ctx -> withMain(ctx, (social, player, group, c) -> social.groups().setText(player, group.id, "tag", str(c, "tag"))))))
                .then(Commands.literal("color").then(Commands.argument("color", StringArgumentType.greedyString())
                        .executes(ctx -> withMain(ctx, (social, player, group, c) -> social.groups().setText(player, group.id, "color", str(c, "color"))))))
                .then(Commands.literal("icon").then(Commands.argument("icon", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(com.takumistudios.socialmod.common.model.GroupIcon.values())
                                .map(com.takumistudios.socialmod.common.model.GroupIcon::id), b))
                        .executes(ctx -> withMain(ctx, (social, player, group, c) -> social.groups().setText(player, group.id, "icon", str(c, "icon"))))))
                .then(Commands.literal("claims")
                        .then(Commands.literal("link").executes(ctx -> withMain(ctx, (social, player, group, c) -> social.groups().setClaimsLink(player, group.id, true))))
                        .then(Commands.literal("unlink").executes(ctx -> withMain(ctx, (social, player, group, c) -> social.groups().setClaimsLink(player, group.id, false)))))
                .then(Commands.literal("voice")
                        .executes(ctx -> withMain(ctx, (social, player, group, c) -> social.voice().join(player, group.id)))
                        .then(Commands.literal("leave").executes(ctx -> run(ctx, (social, player, c) -> {
                            social.voice().leave(player);
                            return 1;
                        }))))
                .then(Commands.literal("channel")
                        .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> withMain(ctx, (social, player, group, c) -> social.groups().createChannel(player, group.id, str(c, "name"), "")))
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("recruit", "member", "officer", "leader"), b))
                                        .executes(ctx -> withMain(ctx, (social, player, group, c) ->
                                                social.groups().createChannel(player, group.id, str(c, "name"), str(c, "role")))))))
                        .then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.word()).suggests(MAIN_CHANNELS)
                                .executes(ctx -> withMain(ctx, (social, player, group, c) -> social.groups().deleteChannel(player, group.id, str(c, "name")))))))
                .then(Commands.literal("ch").then(Commands.argument("channel", StringArgumentType.word()).suggests(MAIN_CHANNELS)
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> withMain(ctx, (social, player, group, c) -> social.chat().send(player,
                                        ConversationId.group(group.id, str(c, "channel")).key(), str(c, "message")))))))
                .then(Commands.literal("to").then(Commands.argument("group", StringArgumentType.word()).suggests(MY_GROUPS)
                        .then(Commands.argument("channel", StringArgumentType.word())
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(ctx -> run(ctx, (social, player, c) -> {
                                            Group group = social.groups().find(str(c, "group"));
                                            if (group == null) {
                                                social.notifier().feedback(player, false, "socialmod.group.not_member");
                                                return 0;
                                            }
                                            return social.chat().send(player, ConversationId.group(group.id, str(c, "channel")).key(), str(c, "message")) ? 1 : 0;
                                        }))))))
                .then(Commands.literal("event").then(Commands.argument("minutes", IntegerArgumentType.integer(1, 60 * 24 * 60))
                        .then(Commands.argument("title", StringArgumentType.greedyString())
                                .executes(ctx -> withMain(ctx, (social, player, group, c) -> social.groups().createEvent(player, group.id,
                                        IntegerArgumentType.getInteger(c, "minutes"), str(c, "title")))))))
                .then(Commands.literal("disband")
                        .executes(ctx -> {
                            ctx.getSource().sendFailure(Lang.tr("socialmod.group.disband_confirm"));
                            return 0;
                        })
                        .then(Commands.literal("confirm")
                                .executes(ctx -> withMain(ctx, (social, player, group, c) -> social.groups().disband(player, group.id)))))
                .then(Commands.literal("info")
                        .executes(ctx -> withMain(ctx, (social, player, group, c) -> info(player, social, group)))
                        .then(Commands.argument("group", StringArgumentType.word()).suggests(MY_GROUPS)
                                .executes(ctx -> run(ctx, (social, player, c) -> {
                                    Group group = social.groups().find(str(c, "group"));
                                    if (group == null || (group.party && !group.isMember(player.getUUID()))) {
                                        social.notifier().feedback(player, false, "socialmod.group.unknown");
                                        return 0;
                                    }
                                    return info(player, social, group) ? 1 : 0;
                                }))))
                .then(Commands.literal("list").executes(ctx -> run(ctx, (social, player, c) -> {
                    List<Group> groups = social.groups().groupsOf(player.getUUID());
                    Group main = social.groups().mainGroup(player.getUUID());
                    player.sendSystemMessage(Lang.tr(player, "socialmod.group.list_header", groups.size()).withStyle(ChatFormatting.GOLD));
                    for (Group group : groups) {
                        player.sendSystemMessage(Component.literal((group == main ? " ★ " : " - ") + (group.party ? "Party" : "[" + group.tag + "] " + group.name)
                                + " (" + group.roleOf(player.getUUID()).id() + ", " + group.members.size() + ")").withColor(group.color));
                    }
                    return groups.size();
                })))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> withMain(ctx, (social, player, group, c) -> social.chat().send(player,
                                ConversationId.group(group.id, group.defaultChannel()).key(), str(c, "message"))))));
    }

    private static boolean info(ServerPlayer player, SocialServer social, Group group) {
        boolean member = group.isMember(player.getUUID());
        player.sendSystemMessage(Component.literal("[" + group.tag + "] " + group.name).withColor(group.color));
        if (!group.description.isEmpty()) {
            player.sendSystemMessage(Component.literal(group.description).withStyle(ChatFormatting.GRAY));
        }
        if (member && !group.motd.isEmpty()) {
            player.sendSystemMessage(Lang.tr(player, "socialmod.group.motd", group.motd).withStyle(ChatFormatting.YELLOW));
        }
        if (member && !group.pinned.isEmpty()) {
            player.sendSystemMessage(Lang.tr(player, "socialmod.group.pinned", group.pinned).withStyle(ChatFormatting.YELLOW));
        }
        MutableComponent members = Component.empty();
        group.members.forEach((id, role) -> {
            boolean online = social.online(id) != null && social.presence().visibleStatus(player.getUUID(), id) != PresenceStatus.OFFLINE;
            members.append(Component.literal("[" + role.letter() + "] " + group.memberNames.getOrDefault(id, "?") + "  ")
                    .withStyle(online ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        });
        player.sendSystemMessage(members);
        if (member) {
            player.sendSystemMessage(Component.literal("#" + String.join("  #", group.channels.stream()
                    .filter(c -> social.groups().canAccess(group, player.getUUID(), c.name)).map(c -> c.name).toList())).withStyle(ChatFormatting.AQUA));
            for (Group.GroupEvent event : group.events) {
                long minutes = Math.max(0, (event.startsAt - System.currentTimeMillis()) / 60_000);
                player.sendSystemMessage(Lang.tr(player, "socialmod.event.line", event.title, minutes).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        return true;
    }

    // =====================================================================
    // Parties
    // =====================================================================

    private static void registerParty(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("party")
                .then(Commands.literal("create").executes(ctx -> run(ctx, (social, player, c) -> social.groups().createParty(player) != null ? 1 : 0)))
                .then(Commands.literal("invite").then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                        .executes(ctx -> run(ctx, (social, player, c) -> {
                            Group party = social.groups().partyOf(player.getUUID());
                            if (party == null) {
                                party = social.groups().createParty(player);
                            }
                            return party != null && social.groups().invite(player, party.id, str(c, "player")) ? 1 : 0;
                        }))))
                .then(Commands.literal("voice")
                        .executes(ctx -> run(ctx, (social, player, c) -> {
                            Group party = social.groups().partyOf(player.getUUID());
                            if (party == null) {
                                social.notifier().feedback(player, false, "socialmod.party.none");
                                return 0;
                            }
                            return social.voice().join(player, party.id) ? 1 : 0;
                        }))
                        .then(Commands.literal("leave").executes(ctx -> run(ctx, (social, player, c) -> {
                            social.voice().leave(player);
                            return 1;
                        }))))
                .then(Commands.literal("accept").executes(ctx -> run(ctx, (social, player, c) -> social.groups().accept(player, "party") ? 1 : 0)))
                .then(Commands.literal("decline").executes(ctx -> run(ctx, (social, player, c) -> social.groups().decline(player, "party") ? 1 : 0)))
                .then(Commands.literal("leave").executes(ctx -> run(ctx, (social, player, c) -> {
                    Group party = social.groups().partyOf(player.getUUID());
                    return party != null && social.groups().leave(player, party.id) ? 1 : 0;
                })))
                .then(Commands.literal("kick").then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                        .executes(ctx -> run(ctx, (social, player, c) -> {
                            Group party = social.groups().partyOf(player.getUUID());
                            return party != null && social.groups().kick(player, party.id, str(c, "player")) ? 1 : 0;
                        }))))
                .then(Commands.literal("list").executes(ctx -> run(ctx, (social, player, c) -> {
                    Group party = social.groups().partyOf(player.getUUID());
                    if (party == null) {
                        social.notifier().feedback(player, false, "socialmod.party.none");
                        return 0;
                    }
                    return info(player, social, party) ? 1 : 0;
                }))));
        dispatcher.register(Commands.literal("p")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> run(ctx, (social, player, c) -> {
                            Group party = social.groups().partyOf(player.getUUID());
                            if (party == null) {
                                social.notifier().feedback(player, false, "socialmod.party.none");
                                return 0;
                            }
                            return social.chat().send(player, ConversationId.group(party.id, party.defaultChannel()).key(), str(c, "message")) ? 1 : 0;
                        }))));
    }

    // =====================================================================
    // Amigos y bloqueos
    // =====================================================================

    private static LiteralArgumentBuilder<CommandSourceStack> friendTarget(String literal, FriendAction action) {
        return Commands.literal(literal).then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                .executes(ctx -> run(ctx, (social, player, c) -> action.run(social, player, str(c, "player")) ? 1 : 0)));
    }

    @FunctionalInterface
    private interface FriendAction {
        boolean run(SocialServer social, ServerPlayer player, String target);
    }

    private static void registerFriends(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("friend")
                .then(friendTarget("add", (social, player, target) -> social.friends().request(player, target)))
                .then(friendTarget("accept", (social, player, target) -> social.friends().accept(player, target)))
                .then(friendTarget("deny", (social, player, target) -> social.friends().deny(player, target)))
                .then(friendTarget("remove", (social, player, target) -> social.friends().remove(player, target)))
                .then(friendTarget("favorite", (social, player, target) -> social.friends().toggleFavorite(player, target)))
                .then(friendTarget("block", (social, player, target) -> social.friends().block(player, target)))
                .then(friendTarget("unblock", (social, player, target) -> social.friends().unblock(player, target)))
                .then(Commands.literal("note").then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                        .then(Commands.argument("note", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx, (social, player, c) -> social.friends().setNote(player, str(c, "player"), str(c, "note")) ? 1 : 0)))))
                .then(Commands.literal("list").executes(ctx -> run(ctx, SocialCommands::friendList)))
                .then(Commands.literal("requests").executes(ctx -> run(ctx, (social, player, c) -> {
                    PlayerRecord record = social.record(player);
                    player.sendSystemMessage(Lang.tr(player, "socialmod.friend.requests_header", record.incomingRequests.size()).withStyle(ChatFormatting.GOLD));
                    for (UUID id : record.incomingRequests) {
                        String name = social.storage().nameOf(id);
                        player.sendSystemMessage(Component.literal(" - " + name + " ").withStyle(ChatFormatting.WHITE)
                                .append(button(Lang.tr(player, "socialmod.button.accept"), "/friend accept " + name, ChatFormatting.GREEN))
                                .append(" ")
                                .append(button(Lang.tr(player, "socialmod.button.deny"), "/friend deny " + name, ChatFormatting.RED)));
                    }
                    return record.incomingRequests.size();
                })))
                .then(Commands.literal("blocked").executes(ctx -> run(ctx, (social, player, c) -> {
                    PlayerRecord record = social.record(player);
                    List<String> names = record.blocked.stream().map(id -> social.storage().nameOf(id)).toList();
                    player.sendSystemMessage(Lang.tr(player, "socialmod.block.list", names.isEmpty() ? "-" : String.join(", ", names)).withStyle(ChatFormatting.GOLD));
                    return names.size();
                }))));
        dispatcher.register(Commands.literal("block").then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                .executes(ctx -> run(ctx, (social, player, c) -> social.friends().block(player, str(c, "player")) ? 1 : 0))));
        dispatcher.register(Commands.literal("unblock").then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                .executes(ctx -> run(ctx, (social, player, c) -> social.friends().unblock(player, str(c, "player")) ? 1 : 0))));
    }

    private static MutableComponent button(MutableComponent label, String command, ChatFormatting color) {
        return Component.literal("[").append(label).append("]").withStyle(Style.EMPTY.withColor(color)
                .withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static int friendList(SocialServer social, ServerPlayer player, CommandContext<CommandSourceStack> ctx) {
        PlayerRecord record = social.record(player);
        player.sendSystemMessage(Lang.tr(player, "socialmod.friend.list_header", record.friends.size()).withStyle(ChatFormatting.GOLD));
        for (UUID id : record.friends) {
            PresenceStatus status = social.presence().visibleStatus(player.getUUID(), id);
            PlayerRecord friend = social.storage().player(id);
            String name = friend == null ? id.toString() : friend.name;
            MutableComponent line = Component.literal(" " + status.symbol() + " ").withColor(status.color() & 0xFFFFFF)
                    .append(Component.literal((record.favorites.contains(id) ? "★ " : "") + name).withStyle(ChatFormatting.WHITE));
            if (friend != null && status != PresenceStatus.OFFLINE && !friend.customStatus.isEmpty()) {
                line.append(Component.literal(" \"" + friend.customStatus + "\"").withStyle(ChatFormatting.GRAY));
            }
            if (record.notes.containsKey(id)) {
                line.append(Component.literal(" (" + record.notes.get(id) + ")").withStyle(ChatFormatting.DARK_GRAY));
            }
            player.sendSystemMessage(line);
        }
        return record.friends.size();
    }

    // =====================================================================
    // Estado y privacidad
    // =====================================================================

    private static void registerStatus(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> status = Commands.literal("status");
        for (PresenceStatus value : PresenceStatus.values()) {
            if (!value.selectable()) {
                continue;
            }
            status.then(Commands.literal(value.id()).executes(ctx -> run(ctx, (social, player, c) -> {
                social.presence().setStatus(player, value);
                social.notifier().feedback(player, true, "socialmod.status.set", value.id());
                return 1;
            })));
        }
        status.then(Commands.literal("text").then(Commands.argument("text", StringArgumentType.greedyString())
                .executes(ctx -> run(ctx, (social, player, c) -> {
                    social.record(player).customStatus = TextSanitizer.clean(str(c, "text"), ServerConfig.get().chat.maxStatusLength);
                    social.storage().markPlayersDirty();
                    social.presence().markChanged(player.getUUID());
                    social.snapshots().send(player);
                    social.notifier().feedback(player, true, "socialmod.status.text_set");
                    return 1;
                }))));
        status.then(Commands.literal("clear").executes(ctx -> run(ctx, (social, player, c) -> {
            social.record(player).customStatus = "";
            social.storage().markPlayersDirty();
            social.presence().markChanged(player.getUUID());
            social.snapshots().send(player);
            social.notifier().feedback(player, true, "socialmod.status.text_cleared");
            return 1;
        })));
        dispatcher.register(status);
    }

    // =====================================================================
    // /socialmod: buzón, privacidad, datos, moderación y administración
    // =====================================================================

    private static @Nullable PlayerRecord known(CommandSourceStack source, SocialServer social, String name) {
        PlayerRecord record = social.friends().resolve(name);
        if (record == null) {
            source.sendFailure(Lang.tr("socialmod.error.unknown_player", name));
        }
        return record;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> privacy(String literal, java.util.function.BiConsumer<PlayerRecord, Privacy> setter) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(literal);
        for (Privacy value : Privacy.values()) {
            node.then(Commands.literal(value.id()).executes(ctx -> run(ctx, (social, player, c) -> {
                setter.accept(social.record(player), value);
                social.storage().markPlayersDirty();
                social.presence().markChanged(player.getUUID());
                social.snapshots().send(player);
                social.notifier().feedback(player, true, "socialmod.privacy.set", literal, value.id());
                return 1;
            })));
        }
        return node;
    }

    private static void registerAdmin(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Menú de cofre para clientes sin el mod (Java vanilla y Bedrock)
        dispatcher.register(Commands.literal("social").executes(ctx -> run(ctx, (social, player, c) -> {
            com.takumistudios.socialmod.server.menu.SocialMenu.open(social, player);
            return 1;
        })));
        dispatcher.register(Commands.literal("socialmod")
                .then(Commands.literal("menu").executes(ctx -> run(ctx, (social, player, c) -> {
                    com.takumistudios.socialmod.server.menu.SocialMenu.open(social, player);
                    return 1;
                })))
                .then(Commands.literal("inbox").executes(ctx -> run(ctx, (social, player, c) -> {
                    social.chat().showInbox(player);
                    return 1;
                })))
                .then(Commands.literal("privacy")
                        .then(privacy("messages", (record, value) -> record.whoCanMessage = value))
                        .then(privacy("status", (record, value) -> record.whoSeesStatus = value)))
                .then(Commands.literal("report")
                        .then(Commands.argument("conversation", StringArgumentType.string())
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(ctx -> run(ctx, (social, player, c) -> {
                                            social.moderation().report(player, str(c, "conversation"), IntegerArgumentType.getInteger(c, "id"));
                                            return 1;
                                        })))))
                .then(Commands.literal("data")
                        .then(Commands.literal("export")
                                .executes(ctx -> run(ctx, (social, player, c) -> {
                                    if (!ServerConfig.get().moderation.allowDataExport || !PermissionBridge.allows(player, PermissionBridge.DATA_EXPORT)) {
                                        social.notifier().feedback(player, false, "socialmod.error.no_permission");
                                        return 0;
                                    }
                                    social.moderation().export(player.getUUID(), player);
                                    return 1;
                                }))
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                                        .requires(PermissionBridge.require(PermissionBridge.MOD_INSPECT, PermissionLevel.GAMEMASTERS))
                                        .executes(ctx -> {
                                            SocialServer social = SocialServer.get();
                                            PlayerRecord record = social == null ? null : known(ctx.getSource(), social, str(ctx, "player"));
                                            if (record == null) return 0;
                                            social.moderation().export(record.id, null);
                                            ctx.getSource().sendSuccess(() -> Lang.tr("socialmod.data.exported", "socialmod/exports/" + record.id + ".json.gz"), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("delete")
                                .executes(ctx -> {
                                    ctx.getSource().sendFailure(Lang.tr("socialmod.data.delete_confirm"));
                                    return 0;
                                })
                                .then(Commands.literal("confirm").executes(ctx -> run(ctx, (social, player, c) -> {
                                    if (!ServerConfig.get().moderation.allowDataDelete || !PermissionBridge.allows(player, PermissionBridge.DATA_DELETE)) {
                                        social.notifier().feedback(player, false, "socialmod.error.no_permission");
                                        return 0;
                                    }
                                    social.moderation().delete(player.getUUID(), player.getGameProfile().name());
                                    social.notifier().feedback(player, true, "socialmod.data.deleted");
                                    return 1;
                                })))))
                .then(Commands.literal("reload")
                        .requires(PermissionBridge.require(PermissionBridge.ADMIN_RELOAD, PermissionLevel.GAMEMASTERS))
                        .executes(ctx -> {
                            String error = ServerConfig.load();
                            PermissionBridge.invalidate(null);
                            SocialServer social = SocialServer.get();
                            if (social != null) {
                                social.reconfigure();
                            }
                            if (error != null) {
                                ctx.getSource().sendFailure(Component.literal(error));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Lang.tr("socialmod.admin.reloaded").withStyle(ChatFormatting.GREEN), true);
                            return 1;
                        }))
                .then(Commands.literal("mod")
                        .then(Commands.literal("mute")
                                .requires(PermissionBridge.require(PermissionBridge.MOD_MUTE, PermissionLevel.GAMEMASTERS))
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                                .executes(ctx -> mute(ctx, ""))
                                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                        .executes(ctx -> mute(ctx, str(ctx, "reason")))))))
                        .then(Commands.literal("unmute")
                                .requires(PermissionBridge.require(PermissionBridge.MOD_MUTE, PermissionLevel.GAMEMASTERS))
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                                        .executes(ctx -> {
                                            SocialServer social = SocialServer.get();
                                            PlayerRecord record = social == null ? null : known(ctx.getSource(), social, str(ctx, "player"));
                                            if (record == null) return 0;
                                            boolean done = social.moderation().unmute(ctx.getSource(), record);
                                            ctx.getSource().sendSuccess(() -> Lang.tr(done ? "socialmod.mod.unmute_done" : "socialmod.mod.not_muted", record.name), true);
                                            return done ? 1 : 0;
                                        })))
                        .then(Commands.literal("history")
                                .requires(PermissionBridge.require(PermissionBridge.MOD_HISTORY, PermissionLevel.GAMEMASTERS))
                                .then(Commands.literal("dm").then(Commands.argument("a", StringArgumentType.word()).suggests(ONLINE)
                                        .then(Commands.argument("b", StringArgumentType.word()).suggests(ONLINE)
                                                .executes(ctx -> {
                                                    SocialServer social = SocialServer.get();
                                                    if (social == null) return 0;
                                                    PlayerRecord a = known(ctx.getSource(), social, str(ctx, "a"));
                                                    PlayerRecord b = a == null ? null : known(ctx.getSource(), social, str(ctx, "b"));
                                                    if (b == null) return 0;
                                                    social.moderation().history(ctx.getSource(), ConversationId.direct(a.id, b.id), 30);
                                                    return 1;
                                                }))))
                                .then(Commands.literal("group").then(Commands.argument("group", StringArgumentType.word())
                                        .then(Commands.argument("channel", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    SocialServer social = SocialServer.get();
                                                    Group group = social == null ? null : social.groups().find(str(ctx, "group"));
                                                    if (group == null) {
                                                        ctx.getSource().sendFailure(Lang.tr("socialmod.group.unknown"));
                                                        return 0;
                                                    }
                                                    social.moderation().history(ctx.getSource(), ConversationId.group(group.id, str(ctx, "channel")), 30);
                                                    return 1;
                                                })))))
                        .then(Commands.literal("disband")
                                .requires(PermissionBridge.require(PermissionBridge.MOD_DISBAND, PermissionLevel.GAMEMASTERS))
                                .then(Commands.argument("group", StringArgumentType.word())
                                        .executes(ctx -> {
                                            SocialServer social = SocialServer.get();
                                            Group group = social == null ? null : social.groups().find(str(ctx, "group"));
                                            if (group == null) {
                                                ctx.getSource().sendFailure(Lang.tr("socialmod.group.unknown"));
                                                return 0;
                                            }
                                            social.moderation().disband(ctx.getSource(), group);
                                            ctx.getSource().sendSuccess(() -> Lang.tr("socialmod.mod.disbanded", group.name), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("inspect")
                                .requires(PermissionBridge.require(PermissionBridge.MOD_INSPECT, PermissionLevel.GAMEMASTERS))
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                                        .executes(ctx -> {
                                            SocialServer social = SocialServer.get();
                                            PlayerRecord record = social == null ? null : known(ctx.getSource(), social, str(ctx, "player"));
                                            if (record == null) return 0;
                                            social.moderation().inspect(ctx.getSource(), record);
                                            return 1;
                                        })))
                        .then(Commands.literal("reports")
                                .requires(PermissionBridge.require(PermissionBridge.MOD_REPORTS, PermissionLevel.GAMEMASTERS))
                                .executes(ctx -> {
                                    SocialServer social = SocialServer.get();
                                    if (social == null) return 0;
                                    social.moderation().listReports(ctx.getSource(), 10);
                                    return 1;
                                }))
                        .then(Commands.literal("spy")
                                .requires(PermissionBridge.require(PermissionBridge.MOD_SPY, PermissionLevel.GAMEMASTERS))
                                .executes(ctx -> run(ctx, (social, player, c) -> {
                                    if (!ServerConfig.get().moderation.spyEnabled) {
                                        social.notifier().feedback(player, false, "socialmod.mod.spy_disabled");
                                        return 0;
                                    }
                                    boolean on = social.moderation().toggleSpy(player);
                                    social.notifier().feedback(player, true, on ? "socialmod.mod.spy_on" : "socialmod.mod.spy_off");
                                    return 1;
                                }))))
                .then(Commands.literal("version").executes(ctx -> {
                    String version = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("socialmod")
                            .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
                    ctx.getSource().sendSuccess(() -> Component.literal("SocialMod " + version + " (protocol " + Payloads.PROTOCOL_VERSION + ")")
                            .withStyle(ChatFormatting.GOLD), false);
                    return 1;
                })));
    }

    private static int mute(CommandContext<CommandSourceStack> ctx, String reason) {
        SocialServer social = SocialServer.get();
        PlayerRecord record = social == null ? null : known(ctx.getSource(), social, str(ctx, "player"));
        if (record == null) return 0;
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        social.moderation().mute(ctx.getSource(), record, seconds, reason);
        ctx.getSource().sendSuccess(() -> Lang.tr("socialmod.mod.muted", record.name, seconds == 0 ? "∞" : seconds + " s"), true);
        return 1;
    }

}
