package com.takumistudios.socialmod.test;

import com.takumistudios.socialmod.common.model.ConversationId;
import com.takumistudios.socialmod.common.model.Privacy;
import com.takumistudios.socialmod.common.model.Role;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.Conversation;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;

import java.util.List;

/**
 * Pruebas de servidor (PLAN 18): mensajes, buzón, bloqueos, privacidad, grupos, roles, parties, anti-spam,
 * filtro, adjuntos y seguridad frente a peticiones manipuladas. Se ejecutan con {@code ./gradlew runGameTest}.
 * Los jugadores falsos comparten nombre, así que todo se resuelve por UUID.
 */
public class SocialModGameTests {
    @GameTest
    public void teamBannerChecksLeaderPermissionsAndRegistry(GameTestHelper helper) {
        var social = social(helper); var leader = player(helper); var stranger = player(helper);
        check(helper, social.teams().create(leader, "Banner " + System.nanoTime(), "shield;#55FF55"), "TEAM creation failed");
        var team = social.teams().of(leader.getUUID());
        String valid = "{\"base\":15,\"layers\":[{\"pattern\":\"minecraft:stripe_center\",\"color\":14}]}";
        check(helper, social.teams().banner(leader, team.id, valid), "leader cannot edit banner");
        check(helper, !social.teams().banner(stranger, team.id, "{}"), "stranger edited banner");
        check(helper, !social.teams().banner(leader, team.id, "{\"layers\":[{\"pattern\":\"minecraft:missing\",\"color\":0}]}"), "unknown pattern accepted");
        check(helper, team.banner.base == 15 && team.banner.layers.size() == 1, "rejected operation modified banner");
        var view = social.snapshots().build(leader).teams.stream().filter(t -> t.id.equals(team.id)).findFirst().orElseThrow();
        check(helper, view.banner.base == 15 && view.leader.equals(leader.getUUID().toString()), "banner not synchronized");
        boolean previous = social.visuals().originalInterface();
        com.takumistudios.socialmod.server.net.ActionTestProbe.dispatch(social, stranger, new com.takumistudios.socialmod.common.net.Payloads.ActionC2S(com.takumistudios.socialmod.common.net.SocialAction.VISUAL_ORIGINAL, String.valueOf(!previous), ""));
        check(helper, previous == social.visuals().originalInterface(), "stranger changed global appearance");
        social.teams().archive(team.id, "test cleanup"); helper.succeed();
    }
    private static SocialServer social(GameTestHelper helper) {
        SocialServer social = SocialServer.get();
        if (social == null) {
            throw helper.assertionException("SocialServer no arrancó");
        }
        return social;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        social(helper).record(player);
        return player;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }

    /**
     * Conversación en memoria o, si la caché la expulsó (otros tests del lote abren muchas), pide cargarla del disco y
     * devuelve {@code null} hasta el tick siguiente. Así los tests no dependen del tamaño de la caché.
     */
    private static Conversation conversation(SocialServer social, String key) {
        Conversation cached = social.storage().cachedConversation(key);
        if (cached == null) {
            social.storage().withConversation(key, loaded -> { });
        }
        return cached;
    }

    private static String id(ServerPlayer player) {
        return player.getUUID().toString();
    }

    @GameTest public void permissionsApplyRevocationsAndProviderFailuresImmediately(GameTestHelper helper) {
        PermissionRegressionTests.check(player(helper)); helper.succeed();
    }

