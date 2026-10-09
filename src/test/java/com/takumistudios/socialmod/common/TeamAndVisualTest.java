package com.takumistudios.socialmod.common;

import com.takumistudios.socialmod.common.model.VisualDesign;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.service.TeamService;
import com.takumistudios.socialmod.server.storage.FileStorageBackend;
import com.takumistudios.socialmod.server.storage.JdbcStorageBackend;
import com.takumistudios.socialmod.server.storage.StorageBackend;
import com.takumistudios.socialmod.server.storage.SocialStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TeamAndVisualTest {
    @TempDir Path directory;
    @Test void visualHistoryRetainsPreviousAndBoundsRevisions() throws Exception {
        Path file = directory.resolve("visual.json");
        for (int i = 0; i < 25; i++) com.takumistudios.socialmod.server.service.VisualRevisionStore.save(file, "previous-" + i, "current-" + i);
        assertEquals("current-24", java.nio.file.Files.readString(file));
        assertEquals("previous-24", java.nio.file.Files.readString(directory.resolve("visual.previous.json")));
        try (var paths = java.nio.file.Files.list(directory.resolve("visual-history"))) { assertEquals(20, paths.count()); }
        assertFalse(java.nio.file.Files.exists(directory.resolve("visual.json.tmp")));
    }
    @Test void retiredStylesResetEntireDesign() {
        for (String style : java.util.List.of("christmas", "dedsafio")) {
            var reset = VisualDesign.parse("{\"seriesStyle\":\""+style+"\",\"mode\":\"full\",\"widthPercent\":99,\"buttonColor\":123}");
            assertEquals(VisualDesign.GSON.toJson(new VisualDesign()), VisualDesign.GSON.toJson(reset));
        }
        assertEquals("none", VisualDesign.parse("{\"decoration\":\"christmas\"}").decoration);
        assertThrows(IllegalArgumentException.class, () -> VisualDesign.parse("{\"decoration\":\"unknown\"}"));
    }
    @Test void visualFrameAndInputSettingsRemainBackwardCompatible() {
        var preset=VisualDesign.parse("{\"mode\":\"compact\",\"panelInset\":20,\"inputTexture\":\"socialmod:custom/input\"}");
        assertEquals(20,preset.copy().panelInset); assertEquals("socialmod:custom/input",preset.copy().inputTexture);
        preset.panelInset=25; assertThrows(IllegalArgumentException.class,preset::validate);
        preset.panelInset=20; preset.inputTexture="socialmod:../invalid"; assertThrows(IllegalArgumentException.class,preset::validate);
    }
    @Test void removedPresetFontReturnsToDefault() {
        assertEquals("minecraft:default",VisualDesign.parse("{\"font\":\"socialmod:christmas\"}").font);
    }
    @Test void oldPlayersKeepGroupsWithoutSelectingTeam() {
        PlayerRecord record = SocialStorage.gson().fromJson("{\"mainGroup\":\"abcd\"}", PlayerRecord.class).normalize();
        assertEquals("abcd", record.mainGroup); assertEquals("", record.teamId); assertTrue(TeamService.mayChoose(record));
        record.teamChosen = true; assertFalse(TeamService.mayChoose(record));
        record.teamChosen = false; record.teamId = "t1234"; assertFalse(TeamService.mayChoose(record));
    }
    @Test void fileStoragePreservesTeamAndArchive() throws Exception {
        roundTrip(new FileStorageBackend(directory));
    }
    @Test void jdbcStoragePreservesTeamAndArchive() throws Exception {
        FileStorageBackend files = new FileStorageBackend(directory.resolve("world"));
        try (JdbcStorageBackend backend = JdbcStorageBackend.open("h2", "", "", "", "team_", directory.resolve("config"), files)) { roundTrip(backend); }
    }
    private void roundTrip(StorageBackend backend) throws Exception {
        UUID id = UUID.randomUUID(); Group team = new Group(); team.id = "tabcd1234"; team.team = true; team.archived = true; team.name = "Forest"; team.archiveReaders.add(id); team.archiveRoles.put(id, com.takumistudios.socialmod.common.model.Role.OFFICER);
        backend.write("groups", "teams-test", SocialStorage.gson().toJsonTree(team));
        Group restored = SocialStorage.gson().fromJson(backend.read("groups", "teams-test"), Group.class).normalize();
        assertTrue(restored.team); assertTrue(restored.archived); assertTrue(restored.archiveReaders.contains(id)); assertTrue(restored.members.isEmpty()); assertEquals(com.takumistudios.socialmod.common.model.Role.OFFICER, restored.archiveRoles.get(id));
        PlayerRecord player = new PlayerRecord(id, "Alex", 1); player.teamId = team.id; player.teamChosen = true;
        backend.write("players", "team-test", SocialStorage.gson().toJsonTree(player));
        PlayerRecord loaded = SocialStorage.gson().fromJson(backend.read("players", "team-test"), PlayerRecord.class).normalize();
        assertEquals(team.id, loaded.teamId); assertTrue(loaded.teamChosen);
    }
    @Test void visualPresetRoundTripAndVersionValidation() {
        VisualDesign design = new VisualDesign(); design.mode = "sidebar";
        design.components.put("SettingsScreen/button/ok", new VisualDesign.Rect(.1f, .2f, .3f, .1f));
        assertEquals("sidebar", design.copy().mode); assertEquals(.3f, design.copy().components.values().iterator().next().w);
        assertThrows(IllegalArgumentException.class, () -> VisualDesign.parse("{\"version\":2}"));
        assertThrows(IllegalArgumentException.class, () -> VisualDesign.parse("{\"mode\":\"broken\"}"));
        assertThrows(IllegalArgumentException.class, () -> VisualDesign.parse("{\"theme\":{\"colors\":{\"text\":\"oops\"}}}"));
        assertThrows(IllegalArgumentException.class, () -> VisualDesign.parse("{\"font\":\"minecraft:../file\"}"));
    }
    @Test void invalidRectanglesAndResourcePacksAreRejected() {
        VisualDesign design = new VisualDesign(); design.components.put("button", new VisualDesign.Rect(.9f, .1f, .3f, .1f));
        assertThrows(IllegalArgumentException.class, design::validate);
        design.components.clear(); design.resourcePackUrl = "file:///secret";
        assertThrows(IllegalArgumentException.class, design::validate);
        design.resourcePackUrl = "https://example.com/series.zip"; design.resourcePackSha1 = "a".repeat(40); assertDoesNotThrow(design::validate);
    }
}
