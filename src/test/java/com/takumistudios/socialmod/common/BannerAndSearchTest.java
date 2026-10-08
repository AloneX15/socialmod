package com.takumistudios.socialmod.common;

import com.takumistudios.socialmod.common.model.TeamBanner;
import com.takumistudios.socialmod.common.model.PlayerSearch;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.storage.SocialStorage;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class BannerAndSearchTest {
    @Test void oldTeamsReceiveWhiteBannerAndCopiesAreIndependent() {
        var team = SocialStorage.gson().fromJson("{\"id\":\"team\",\"team\":true}", Group.class).normalize();
        assertEquals(0, team.banner.base); assertTrue(team.banner.layers.isEmpty());
        team.banner.layers.add(new TeamBanner.Layer("minecraft:stripe_center", 14));
        var restored = SocialStorage.gson().fromJson(SocialStorage.gson().toJson(team), Group.class).normalize();
        assertEquals(team.banner.layers, restored.banner.layers);
        var copy = team.banner.copy(); copy.layers.clear(); assertEquals(1, team.banner.layers.size());
    }
    @Test void rejectsMalformedAndOversizedBannerData() {
        assertThrows(IllegalArgumentException.class, () -> TeamBanner.parse("null"));
        assertThrows(IllegalArgumentException.class, () -> TeamBanner.parse("{\"base\":16}"));
        assertThrows(IllegalArgumentException.class, () -> TeamBanner.parse("{\"layers\":null}"));
        var banner = new TeamBanner(); banner.layers.add(new TeamBanner.Layer("minecraft:../secret", 0));
        assertThrows(IllegalArgumentException.class, banner::validate);
        banner.layers = IntStream.range(0, 7).mapToObj(i -> new TeamBanner.Layer("minecraft:cross", i)).toList();
        assertThrows(IllegalArgumentException.class, banner::validate);
        banner.layers = List.of(new TeamBanner.Layer("minecraft:cross", -1)); assertThrows(IllegalArgumentException.class, banner::validate);
    }
    @Test void directoryPagesAreStableAndBounded() {
        UUID self = UUID.randomUUID();
        var players = new java.util.ArrayList<>(IntStream.range(0, 45).mapToObj(i -> new PlayerSearch.Entry(UUID.randomUUID(), "Alex" + String.format("%02d", i), "offline")).toList());
        players.add(new PlayerSearch.Entry(self, "AlexSelf", "online"));
        var first = PlayerSearch.find(7, "aLeX", 0, self, players);
        assertEquals(20, first.entries().size()); assertTrue(first.more()); assertEquals(7, first.request()); assertEquals("Alex00", first.entries().getFirst().name());
        var last = PlayerSearch.find(8, "alex", 2, self, players); assertEquals(5, last.entries().size()); assertFalse(last.more());
        assertTrue(PlayerSearch.find(9, "alex", 10, self, players).entries().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> PlayerSearch.find(1, "a".repeat(33), 0, self, players));
        assertThrows(IllegalArgumentException.class, () -> PlayerSearch.find(1, "alex", -1, self, players));
    }
    @Test void emptyOrShortSearchDoesNotRevealHiddenPresence() {
        UUID self = UUID.randomUUID();
        var visible = new PlayerSearch.Entry(UUID.randomUUID(), "Alex", "online");
        var hidden = new PlayerSearch.Entry(UUID.randomUUID(), "Alice", "offline");
        assertEquals(List.of(visible), PlayerSearch.find(1, "", 0, self, List.of(visible, hidden)).entries());
        assertEquals(List.of(visible), PlayerSearch.find(2, "a", 0, self, List.of(visible, hidden)).entries());
        var result = PlayerSearch.find(3, "ali", 0, self, List.of(visible, hidden));
        assertEquals(List.of(hidden), result.entries()); assertEquals("offline", result.entries().getFirst().status());
    }
}
