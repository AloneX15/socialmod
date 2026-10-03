package com.takumistudios.socialmod.server;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import com.takumistudios.socialmod.server.integration.ClaimsSync;
import com.takumistudios.socialmod.server.security.RateLimiter;
import com.takumistudios.socialmod.server.service.ChatService;
import com.takumistudios.socialmod.server.service.FriendService;
import com.takumistudios.socialmod.server.service.GroupService;
import com.takumistudios.socialmod.server.service.ModerationService;
import com.takumistudios.socialmod.server.service.Notifier;
import com.takumistudios.socialmod.server.service.PartyService;
import com.takumistudios.socialmod.server.service.PresenceService;
import com.takumistudios.socialmod.server.service.SnapshotService;
import com.takumistudios.socialmod.server.service.VoiceService;
import com.takumistudios.socialmod.server.storage.FileStorageBackend;
import com.takumistudios.socialmod.server.storage.JdbcStorageBackend;
import com.takumistudios.socialmod.server.storage.SocialStorage;
import com.takumistudios.socialmod.server.storage.StorageBackend;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Estado de SocialMod para un servidor en marcha: almacenamiento, servicios y sesiones de los clientes con el mod.
 * Todo se usa desde el hilo principal del servidor.
 */
public final class SocialServer {
    private static volatile @Nullable SocialServer instance;

    private final MinecraftServer server;
    private final SocialStorage storage;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final RateLimiter packetLimiter;
    private final Notifier notifier;
    private final PresenceService presence;
    private final FriendService friends;
    private final GroupService groups;
    private final ChatService chat;
    private final ModerationService moderation;
    private final SnapshotService snapshots;
    private final PartyService party;
    private final VoiceService voice;
    private final ClaimsSync claims;

    /** Cliente con SocialMod y protocolo compatible. */
    public static final class Session {
        public final int protocol;
        public boolean panelOpen;
        public long lastTypingSignal;

        Session(int protocol) {
            this.protocol = protocol;
        }
    }

    private SocialServer(MinecraftServer server, StorageBackend backend) {
        this.server = server;
        this.storage = new SocialStorage(backend, server);
        ServerConfig.Limits limits = ServerConfig.get().limits;
        this.packetLimiter = new RateLimiter(limits.packetsPerSecond, limits.packetBurst);
        this.notifier = new Notifier(this);
        this.presence = new PresenceService(this);
        this.friends = new FriendService(this);
        this.groups = new GroupService(this);
        this.chat = new ChatService(this);
        this.moderation = new ModerationService(this);
        this.snapshots = new SnapshotService(this);
        this.party = new PartyService(this);
        this.voice = new VoiceService(this);
        this.claims = new ClaimsSync(this);
    }

    public static @Nullable SocialServer get() {
        return instance;
    }

