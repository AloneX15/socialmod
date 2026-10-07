package com.takumistudios.socialmod.server.service;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.api.MessageFilter;
import com.takumistudios.socialmod.api.event.ChatEvents;
import com.takumistudios.socialmod.common.model.ConversationId;
import com.takumistudios.socialmod.common.model.GroupPermission;
import com.takumistudios.socialmod.common.model.Privacy;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.common.text.MessageFormatter;
import com.takumistudios.socialmod.common.text.SafeMarkdown;
import com.takumistudios.socialmod.common.text.TextSanitizer;
import com.takumistudios.socialmod.server.Lang;
import com.takumistudios.socialmod.server.PermissionBridge;
import com.takumistudios.socialmod.server.ServerApiImpl;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.ChatMessage;
import com.takumistudios.socialmod.server.data.Conversation;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import com.takumistudios.socialmod.server.security.SpamGuard;
import com.takumistudios.socialmod.server.security.WordFilter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mensajes privados y de grupo (PLAN 5.1). Valida en el servidor: módulo activo, silencio, chat del cliente,
 * longitud, anti-spam, filtro de palabras, filtros de la API, privacidad, bloqueos y pertenencia al canal.
 * Guarda el mensaje en el historial y lo entrega: con el mod como payload; sin él como chat de sistema.
 */
public final class ChatService {
    private static final int MAX_INBOX_LINES = 10;

    private final SocialServer social;
    private SpamGuard spamGuard;
    private WordFilter wordFilter = WordFilter.empty();

    public ChatService(SocialServer social) {
        this.social = social;
        reconfigure();
    }

    public void reconfigure() {
        ServerConfig config = ServerConfig.get();
        SpamGuard.Settings settings = new SpamGuard.Settings(config.antiSpam.maxMessages, config.antiSpam.windowSeconds * 1000L,
                config.antiSpam.maxRepeats, config.antiSpam.repeatWindowSeconds * 1000L, config.antiSpam.strikesToMute,
                config.antiSpam.autoMuteSeconds * 1000L);
        if (spamGuard == null) {
            spamGuard = new SpamGuard(settings);
        } else {
            spamGuard.reconfigure(settings);
        }
        List<String> words = new ArrayList<>(config.filter.words);
        words.addAll(DatapackFilters.words());
        wordFilter = config.filter.enabled || !DatapackFilters.words().isEmpty()
                ? new WordFilter(words, config.filter.regex, "block".equalsIgnoreCase(config.filter.mode) ? WordFilter.Mode.BLOCK : WordFilter.Mode.CENSOR)
                : WordFilter.empty();
        wordFilter.errors().forEach(error -> SocialMod.LOGGER.warn("[SocialMod] Expresión del filtro ignorada: {}", error));
    }

    public void forget(UUID player) {
        spamGuard.forget(player);
    }

    // =====================================================================
    // Envío
    // =====================================================================

