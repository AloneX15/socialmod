package com.takumistudios.socialmod.server;

import com.google.gson.JsonObject;
import com.takumistudios.socialmod.server.storage.FileStorageBackend;
import com.takumistudios.socialmod.server.storage.JdbcStorageBackend;
import com.takumistudios.socialmod.server.storage.SocialStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Backend H2 (el driver está en el classpath de pruebas; en un servidor real va en config/socialmod/drivers). */
class JdbcStorageTest {
    @TempDir
    Path dir;

    private static JsonObject doc(String value) {
        JsonObject json = new JsonObject();
        json.addProperty("value", value);
        return json;
    }

    @Test
    void roundTripUpsertDeleteAndKeys() throws IOException {
        FileStorageBackend files = new FileStorageBackend(dir.resolve("world"));
        try (JdbcStorageBackend backend = JdbcStorageBackend.open("h2", "", "", "", "socialmod_", dir.resolve("config"), files)) {
            backend.write(SocialStorage.GROUPS, "groups", doc("a"));
            backend.write(SocialStorage.GROUPS, "groups", doc("b"));
            assertEquals("b", backend.read(SocialStorage.GROUPS, "groups").getAsJsonObject().get("value").getAsString());
            assertEquals(List.of("groups"), backend.keys(SocialStorage.GROUPS));
            backend.delete(SocialStorage.GROUPS, "groups");
            assertNull(backend.read(SocialStorage.GROUPS, "groups"));
        }
    }

    @Test
    void importsFileDataOnceAndKeepsAuditAndExportsInFiles() throws IOException {
        Path world = dir.resolve("world");
        FileStorageBackend files = new FileStorageBackend(world);
        files.write(SocialStorage.PLAYERS, "players", doc("legacy"));
        try (JdbcStorageBackend backend = JdbcStorageBackend.open("h2", "", "", "", "sm_", dir.resolve("config"), files)) {
            assertEquals("legacy", backend.read(SocialStorage.PLAYERS, "players").getAsJsonObject().get("value").getAsString());
            backend.appendLine(SocialStorage.AUDIT_LOG, "linea");
            backend.write(SocialStorage.EXPORTS, "alex", doc("export"));
        }
        assertTrue(Files.readString(world.resolve("audit.log")).contains("linea"));
        assertTrue(Files.exists(world.resolve("exports/alex.json.gz")));
    }

    @Test
    void badPrefixAndUnknownBackendAreRejected() {
        FileStorageBackend files = new FileStorageBackend(dir);
        assertThrows(IOException.class, () -> JdbcStorageBackend.open("h2", "", "", "", "x; DROP TABLE", dir, files));
        assertThrows(IOException.class, () -> JdbcStorageBackend.open("oracle", "", "", "", "sm_", dir, files));
    }
}
