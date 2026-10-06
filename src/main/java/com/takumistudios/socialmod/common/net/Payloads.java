package com.takumistudios.socialmod.common.net;

import com.takumistudios.socialmod.SocialMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Paquetes de red (PLAN 4.3). El cliente solo envía intenciones con textos acotados; el servidor valida todo.
 * Las cadenas se leen con un tope para descartar basura antes de validar con los límites reales de la config.
 * Si la versión de protocolo no coincide, el cliente funciona en modo "solo chat" (sin crash).
 */
public final class Payloads {
    /** Se incrementa con cada cambio incompatible de los paquetes. */
    public static final int PROTOCOL_VERSION = 3;

    public static final int MAX_TEXT = 1024;
    public static final int MAX_ARG = 256;
    public static final int MAX_CONV = 96;
    public static final int MAX_NAME = 64;
    public static final int MAX_JSON = 1 << 20;
    public static final int MAX_LIST = 200;

    private Payloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SocialMod.MOD_ID, name));
    }

    private static int readCount(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_LIST) {
            throw new IllegalArgumentException("Lista demasiado grande: " + size);
        }
        return size;
    }

    // =====================================================================
    // Cliente -> Servidor
    // =====================================================================

    /** Handshake: el cliente anuncia su versión de protocolo. */
    public record HelloC2S(int protocol) implements CustomPacketPayload {
        public static final Type<HelloC2S> TYPE = id("hello_c2s");
        public static final StreamCodec<RegistryFriendlyByteBuf, HelloC2S> CODEC = StreamCodec.ofMember(
                (p, buf) -> buf.writeVarInt(p.protocol), buf -> new HelloC2S(buf.readVarInt()));

        @Override
        public Type<HelloC2S> type() {
            return TYPE;
        }
    }

    /**
     * Enviar un mensaje. {@code target}: {@code dm:<uuid del destinatario>} o {@code g:<grupo>:<canal>}.
     * {@code [coords]} e {@code [item]} en el texto los genera el servidor.
     */
    public record SendC2S(String target, String text) implements CustomPacketPayload {
        public static final Type<SendC2S> TYPE = id("send");
        public static final StreamCodec<RegistryFriendlyByteBuf, SendC2S> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeUtf(p.target, MAX_CONV);
                    buf.writeUtf(p.text, MAX_TEXT);
                },
                buf -> new SendC2S(buf.readUtf(MAX_CONV), buf.readUtf(MAX_TEXT)));

        @Override
        public Type<SendC2S> type() {
            return TYPE;
        }
    }

    public enum MessageOp {
        EDIT, DELETE;

        static MessageOp byId(int id) {
            return id == 1 ? DELETE : EDIT;
        }
    }

    /** Editar o borrar un mensaje propio (durante la ventana configurada, por defecto 2 minutos). */
    public record MessageOpC2S(MessageOp op, String conversation, long messageId, String text) implements CustomPacketPayload {
        public static final Type<MessageOpC2S> TYPE = id("message_op");
        public static final StreamCodec<RegistryFriendlyByteBuf, MessageOpC2S> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeVarInt(p.op.ordinal());
                    buf.writeUtf(p.conversation, MAX_CONV);
                    buf.writeVarLong(p.messageId);
                    buf.writeUtf(p.text, MAX_TEXT);
                },
                buf -> new MessageOpC2S(MessageOp.byId(buf.readVarInt()), buf.readUtf(MAX_CONV), buf.readVarLong(), buf.readUtf(MAX_TEXT)));

        @Override
        public Type<MessageOpC2S> type() {
            return TYPE;
        }
    }

    /** Pedir historial anterior a {@code beforeId} (0 = lo más reciente). Paginado (lazy loading). */
    public record HistoryC2S(String conversation, long beforeId) implements CustomPacketPayload {
        public static final Type<HistoryC2S> TYPE = id("history");
        public static final StreamCodec<RegistryFriendlyByteBuf, HistoryC2S> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeUtf(p.conversation, MAX_CONV);
                    buf.writeVarLong(p.beforeId);
                },
                buf -> new HistoryC2S(buf.readUtf(MAX_CONV), buf.readVarLong()));

        @Override
        public Type<HistoryC2S> type() {
            return TYPE;
        }
    }

    public enum Signal {
        TYPING, READ;

        static Signal byId(int id) {
            return id == 1 ? READ : TYPING;
        }
    }

    /** Señales ligeras: "escribiendo..." y "leído hasta". */
    public record SignalC2S(Signal signal, String conversation, long value) implements CustomPacketPayload {
        public static final Type<SignalC2S> TYPE = id("signal_c2s");
        public static final StreamCodec<RegistryFriendlyByteBuf, SignalC2S> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeVarInt(p.signal.ordinal());
                    buf.writeUtf(p.conversation, MAX_CONV);
                    buf.writeVarLong(p.value);
                },
                buf -> new SignalC2S(Signal.byId(buf.readVarInt()), buf.readUtf(MAX_CONV), buf.readVarLong()));

        @Override
        public Type<SignalC2S> type() {
            return TYPE;
        }
    }

    /** Acción social genérica (amigos, grupos, estado...). Ver {@link SocialAction}. */
    public record ActionC2S(@Nullable SocialAction action, String a, String b) implements CustomPacketPayload {
        public static final Type<ActionC2S> TYPE = id("action");
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionC2S> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeVarInt(p.action == null ? -1 : p.action.ordinal());
                    buf.writeUtf(p.a, MAX_ARG);
                    buf.writeUtf(p.b, p.action == SocialAction.VISUAL_PUBLISH ? 4096 : MAX_ARG);
                },
                buf -> { SocialAction action = SocialAction.byOrdinal(buf.readVarInt());
                    return new ActionC2S(action, buf.readUtf(MAX_ARG), buf.readUtf(action == SocialAction.VISUAL_PUBLISH ? 4096 : MAX_ARG)); });

        @Override
        public Type<ActionC2S> type() {
            return TYPE;
        }
    }

    // =====================================================================
    // Servidor -> Cliente
    // =====================================================================

    /** Respuesta al handshake con las reglas del servidor que afectan a la UI. */
    public record HelloS2C(int protocol, int maxMessageLength, int editWindowSeconds, boolean linksAllowed, boolean spyActive,
                           boolean typingEnabled, boolean readReceiptsEnabled, boolean groupsEnabled, boolean partiesEnabled,
                           boolean sharingEnabled) implements CustomPacketPayload {
        public static final Type<HelloS2C> TYPE = id("hello_s2c");
        public static final StreamCodec<RegistryFriendlyByteBuf, HelloS2C> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeVarInt(p.protocol);
                    buf.writeVarInt(p.maxMessageLength);
                    buf.writeVarInt(p.editWindowSeconds);
                    buf.writeBoolean(p.linksAllowed);
                    buf.writeBoolean(p.spyActive);
                    buf.writeBoolean(p.typingEnabled);
                    buf.writeBoolean(p.readReceiptsEnabled);
                    buf.writeBoolean(p.groupsEnabled);
                    buf.writeBoolean(p.partiesEnabled);
                    buf.writeBoolean(p.sharingEnabled);
                },
                buf -> new HelloS2C(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
                        buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

        @Override
        public Type<HelloS2C> type() {
            return TYPE;
        }
    }

    /** Estado social completo (ver {@link SnapshotDto}). */
    public record SnapshotS2C(String json) implements CustomPacketPayload {
        public static final Type<SnapshotS2C> TYPE = id("snapshot");
        public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotS2C> CODEC = StreamCodec.ofMember(
                (p, buf) -> buf.writeUtf(p.json, MAX_JSON), buf -> new SnapshotS2C(buf.readUtf(MAX_JSON)));

        @Override
        public Type<SnapshotS2C> type() {
            return TYPE;
        }
    }

    /** Cambio de presencia de un jugador. {@code dimension} vacío si el jugador no la comparte. */
    public record PresenceEntry(UUID player, String name, int status, String customStatus, String dimension) {
        static void write(FriendlyByteBuf buf, PresenceEntry e) {
            buf.writeUUID(e.player);
            buf.writeUtf(e.name, MAX_NAME);
            buf.writeVarInt(e.status);
            buf.writeUtf(e.customStatus, MAX_ARG);
            buf.writeUtf(e.dimension, MAX_ARG);
        }

        static PresenceEntry read(FriendlyByteBuf buf) {
            return new PresenceEntry(buf.readUUID(), buf.readUtf(MAX_NAME), buf.readVarInt(), buf.readUtf(MAX_ARG), buf.readUtf(MAX_ARG));
        }
    }

    /** Deltas de presencia agrupados (como máximo cada 250 ms). */
    public record PresenceS2C(List<PresenceEntry> entries) implements CustomPacketPayload {
        public static final Type<PresenceS2C> TYPE = id("presence");
        public static final StreamCodec<RegistryFriendlyByteBuf, PresenceS2C> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeVarInt(p.entries.size());
                    p.entries.forEach(e -> PresenceEntry.write(buf, e));
                },
                buf -> {
                    int size = readCount(buf);
                    List<PresenceEntry> entries = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        entries.add(PresenceEntry.read(buf));
                    }
                    return new PresenceS2C(entries);
                });

        @Override
        public Type<PresenceS2C> type() {
            return TYPE;
        }
    }

    /** Adjunto generado por el servidor: un ítem o unas coordenadas. */
    public record AttachmentView(boolean isItem, @Nullable ItemStackTemplate item, String dimension, int x, int y, int z) {
        static void write(RegistryFriendlyByteBuf buf, AttachmentView a) {
            buf.writeBoolean(a.isItem);
            if (a.isItem) {
                ItemStackTemplate.STREAM_CODEC.encode(buf, a.item);
            } else {
                buf.writeUtf(a.dimension, MAX_ARG);
                buf.writeVarInt(a.x);
                buf.writeVarInt(a.y);
                buf.writeVarInt(a.z);
            }
        }

        static AttachmentView read(RegistryFriendlyByteBuf buf) {
            if (buf.readBoolean()) {
                return new AttachmentView(true, ItemStackTemplate.STREAM_CODEC.decode(buf), "", 0, 0, 0);
            }
            return new AttachmentView(false, null, buf.readUtf(MAX_ARG), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    public record MessageView(long id, UUID sender, String senderName, String text, long time, boolean edited, boolean deleted,
                              List<AttachmentView> attachments) {
        static void write(RegistryFriendlyByteBuf buf, MessageView m) {
            buf.writeVarLong(m.id);
            buf.writeUUID(m.sender);
            buf.writeUtf(m.senderName, MAX_NAME);
            buf.writeUtf(m.text, MAX_TEXT);
            buf.writeLong(m.time);
            buf.writeBoolean(m.edited);
            buf.writeBoolean(m.deleted);
            buf.writeVarInt(m.attachments.size());
            m.attachments.forEach(a -> AttachmentView.write(buf, a));
        }

        static MessageView read(RegistryFriendlyByteBuf buf) {
            long id = buf.readVarLong();
            UUID sender = buf.readUUID();
            String name = buf.readUtf(MAX_NAME);
            String text = buf.readUtf(MAX_TEXT);
            long time = buf.readLong();
            boolean edited = buf.readBoolean();
            boolean deleted = buf.readBoolean();
            int size = buf.readVarInt();
            if (size < 0 || size > 8) {
                throw new IllegalArgumentException("Demasiados adjuntos: " + size);
            }
            List<AttachmentView> attachments = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                attachments.add(AttachmentView.read(buf));
            }
            return new MessageView(id, sender, name, text, time, edited, deleted, attachments);
        }
    }

    public enum MessagesMode {
        /** Mensaje nuevo en directo. */
        LIVE,
        /** Página de historial (más antiguos). */
        HISTORY,
        /** Un mensaje editado o borrado. */
        UPDATE;

        static MessagesMode byId(int id) {
            MessagesMode[] values = values();
            return id >= 0 && id < values.length ? values[id] : LIVE;
        }
    }

    public record MessagesS2C(String conversation, String title, MessagesMode mode, boolean hasMore, List<MessageView> messages)
            implements CustomPacketPayload {
        public static final Type<MessagesS2C> TYPE = id("messages");
        public static final StreamCodec<RegistryFriendlyByteBuf, MessagesS2C> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeUtf(p.conversation, MAX_CONV);
                    buf.writeUtf(p.title, MAX_ARG);
                    buf.writeVarInt(p.mode.ordinal());
                    buf.writeBoolean(p.hasMore);
                    buf.writeVarInt(p.messages.size());
                    p.messages.forEach(m -> MessageView.write(buf, m));
                },
                buf -> {
                    String conversation = buf.readUtf(MAX_CONV);
                    String title = buf.readUtf(MAX_ARG);
                    MessagesMode mode = MessagesMode.byId(buf.readVarInt());
                    boolean hasMore = buf.readBoolean();
                    int size = readCount(buf);
                    List<MessageView> messages = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        messages.add(MessageView.read(buf));
                    }
                    return new MessagesS2C(conversation, title, mode, hasMore, messages);
                });

        @Override
        public Type<MessagesS2C> type() {
            return TYPE;
        }
    }

    /** "X está escribiendo" o "X leyó hasta el mensaje N". */
    public record SignalS2C(Signal signal, String conversation, UUID player, String name, long value) implements CustomPacketPayload {
        public static final Type<SignalS2C> TYPE = id("signal_s2c");
        public static final StreamCodec<RegistryFriendlyByteBuf, SignalS2C> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeVarInt(p.signal.ordinal());
                    buf.writeUtf(p.conversation, MAX_CONV);
                    buf.writeUUID(p.player);
                    buf.writeUtf(p.name, MAX_NAME);
                    buf.writeVarLong(p.value);
                },
                buf -> new SignalS2C(Signal.byId(buf.readVarInt()), buf.readUtf(MAX_CONV), buf.readUUID(), buf.readUtf(MAX_NAME), buf.readVarLong()));

        @Override
        public Type<SignalS2C> type() {
            return TYPE;
        }
    }

    public enum NotifyKind {
        PRIVATE, GROUP, MENTION, INVITE, FRIEND, EVENT, SYSTEM;

        static NotifyKind byId(int id) {
            NotifyKind[] values = values();
            return id >= 0 && id < values.length ? values[id] : SYSTEM;
        }

        /** Prioridad: menciones e invitaciones por encima de los mensajes de grupo (PLAN 6). */
        public int priority() {
            return switch (this) {
                case MENTION, INVITE -> 3;
                case PRIVATE, EVENT, FRIEND -> 2;
                case SYSTEM -> 1;
                case GROUP -> 0;
            };
        }
    }

    /** Notificación (toast). {@code source} es el jugador que la origina (para la cabeza), o null. */
    public record NotifyS2C(NotifyKind kind, @Nullable UUID source, String title, String body, String conversation)
            implements CustomPacketPayload {
        public static final Type<NotifyS2C> TYPE = id("notify");
        public static final StreamCodec<RegistryFriendlyByteBuf, NotifyS2C> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeVarInt(p.kind.ordinal());
                    buf.writeBoolean(p.source != null);
                    if (p.source != null) {
                        buf.writeUUID(p.source);
                    }
                    buf.writeUtf(p.title, MAX_ARG);
                    buf.writeUtf(p.body, MAX_TEXT);
                    buf.writeUtf(p.conversation, MAX_CONV);
                },
                buf -> {
                    NotifyKind kind = NotifyKind.byId(buf.readVarInt());
                    UUID source = buf.readBoolean() ? buf.readUUID() : null;
                    return new NotifyS2C(kind, source, buf.readUtf(MAX_ARG), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_CONV));
                });

        @Override
        public Type<NotifyS2C> type() {
            return TYPE;
        }
    }

    /** Etiqueta del grupo principal de un jugador conectado (nametags). Etiqueta vacía = sin grupo. Icono: id de {@code GroupIcon}; rol: id de {@code Role}. */
    public record TagEntry(UUID player, String tag, int color, String icon, String role) {
    }

    /** Etiquetas de grupo: la lista completa al conectarse y deltas cuando cambian. */
    public record TagsS2C(boolean full, List<TagEntry> entries) implements CustomPacketPayload {
        public static final Type<TagsS2C> TYPE = id("tags");
        public static final StreamCodec<RegistryFriendlyByteBuf, TagsS2C> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeBoolean(p.full);
                    buf.writeVarInt(p.entries.size());
                    for (TagEntry e : p.entries) {
                        buf.writeUUID(e.player);
                        buf.writeUtf(e.tag, 64);
                        buf.writeInt(e.color);
                        buf.writeUtf(e.icon, 16);
                        buf.writeUtf(e.role, 16);
                    }
                },
                buf -> {
                    boolean full = buf.readBoolean();
                    int size = buf.readVarInt();
                    if (size < 0 || size > 10_000) {
                        throw new IllegalArgumentException("Demasiadas etiquetas: " + size);
                    }
                    List<TagEntry> entries = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        entries.add(new TagEntry(buf.readUUID(), buf.readUtf(64), buf.readInt(), buf.readUtf(16), buf.readUtf(16)));
                    }
                    return new TagsS2C(full, entries);
                });

        @Override
        public Type<TagsS2C> type() {
            return TYPE;
        }
    }

    // =====================================================================
    // Protocolo 2: party en el HUD y ping en el mundo
    // =====================================================================

    /** Vida de un miembro de la party (PLAN 5.2). {@code health < 0}: no está en la misma dimensión o está muerto. */
    public record PartyMember(UUID player, String name, float health, float maxHealth) {
    }

    /** Estado de la party para el HUD: lista completa, se envía solo cuando cambia (como mucho 2 veces por segundo). */
    public record PartyS2C(List<PartyMember> members) implements CustomPacketPayload {
        public static final Type<PartyS2C> TYPE = id("party");
        public static final StreamCodec<RegistryFriendlyByteBuf, PartyS2C> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeVarInt(p.members.size());
                    for (PartyMember m : p.members) {
                        buf.writeUUID(m.player);
                        buf.writeUtf(m.name, MAX_NAME);
                        buf.writeFloat(m.health);
                        buf.writeFloat(m.maxHealth);
                    }
                },
                buf -> {
                    int size = buf.readVarInt();
                    if (size < 0 || size > MAX_LIST) {
                        throw new IllegalArgumentException("Party demasiado grande: " + size);
                    }
                    List<PartyMember> members = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        members.add(new PartyMember(buf.readUUID(), buf.readUtf(MAX_NAME), buf.readFloat(), buf.readFloat()));
                    }
                    return new PartyS2C(members);
                });

        @Override
        public Type<PartyS2C> type() {
            return TYPE;
        }
    }

    /** Marcar un punto del mundo para la party (PLAN 5.5). El servidor comprueba distancia, dimensión y frecuencia. */
    public record PingC2S(int x, int y, int z) implements CustomPacketPayload {
        public static final Type<PingC2S> TYPE = id("ping");
        public static final StreamCodec<RegistryFriendlyByteBuf, PingC2S> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeVarInt(p.x);
                    buf.writeVarInt(p.y);
                    buf.writeVarInt(p.z);
                },
                buf -> new PingC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<PingC2S> type() {
            return TYPE;
        }
    }

    public record PingS2C(UUID source, String name, String dimension, int x, int y, int z, int color) implements CustomPacketPayload {
        public static final Type<PingS2C> TYPE = id("ping_show");
        public static final StreamCodec<RegistryFriendlyByteBuf, PingS2C> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeUUID(p.source);
                    buf.writeUtf(p.name, MAX_NAME);
                    buf.writeUtf(p.dimension, MAX_ARG);
                    buf.writeVarInt(p.x);
                    buf.writeVarInt(p.y);
                    buf.writeVarInt(p.z);
                    buf.writeInt(p.color);
                },
                buf -> new PingS2C(buf.readUUID(), buf.readUtf(MAX_NAME), buf.readUtf(MAX_ARG), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readInt()));

        @Override
        public Type<PingS2C> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(PartyS2C.TYPE, PartyS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PingS2C.TYPE, PingS2C.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PingC2S.TYPE, PingC2S.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TagsS2C.TYPE, TagsS2C.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HelloC2S.TYPE, HelloC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SendC2S.TYPE, SendC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MessageOpC2S.TYPE, MessageOpC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HistoryC2S.TYPE, HistoryC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SignalC2S.TYPE, SignalC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ActionC2S.TYPE, ActionC2S.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HelloS2C.TYPE, HelloS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SnapshotS2C.TYPE, SnapshotS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PresenceS2C.TYPE, PresenceS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MessagesS2C.TYPE, MessagesS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SignalS2C.TYPE, SignalS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NotifyS2C.TYPE, NotifyS2C.CODEC);
    }
}
