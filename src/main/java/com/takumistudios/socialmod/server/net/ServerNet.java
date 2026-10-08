package com.takumistudios.socialmod.server.net;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.common.model.PresenceStatus;
import com.takumistudios.socialmod.common.model.Privacy;
import com.takumistudios.socialmod.common.model.Role;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.common.net.SocialAction;
import com.takumistudios.socialmod.common.text.TextSanitizer;
import com.takumistudios.socialmod.server.PermissionBridge;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Receptores C→S. Cada paquete pasa por el rate limit del jugador y un try/catch: un paquete malformado o
 * malicioso se ignora y se registra, nunca tumba el servidor (PLAN 4.3 y 14).
 */
public final class ServerNet {
    private ServerNet() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(Payloads.PlayerSearchC2S.TYPE, (payload, context) ->
            guarded(context.player(), "player_search", 2, social -> {
                var player = context.player();
                if (payload.request() < 0 || payload.page() < 0 || payload.page() > 10000 || payload.query().length() > 32) return;
                var candidates = social.storage().players().stream().map(record -> new com.takumistudios.socialmod.common.model.PlayerSearch.Entry(
                    record.id, record.name, social.presence().visibleStatus(player.getUUID(), record.id).id())).toList();
                social.send(player, new Payloads.PlayerSearchS2C(com.takumistudios.socialmod.common.model.PlayerSearch.find(payload.request(), payload.query(), payload.page(), player.getUUID(), candidates)));
            }));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.HelloC2S.TYPE, (payload, context) ->
                guarded(context.player(), "hello", 1, social -> social.onHello(context.player(), payload.protocol())));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.SendC2S.TYPE, (payload, context) ->
                guarded(context.player(), "send", 1, social -> social.chat().send(context.player(), payload.target(), payload.text())));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.MessageOpC2S.TYPE, (payload, context) ->
                guarded(context.player(), "message_op", 1, social -> social.chat().editOrDelete(context.player(), payload.op(),
                        payload.conversation(), payload.messageId(), payload.text())));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.HistoryC2S.TYPE, (payload, context) ->
                guarded(context.player(), "history", 2, social -> social.chat().history(context.player(), payload.conversation(), payload.beforeId())));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.SignalC2S.TYPE, (payload, context) ->
                guarded(context.player(), "signal", 0.5, social -> social.chat().signal(context.player(), payload.signal(), payload.conversation(), payload.value())));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.ActionC2S.TYPE, (payload, context) ->
                guarded(context.player(), "action", payload.action() == SocialAction.VISUAL_PUBLISH ? 0.05 : 1, social -> handleAction(social, context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.PingC2S.TYPE, (payload, context) ->
                guarded(context.player(), "ping", 2, social -> social.party().ping(context.player(), payload.x(), payload.y(), payload.z())));
    }

    private interface Handler {
        void handle(SocialServer social);
    }

    private static void guarded(ServerPlayer player, String name, double cost, Handler handler) {
        SocialServer social = SocialServer.get();
        if (social == null) {
            return;
        }
        // Todos los paquetes salvo el handshake exigen un cliente con protocolo compatible
        if (!name.equals("hello") && !social.hasMod(player.getUUID())) {
            return;
        }
        if (!social.packetLimiter().tryAcquire(player.getUUID(), cost)) {
            SocialMod.LOGGER.debug("[SocialMod] Paquete {} de {} descartado por rate limit", name, player.getGameProfile().name());
            return;
        }
        try {
            handler.handle(social);
        } catch (RuntimeException e) {
            SocialMod.warnOnce("packet_" + name, "Error procesando el paquete " + name + " de " + player.getGameProfile().name(), e);
        }
    }

    /** Las mismas acciones que los comandos: el cliente no tiene más poder que un jugador vanilla. */
    static void handleAction(SocialServer social, ServerPlayer player, Payloads.ActionC2S payload) {
        SocialAction action = payload.action();
        if (action == null) {
            return;
        }
        String a = payload.a();
        String b = payload.b();
        ServerConfig config = ServerConfig.get();
        PlayerRecord record = social.record(player);
        switch (action) {
            case TEAM_BANNER -> social.teams().banner(player, a, b);
            case VISUAL_ORIGINAL -> {
                if (PermissionBridge.isStaff(player, "admin.visuals") && (a.equals("true") || a.equals("false"))) social.visuals().setOriginalInterface(Boolean.parseBoolean(a), player.getGameProfile().name());
            }
            case VISUAL_PERSONAL_ORIGINAL -> {
                if (a.equals("true") || a.equals("false")) social.visuals().setPersonalOriginal(player, Boolean.parseBoolean(a));
            }
            case VISUAL_ROLLBACK -> {
                if (PermissionBridge.isStaff(player, "admin.visuals")) social.visuals().rollback(player.getGameProfile().name());
            }
            case VISUAL_PUBLISH -> {
                if (PermissionBridge.isStaff(player, "admin.visuals")) social.visuals().receive(player, a, b);
            }
            case TEAM_LIMIT -> {
                if (PermissionBridge.isStaff(player, PermissionBridge.TEAM_ADMIN)) {
                    int limit = Integer.parseInt(a);
                    if (limit >= 1 && limit <= 1000) { config.maxTeams = limit; social.visuals().persistConfig();
                        social.server().getPlayerList().getPlayers().forEach(social.snapshots()::send); }
                }
            }
            case TEAM_CREATE -> social.teams().create(player, a, b);
            case TEAM_CHOOSE -> social.teams().choose(player, a);
            case TEAM_ASSIGN, TEAM_RESET, TEAM_ARCHIVE, TEAM_RESTORE, TEAM_RENAME, TEAM_STYLE -> {
                if (PermissionBridge.isStaff(player, PermissionBridge.TEAM_ADMIN) && !social.teams().admin(action, a, b, player.getGameProfile().name())) social.notifier().feedback(player, false, "socialmod.team.invalid");
            }
            case FRIEND_REQUEST -> social.friends().request(player, a);
            case FRIEND_ACCEPT -> social.friends().accept(player, a);
            case FRIEND_DENY -> social.friends().deny(player, a);
            case FRIEND_REMOVE -> social.friends().remove(player, a);
            case FRIEND_FAVORITE -> social.friends().toggleFavorite(player, a);
            case FRIEND_NOTE -> social.friends().setNote(player, a, b);
            case BLOCK -> social.friends().block(player, a);
            case UNBLOCK -> social.friends().unblock(player, a);
            case SET_STATUS -> {
                social.presence().setStatus(player, PresenceStatus.byId(a));
                // Sin esto el botón de estado del cliente leía el snapshot viejo y no rotaba
                social.snapshots().send(player);
            }
            case SET_CUSTOM_STATUS -> {
                record.customStatus = TextSanitizer.clean(a, config.chat.maxStatusLength);
                social.storage().markPlayersDirty();
                social.presence().markChanged(player.getUUID());
                social.snapshots().send(player);
            }
            case SET_PRIVACY_MESSAGES -> updateRecord(social, player, r -> r.whoCanMessage = Privacy.byId(a));
            case SET_PRIVACY_STATUS -> updateRecord(social, player, r -> r.whoSeesStatus = Privacy.byId(a));
            case SET_SHOW_DIMENSION -> updateRecord(social, player, r -> r.showDimension = Boolean.parseBoolean(a));
            case SET_READ_RECEIPTS -> updateRecord(social, player, r -> r.readReceipts = Boolean.parseBoolean(a));
            case SET_TYPING_INDICATOR -> updateRecord(social, player, r -> r.typingIndicator = Boolean.parseBoolean(a));
            case GROUP_CREATE -> {
                // b = "TAG" o "TAG;icono;#RRGGBB" (la pantalla de crear grupo manda también el estilo)
                String[] parts = b.split(";", 3);
                social.groups().create(player, a, parts[0], parts.length > 1 ? parts[1] : null, parts.length > 2 ? parts[2] : null);
            }
            case GROUP_INVITE -> social.groups().invite(player, a, b);
            case GROUP_ACCEPT -> social.groups().accept(player, a);
            case GROUP_DECLINE -> social.groups().decline(player, a);
            case GROUP_LEAVE -> social.groups().leave(player, a);
            case GROUP_KICK -> social.groups().kick(player, a, b);
            case GROUP_PROMOTE -> social.groups().changeRole(player, a, b, true);
            case GROUP_DEMOTE -> social.groups().changeRole(player, a, b, false);
            case GROUP_TRANSFER -> social.groups().transfer(player, a, b);
            case GROUP_SET_MAIN -> social.groups().setMain(player, a);
            case GROUP_SET_MOTD -> social.groups().setText(player, a, "motd", b);
            case GROUP_SET_DESCRIPTION -> social.groups().setText(player, a, "description", b);
            case GROUP_SET_COLOR -> social.groups().setText(player, a, "color", b);
            case GROUP_SET_TAG -> social.groups().setText(player, a, "tag", b);
            case GROUP_SET_ICON -> social.groups().setText(player, a, "icon", b);
            case VOICE_JOIN -> social.voice().join(player, a);
            case VOICE_LEAVE -> social.voice().leave(player);
            case GROUP_PIN -> social.groups().setText(player, a, "pinned", b);
            case GROUP_CHANNEL_CREATE -> {
                String[] parts = b.split(" ", 2);
                social.groups().createChannel(player, a, parts[0], parts.length > 1 ? parts[1] : Role.RECRUIT.id());
            }
            case GROUP_CHANNEL_DELETE -> social.groups().deleteChannel(player, a, b);
            case GROUP_DISBAND -> social.groups().disband(player, a);
            case GROUP_EVENT_CREATE -> {
                String[] parts = b.split(" ", 2);
                try {
                    social.groups().createEvent(player, a, Integer.parseInt(parts[0]), parts.length > 1 ? parts[1] : "");
                } catch (NumberFormatException e) {
                    social.notifier().feedback(player, false, "socialmod.event.invalid");
                }
            }
            case GROUP_EVENT_DELETE -> social.groups().deleteEvent(player, a, b);
            case PARTY_CREATE -> social.groups().createParty(player);
            case PARTY_INVITE -> {
                Group party = social.groups().partyOf(player.getUUID());
                if (party == null) {
                    party = social.groups().createParty(player);
                }
                if (party != null) {
                    social.groups().invite(player, party.id, a);
                }
            }
            case PANEL_OPEN -> { social.presence().setPanelOpen(player, true); social.snapshots().resend(player); }
            case PANEL_CLOSE -> social.presence().setPanelOpen(player, false);
            case REPORT_MESSAGE -> {
                try {
                    social.moderation().report(player, a, Long.parseLong(b));
                } catch (NumberFormatException ignored) {
                    // id inválido
                }
            }
            case DATA_EXPORT -> {
                if (config.moderation.allowDataExport && PermissionBridge.allows(player, PermissionBridge.DATA_EXPORT)) {
                    social.moderation().export(player.getUUID(), player);
                }
            }
        }
    }

    private static void updateRecord(SocialServer social, ServerPlayer player, java.util.function.Consumer<PlayerRecord> change) {
        change.accept(social.record(player));
        social.storage().markPlayersDirty();
        social.presence().markChanged(player.getUUID());
        social.snapshots().send(player);
    }
}
