package com.takumistudios.socialmod.server.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceRecoveryTest {
    private static class Backend implements StorageBackend {
        final Map<String, JsonElement> documents = new ConcurrentHashMap<>();
        int failures, writes;
        volatile boolean closed;
        public String id() { return "test"; }
        public JsonElement read(String bucket, String key) { return documents.get(bucket + "/" + key); }
        public void write(String bucket, String key, JsonElement data) throws IOException {
            writes++;
            if (failures-- > 0) throw new IOException("temporary failure");
            if (closed) throw new IOException("write after close");
            documents.put(bucket + "/" + key, data);
        }
        public void delete(String bucket, String key) { documents.remove(bucket + "/" + key); }
        public List<String> keys(String bucket) { return List.of(); }
        public void appendLine(String log, String line) { }
        public void close() { closed = true; }
    }
    private static class Queue extends AbstractExecutorService {
        final Deque<Runnable> tasks = new ArrayDeque<>();
        boolean shutdown;
        public void execute(Runnable task) { if (shutdown) throw new RejectedExecutionException(); tasks.add(task); }
        void drain() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }
        public void shutdown() { shutdown = true; drain(); }
        public List<Runnable> shutdownNow() { shutdown(); return List.of(); }
        public boolean isShutdown() { return shutdown; }
        public boolean isTerminated() { return shutdown && tasks.isEmpty(); }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }
    @Test void failedReadDoesNotCreateWritableEmptyHistoryAndCanBeRetried() {
        Backend backend=new Backend() { boolean fail=true; public JsonElement read(String bucket,String key) { if(fail) { fail=false; throw new IllegalStateException("unavailable"); } return super.read(bucket,key); } };
        String key="dm:test"; var old=new com.takumistudios.socialmod.server.data.Conversation(key);old.nextId=99;
        backend.documents.put("conversations/dm_test",SocialStorage.gson().toJsonTree(old));
        Queue io=new Queue();var storage=new SocialStorage(backend,Runnable::run,io);
        var failed=new java.util.concurrent.atomic.AtomicBoolean();
        var called=new java.util.concurrent.atomic.AtomicBoolean();
        storage.withConversation(key,c->{called.set(true); storage.markConversationDirty(key);},()->failed.set(true));io.drain();assertTrue(failed.get());
        assertFalse(called.get());assertNull(storage.cachedConversation(key));storage.flush();io.drain();assertEquals(0,backend.writes);
        storage.withConversation(key,c->{assertEquals(99,c.nextId);called.set(true);});io.drain();assertTrue(called.get());storage.close();
    }
    @Test void largeIndexesPrepareOverSeveralTicksAndShutdownDrainsThem() {
        Backend backend=new Backend();Queue io=new Queue();var storage=new SocialStorage(backend,Runnable::run,io);
        for(int i=0;i<1000;i++)storage.getOrCreate(UUID.randomUUID(),"p"+i,1);
        storage.flush();io.drain();assertFalse(backend.documents.containsKey("players/players"));
        storage.close();assertEquals(1000,backend.documents.get("players/players").getAsJsonArray().size());
    }
    @Test void repeatedChangesCoalesceAndSnapshotsDoNotFollowCallerMutations() {
        Backend backend = new Backend(); Queue io = new Queue();
        SocialStorage storage = new SocialStorage(backend, Runnable::run, io);
        var data = new com.google.gson.JsonObject();
        for (int i = 0; i < 10000; i++) { data.addProperty("value", i); storage.writeDocument("players", "one", data); }
        data.addProperty("value", -1);
        assertEquals(1, io.tasks.size());
        io.drain();
        assertEquals(1, backend.writes);
        assertEquals(9999, backend.read("players", "one").getAsJsonObject().get("value").getAsInt());
        storage.close(); assertTrue(backend.closed);
        assertThrows(IllegalStateException.class, () -> storage.writeDocument("players", "one", data));
    }
    @Test void exhaustedRetriesRetainLatestDataForNextFlush() {
        Backend backend = new Backend(); Queue io = new Queue();
        SocialStorage storage = new SocialStorage(backend, Runnable::run, io);
        backend.failures = 3;
        storage.writeDocument("reports", "one", new JsonPrimitive("first")); io.drain();
        assertNull(backend.read("reports", "one"));
        storage.writeDocument("reports", "one", new JsonPrimitive("latest"));
        io.drain(); assertEquals("latest", backend.read("reports", "one").getAsString());
        storage.close();
    }
    @Test void flushRetriesFailureWithoutAnotherMutation() {
        Backend backend = new Backend(); Queue io = new Queue();
        SocialStorage storage = new SocialStorage(backend, Runnable::run, io);
        backend.failures = 3;
        storage.writeDocument("reports", "one", new JsonPrimitive("keep")); io.drain();
        storage.flush(); io.drain();
        assertEquals("keep", backend.read("reports", "one").getAsString()); storage.close();
    }
    @Test void interruptedShutdownClosesBackendOnlyAfterWriterFinishes() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Backend backend = new Backend() {
            public void write(String bucket, String key, JsonElement data) throws IOException {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("test timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                super.write(bucket, key, data);
            }
        };
        ExecutorService io = Executors.newSingleThreadExecutor();
        SocialStorage storage = new SocialStorage(backend, Runnable::run, io);
        try {
            storage.writeDocument("reports", "one", new JsonPrimitive("keep"));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Thread.currentThread().interrupt(); storage.close();
            assertTrue(Thread.interrupted()); assertFalse(backend.closed);
        } finally { Thread.interrupted(); release.countDown(); }
        assertTrue(io.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(backend.closed); assertEquals("keep", backend.read("reports", "one").getAsString());
    }
    @Test void shutdownDrainsRemainingSweepBatchesBeforeBackendClose() {
        Backend backend = new Backend() {
            public List<String> keys(String bucket) { return java.util.stream.IntStream.range(0, 40).mapToObj(i -> "c" + i).toList(); }
        };
        for (int i = 0; i < 40; i++) backend.documents.put("conversations/c" + i, SocialStorage.gson().toJsonTree(new com.takumistudios.socialmod.server.data.Conversation("c" + i)));
        Queue io = new Queue(); SocialStorage storage = new SocialStorage(backend, Runnable::run, io);
        var done = new java.util.concurrent.atomic.AtomicBoolean();
        storage.editAllConversations(c -> { c.nextId = 2; return true; }, () -> done.set(true));
        storage.close();
        assertTrue(done.get()); assertTrue(backend.closed);
        for (int i = 0; i < 40; i++) assertEquals(2, backend.documents.get("conversations/c" + i).getAsJsonObject().get("nextId").getAsInt());
    }
}