    /** Resuelve el destino que manda el cliente o un comando: {@code dm:<uuid>}, {@code dm:<a>:<b>} o {@code g:<id>:<canal>}. */
    public @Nullable ConversationId resolveTarget(UUID sender, String target) {
        if (target.startsWith("dm:") && target.length() == 3 + 36) {
            try {
                UUID other = UUID.fromString(target.substring(3));
                return other.equals(sender) ? null : ConversationId.direct(sender, other);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        ConversationId parsed = ConversationId.parse(target);
        // Un privado ajeno (dm:a:b sin ser a ni b) o consigo mismo no es un destino válido
        if (parsed != null && parsed.isDirect() && (!parsed.involves(sender) || parsed.a().equals(parsed.b()))) {
            return null;
        }
        return parsed;
    }

    /** Envía un mensaje. Devuelve {@code true} si se aceptó (la entrega puede completarse un instante después). */
    public boolean send(ServerPlayer sender, String target, String rawText) {
        ServerConfig config = ServerConfig.get();
        ConversationId conversation = resolveTarget(sender.getUUID(), target);
        if (conversation == null) {
            return false;
        }
        PlayerRecord record = social.record(sender);
        long now = System.currentTimeMillis();
        if (record.isMuted(now)) {
            social.notifier().feedback(sender, false, "socialmod.error.muted", Math.max(1, (record.mutedUntil - now) / 1000));
            return false;
        }
        if (sender.getChatVisibility() == ChatVisiblity.HIDDEN) {
            social.notifier().feedback(sender, false, "socialmod.error.chat_disabled");
            return false;
        }
        String text = TextSanitizer.clean(rawText, config.chat.maxMessageLength);
        if (text.isEmpty()) {
            return false;
        }
        if (TextSanitizer.length(TextSanitizer.clean(rawText, Payloads.MAX_TEXT)) > config.chat.maxMessageLength) {
            social.notifier().feedback(sender, false, "socialmod.error.too_long", config.chat.maxMessageLength);
            return false;
        }

        // Destino y permisos (antes del anti-spam para no penalizar errores de destino)
        String title;
        if (conversation.isDirect()) {
            if (!config.modules.privateMessages || !PermissionBridge.allows(sender, PermissionBridge.CHAT_PRIVATE)) {
                social.notifier().feedback(sender, false, "socialmod.error.module_disabled");
                return false;
            }
            UUID otherId = conversation.other(sender.getUUID());
            PlayerRecord other = social.storage().player(otherId);
            if (other == null) {
                social.notifier().feedback(sender, false, "socialmod.error.unknown_player", otherId);
                return false;
            }
            if (record.blocked.contains(otherId)) {
                social.notifier().feedback(sender, false, "socialmod.error.you_blocked", other.name);
                return false;
            }
            boolean bypass = PermissionBridge.isStaff(sender, PermissionBridge.MOD_BYPASS);
            if (!bypass && (other.blocked.contains(sender.getUUID())
                    || !PresenceService.privacyAllows(other.whoCanMessage, other.friends.contains(sender.getUUID())))) {
                social.notifier().feedback(sender, false, "socialmod.error.cannot_message", other.name);
                return false;
            }
            if (!config.modules.mailbox && social.online(otherId) == null) {
                social.notifier().feedback(sender, false, "socialmod.error.offline", other.name);
                return false;
            }
            title = other.name;
        } else {
            Group group = social.groups().get(conversation.groupId());
            if (group == null || group.archived || !social.groups().canAccess(group, sender.getUUID(), conversation.channel())) {
                social.notifier().feedback(sender, false, "socialmod.group.not_member");
                return false;
            }
            if (!(group.party ? config.modules.parties : config.modules.groups)) {
                social.notifier().feedback(sender, false, "socialmod.error.module_disabled");
                return false;
            }
            title = social.groups().title(group, conversation.channel());
        }

        // Anti-spam
        if (config.antiSpam.enabled && !PermissionBridge.isStaff(sender, PermissionBridge.MOD_BYPASS)) {
            SpamGuard.Verdict verdict = spamGuard.check(sender.getUUID(), text);
            switch (verdict) {
                case TOO_FAST -> {
                    social.notifier().feedback(sender, false, "socialmod.error.too_fast");
                    return false;
                }
                case REPEATED -> {
                    social.notifier().feedback(sender, false, "socialmod.error.repeated");
                    return false;
                }
                case AUTO_MUTED -> {
                    social.moderation().mute(null, record, config.antiSpam.autoMuteSeconds, "spam");
                    return false;
                }
                default -> {
                }
            }
        }

        // Filtro de palabras y filtros externos
        WordFilter.Result filtered = wordFilter.apply(text);
        if (filtered.blocked()) {
            social.notifier().feedback(sender, false, "socialmod.error.filtered");
            social.storage().audit("FILTER_BLOCK " + record.name + " " + conversation.key());
            return false;
        }
        text = filtered.text();
        for (MessageFilter filter : ServerApiImpl.filters()) {
            try {
                String result = filter.filter(sender, conversation.key(), text);
                if (result == null) {
                    social.notifier().feedback(sender, false, "socialmod.error.filtered");
                    return false;
                }
                text = TextSanitizer.clean(result, config.chat.maxMessageLength);
            } catch (RuntimeException e) {
                SocialMod.warnOnce("external_filter", "Un filtro de mensajes externo falló", e);
            }
        }
        if (!ChatEvents.ALLOW_MESSAGE.invoker().allowMessage(sender, conversation.key(), text)) {
            social.notifier().feedback(sender, false, "socialmod.error.filtered");
            return false;
        }

        // Adjuntos generados por el servidor
        List<ChatMessage.Attachment> attachments = new ArrayList<>();
        text = buildAttachments(sender, text, attachments);

        ChatMessage message = new ChatMessage(0, sender.getUUID(), record.name, text, now);
        message.attachments = attachments;
        String finalTitle = title;
        social.storage().withConversation(conversation.key(), loaded -> {
            if (!canCompleteSend(sender, conversation)) return;
            append(sender, conversation, loaded, message, finalTitle);
        }, () -> social.notifier().feedback(sender,false,"socialmod.error.storage_unavailable"));
        if (conversation.isDirect()) {
            UUID other = conversation.other(sender.getUUID());
            record.lastDirectPartner = other;
            PlayerRecord otherRecord = social.storage().player(other);
            if (otherRecord != null) {
                otherRecord.lastDirectPartner = sender.getUUID();
            }
        }
        return true;
    }

    /** Sustituye {@code [coords]} e {@code [item]} (uno de cada como máximo) por adjuntos del servidor. */
    private String buildAttachments(ServerPlayer sender, String text, List<ChatMessage.Attachment> out) {
        boolean sharing = ServerConfig.get().modules.sharing;
        String result = MessageFormatter.normalizeTokens(text);
        int coords = result.indexOf(MessageFormatter.COORDS_TOKEN);
        if (coords >= 0) {
            if (sharing) {
                out.add(ChatMessage.Attachment.coords(sender.level().dimension().identifier().toString(),
                        sender.getBlockX(), sender.getBlockY(), sender.getBlockZ()));
            }
            result = result.substring(0, coords + MessageFormatter.COORDS_TOKEN.length())
                    + result.substring(coords + MessageFormatter.COORDS_TOKEN.length()).replace(MessageFormatter.COORDS_TOKEN, "(coords)");
        }
        int item = result.indexOf(MessageFormatter.ITEM_TOKEN);
        if (item >= 0) {
            ItemStack stack = sender.getMainHandItem();
            JsonElement encoded = sharing && !stack.isEmpty() ? encodeItem(stack) : null;
            if (encoded != null) {
                out.add(ChatMessage.Attachment.item(encoded));
            }
            result = result.substring(0, item + MessageFormatter.ITEM_TOKEN.length())
                    + result.substring(item + MessageFormatter.ITEM_TOKEN.length()).replace(MessageFormatter.ITEM_TOKEN, "(item)");
        }
        return result;
    }

    private @Nullable JsonElement encodeItem(ItemStack stack) {
        try {
            RegistryOps<JsonElement> ops = social.server().registryAccess().createSerializationContext(JsonOps.INSTANCE);
            return ItemStackTemplate.CODEC.encodeStart(ops, ItemStackTemplate.fromNonEmptyStack(stack)).result().orElse(null);
        } catch (RuntimeException e) {
            SocialMod.warnOnce("encode_item", "No se pudo codificar un ítem compartido", e);
            return null;
        }
    }

    private @Nullable ItemStackTemplate decodeItem(JsonElement json) {
        try {
            RegistryOps<JsonElement> ops = social.server().registryAccess().createSerializationContext(JsonOps.INSTANCE);
            return ItemStackTemplate.CODEC.parse(ops, json).result().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void append(ServerPlayer sender, ConversationId conversation, Conversation loaded, ChatMessage message, String title) {
        message.id = loaded.nextId++;
        loaded.messages.add(message);
        social.storage().applyRetention(loaded);
        social.storage().markConversationDirty(conversation.key());
        deliver(sender, conversation, message, title);
        ChatEvents.MESSAGE_SENT.invoker().onMessageSent(sender, conversation.key(), message.text);
    }

    /** Participantes de una conversación (conectados o no). */
    public List<UUID> participants(ConversationId conversation) {
        if (conversation.isDirect()) {
            return List.of(conversation.a(), conversation.b());
        }
        Group group = social.groups().get(conversation.groupId());
        if (group == null) {
            return List.of();
        }
        List<UUID> result = new ArrayList<>();
        for (UUID member : group.members.keySet()) {
            if (social.groups().canAccess(group, member, conversation.channel())) {
                result.add(member);
            }
        }
        return result;
    }

    private void deliver(ServerPlayer sender, ConversationId conversation, ChatMessage message, String title) {
        ServerConfig config = ServerConfig.get();
        Payloads.MessageView view = toView(message);
        Set<UUID> mentioned = mentionedPlayers(sender, conversation, message.text);
        String preview = MessageFormatter.preview(message.text, view.attachments(), 80);
        Group group = conversation.isDirect() ? null : social.groups().get(conversation.groupId());

        for (UUID participant : participants(conversation)) {
            PlayerRecord record = social.storage().player(participant);
            if (record == null) {
                continue;
            }
            boolean self = participant.equals(sender.getUUID());
            if (!self) {
                if (record.blocked.contains(sender.getUUID())) {
                    continue; // en grupos, los mensajes de un bloqueado no se muestran
                }
                record.unread.merge(conversation.key(), 1, Integer::sum);
            }
            record.touch(conversation.key());
            ServerPlayer online = social.online(participant);
            if (online == null || (!self && online.getChatVisibility() == ChatVisiblity.HIDDEN)) {
                continue; // buzón: se entrega al conectarse o al activar el chat
            }
            String localTitle = conversation.isDirect() ? (self ? title : message.senderName) : title;
            if (social.hasMod(participant)) {
                social.send(online, new Payloads.MessagesS2C(conversation.key(), localTitle, Payloads.MessagesMode.LIVE, false, List.of(view)));
                if (!self) {
                    Payloads.NotifyKind kind = mentioned.contains(participant) ? Payloads.NotifyKind.MENTION
                            : conversation.isDirect() ? Payloads.NotifyKind.PRIVATE : Payloads.NotifyKind.GROUP;
                    String titleKey = conversation.isDirect() ? "socialmod.notify.private" : "socialmod.notify.group";
                    social.notifier().notify(online, kind, sender.getUUID(), titleKey, new Object[]{message.senderName, title},
                            preview, conversation.key());
                }
            } else {
                online.sendSystemMessage(formatVanilla(online, conversation, group, message, view, self, title));
                if (!self && mentioned.contains(participant)) {
                    social.notifier().notify(online, Payloads.NotifyKind.MENTION, sender.getUUID(), "socialmod.notify.mention",
                            new Object[]{message.senderName, title}, "", conversation.key());
                }
            }
        }
        social.storage().markPlayersDirty();
        if (conversation.isDirect() && config.moderation.spyEnabled) {
            social.moderation().spy(conversation, message, view);
        }
    }

    /** Menciones válidas: {@code @jugador} (miembro del canal o interlocutor) y {@code @etiqueta} del grupo (con permiso PIN). */
    private Set<UUID> mentionedPlayers(ServerPlayer sender, ConversationId conversation, String text) {
        Set<UUID> result = new LinkedHashSet<>();
        if (!ServerConfig.get().modules.mentions) {
            return result;
        }
        Set<String> names = SafeMarkdown.mentions(text);
        if (names.isEmpty()) {
            return result;
        }
        List<UUID> participants = participants(conversation);
        Group group = conversation.isDirect() ? null : social.groups().get(conversation.groupId());
        boolean groupMention = group != null && (names.contains(group.tag.toLowerCase(Locale.ROOT)) || names.contains("everyone"))
                && social.groups().has(group, sender.getUUID(), GroupPermission.PIN);
        for (UUID participant : participants) {
            if (participant.equals(sender.getUUID())) {
                continue;
            }
            PlayerRecord record = social.storage().player(participant);
            if (record == null || record.blocked.contains(sender.getUUID())) {
                continue; // un bloqueado no puede mencionarte
            }
            if (groupMention || names.contains(record.name.toLowerCase(Locale.ROOT))) {
                result.add(participant);
            }
        }
        return result;
    }

    // =====================================================================
    // Formato para jugadores sin el mod (PLAN 10)
    // =====================================================================

    private MutableComponent formatVanilla(ServerPlayer viewer, ConversationId conversation, @Nullable Group group, ChatMessage message,
                                           Payloads.MessageView view, boolean self, String title) {
        ServerConfig.Formats formats = ServerConfig.get().formats;
        String template;
        int color;
        String receiver = "";
        if (conversation.isDirect()) {
            template = self ? formats.directOut : formats.directIn;
            color = color(formats.directColor, 0xFF55FF);
            receiver = social.storage().nameOf(conversation.other(message.sender));
        } else if (group != null && group.party) {
            template = formats.party;
            color = color(formats.partyColor, 0x5555FF);
        } else {
            template = formats.group;
            color = color(formats.groupColor, 0x55FFFF);
        }
        boolean links = ServerConfig.get().chat.allowLinks;
        MutableComponent body = MessageFormatter.format(message.text, view.attachments(), links,
                Set.of(viewer.getGameProfile().name().toLowerCase(Locale.ROOT)));
        String replyCommand = conversation.isDirect()
                ? "/pm " + (self ? receiver : message.senderName) + " "
                : group != null && group.party ? "/p " : "/g to " + (group == null ? "" : group.tag) + " " + conversation.channel() + " ";
        Style prefixStyle = Style.EMPTY.withColor(color)
                .withClickEvent(new ClickEvent.SuggestCommand(replyCommand))
                .withHoverEvent(new HoverEvent.ShowText(Lang.tr(viewer, "socialmod.chat.click_reply")));

        MutableComponent out = Component.empty();
        int i = 0;
        while (i < template.length()) {
            int open = template.indexOf('{', i);
            int close = open < 0 ? -1 : template.indexOf('}', open);
            if (open < 0 || close < 0) {
                out.append(Component.literal(template.substring(i)).withStyle(prefixStyle));
                break;
            }
            if (open > i) {
                out.append(Component.literal(template.substring(i, open)).withStyle(prefixStyle));
            }
            String token = template.substring(open + 1, close);
            switch (token) {
                case "message" -> out.append(body);
                case "sender" -> out.append(Component.literal(self ? Lang.raw(viewer.clientInformation().language(), "socialmod.chat.me")
                        : message.senderName).withStyle(prefixStyle));
                case "receiver" -> out.append(Component.literal(receiver).withStyle(prefixStyle));
                case "group" -> out.append(Component.literal(group == null ? "" : group.name).withStyle(prefixStyle));
                case "tag" -> out.append(Component.literal(group == null ? "" : group.tag).withStyle(prefixStyle.withColor(group == null ? 0xFFFFFF : group.color)));
                case "channel" -> out.append(Component.literal(conversation.isDirect() ? "" : conversation.channel()).withStyle(prefixStyle));
                default -> out.append(Component.literal(template.substring(open, close + 1)).withStyle(prefixStyle));
            }
            i = close + 1;
        }
        return out;
    }

    private boolean canCompleteSend(ServerPlayer sender, ConversationId conversation) {
        ServerConfig config = ServerConfig.get();
        PlayerRecord self = social.storage().player(sender.getUUID());
        if (sender.hasDisconnected() || self == null || self.isMuted(System.currentTimeMillis())) return false;
        if (conversation.isDirect()) {
            if (!config.modules.privateMessages || !PermissionBridge.allows(sender, PermissionBridge.CHAT_PRIVATE)) return false;
            UUID otherId = conversation.other(sender.getUUID());
            PlayerRecord other = social.storage().player(otherId);
            return other != null && !self.blocked.contains(otherId)
                    && (config.modules.mailbox || social.online(otherId) != null)
                    && (PermissionBridge.isStaff(sender, PermissionBridge.MOD_BYPASS)
                        || (!other.blocked.contains(sender.getUUID())
                            && PresenceService.privacyAllows(other.whoCanMessage, other.friends.contains(sender.getUUID()))));
        }
        Group group = social.groups().get(conversation.groupId());
        return group != null && !group.archived && (group.party ? config.modules.parties : config.modules.groups)
                && social.groups().canAccess(group, sender.getUUID(), conversation.channel());
    }

    private static int color(String name, int fallback) {
        return name == null ? fallback : net.minecraft.network.chat.TextColor.parseColor(name.toLowerCase(Locale.ROOT)).result()
                .map(net.minecraft.network.chat.TextColor::getValue).orElse(fallback);
    }

    // =====================================================================
    // Vistas, historial, edición
    // =====================================================================

    public Payloads.MessageView toView(ChatMessage message) {
        List<Payloads.AttachmentView> attachments = new ArrayList<>();
        if (!message.deleted) {
            for (ChatMessage.Attachment attachment : message.attachments) {
                if (ChatMessage.Attachment.ITEM.equals(attachment.kind) && attachment.item != null) {
                    ItemStackTemplate template = decodeItem(attachment.item);
                    if (template != null) {
                        attachments.add(new Payloads.AttachmentView(true, template, "", 0, 0, 0));
                    }
                } else if (ChatMessage.Attachment.COORDS.equals(attachment.kind)) {
                    attachments.add(new Payloads.AttachmentView(false, null, attachment.dimension, attachment.x, attachment.y, attachment.z));
                }
            }
        }
        return new Payloads.MessageView(message.id, message.sender, message.senderName, message.deleted ? "" : message.text,
                message.time, message.editedAt > 0, message.deleted, attachments);
    }

    /** ¿Puede este jugador leer la conversación? */
    public boolean canRead(UUID player, ConversationId conversation) {
        if (conversation.isDirect()) {
            return conversation.involves(player);
        }
        Group group = social.groups().get(conversation.groupId());
        return group != null && social.groups().canAccess(group, player, conversation.channel());
    }

    public String titleFor(UUID viewer, ConversationId conversation) {
        if (conversation.isDirect()) {
            return social.storage().nameOf(conversation.other(viewer));
        }
        Group group = social.groups().get(conversation.groupId());
        return group == null ? conversation.key() : social.groups().title(group, conversation.channel());
    }

    public void history(ServerPlayer player, String target, long beforeId) {
        ConversationId conversation = resolveTarget(player.getUUID(), target);
        if (conversation == null || !canRead(player.getUUID(), conversation)) {
            return;
        }
        int pageSize = ServerConfig.get().chat.historyPageSize;
        String title = titleFor(player.getUUID(), conversation);
        social.storage().withConversation(conversation.key(), loaded -> {
            if (player.hasDisconnected() || !canRead(player.getUUID(), conversation)) return;
            List<ChatMessage> page = loaded.page(beforeId, pageSize + 1);
            boolean hasMore = page.size() > pageSize;
            if (hasMore) {
                page = page.subList(1, page.size());
            }
            PlayerRecord viewer = social.storage().player(player.getUUID());
            List<Payloads.MessageView> views = new ArrayList<>();
            for (ChatMessage message : page) {
                if (viewer != null && !conversation.isDirect() && viewer.blocked.contains(message.sender)) {
                    continue;
                }
                views.add(toView(message));
            }
            social.send(player, new Payloads.MessagesS2C(conversation.key(), title, Payloads.MessagesMode.HISTORY, hasMore, views));
        }, () -> {
            if(player.hasDisconnected())return;
            social.notifier().feedback(player,false,"socialmod.error.storage_unavailable");
            social.send(player,new Payloads.MessagesS2C(conversation.key(),title,Payloads.MessagesMode.UNAVAILABLE,true,List.of()));
        });
    }

    public void editOrDelete(ServerPlayer player, Payloads.MessageOp op, String target, long messageId, String rawText) {
        ConversationId conversation = resolveTarget(player.getUUID(), target);
        if (conversation != null && !conversation.isDirect()) {
            Group group = social.groups().get(conversation.groupId());
            if (group == null || group.archived) return;
        }
        if (conversation == null || !canRead(player.getUUID(), conversation)) {
            return;
        }
        long window = ServerConfig.get().chat.editWindowSeconds * 1000L;
        social.storage().withConversation(conversation.key(), loaded -> {
            if (player.hasDisconnected() || !canRead(player.getUUID(), conversation)) return;
            boolean staff = PermissionBridge.isStaff(player, PermissionBridge.MOD_HISTORY);
            if (!conversation.isDirect()) {
                Group group = social.groups().get(conversation.groupId()); if (group == null || group.archived || !canRead(player.getUUID(), conversation)) return;
            }
            ChatMessage message = loaded.find(messageId);
            if (message == null || message.deleted) {
                return;
            }
            boolean own = message.sender.equals(player.getUUID());
            boolean inWindow = System.currentTimeMillis() - message.time <= window;
            if (op == Payloads.MessageOp.EDIT) {
                if (!own || !inWindow) {
                    social.notifier().feedback(player, false, "socialmod.error.edit_window");
                    return;
                }
                String text = TextSanitizer.clean(rawText, ServerConfig.get().chat.maxMessageLength);
                WordFilter.Result filtered = wordFilter.apply(text);
                if (text.isEmpty() || filtered.blocked()) {
                    return;
                }
                // Los adjuntos se conservan: los marcadores nuevos se escapan
                message.text = filtered.text();
                message.editedAt = System.currentTimeMillis();
            } else {
                if (!((own && inWindow) || staff)) {
                    social.notifier().feedback(player, false, "socialmod.error.edit_window");
                    return;
                }
                message.deleted = true;
                message.text = "";
                message.attachments.clear();
                if (!own) {
                    social.storage().audit("MOD_DELETE " + player.getGameProfile().name() + " " + conversation.key() + "#" + messageId);
                }
            }
            social.storage().markConversationDirty(conversation.key());
            Payloads.MessageView view = toView(message);
            for (UUID participant : participants(conversation)) {
                ServerPlayer online = social.online(participant);
                if (online != null) {
                    social.sendModded(online, new Payloads.MessagesS2C(conversation.key(), titleFor(participant, conversation),
                            Payloads.MessagesMode.UPDATE, false, List.of(view)));
                }
            }
        });
    }

    // =====================================================================
    // Señales: escribiendo y leído
    // =====================================================================

    public void signal(ServerPlayer player, Payloads.Signal signal, String target, long value) {
        ConversationId conversation = resolveTarget(player.getUUID(), target);
        if (conversation == null || !canRead(player.getUUID(), conversation)) {
            return;
        }
        ServerConfig config = ServerConfig.get();
        PlayerRecord self = social.record(player);
        if (signal == Payloads.Signal.READ) {
            Integer previous = self.unread.remove(conversation.key());
            if (previous != null) {
                social.storage().markPlayersDirty();
            }
            if (!conversation.isDirect() || !config.chat.readReceipts || !self.readReceipts) {
                return;
            }
        } else {
            if (!config.chat.typingIndicator || !self.typingIndicator) {
                return;
            }
            SocialServer.Session session = social.session(player.getUUID());
            long now = System.currentTimeMillis();
            if (session == null || now - session.lastTypingSignal < 2000) {
                return;
            }
            session.lastTypingSignal = now;
        }
        for (UUID participant : participants(conversation)) {
            if (participant.equals(player.getUUID())) {
                continue;
            }
            PlayerRecord record = social.storage().player(participant);
            if (record == null || record.blocked.contains(player.getUUID())) {
                continue;
            }
            if (signal == Payloads.Signal.READ && !record.readReceipts) {
                continue; // recíproco: quien no envía confirmaciones tampoco las recibe
            }
            if (signal == Payloads.Signal.TYPING && !record.typingIndicator) {
                continue;
            }
            ServerPlayer online = social.online(participant);
            if (online != null) {
                social.sendModded(online, new Payloads.SignalS2C(signal, conversation.key(), player.getUUID(), self.name, value));
            }
        }
    }

    // =====================================================================
    // Buzón (PLAN 5.1)
    // =====================================================================

    public void onJoin(ServerPlayer player, PlayerRecord record) {
        if (!ServerConfig.get().modules.mailbox || record.unread.isEmpty()) {
            return;
        }
        // Resumen: "Tienes 3 mensajes de Alex"
        int lines = 0;
        for (Map.Entry<String, Integer> entry : record.unread.entrySet()) {
            ConversationId conversation = ConversationId.parse(entry.getKey());
            if (conversation == null || !canRead(player.getUUID(), conversation)) {
                continue;
            }
            if (lines++ >= 5) {
                break;
            }
            String key = conversation.isDirect() ? "socialmod.mailbox.direct" : "socialmod.mailbox.group";
            player.sendSystemMessage(Lang.tr(player, key, entry.getValue(), titleFor(player.getUUID(), conversation)).withStyle(ChatFormatting.GOLD));
        }
        record.unread.keySet().removeIf(key -> {
            ConversationId conversation = ConversationId.parse(key);
            return conversation == null || !canRead(player.getUUID(), conversation);
        });
        if (lines > 0 && !social.hasMod(player.getUUID())) {
            player.sendSystemMessage(Lang.tr(player, "socialmod.mailbox.read_hint").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                    .withUnderlined(true).withClickEvent(new ClickEvent.RunCommand("/socialmod inbox"))));
        }
    }

    /** Muestra en el chat los mensajes sin leer (para jugadores sin el mod) y los marca como leídos. */
    public void showInbox(ServerPlayer player) {
        PlayerRecord record = social.record(player);
        if (record.unread.isEmpty()) {
            social.notifier().feedback(player, true, "socialmod.mailbox.empty");
            return;
        }
        for (Map.Entry<String, Integer> entry : Map.copyOf(record.unread).entrySet()) {
            ConversationId conversation = ConversationId.parse(entry.getKey());
            if (conversation == null || !canRead(player.getUUID(), conversation)) {
                record.unread.remove(entry.getKey());
                continue;
            }
            int count = Math.min(entry.getValue(), MAX_INBOX_LINES);
            String title = titleFor(player.getUUID(), conversation);
            Group group = conversation.isDirect() ? null : social.groups().get(conversation.groupId());
            social.storage().withConversation(conversation.key(), loaded -> {
                if (player.hasDisconnected() || !canRead(player.getUUID(), conversation)) return;
                player.sendSystemMessage(Component.literal("— " + title + " —").withStyle(ChatFormatting.GOLD));
                List<ChatMessage> page = loaded.page(0, count);
                for (ChatMessage message : page) {
                    if (message.deleted || record.blocked.contains(message.sender)) {
                        continue;
                    }
                    boolean self = message.sender.equals(player.getUUID());
                    player.sendSystemMessage(formatVanilla(player, conversation, group, message, toView(message), self, title));
                }
                record.unread.remove(conversation.key());
                social.storage().markPlayersDirty();
            });
        }
    }
}
