package com.takumistudios.socialmod.server.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.Conversation;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Datos en memoria + escritura as铆ncrona (PLAN 4.2). El hilo principal modifica los datos y los marca como sucios;
 * cada {@code flushIntervalSeconds} se preparan copias por lotes en el hilo principal y se escriben en un hilo propio.
 * Las conversaciones se cargan bajo demanda en el hilo de E/S y la acci贸n contin煤a en el hilo principal:
 * el servidor nunca espera al disco.
 */
public final class SocialStorage {
    public static final String PLAYERS = "players";
    public static final String GROUPS = "groups";
    public static final String CONVERSATIONS = "conversations";
    public static final String REPORTS = "reports";
    public static final String EXPORTS = "exports";
    public static final String AUDIT_LOG = "audit";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final StorageBackend backend;
    private final Executor mainThread;
    private final ExecutorService io;
    private final Map<UUID, PlayerRecord> players = new HashMap<>();
    private final Map<String, UUID> nameIndex = new HashMap<>();
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final LinkedHashMap<String, Conversation> conversations = new LinkedHashMap<>(64, 0.75f, true);
    private final Map<String, List<Consumer<Conversation>>> loading = new HashMap<>();
    private final Map<String,List<Runnable>> loadFailures=new HashMap<>();
    private final Set<String> dirtyConversations = new HashSet<>();
    private boolean playersDirty;
    private boolean groupsDirty;
    private int ticks;
    private static final int PREPARE_PER_TICK = 16;
    private static final class IndexPreparation {
        final String bucket; final List<?> records; final com.google.gson.JsonArray json=new com.google.gson.JsonArray(); int offset;
        IndexPreparation(String bucket,List<?> records) { this.bucket=bucket; this.records=records; }
    }
    private final java.util.ArrayDeque<IndexPreparation> preparations=new java.util.ArrayDeque<>();
    private boolean preparing(String bucket) { return preparations.stream().anyMatch(p -> p.bucket.equals(bucket)); }
    private void prepare(int budget) {
        while(!preparations.isEmpty() && budget>0) {
            var task=preparations.peek();
            while(task.offset<task.records.size() && budget-->0) task.json.add(GSON.toJsonTree(task.records.get(task.offset++)));
            if(task.offset==task.records.size()) { preparations.remove(); writeSnapshot(task.bucket,task.bucket,task.json); }
        }
    }
    private volatile boolean closed;
    private final ThreadLocal<Boolean> ioWorker = ThreadLocal.withInitial(() -> false);
    private record Document(String bucket, String key) { }
    // One immutable snapshot and at most one queued writer per document.
    private final Map<Document, JsonElement> pendingWrites = new LinkedHashMap<>();
    private final Set<Document> scheduledWrites = new HashSet<>();