    public static SocialServer start(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT).resolve(SocialMod.MOD_ID);
        ServerConfig.Storage storageConfig = ServerConfig.get().storage;
        FileStorageBackend files = new FileStorageBackend(root);
        StorageBackend backend = files;
        if (!storageConfig.backend.equals("file")) {
            try {
                backend = JdbcStorageBackend.open(storageConfig.backend, storageConfig.jdbcUrl, storageConfig.user, storageConfig.password,
                        storageConfig.tablePrefix, ServerConfig.directory(), files);
                SocialMod.LOGGER.info("[SocialMod] Almacenamiento: {}", storageConfig.backend);
            } catch (IOException | RuntimeException e) {
                SocialMod.LOGGER.warn("[SocialMod] El backend '{}' no está disponible ({}); se usa 'file'", storageConfig.backend, e.getMessage());
            }
        }
        SocialServer social = new SocialServer(server, backend);
        social.storage.loadIndex();
        social.groups.restoreAfterLoad();
        instance = social;
        return social;
    }

    public void stop() {
        try {
            storage.close();
        } finally {
            if (instance == this) {
                instance = null;
            }
        }
    }

    public void reconfigure() {
        ServerConfig config = ServerConfig.get();
        packetLimiter.reconfigure(config.limits.packetsPerSecond, config.limits.packetBurst);
        chat.reconfigure();
    }

    public void tick() {
        storage.tick();
        presence.tick();
        groups.tick();
        party.tick();
        claims.tick();
        snapshots.tick();
    }

    // ---------- Accesores ----------

    public MinecraftServer server() {
        return server;
    }

    public SocialStorage storage() {
        return storage;
    }

    public Notifier notifier() {
        return notifier;
    }

    public PresenceService presence() {
        return presence;
    }

    public FriendService friends() {
        return friends;
    }

    public ClaimsSync claims() {
        return claims;
    }

    public VoiceService voice() {
        return voice;
    }

    public PartyService party() {
        return party;
    }

    public GroupService groups() {
        return groups;
    }

    public ChatService chat() {
        return chat;
    }

    public ModerationService moderation() {
        return moderation;
    }

    public SnapshotService snapshots() {
        return snapshots;
    }

    public RateLimiter packetLimiter() {
        return packetLimiter;
    }

    // ---------- Jugadores y sesiones ----------

    public @Nullable ServerPlayer online(UUID id) {
        return server.getPlayerList().getPlayer(id);
    }

    public PlayerRecord record(ServerPlayer player) {
        return storage.getOrCreate(player.getUUID(), player.getGameProfile().name(), System.currentTimeMillis());
    }

    public @Nullable Session session(UUID player) {
        return sessions.get(player);
    }

    public boolean hasMod(UUID player) {
        return sessions.containsKey(player);
    }

    /** Handshake: el cliente anunció su protocolo. Si no coincide, sigue en modo "solo chat". */
    public void onHello(ServerPlayer player, int protocol) {
        if (protocol != Payloads.PROTOCOL_VERSION) {
            SocialMod.LOGGER.info("[SocialMod] {} usa el protocolo {} (servidor: {}): modo solo chat", player.getGameProfile().name(),
                    protocol, Payloads.PROTOCOL_VERSION);
            sessions.remove(player.getUUID());
        } else {
            sessions.put(player.getUUID(), new Session(protocol));
        }
        ServerConfig config = ServerConfig.get();
        send(player, new Payloads.HelloS2C(Payloads.PROTOCOL_VERSION, config.chat.maxMessageLength, config.chat.editWindowSeconds,
                config.chat.allowLinks, config.moderation.spyEnabled, config.chat.typingIndicator, config.chat.readReceipts,
                config.modules.groups, config.modules.parties, config.modules.sharing));
        if (protocol == Payloads.PROTOCOL_VERSION) {
            snapshots.send(player);
            presence.sendInitial(player);
            groups.nametags().sendAll(player);
        }
    }

    public void onJoin(ServerPlayer player) {
        PlayerRecord record = record(player);
        record.lastSeen = System.currentTimeMillis();
        storage.markPlayersDirty();
        presence.onJoin(player);
        groups.onJoin(player);
        chat.onJoin(player, record);
    }

    public void onLeave(ServerPlayer player) {
        UUID id = player.getUUID();
        PlayerRecord record = storage.player(id);
        if (record != null) {
            record.lastSeen = System.currentTimeMillis();
            storage.markPlayersDirty();
        }
        sessions.remove(id);
        packetLimiter.forget(id);
        chat.forget(id);
        presence.onLeave(player);
        groups.onLeave(player);
        party.forget(id);
        PermissionBridge.invalidate(id);
    }

    /** Envía un payload solo si el cliente tiene el canal registrado (cliente con el mod). */
    public void send(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /** Envía un payload a un jugador con el mod y protocolo compatible. */
    public void sendModded(ServerPlayer player, CustomPacketPayload payload) {
        if (sessions.containsKey(player.getUUID())) {
            send(player, payload);
        }
    }
}
