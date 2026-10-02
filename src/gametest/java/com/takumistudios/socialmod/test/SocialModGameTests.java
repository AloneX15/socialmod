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

    private static String id(ServerPlayer player) {
        return player.getUUID().toString();
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
            Conversation conversation = social.storage().cachedConversation(key);
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

    private static String uniqueTag() {
        return ("T" + Long.toString(System.nanoTime(), 36)).substring(0, 5).toUpperCase(java.util.Locale.ROOT);
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
            Conversation conversation = social.storage().cachedConversation(key);
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
}
