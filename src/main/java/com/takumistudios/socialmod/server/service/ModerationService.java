package com.takumistudios.socialmod.server.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.takumistudios.socialmod.common.model.ConversationId;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.common.text.MessageFormatter;
import com.takumistudios.socialmod.server.Lang;
import com.takumistudios.socialmod.server.PermissionBridge;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.ChatMessage;
import com.takumistudios.socialmod.server.data.Conversation;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import com.takumistudios.socialmod.server.storage.SocialStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Moderación (PLAN 9): silencios, reportes con contexto, lectura de historiales, disolver grupos, inspección,
 * "spy" opcional y visible, y los derechos sobre los datos (exportar y borrar). Todo queda en el registro de auditoría.
 */
public final class ModerationService {
    private final SocialServer social;
    private final Set<UUID> spies = new HashSet<>();

    public ModerationService(SocialServer social) {
        this.social = social;
    }

    // ---------- Silencios ----------

    /** @param actor null si es automático (anti-spam). {@code seconds <= 0}: indefinido (10 años). */
    public void mute(@Nullable CommandSourceStack actor, PlayerRecord target, long seconds, String reason) {
        long duration = seconds <= 0 ? 315_360_000_000L : seconds * 1000L;
        target.mutedUntil = System.currentTimeMillis() + duration;
        target.muteReason = reason == null ? "" : reason;
        social.storage().markPlayersDirty();
        social.storage().audit("MUTE " + (actor == null ? "auto" : actor.getTextName()) + " " + target.name + " " + seconds + "s " + target.muteReason);
        ServerPlayer online = social.online(target.id);
        if (online != null) {
            online.sendSystemMessage(Lang.tr(online, actor == null ? "socialmod.mod.auto_muted" : "socialmod.mod.you_are_muted",
                    seconds <= 0 ? "∞" : String.valueOf(seconds), target.muteReason).withStyle(ChatFormatting.RED));
            social.snapshots().send(online);
        }
    }

    public boolean unmute(CommandSourceStack actor, PlayerRecord target) {
        if (target.mutedUntil <= System.currentTimeMillis()) {
            return false;
        }
        target.mutedUntil = 0;
        target.muteReason = "";
        social.storage().markPlayersDirty();
        social.storage().audit("UNMUTE " + actor.getTextName() + " " + target.name);
        ServerPlayer online = social.online(target.id);
        if (online != null) {
            online.sendSystemMessage(Lang.tr(online, "socialmod.mod.unmuted").withStyle(ChatFormatting.GREEN));
            social.snapshots().send(online);
        }
        return true;
    }

    // ---------- Spy (opcional y visible) ----------

    public boolean toggleSpy(ServerPlayer staff) {
        if (!spies.remove(staff.getUUID())) {
            spies.add(staff.getUUID());
            social.storage().audit("SPY_ON " + staff.getGameProfile().name());
            return true;
        }
        social.storage().audit("SPY_OFF " + staff.getGameProfile().name());
        return false;
    }

