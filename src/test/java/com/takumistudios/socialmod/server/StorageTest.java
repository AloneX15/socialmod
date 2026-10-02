package com.takumistudios.socialmod.server;

import com.google.gson.JsonElement;
import com.takumistudios.socialmod.server.data.ChatMessage;
import com.takumistudios.socialmod.server.data.Conversation;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import com.takumistudios.socialmod.server.storage.FileStorageBackend;
import com.takumistudios.socialmod.server.storage.SocialStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageTest {
    @TempDir
    Path dir;

    @Test
    void conversationRoundTripCompressed() throws IOException {
        FileStorageBackend backend = new FileStorageBackend(dir);
        Conversation conversation = new Conversation("g:abcd1234:general");
        for (int i = 0; i < 3; i++) {
            conversation.messages.add(new ChatMessage(conversation.nextId++, UUID.randomUUID(), "Alex", "hola " + i, 1000L * i));
        }
        backend.write(SocialStorage.CONVERSATIONS, "g_abcd1234_general", SocialStorage.gson().toJsonTree(conversation));
        assertTrue(Files.exists(dir.resolve("conversations/g_abcd1234_general.json.gz")));
        JsonElement json = backend.read(SocialStorage.CONVERSATIONS, "g_abcd1234_general");
        Conversation loaded = SocialStorage.gson().fromJson(json, Conversation.class).normalize();
        assertEquals(3, loaded.messages.size());
        assertEquals(4, loaded.nextId);
        assertEquals("hola 1", loaded.find(2).text);
        assertEquals(List.of("g_abcd1234_general"), backend.keys(SocialStorage.CONVERSATIONS));
    }

    @Test
    void corruptFilesAreQuarantined() throws IOException {
        FileStorageBackend backend = new FileStorageBackend(dir);
        Files.createDirectories(dir.resolve("players"));
        Files.writeString(dir.resolve("players/players.json.gz"), "esto no es gzip");
        assertThrows(IOException.class, () -> backend.read("players", "players"));
        assertNull(backend.read("players", "players"), "el archivo corrupto se aparta y no se vuelve a leer");
    }

    @Test
    void pathTraversalIsRejected() {
        FileStorageBackend backend = new FileStorageBackend(dir);
        assertThrows(IllegalArgumentException.class, () -> backend.read("conversations", "../../secret"));
    }

    @Test
    void retentionByCountAndAge() {
        Conversation conversation = new Conversation("x");
        for (int i = 1; i <= 10; i++) {
            conversation.messages.add(new ChatMessage(i, UUID.randomUUID(), "a", "m" + i, i * 1000L));
        }
        assertTrue(conversation.applyRetention(8, 0));
        assertEquals(3, conversation.messages.getFirst().id);
        assertTrue(conversation.applyRetention(100, 6000));
        assertEquals(6, conversation.messages.getFirst().id);
    }

    @Test
    void historyPaging() {
        Conversation conversation = new Conversation("x");
        for (int i = 1; i <= 10; i++) {
            conversation.messages.add(new ChatMessage(i, UUID.randomUUID(), "a", "m" + i, i));
        }
        List<ChatMessage> last = conversation.page(0, 3);
        assertEquals(List.of(8L, 9L, 10L), last.stream().map(m -> m.id).toList());
        List<ChatMessage> before = conversation.page(8, 3);
        assertEquals(List.of(5L, 6L, 7L), before.stream().map(m -> m.id).toList());
        assertTrue(conversation.page(1, 3).isEmpty());
    }

    @Test
    void playerRecordNormalizesNulls() {
        PlayerRecord record = SocialStorage.gson().fromJson("{\"id\":\"" + UUID.randomUUID() + "\",\"friends\":null,\"status\":\"OFFLINE\"}",
                PlayerRecord.class).normalize();
        assertNotNull(record.friends);
        assertNotNull(record.recent);
        assertEquals(com.takumistudios.socialmod.common.model.PresenceStatus.ONLINE, record.status);
    }
}