    @GameTest(maxTicks = 400) public void optionalLuckPermsChecksRealPermissionsAndIntegerMetadata(GameTestHelper helper) {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("luckperms")) LuckPermsIntegrationTests.run(helper);
        else helper.succeed();
    }

    @GameTest public void pendingPrivateMessageRechecksRecipientPrivacy(GameTestHelper helper) {
        var social = social(helper); var sender = player(helper); var recipient = player(helper);
        var conversation = ConversationId.direct(sender.getUUID(), recipient.getUUID());
        social.chat().send(sender, conversation.key(), "privacy regression");
        social.record(recipient).whoCanMessage = Privacy.NOBODY;
        helper.succeedWhen(() -> {
            var loaded = social.storage().cachedConversation(conversation.key());
            check(helper, loaded != null, "Conversation is still loading");
            check(helper, loaded.messages.isEmpty(), "Pending message bypassed recipient privacy");
        });
    }

    @GameTest public void invisibleFriendDoesNotExposeLastSeenInSnapshot(GameTestHelper helper) {
        var social = social(helper); var viewer = player(helper); var hidden = player(helper);
        social.record(viewer).friends.add(hidden.getUUID());
        social.record(hidden).status = com.takumistudios.socialmod.common.model.PresenceStatus.INVISIBLE;
        social.record(hidden).lastSeen = System.currentTimeMillis();
        var friend = social.snapshots().build(viewer).friends.getFirst();
        check(helper, friend.status.equals("offline") && friend.lastSeen == 0, "Invisible friend exposed activity timestamp");
        helper.succeed();
    }

    @GameTest public void historicalIndexesPrepareInBoundedBatches(GameTestHelper helper) { StoragePerformanceProbe.measure(); helper.succeed(); }

    @GameTest public void snapshotsUseBoundedBatchesAndSuppressIdenticalPayloads(GameTestHelper helper) throws Exception {
        var social = social(helper); var players = new java.util.ArrayList<ServerPlayer>();
        for (int i = 0; i < 25; i++) { var p = player(helper); players.add(p); social.onHello(p, com.takumistudios.socialmod.common.net.Payloads.PROTOCOL_VERSION); }
        var pendingField = social.snapshots().getClass().getDeclaredField("pending"); pendingField.setAccessible(true);
        @SuppressWarnings("unchecked") var pending = (java.util.Set<java.util.UUID>) pendingField.get(social.snapshots());
        long before = players.stream().filter(p -> pending.contains(p.getUUID())).count();
        social.snapshots().tick();
        long after = players.stream().filter(p -> pending.contains(p.getUUID())).count();
        check(helper, before == 25 && after >= 5, "Snapshot tick exceeded its 20 recipient budget");
        var viewer = players.getFirst(); social.snapshots().sendNow(viewer);
        var cacheField = social.snapshots().getClass().getDeclaredField("lastSent"); cacheField.setAccessible(true);
        @SuppressWarnings("unchecked") var cache = (java.util.Map<java.util.UUID, com.google.gson.JsonObject>) cacheField.get(social.snapshots());
        var first = cache.get(viewer.getUUID()); social.snapshots().tick(); social.snapshots().sendNow(viewer);
        check(helper, first != null && first == cache.get(viewer.getUUID()), "Identical snapshot was retransmitted");
        for (var p : players) { social.snapshots().forget(p.getUUID()); check(helper, !pending.contains(p.getUUID()) && !cache.containsKey(p.getUUID()), "Snapshot cache leaked after disconnect"); }
        helper.succeed();
    }

    @GameTest public void repeatedHandshakeKeepsPanelSubscription(GameTestHelper helper) {
        var social = social(helper); var viewer = player(helper);
        social.onHello(viewer, com.takumistudios.socialmod.common.net.Payloads.PROTOCOL_VERSION);
        social.presence().setPanelOpen(viewer, true);
        var session = social.session(viewer.getUUID());
        social.onHello(viewer, -1);
        check(helper, social.session(viewer.getUUID()) == session && session.panelOpen, "Handshake reset valid session");
        social.presence().setPanelOpen(viewer, false); helper.succeed();
    }

    @GameTest
    public void christmasPresetSharesTexturesFontAndFrameSettings(GameTestHelper helper) {
        SocialServer social = social(helper); ServerPlayer first = player(helper), second = player(helper);
        var previous = social.visuals().design().copy();
        var preset = com.takumistudios.socialmod.common.model.VisualPresets.christmas();
        social.visuals().publish(com.takumistudios.socialmod.common.model.VisualDesign.GSON.toJson(preset), "test");
        var a = social.snapshots().build(first).visual; var b = social.snapshots().build(second).visual;
        check(helper, a.font.equals("socialmod:christmas") && b.font.equals(a.font), "Christmas font differs between players");
        check(helper, a.panelInset == 20 && b.inputTexture.equals(preset.inputTexture), "Christmas frame/input settings not shared");
        social.visuals().publish(com.takumistudios.socialmod.common.model.VisualDesign.GSON.toJson(previous), "test cleanup");
        check(helper, social.snapshots().build(first).visual.font.equals(previous.font), "Previous design not restored");
        helper.succeed();
    }

    @GameTest
    public void largeVisualPresetIsAppliedOnlyAfterCompleteUpload(GameTestHelper helper) {
        SocialServer social = social(helper); ServerPlayer staff = player(helper);
        var previous = social.visuals().design().copy(); var next = previous.copy(); next.widthPercent = 89;
        for (int i = 0; i < 140; i++) next.components.put("full/Screen/" + i + "x".repeat(155), new com.takumistudios.socialmod.common.model.VisualDesign.Rect(.1f, .2f, .1f, .1f));
        String json = com.takumistudios.socialmod.common.model.VisualDesign.GSON.toJson(next);
        check(helper, json.length() > 32767, "large preset fixture not large enough");
        int total = (json.length() + 4095) / 4096; String upload = java.util.UUID.randomUUID().toString();
        for (int index = 0; index < total; index++) {
            social.visuals().receive(staff, upload + "/" + index + "/" + total, json.substring(index * 4096, Math.min(json.length(), (index + 1) * 4096)));
            if (index < total - 1) check(helper, social.visuals().design().widthPercent == previous.widthPercent, "partial preset applied");
        }
        check(helper, social.visuals().design().widthPercent == 89 && social.visuals().design().components.size() == 140, "complete large preset not applied");
        social.visuals().receive(staff, java.util.UUID.randomUUID() + "/1/2", "garbage");
        check(helper, social.visuals().design().widthPercent == 89, "out-of-order upload changed preset");
        social.visuals().publish(com.takumistudios.socialmod.common.model.VisualDesign.GSON.toJson(previous), "test");
        helper.succeed();
    }

    @GameTest
    public void teamLimitAndForgedStaffActionsAreRejected(GameTestHelper helper) {
        SocialServer social = social(helper); ServerPlayer first = player(helper), second = player(helper);
        int previous = ServerConfig.get().maxTeams;
        String previousMode = social.visuals().design().mode;
        ServerConfig.get().maxTeams = (int) social.teams().all().stream().filter(g -> !g.archived).count() + 1;
        try {
            check(helper, social.teams().create(first, "Limit " + System.nanoTime(), "shield;#55FF55"), "first slot failed");
            Group team = social.teams().of(first.getUUID());
            check(helper, !social.teams().create(second, "Overflow " + System.nanoTime(), "shield;#55FF55"), "limit overflow");
            check(helper, !social.record(second).teamChosen, "rejected creation consumed choice");
            check(helper, social.teams().admin(com.takumistudios.socialmod.common.net.SocialAction.TEAM_ASSIGN, id(first), team.id, "test"), "same TEAM assignment failed");
            check(helper, team.roleOf(first.getUUID()) == Role.LEADER, "same TEAM assignment lost leader role");
            var action = com.takumistudios.socialmod.common.net.SocialAction.TEAM_ASSIGN;
            com.takumistudios.socialmod.server.net.ActionTestProbe.dispatch(social, second, new com.takumistudios.socialmod.common.net.Payloads.ActionC2S(action, id(second), team.id));
            check(helper, social.teams().of(second.getUUID()) == null, "nonstaff assigned TEAM");
            com.takumistudios.socialmod.server.net.ActionTestProbe.dispatch(social, second, new com.takumistudios.socialmod.common.net.Payloads.ActionC2S(com.takumistudios.socialmod.common.net.SocialAction.VISUAL_PUBLISH, "", "{\"mode\":\"sidebar\"}"));
            com.takumistudios.socialmod.server.net.ActionTestProbe.dispatch(social, second, new com.takumistudios.socialmod.common.net.Payloads.ActionC2S(com.takumistudios.socialmod.common.net.SocialAction.VISUAL_ROLLBACK, "", ""));
            check(helper, social.visuals().design().mode.equals(previousMode), "nonstaff published visual design");
            var previousDesign = social.visuals().design().copy();
            var published = previousDesign.copy(); published.mode = "sidebar";
            social.visuals().publish(com.takumistudios.socialmod.common.model.VisualDesign.GSON.toJson(published), "test");
            check(helper, social.snapshots().build(first).visual.mode.equals("sidebar") && social.snapshots().build(second).visual.mode.equals("sidebar"), "visual preset not shared between viewers");
            social.visuals().publish(com.takumistudios.socialmod.common.model.VisualDesign.GSON.toJson(previousDesign), "test");
            social.teams().archive(team.id, "test");
        } finally { ServerConfig.get().maxTeams = previous; }
        helper.succeed();
    }

    @GameTest(maxTicks = 80)
    public void teamIdentitySurvivesMultipleGroupsAndArchiveRestore(GameTestHelper helper) {
        SocialServer social = social(helper); ServerPlayer owner = player(helper), other = player(helper);
        String name = "TEAM " + System.nanoTime();
        check(helper, social.teams().create(owner, name, "swords;#3366FF"), "create TEAM");
        Group team = social.teams().of(owner.getUUID());
        check(helper, team != null && team.isMember(owner.getUUID()) && social.record(owner).teamChosen, "creator assignment");
        Group first = social.groups().create(owner, "First " + System.nanoTime(), uniqueTag());
        Group second = social.groups().create(owner, "Second " + System.nanoTime(), uniqueTag());
        check(helper, first != null && second != null, "two ordinary groups");
        social.groups().setMain(owner, second.id);
        check(helper, social.groups().nametags().entryFor(owner.getUUID()).tag().equals(name), "multiple groups changed TEAM nametag");
        check(helper, !social.teams().choose(owner, team.id), "second choice accepted");
        check(helper, !social.groups().leave(owner, team.id), "managed group left");
        check(helper, !social.groups().invite(owner, team.id, id(other)), "managed group invitation");
        check(helper, !social.groups().disband(owner, team.id), "managed group dissolved");
        check(helper, social.teams().choose(other, team.id), "first choice rejected");
        team.channels.add(new Group.Channel("staff", Role.OFFICER));
        String key = ConversationId.group(team.id, "general").key();
        check(helper, social.chat().send(owner, key, "persistent TEAM chat"), "TEAM chat failed");
        helper.succeedWhen(() -> {
            Conversation saved = conversation(social, key); check(helper, saved != null && !saved.messages.isEmpty(), "chat not stored");
            check(helper, social.teams().archive(team.id, "test"), "archive failed");
            check(helper, social.teams().of(owner.getUUID()) == null && !social.record(owner).teamChosen, "archive did not release choice");
            check(helper, social.groups().nametags().entryFor(owner.getUUID()).tag().isEmpty(), "fallback ordinary group after archive");
            check(helper, social.chat().canRead(owner.getUUID(), ConversationId.group(team.id, "general")), "archived history inaccessible");
            check(helper, !social.chat().canRead(other.getUUID(), ConversationId.group(team.id, "staff")), "archive leaked staff channel");
            check(helper, !social.chat().send(owner, key, "must fail"), "archive writable");
            check(helper, social.teams().create(owner, "New " + System.nanoTime(), "shield;#55FF55"), "new choice after archive failed");
            Group replacement = social.teams().of(owner.getUUID());
            check(helper, social.teams().admin(com.takumistudios.socialmod.common.net.SocialAction.TEAM_RESTORE, team.id, "", "test"), "restore failed");
            check(helper, social.teams().of(owner.getUUID()) == replacement && team.members.isEmpty(), "restore overwrote later choice");
            check(helper, saved.messages.getFirst().text.equals("persistent TEAM chat"), "restore lost history");
            check(helper, social.teams().admin(com.takumistudios.socialmod.common.net.SocialAction.TEAM_ASSIGN, id(other), replacement.id, "test"), "offline UUID assignment failed");
            check(helper, social.teams().of(other.getUUID()) == replacement, "assignment mismatch");
            social.teams().archive(team.id, "test"); social.teams().archive(replacement.id, "test");
            social.groups().disbandInternal(first, "test"); social.groups().disbandInternal(second, "test");
        });
    }

    // ---------- Mensajes privados ----------

    @GameTest(maxTicks = 60)
    public void privateMessageIsStoredAndCountedAsUnread(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer alex = player(helper);
        ServerPlayer luna = player(helper);
        check(helper, social.chat().send(alex, "dm:" + luna.getUUID(), "hola **Luna**"), "el privado no se aceptó");
        String key = ConversationId.direct(alex.getUUID(), luna.getUUID()).key();
        helper.succeedWhen(() -> {
            Conversation conversation = conversation(social, key);
            check(helper, conversation != null && conversation.messages.size() == 1, "el mensaje no se guardó");
            check(helper, conversation.messages.getFirst().text.equals("hola **Luna**"), "texto alterado");
            check(helper, social.record(luna).unread.getOrDefault(key, 0) == 1, "el destinatario no tiene el mensaje sin leer");
            check(helper, social.record(alex).unread.getOrDefault(key, 0) == 0, "el remitente no debe tener sin leer");
            check(helper, luna.getUUID().equals(social.record(alex).lastDirectPartner), "/r no apunta al destinatario");
        });
    }

    @GameTest
    public void blockedPlayerCannotSendPrivateMessages(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer alex = player(helper);
        ServerPlayer troll = player(helper);
        check(helper, social.friends().block(alex, id(troll)), "no se pudo bloquear");
        check(helper, !social.chat().send(troll, "dm:" + alex.getUUID(), "hola"), "un bloqueado envió un privado");
        check(helper, !social.chat().send(alex, "dm:" + troll.getUUID(), "hola"), "no se puede escribir a alguien que bloqueaste");
        check(helper, social.presence().visibleStatus(troll.getUUID(), alex.getUUID())
                == com.takumistudios.socialmod.common.model.PresenceStatus.OFFLINE, "un bloqueado ve el estado");
        helper.succeed();
    }

    @GameTest
    public void privacyFriendsOnly(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer alex = player(helper);
        ServerPlayer stranger = player(helper);
        ServerPlayer friend = player(helper);
        social.record(alex).whoCanMessage = Privacy.FRIENDS;
        check(helper, social.friends().request(friend, id(alex)), "solicitud");
        check(helper, social.friends().accept(alex, id(friend)), "aceptar");
        check(helper, !social.chat().send(stranger, "dm:" + alex.getUUID(), "hola"), "un desconocido escribió con privacidad 'amigos'");
        check(helper, social.chat().send(friend, "dm:" + alex.getUUID(), "hola"), "un amigo no pudo escribir");
        helper.succeed();
    }

    @GameTest
    public void cannotPostIntoSomeoneElsesConversation(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer a = player(helper);
        ServerPlayer b = player(helper);
        ServerPlayer intruder = player(helper);
        String key = ConversationId.direct(a.getUUID(), b.getUUID()).key();
        check(helper, !social.chat().send(intruder, key, "suplantación"), "se pudo escribir en un privado ajeno");
        check(helper, !social.chat().canRead(intruder.getUUID(), ConversationId.parse(key)), "se puede leer un privado ajeno");
        check(helper, !social.chat().send(a, "dm:" + a.getUUID(), "a mí mismo"), "privado consigo mismo");
        check(helper, !social.chat().send(a, "g:zzzzzzzz:general", "grupo inexistente"), "grupo inexistente");
        check(helper, !social.chat().send(a, "{\"text\":\"json\"}", "x"), "destino basura aceptado");
        helper.succeed();
    }

    @GameTest
    public void hugeAndControlTextIsSanitized(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer a = player(helper);
        ServerPlayer b = player(helper);
        check(helper, !social.chat().send(a, "dm:" + b.getUUID(), "x".repeat(100_000)), "texto enorme aceptado");
        check(helper, !social.chat().send(a, "dm:" + b.getUUID(), "\u0000\u0007§c‮"), "texto vacío tras sanear aceptado");
        helper.succeed();
    }

    // ---------- Anti-spam y filtro ----------

    @GameTest(maxTicks = 40)
    public void spamGetsAutoMuted(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer spammer = player(helper);
        ServerPlayer victim = player(helper);
        for (int i = 0; i < 30; i++) {
            social.chat().send(spammer, "dm:" + victim.getUUID(), "spam " + i);
        }
        check(helper, social.record(spammer).isMuted(System.currentTimeMillis()), "el spam no silenció al jugador");
        check(helper, !social.chat().send(spammer, "dm:" + victim.getUUID(), "sigo aquí"), "un silenciado pudo escribir");
        helper.succeed();
    }

    @GameTest
    public void wordFilterBlocksMessages(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerConfig original = ServerConfig.get();
        try {
            ServerConfig config = new ServerConfig();
            config.filter.enabled = true;
            config.filter.mode = "block";
            config.filter.words = new java.util.ArrayList<>(List.of("palabrota"));
            ServerConfig.set(config);
            social.reconfigure();
            ServerPlayer a = player(helper);
            ServerPlayer b = player(helper);
            check(helper, !social.chat().send(a, "dm:" + b.getUUID(), "eres una PALABROTA"), "el filtro no bloqueó");
            check(helper, social.chat().send(a, "dm:" + b.getUUID(), "hola"), "el filtro bloqueó un mensaje limpio");
        } finally {
            ServerConfig.set(original);
            social.reconfigure();
        }
        helper.succeed();
    }

    // ---------- Amigos ----------

    @GameTest
    public void friendRequestFlow(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer a = player(helper);
        ServerPlayer b = player(helper);
        check(helper, social.friends().request(a, id(b)), "solicitud");
        check(helper, social.record(b).incomingRequests.contains(a.getUUID()), "la solicitud no llegó");
        check(helper, social.friends().accept(b, id(a)), "aceptar");
        check(helper, social.friends().areFriends(a.getUUID(), b.getUUID()) && social.friends().areFriends(b.getUUID(), a.getUUID()),
                "la amistad no es mutua");
        check(helper, social.friends().block(a, id(b)), "bloquear");
        check(helper, !social.friends().areFriends(b.getUUID(), a.getUUID()), "bloquear no rompió la amistad");
        helper.succeed();
    }

    @GameTest
    public void blockedPlayerRequestIsSilentlyDropped(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer a = player(helper);
        ServerPlayer troll = player(helper);
        social.friends().block(a, id(troll));
        social.friends().request(troll, id(a));
        check(helper, !social.record(a).incomingRequests.contains(troll.getUUID()), "un bloqueado envió una solicitud");
        helper.succeed();
    }

    // ---------- Grupos ----------

    private static final java.util.Set<String> USED_TAGS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Etiqueta distinta para cada test (los tests de un lote corren a la vez). Antes salía de los primeros dígitos de
     * nanoTime, que solo cambian cada ~2 s: dos tests creando grupos a la vez chocaban con "etiqueta en uso".
     */
    private static String uniqueTag() {
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        String tag;
        do {
            tag = "T" + Integer.toString(random.nextInt(36 * 36 * 36 * 36), 36).toUpperCase(java.util.Locale.ROOT);
        } while (tag.length() < 2 || !USED_TAGS.add(tag));
        return tag;
    }

    @GameTest
    public void groupRolesAndPermissions(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer leader = player(helper);
        ServerPlayer recruit = player(helper);
        ServerPlayer outsider = player(helper);
        Group group = social.groups().create(leader, "Team " + System.nanoTime(), uniqueTag());
        check(helper, group != null, "no se creó el grupo");
        check(helper, social.groups().invite(leader, group.id, id(recruit)), "invitar");
        check(helper, social.groups().accept(recruit, group.id), "aceptar invitación");
        check(helper, group.roleOf(recruit.getUUID()) == Role.RECRUIT, "el nuevo miembro no es recluta");
        check(helper, !social.groups().kick(recruit, group.id, id(leader)), "un recluta expulsó al líder");
        check(helper, !social.groups().accept(outsider, group.id), "se pudo entrar sin invitación");
        String general = ConversationId.group(group.id, "general").key();
        check(helper, social.chat().send(recruit, general, "hola equipo"), "un miembro no pudo escribir");
        check(helper, !social.chat().send(outsider, general, "intruso"), "un ajeno escribió en el grupo");
        check(helper, social.groups().changeRole(leader, group.id, id(recruit), true), "ascender");
        check(helper, group.roleOf(recruit.getUUID()) == Role.MEMBER, "el ascenso no se aplicó");
        check(helper, social.groups().createChannel(leader, group.id, "oficiales", "officer"), "crear canal");
        check(helper, !social.chat().send(recruit, ConversationId.group(group.id, "oficiales").key(), "x"),
                "un miembro escribió en un canal de oficiales");
        check(helper, social.groups().kick(leader, group.id, id(recruit)), "expulsar");
        check(helper, !group.isMember(recruit.getUUID()), "la expulsión no se aplicó");
        social.groups().disbandInternal(group, "test");
        check(helper, social.groups().get(group.id) == null, "disolver");
        helper.succeed();
    }

    @GameTest
    public void leaderLeavingPromotesHeir(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer leader = player(helper);
        ServerPlayer member = player(helper);
        Group group = social.groups().create(leader, "Heirs " + System.nanoTime(), uniqueTag());
        check(helper, group != null, "crear");
        social.groups().invite(leader, group.id, id(member));
        social.groups().accept(member, group.id);
        social.groups().leave(leader, group.id);
        check(helper, group.roleOf(member.getUUID()) == Role.LEADER, "el grupo se quedó sin líder");
        social.groups().leave(member, group.id);
        check(helper, social.groups().get(group.id) == null, "un grupo vacío debe disolverse");
        helper.succeed();
    }

    @GameTest
    public void partyIsTemporary(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer a = player(helper);
        ServerPlayer b = player(helper);
        Group party = social.groups().createParty(a);
        check(helper, party != null && party.party, "crear party");
        check(helper, social.groups().invite(a, party.id, id(b)), "invitar");
        check(helper, social.groups().accept(b, "party"), "aceptar con /party accept");
        check(helper, social.groups().partyOf(b.getUUID()) == party, "no está en la party");
        social.groups().leave(a, party.id);
        social.groups().leave(b, party.id);
        check(helper, social.groups().get(party.id) == null, "la party no desapareció al quedarse vacía");
        helper.succeed();
    }

    // ---------- Compartir ----------

    @GameTest(maxTicks = 60)
    public void sharedItemAndCoordsAreServerGenerated(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer a = player(helper);
        ServerPlayer b = player(helper);
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        check(helper, social.chat().send(a, "dm:" + b.getUUID(), "mira [item] en [coords] [item]"), "enviar");
        String key = ConversationId.direct(a.getUUID(), b.getUUID()).key();
        helper.succeedWhen(() -> {
            Conversation conversation = conversation(social, key);
            check(helper, conversation != null && !conversation.messages.isEmpty(), "no guardado");
            var message = conversation.messages.getLast();
            check(helper, message.attachments.size() == 2, "se esperaban 2 adjuntos (uno de cada), hay " + message.attachments.size());
            var view = social.chat().toView(message);
            check(helper, view.attachments().stream().anyMatch(v -> v.isItem() && v.item() != null
                    && v.item().create().is(Items.DIAMOND_SWORD)), "el ítem no se reconstruyó");
            check(helper, message.text.contains("(item)"), "el segundo [item] debía escaparse");
        });
    }

    // ---------- Moderación ----------

    @GameTest
    public void dataDeleteRemovesRelations(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer a = player(helper);
        ServerPlayer b = player(helper);
        social.friends().request(a, id(b));
        social.friends().accept(b, id(a));
        social.moderation().delete(a.getUUID(), "test");
        PlayerRecord recordB = social.record(b);
        check(helper, !recordB.friends.contains(a.getUUID()), "la amistad sobrevivió al borrado");
        PlayerRecord recordA = social.record(a);
        check(helper, recordA.friends.isEmpty(), "el perfil no se reinició");
        helper.succeed();
    }

    // ---------- SOCIALMOD_ERRORES (v0.2.0) ----------

    /** Error 1: el estado elegido llega al snapshot (antes el botón leía el valor viejo y no rotaba). */
    @GameTest
    public void statusChangeIsVisibleInSnapshot(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer alex = player(helper);
        for (com.takumistudios.socialmod.common.model.PresenceStatus status : List.of(
                com.takumistudios.socialmod.common.model.PresenceStatus.AWAY,
                com.takumistudios.socialmod.common.model.PresenceStatus.DND,
                com.takumistudios.socialmod.common.model.PresenceStatus.INVISIBLE,
                com.takumistudios.socialmod.common.model.PresenceStatus.ONLINE)) {
            social.presence().setStatus(alex, status);
            check(helper, social.snapshots().build(alex).self.status.equals(status.id()), "el snapshot no refleja " + status.id());
        }
        helper.succeed();
    }

    /** Error 3: icono y color al crear, validación del icono y etiqueta con icono y rol para los nametags. */
    @GameTest
    public void groupStyleIconAndNametagEntry(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer leader = player(helper);
        Group group = social.groups().create(leader, "Estilo " + System.nanoTime(), uniqueTag(), "swords", "#FF0000");
        check(helper, group != null, "crear con estilo");
        check(helper, group.icon.equals("swords") && group.color == 0xFF0000, "estilo no aplicado: " + group.icon + " " + group.color);
        check(helper, !social.groups().setText(leader, group.id, "icon", "<b>"), "icono fuera de la lista aceptado");
        check(helper, social.groups().setText(leader, group.id, "icon", "crown"), "cambiar icono");
        social.record(leader).mainGroup = group.id;
        var entry = social.groups().nametags().entryFor(leader.getUUID());
        check(helper, entry.tag().isEmpty(), "ordinary group must not become TEAM automatically");
        social.groups().disbandInternal(group, "test");
        helper.succeed();
    }

    /** Error 4: los marcadores se resuelven en la vista previa que usan los toasts, también en mayúsculas. */
    @GameTest(maxTicks = 60)
    public void toastPreviewResolvesPlaceholders(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer a = player(helper);
        ServerPlayer b = player(helper);
        check(helper, social.chat().send(a, "dm:" + b.getUUID(), "estoy en [CORDS]"), "envío");
        String key = ConversationId.direct(a.getUUID(), b.getUUID()).key();
        helper.succeedWhen(() -> {
            Conversation conversation = conversation(social, key);
            check(helper, conversation != null && !conversation.messages.isEmpty(), "no guardado");
            var message = conversation.messages.getLast();
            String preview = com.takumistudios.socialmod.common.text.MessageFormatter.preview(message.text,
                    social.chat().toView(message).attachments(), 80);
            check(helper, !preview.contains("[") && preview.contains("x: " + a.getBlockX()), "vista previa sin resolver: " + preview);
        });
    }

    /**
     * Carga (PLAN 18): 200 jugadores en un grupo, cada uno con 5 mensajes de grupo y 1 privado (1200 mensajes).
     * Mide el coste en el hilo del servidor; el presupuesto es generoso para CI pero detecta regresiones graves.
     */
    @GameTest(maxTicks = 400)
    public void loadTwoHundredPlayers(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerConfig original = ServerConfig.get();
        ServerConfig config = new ServerConfig();
        config.antiSpam.enabled = false;
        config.limits.maxMembersPerGroup = 250;
        ServerConfig.set(config);
        social.reconfigure();
        List<ServerPlayer> players = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            players.add(player(helper));
        }
        ServerPlayer leader = players.getFirst();
        Group group = social.groups().create(leader, "Carga " + System.nanoTime(), uniqueTag());
        check(helper, group != null, "crear grupo de carga");
        long start = System.nanoTime();
        for (ServerPlayer member : players.subList(1, players.size())) {
            social.groups().invite(leader, group.id, id(member));
            social.groups().accept(member, group.id);
        }
        check(helper, group.members.size() == 200, "miembros: " + group.members.size());
        String general = ConversationId.group(group.id, "general").key();
        int sent = 0;
        for (int round = 0; round < 5; round++) {
            for (ServerPlayer member : players) {
                if (social.chat().send(member, general, "mensaje " + round + " de " + member.getUUID())) {
                    sent++;
                }
            }
        }
        List<String> direct = new java.util.ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer from = players.get(i);
            ServerPlayer to = players.get((i + 1) % players.size());
            if (social.chat().send(from, "dm:" + to.getUUID(), "hola " + i)) {
                sent++;
            }
            direct.add(ConversationId.direct(from.getUUID(), to.getUUID()).key());
        }
        SnapshotPerformanceProbe.measure(social, players);
        long millis = (System.nanoTime() - start) / 1_000_000;
        com.takumistudios.socialmod.SocialMod.LOGGER.info("[SocialMod] Carga: 200 jugadores, {} mensajes en {} ms", sent, millis);
        check(helper, sent == 1200, "mensajes aceptados: " + sent);
        check(helper, millis < 20_000, "demasiado lento: " + millis + " ms");
        // La config es global y los tests del lote corren a la vez: se restaura en este mismo tick
        ServerConfig.set(original);
        social.reconfigure();
        helper.succeedWhen(() -> {
            // Los 200 privados se cargan y guardan en el hilo de E/S: todos deben acabar con su mensaje
            for (String key : direct) {
                Conversation conversation = conversation(social, key);
                check(helper, conversation != null && conversation.messages.size() == 1, "privado sin guardar: " + key);
            }
            direct.forEach(social.storage()::deleteConversation);
            social.groups().disbandInternal(group, "test");
        });
    }

    /** Open Parties and Claims (solo con -PcompatPack): el grupo enlazado se refleja en la party del líder y al revés. */
    @GameTest
    public void claimsSyncWithOpenPartiesAndClaims(GameTestHelper helper) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("openpartiesandclaims")) {
            helper.succeed();
            return;
        }
        SocialServer social = social(helper);
        ServerConfig original = ServerConfig.get();
        try {
            ServerConfig config = new ServerConfig();
            config.integrations.claimsSync = "both";
            ServerConfig.set(config);
            social.reconfigure();
            ServerPlayer leader = player(helper);
            ServerPlayer member = player(helper);
            Group group = social.groups().create(leader, "Claims " + System.nanoTime(), uniqueTag());
            check(helper, group != null, "crear");
            social.groups().invite(leader, group.id, id(member));
            social.groups().accept(member, group.id);
            check(helper, social.groups().setClaimsLink(leader, group.id, true), "enlazar");
            var parties = xaero.pac.common.server.api.OpenPACServerAPI.get(social.server()).getPartyManager();
            var party = parties.getPartyByOwner(leader.getUUID());
            check(helper, party != null && party.getMemberInfo(member.getUUID()) != null, "el miembro no entró en la party de OPAC");
            // OPAC → grupo
            java.util.UUID outsider = java.util.UUID.randomUUID();
            party.addMember(outsider, xaero.pac.common.parties.party.member.PartyMemberRank.MEMBER, "Fuera");
            social.claims().syncAll();
            check(helper, group.isMember(outsider), "quien entra en la party debe entrar al grupo");
            // grupo → OPAC
            social.groups().kick(leader, group.id, id(member));
            social.claims().syncAll();
            check(helper, party.getMemberInfo(member.getUUID()) == null, "el expulsado sigue en la party");
            parties.removePartyByOwner(leader.getUUID());
            social.groups().disbandInternal(group, "test");
        } finally {
            ServerConfig.set(original);
            social.reconfigure();
        }
        helper.succeed();
    }

    /** Simple Voice Chat (solo con -PcompatPack): el plugin se registra y un jugador sin el mod de voz no rompe nada. */
    @GameTest(maxTicks = 200)
    public void voiceChatIntegrationIsSafe(GameTestHelper helper) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat")) {
            helper.succeed();
            return;
        }
        SocialServer social = social(helper);
        ServerPlayer leader = player(helper);
        Group group = social.groups().create(leader, "Voz " + System.nanoTime(), uniqueTag());
        check(helper, group != null, "crear");
        helper.succeedWhen(() -> {
            check(helper, social.voice().available(), "el plugin de Simple Voice Chat no se registró");
            check(helper, !social.voice().join(leader, group.id), "un jugador sin el mod de voz no debe entrar");
            check(helper, social.voice().currentGroup(leader.getUUID()) == null, "no debe estar en ningún grupo de voz");
            social.groups().disbandInternal(group, "test");
        });
    }

    /** Menú de cofre para jugadores sin el mod: se abre, no deja coger ítems y el clic en el estado lo rota. */
    @GameTest
    public void vanillaChestMenu(GameTestHelper helper) {
        SocialServer social = social(helper);
        ServerPlayer alex = player(helper);
        ServerPlayer luna = player(helper);
        social.friends().request(alex, id(luna));
        social.friends().accept(luna, id(alex));
        social.presence().setStatus(alex, com.takumistudios.socialmod.common.model.PresenceStatus.ONLINE);
        com.takumistudios.socialmod.server.menu.SocialMenu.open(social, alex);
        check(helper, alex.containerMenu instanceof com.takumistudios.socialmod.server.menu.SocialMenu, "el menú no se abrió");
        var menu = alex.containerMenu;
        check(helper, !menu.getSlot(9).getItem().isEmpty(), "el amigo no aparece en el menú");
        menu.clicked(0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, alex);
        check(helper, social.record(alex).status == com.takumistudios.socialmod.common.model.PresenceStatus.AWAY, "el clic no rotó el estado");
        check(helper, menu.getCarried().isEmpty(), "se pudo coger un ítem del menú");
        menu.clicked(9, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, alex);
        check(helper, alex.getInventory().isEmpty(), "un ítem del menú acabó en el inventario");
        alex.closeContainer();
        helper.succeed();
    }
}