    public void spy(ConversationId conversation, ChatMessage message, Payloads.MessageView view) {
        if (spies.isEmpty()) {
            return;
        }
        String receiver = social.storage().nameOf(conversation.other(message.sender));
        for (UUID id : Set.copyOf(spies)) {
            ServerPlayer staff = social.online(id);
            if (staff == null || conversation.involves(id) || !PermissionBridge.isStaff(staff, PermissionBridge.MOD_SPY)) {
                continue;
            }
            staff.sendSystemMessage(Component.literal("[Spy] " + message.senderName + " → " + receiver + ": ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(MessageFormatter.format(message.text, view.attachments(), false, Set.of()).withStyle(ChatFormatting.GRAY)));
        }
    }

    // ---------- Reportes ----------

    public void report(ServerPlayer reporter, String target, long messageId) {
        ServerConfig config = ServerConfig.get();
        if (!config.moderation.reportsEnabled) {
            social.notifier().feedback(reporter, false, "socialmod.error.module_disabled");
            return;
        }
        ConversationId conversation = social.chat().resolveTarget(reporter.getUUID(), target);
        if (conversation == null || !social.chat().canRead(reporter.getUUID(), conversation)) {
            return;
        }
        social.storage().withConversation(conversation.key(), loaded -> {
            ChatMessage message = loaded.find(messageId);
            if (message == null || message.sender.equals(reporter.getUUID())) {
                social.notifier().feedback(reporter, false, "socialmod.report.invalid");
                return;
            }
            JsonObject report = new JsonObject();
            long now = System.currentTimeMillis();
            String id = now + "-" + reporter.getUUID().toString().substring(0, 8);
            report.addProperty("id", id);
            report.addProperty("time", Instant.ofEpochMilli(now).toString());
            report.addProperty("reporter", reporter.getGameProfile().name());
            report.addProperty("reporterUuid", reporter.getUUID().toString());
            report.addProperty("reported", message.senderName);
            report.addProperty("reportedUuid", message.sender.toString());
            report.addProperty("conversation", conversation.key());
            report.addProperty("messageId", messageId);
            report.add("context", context(loaded, messageId, config.moderation.reportContext));
            social.storage().writeDocument(SocialStorage.REPORTS, id, report);
            social.storage().audit("REPORT " + reporter.getGameProfile().name() + " -> " + message.senderName + " " + conversation.key() + "#" + messageId);
            social.notifier().feedback(reporter, true, "socialmod.report.sent");
            for (ServerPlayer player : social.server().getPlayerList().getPlayers()) {
                if (PermissionBridge.isStaff(player, PermissionBridge.MOD_REPORTS)) {
                    social.notifier().notify(player, Payloads.NotifyKind.SYSTEM, message.sender, "socialmod.notify.report",
                            new Object[]{reporter.getGameProfile().name(), message.senderName}, MessageFormatter.preview(message.text, social.chat().toView(message).attachments(), 80), "");
                }
            }
        });
    }

    private static JsonArray context(Conversation conversation, long messageId, int around) {
        JsonArray array = new JsonArray();
        List<ChatMessage> messages = conversation.messages;
        int index = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).id == messageId) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return array;
        }
        for (int i = Math.max(0, index - around); i <= Math.min(messages.size() - 1, index + around); i++) {
            ChatMessage message = messages.get(i);
            JsonObject line = new JsonObject();
            line.addProperty("id", message.id);
            line.addProperty("time", Instant.ofEpochMilli(message.time).toString());
            line.addProperty("sender", message.senderName);
            line.addProperty("text", message.deleted ? "[deleted]" : message.text);
            line.addProperty("reported", message.id == messageId);
            array.add(line);
        }
        return array;
    }

    public void listReports(CommandSourceStack source, int limit) {
        social.storage().readDocuments(SocialStorage.REPORTS, limit, reports -> {
            source.sendSuccess(() -> Lang.tr("socialmod.mod.reports_header", reports.size()).withStyle(ChatFormatting.GOLD), false);
            for (JsonObject report : reports) {
                String line = string(report, "time") + "  " + string(report, "reporter") + " → " + string(report, "reported")
                        + "  (" + string(report, "conversation") + "#" + string(report, "messageId") + ")";
                source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.YELLOW), false);
                if (report.get("context") instanceof JsonArray context) {
                    for (JsonElement element : context) {
                        JsonObject message = element.getAsJsonObject();
                        boolean reported = message.has("reported") && message.get("reported").getAsBoolean();
                        source.sendSuccess(() -> Component.literal((reported ? " > " : "   ") + string(message, "sender") + ": " + string(message, "text"))
                                .withStyle(reported ? ChatFormatting.RED : ChatFormatting.GRAY), false);
                    }
                }
            }
        });
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    // ---------- Historial e inspección ----------

    /** Últimos mensajes de una conversación (para el staff). */
    public void history(CommandSourceStack source, ConversationId conversation, int count) {
        social.storage().audit("HISTORY " + source.getTextName() + " " + conversation.key());
        social.storage().withConversation(conversation.key(), loaded -> {
            List<ChatMessage> page = loaded.page(0, count);
            source.sendSuccess(() -> Component.literal("— " + conversation.key() + " (" + page.size() + ") —").withStyle(ChatFormatting.GOLD), false);
            for (ChatMessage message : page) {
                String text = message.deleted ? "[deleted]" : message.text;
                source.sendSuccess(() -> Component.literal("#" + message.id + " " + Instant.ofEpochMilli(message.time) + " " + message.senderName + ": ")
                        .withStyle(ChatFormatting.GRAY).append(Component.literal(text).withStyle(ChatFormatting.WHITE)), false);
            }
        });
    }

    public void inspect(CommandSourceStack source, PlayerRecord record) {
        social.storage().audit("INSPECT " + source.getTextName() + " " + record.name);
        long now = System.currentTimeMillis();
        source.sendSuccess(() -> Component.literal("— " + record.name + " (" + record.id + ") —").withStyle(ChatFormatting.GOLD), false);
        line(source, "Estado", record.status.id() + (record.customStatus.isEmpty() ? "" : " \"" + record.customStatus + "\""));
        line(source, "Visto", Instant.ofEpochMilli(record.lastSeen).toString());
        line(source, "Amigos", String.valueOf(record.friends.size()));
        line(source, "Bloqueados", String.valueOf(record.blocked.size()));
        line(source, "Grupos", String.join(", ", social.groups().groupsOf(record.id).stream().map(g -> g.party ? "party" : g.name + " [" + g.tag + "] " + g.roleOf(record.id).id()).toList()));
        line(source, "Silenciado", record.isMuted(now) ? ((record.mutedUntil - now) / 1000) + " s (" + record.muteReason + ")" : "no");
        line(source, "Sin leer", String.valueOf(record.totalUnread()));
        line(source, "Privacidad", "mensajes=" + record.whoCanMessage.id() + ", estado=" + record.whoSeesStatus.id());
        line(source, "Conversaciones", String.join(", ", record.recent.stream().limit(10).toList()));
    }

    private static void line(CommandSourceStack source, String label, String value) {
        source.sendSuccess(() -> Component.literal(" " + label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
    }

    public void disband(CommandSourceStack source, Group group) {
        social.groups().disbandInternal(group, source.getTextName());
    }

    // ---------- Datos personales (PLAN 9) ----------

    /** Exporta los datos de un jugador a {@code <mundo>/socialmod/exports/<uuid>.json.gz} (incluidos sus mensajes). */
    public void export(UUID playerId, @Nullable ServerPlayer requester) {
        PlayerRecord record = social.storage().player(playerId);
        if (record == null) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("exportedAt", Instant.now().toString());
        root.add("profile", SocialStorage.gson().toJsonTree(record));
        JsonArray groups = new JsonArray();
        for (Group group : social.groups().groupsOf(playerId)) {
            JsonObject g = new JsonObject();
            g.addProperty("id", group.id);
            g.addProperty("name", group.name);
            g.addProperty("tag", group.tag);
            g.addProperty("role", group.roleOf(playerId).id());
            g.addProperty("party", group.party);
            groups.add(g);
        }
        root.add("groups", groups);
        JsonArray messages = new JsonArray();
        root.add("messages", messages);
        social.storage().editAllConversations(conversation -> {
            for (ChatMessage message : conversation.messages) {
                if (playerId.equals(message.sender) && !message.deleted) {
                    JsonObject m = new JsonObject();
                    m.addProperty("conversation", conversation.id);
                    m.addProperty("time", Instant.ofEpochMilli(message.time).toString());
                    m.addProperty("text", message.text);
                    synchronized (messages) {
                        messages.add(m);
                    }
                }
            }
            return false;
        }, () -> {
            // Cuando se han recorrido todas las conversaciones (también las del disco, por lotes)
            social.storage().writeDocument(SocialStorage.EXPORTS, playerId.toString(), root);
            social.storage().audit("DATA_EXPORT " + record.name);
            if (requester != null) {
                social.notifier().feedback(requester, true, "socialmod.data.exported", "socialmod/exports/" + playerId + ".json.gz");
            }
        });
    }

    /** Borra los datos sociales de un jugador: perfil, relaciones, grupos y el texto de sus mensajes. */
    public void delete(UUID playerId, String actor) {
        PlayerRecord record = social.storage().player(playerId);
        if (record == null) {
            return;
        }
        for (Group group : social.groups().groupsOf(playerId)) {
            social.groups().removeMember(group, playerId, "socialmod.group.left");
        }
        for (PlayerRecord other : social.storage().players()) {
            boolean changed = other.friends.remove(playerId) | other.favorites.remove(playerId) | other.blocked.remove(playerId)
                    | other.incomingRequests.remove(playerId) | other.outgoingRequests.remove(playerId) | other.notes.remove(playerId) != null;
            if (playerId.equals(other.lastDirectPartner)) {
                other.lastDirectPartner = null;
            }
            if (changed) {
                ServerPlayer online = social.online(other.id);
                if (online != null) {
                    social.snapshots().send(online);
                }
            }
        }
        for (Group group : social.groups().all().values()) {
            group.invited.remove(playerId);
        }
        social.storage().removePlayer(playerId);
        social.storage().markGroupsDirty();
        social.storage().editAllConversations(conversation -> {
            boolean changed = false;
            for (ChatMessage message : conversation.messages) {
                if (playerId.equals(message.sender) && !message.deleted) {
                    message.deleted = true;
                    message.text = "";
                    message.senderName = "";
                    message.attachments.clear();
                    changed = true;
                }
            }
            return changed;
        });
        social.storage().audit("DATA_DELETE " + actor + " " + playerId);
        ServerPlayer online = social.online(playerId);
        if (online != null) {
            // Se vuelve a crear un perfil vacío para la sesión actual
            social.record(online);
            social.snapshots().send(online);
        }
    }

    public boolean isSpying(UUID staff) {
        return spies.contains(staff);
    }
}