    public SocialStorage(StorageBackend backend, Executor mainThread) {
        this(backend, mainThread, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "SocialMod-IO");
            thread.setDaemon(true);
            return thread;
        }));
    }

    SocialStorage(StorageBackend backend, Executor mainThread, ExecutorService io) {
        this.backend = backend;
        this.mainThread = mainThread;
        this.io = io;
    }

    public StorageBackend backend() {
        return backend;
    }

    /** Carga jugadores y grupos (al arrancar el servidor, antes de aceptar conexiones). */
    public void loadIndex() {
        try {
            JsonElement playersJson = backend.read(PLAYERS, PLAYERS);
            if (playersJson != null) {
                List<PlayerRecord> list = GSON.fromJson(playersJson, new TypeToken<List<PlayerRecord>>() { }.getType());
                for (PlayerRecord record : list) {
                    if (record != null && record.id != null) {
                        players.put(record.id, record.normalize());
                        nameIndex.put(record.name.toLowerCase(Locale.ROOT), record.id);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            SocialMod.LOGGER.error("[SocialMod] No se pudieron cargar los jugadores; se empieza con datos vac铆os", e);
        }
        try {
            JsonElement groupsJson = backend.read(GROUPS, GROUPS);
            if (groupsJson != null) {
                List<Group> list = GSON.fromJson(groupsJson, new TypeToken<List<Group>>() { }.getType());
                for (Group group : list) {
                    if (group != null && group.id != null && !group.party) {
                        groups.put(group.id, group.normalize());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            SocialMod.LOGGER.error("[SocialMod] No se pudieron cargar los grupos; se empieza con datos vac铆os", e);
        }
        SocialMod.LOGGER.info("[SocialMod] Datos cargados ({}): {} jugadores, {} grupos", backend.id(), players.size(), groups.size());
    }

    // ---------- Jugadores ----------

    public PlayerRecord player(UUID id) {
        return players.get(id);
    }

    public PlayerRecord getOrCreate(UUID id, String name, long now) {
        PlayerRecord record = players.get(id);
        if (record == null) {
            record = new PlayerRecord(id, name, now);
            players.put(id, record);
            playersDirty = true;
        } else if (!record.name.equals(name)) {
            nameIndex.remove(record.name.toLowerCase(Locale.ROOT));
            record.name = name;
            playersDirty = true;
        }
        nameIndex.put(name.toLowerCase(Locale.ROOT), id);
        return record;
    }

    public PlayerRecord findByName(String name) {
        UUID id = nameIndex.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : players.get(id);
    }

    public Collection<PlayerRecord> players() {
        return players.values();
    }

    public void removePlayer(UUID id) {
        PlayerRecord removed = players.remove(id);
        if (removed != null) {
            nameIndex.remove(removed.name.toLowerCase(Locale.ROOT));
            playersDirty = true;
        }
    }

    public String nameOf(UUID id) {
        PlayerRecord record = players.get(id);
        return record == null ? id.toString().substring(0, 8) : record.name;
    }

    public void markPlayersDirty() {
        playersDirty = true;
    }

    // ---------- Grupos ----------

    public Map<String, Group> groups() {
        return groups;
    }

    public void markGroupsDirty() {
        groupsDirty = true;
    }

    // ---------- Conversaciones ----------

    /** Ejecuta {@code action} en el hilo principal con la conversaci贸n (carg谩ndola si hace falta). */
    public void withConversation(String key, Consumer<Conversation> action) { withConversation(key,action,null); }
    public void withConversation(String key, Consumer<Conversation> action, @Nullable Runnable onFailure) {
        Conversation cached = conversations.get(key);
        if (cached != null) {
            action.accept(cached);
            return;
        }
        if(onFailure!=null)loadFailures.computeIfAbsent(key,k->new ArrayList<>()).add(onFailure);
        List<Consumer<Conversation>> waiting = loading.get(key);
        if (waiting != null) {
            waiting.add(action);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(action);
        loading.put(key, waiting);
        String file = key.replace(':', '_');
        runIo(() -> {
            Conversation loaded;
            try {
                JsonElement json;
                synchronized (pendingWrites) { json = pendingWrites.get(new Document(CONVERSATIONS, file)); }
                if (json == null) json = backend.read(CONVERSATIONS, file);
                loaded = json == null ? null : GSON.fromJson(json, Conversation.class);
            } catch (IOException | RuntimeException e) {
                SocialMod.LOGGER.warn("[SocialMod] No se pudo leer la conversaci贸n {}: {}", key, e.getMessage());
                mainThread.execute(() -> failLoad(key));
                return; // A failed read is not an absent history; never create writable empty data.
            }
            Conversation result = loaded == null ? new Conversation(key) : loaded.normalize();
            result.id = key;
            mainThread.execute(() -> finishLoad(key, result));
        }, () -> mainThread.execute(() -> failLoad(key)));
    }

    private void failLoad(String key) {
        loading.remove(key);var callbacks=loadFailures.remove(key);
        if(callbacks!=null)for(var callback:callbacks)try { callback.run(); } catch(RuntimeException e) { SocialMod.warnOnce("conversation_failure","Could not report unavailable conversation",e); }
    }
    private void finishLoad(String key, Conversation loaded) {
        loadFailures.remove(key);
        Conversation conversation = conversations.computeIfAbsent(key, k -> loaded);
        if (applyRetention(conversation)) {
            dirtyConversations.add(key);
        }
        List<Consumer<Conversation>> waiting = loading.remove(key);
        if (waiting != null) {
            for (Consumer<Conversation> action : waiting) {
                try {
                    action.accept(conversation);
                } catch (RuntimeException e) {
                    SocialMod.warnOnce("conversation_action", "Error procesando una conversaci贸n", e);
                }
            }
        }
        evictIfNeeded();
    }

    public Conversation cachedConversation(String key) {
        return conversations.get(key);
    }

    public void markConversationDirty(String key) {
        dirtyConversations.add(key);
    }

    public boolean applyRetention(Conversation conversation) {
        ServerConfig.Storage config = ServerConfig.get().storage;
        long minTime = config.retentionDays > 0 ? System.currentTimeMillis() - config.retentionDays * 86_400_000L : 0;
        return conversation.applyRetention(config.maxMessagesPerConversation, minTime);
    }

    private void evictIfNeeded() {
        int max = ServerConfig.get().storage.cachedConversations;
        if (conversations.size() <= max) {
            return;
        }
        var iterator = conversations.entrySet().iterator();
        while (conversations.size() > max && iterator.hasNext()) {
            var entry = iterator.next();
            if (dirtyConversations.contains(entry.getKey())) {
                continue; // se expulsa despu茅s de guardarla
            }
            iterator.remove();
        }
    }

    /** Borra el historial de una conversaci贸n (al disolver un grupo o borrar un canal). */
    public void deleteConversation(String key) {
        conversations.remove(key);
        dirtyConversations.remove(key);
        String file = key.replace(':', '_');
        synchronized (pendingWrites) { pendingWrites.remove(new Document(CONVERSATIONS, file)); }
        runIo(() -> {
            try {
                backend.delete(CONVERSATIONS, file);
            } catch (IOException e) {
                SocialMod.LOGGER.warn("[SocialMod] No se pudo borrar la conversaci贸n {}: {}", key, e.getMessage());
            }
        }, null);
    }

    /**
     * Modifica todas las conversaciones (cargadas y en disco), p. ej. para borrar los mensajes de un jugador.
     * {@code editor} devuelve {@code true} si cambi贸 algo.
     */
    public void editAllConversations(Predicate<Conversation> editor) {
        editAllConversations(editor, null);
    }

    /**
     * Igual, y {@code onDone} se ejecuta en el hilo principal cuando se han recorrido tambi茅n las del disco.
     * <p>El recorrido del disco va por lotes de {@link #SWEEP_BATCH} archivos, y cada lote se vuelve a encolar en el hilo
     * de E/S: as铆 las cargas de conversaciones de los jugadores no esperan detr谩s de miles de archivos (antes un
     * {@code /socialmod data delete} bloqueaba todos los chats varios segundos en un servidor grande).</p>
     */
    public void editAllConversations(Predicate<Conversation> editor, @Nullable Runnable onDone) {
        for (Map.Entry<String, Conversation> entry : conversations.entrySet()) {
            if (editor.test(entry.getValue())) {
                dirtyConversations.add(entry.getKey());
            }
        }
        Set<String> skip = new HashSet<>();
        conversations.keySet().forEach(k -> skip.add(k.replace(':', '_')));
        loading.keySet().forEach(k -> skip.add(k.replace(':', '_')));
        runIo(() -> {
            List<String> files;
            try {
                files = backend.keys(CONVERSATIONS);
            } catch (IOException | RuntimeException e) {
                SocialMod.LOGGER.warn("[SocialMod] Error listando conversaciones en disco: {}", e.getMessage());
                files = List.of();
            }
            sweep(files, 0, skip, editor, onDone);
        }, onDone == null ? null : () -> mainThread.execute(onDone));
    }

    private static final int SWEEP_BATCH = 16;

    private void sweep(List<String> files, int from, Set<String> skip, Predicate<Conversation> editor, @Nullable Runnable onDone) {
        int to = closed && ioWorker.get() ? files.size() : Math.min(files.size(), from + SWEEP_BATCH);
        for (int i = from; i < to; i++) {
            String file = files.get(i);
            if (skip.contains(file)) {
                continue;
            }
            try {
                JsonElement json = backend.read(CONVERSATIONS, file);
                if (json == null) {
                    continue;
                }
                Conversation conversation = GSON.fromJson(json, Conversation.class).normalize();
                if (editor.test(conversation)) {
                    backend.write(CONVERSATIONS, file, GSON.toJsonTree(conversation));
                }
            } catch (IOException | RuntimeException e) {
                SocialMod.LOGGER.warn("[SocialMod] Error editando la conversaci贸n {} en disco: {}", file, e.getMessage());
            }
        }
        if (to < files.size()) {
            runIo(() -> sweep(files, to, skip, editor, onDone), onDone == null ? null : () -> mainThread.execute(onDone));
        } else if (onDone != null) {
            mainThread.execute(onDone);
        }
    }

    // ---------- Otros documentos ----------

    public void writeDocument(String bucket, String key, JsonElement data) {
        if (closed) throw new IllegalStateException("Storage is closed");
        writeSnapshot(bucket, key, data.deepCopy());
    }

    /** Takes ownership of an already detached JSON tree created by flush(). */
    private void writeSnapshot(String bucket, String key, JsonElement data) {
        if (closed) throw new IllegalStateException("Storage is closed");
        Document document = new Document(bucket, key);
        synchronized (pendingWrites) { pendingWrites.put(document, data); }
        scheduleWrite(document);
    }

    private void scheduleWrite(Document document) {
        synchronized (pendingWrites) {
            if (!pendingWrites.containsKey(document) || !scheduledWrites.add(document)) return;
        }
        runIo(() -> {
            boolean released = false;
            try {
                for (int attempt = 0; attempt < 3; attempt++) {
                    JsonElement snapshot;
                    synchronized (pendingWrites) {
                        snapshot = pendingWrites.get(document);
                        if (snapshot == null) { scheduledWrites.remove(document); released = true; return; }
                    }
                    try {
                        backend.write(document.bucket(), document.key(), snapshot);
                        synchronized (pendingWrites) {
                            if (pendingWrites.get(document) == snapshot) {
                                pendingWrites.remove(document);
                                scheduledWrites.remove(document);
                                released = true; return;
                            }
                        }
                    } catch (IOException | RuntimeException e) {
                        SocialMod.warnOnce("storage_write_" + document.bucket(),
                                "Could not persist " + document.bucket() + "; retaining changes for retry", e);
                    }
                }
            } finally {
                if (!released) synchronized (pendingWrites) { scheduledWrites.remove(document); }
            }
        }, () -> { synchronized (pendingWrites) { scheduledWrites.remove(document); } });
    }

    private void retryWrites() {
        List<Document> documents;
        synchronized (pendingWrites) { documents = new ArrayList<>(pendingWrites.keySet()); }
        documents.forEach(this::scheduleWrite);
    }

    /** Lee varios documentos en el hilo de E/S y entrega el resultado en el hilo principal. */
    public void readDocuments(String bucket, int limit, Consumer<List<JsonObject>> callback) {
        runIo(() -> {
            List<JsonObject> result = new ArrayList<>();
            try {
                List<String> keys = backend.keys(bucket);
                keys.sort(java.util.Comparator.reverseOrder());
                for (String key : keys) {
                    if (result.size() >= limit) {
                        break;
                    }
                    JsonElement json = backend.read(bucket, key);
                    if (json != null && json.isJsonObject()) {
                        result.add(json.getAsJsonObject());
                    }
                }
            } catch (IOException | RuntimeException e) {
                SocialMod.LOGGER.warn("[SocialMod] No se pudo leer {}: {}", bucket, e.getMessage());
            }
            mainThread.execute(() -> callback.accept(result));
        }, null);
    }

    public void audit(String line) {
        if (!ServerConfig.get().moderation.auditLog) {
            return;
        }
        String stamped = java.time.Instant.now().toString() + " " + line;
        runIo(() -> {
            try {
                backend.appendLine(AUDIT_LOG, stamped);
            } catch (IOException e) {
                SocialMod.warnOnce("audit_write", "No se pudo escribir el registro de auditor铆a", e);
            }
        }, null);
    }

    public static Gson gson() {
        return GSON;
    }

    // ---------- Guardado ----------

    /** Llamar cada tick del servidor: agrupa las escrituras. */
    public void tick() {
        prepare(PREPARE_PER_TICK);
        if (++ticks >= ServerConfig.get().storage.flushIntervalSeconds * 20) {
            ticks = 0;
            flush();
        }
    }

    /** Programa la preparaci髇 por lotes de 韓dices y guarda conversaciones en el hilo de E/S. */
    public void flush() {
        if (closed) return;
        retryWrites();
        if (playersDirty && !preparing(PLAYERS)) {
            playersDirty = false;
            preparations.add(new IndexPreparation(PLAYERS,new ArrayList<>(players.values())));
        }
        if (groupsDirty && !preparing(GROUPS)) {
            groupsDirty = false;
            List<Group> persistent = groups.values().stream().filter(g -> !g.party).toList();
            preparations.add(new IndexPreparation(GROUPS,persistent));
        }
        if (!dirtyConversations.isEmpty()) {
            for (String key : dirtyConversations) {
                Conversation conversation = conversations.get(key);
                if (conversation != null) {
                    writeSnapshot(CONVERSATIONS, key.replace(':', '_'), GSON.toJsonTree(conversation));
                }
            }
            dirtyConversations.clear();
            evictIfNeeded();
        }
        prepare(PREPARE_PER_TICK);
    }

    /** Guarda todo y espera a que termine (al parar el servidor). */
    public void close() {
        if (closed) {
            return;
        }
        prepare(Integer.MAX_VALUE);
        flush();
        prepare(Integer.MAX_VALUE);
        closed = true;
        // The backend closes on its own executor, after all previously queued operations.
        // A timeout or interruption of the caller must never close an in-use JDBC connection.
        io.execute(() -> {
            // No producer can mutate these snapshots after closed=true. Drain retained failures
            // and revisions that arrived while an earlier writer was completing its last attempt.
            List<Document> remaining;
            synchronized (pendingWrites) { remaining = new ArrayList<>(pendingWrites.keySet()); }
            for (Document document : remaining) {
                JsonElement snapshot;
                synchronized (pendingWrites) { snapshot = pendingWrites.get(document); }
                for (int attempt = 0; snapshot != null && attempt < 3; attempt++) {
                    try {
                        backend.write(document.bucket(), document.key(), snapshot);
                        synchronized (pendingWrites) { pendingWrites.remove(document); }
                        break;
                    } catch (IOException | RuntimeException e) {
                        SocialMod.warnOnce("storage_final_write", "Final storage write failed", e);
                    }
                }
            }
            synchronized (pendingWrites) {
                if (!pendingWrites.isEmpty()) SocialMod.LOGGER.error(
                        "[SocialMod] {} documents remain unsaved after retries", pendingWrites.size());
            }
            backend.close();
        });
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                SocialMod.LOGGER.warn("[SocialMod] Storage still draining; backend will close after pending operations");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void runIo(Runnable task, Runnable onRejected) {
        Runnable guarded = () -> {
            boolean wasWorker = ioWorker.get();
            ioWorker.set(true);
            try { task.run(); }
            catch (RuntimeException e) {
                SocialMod.LOGGER.warn("[SocialMod] Error de E/S", e);
                if (onRejected != null) onRejected.run();
            } finally { ioWorker.set(wasWorker); }
        };
        if (closed || io.isShutdown()) {
            // Sweep continuations already running on the worker drain before backend.close().
            if (ioWorker.get()) guarded.run();
            else if (onRejected != null) onRejected.run();
            return;
        }
        try { io.execute(guarded); }
        catch (java.util.concurrent.RejectedExecutionException e) {
            SocialMod.LOGGER.warn("[SocialMod] Storage operation rejected during shutdown", e);
            if (onRejected != null) onRejected.run();
        }
    }
}
