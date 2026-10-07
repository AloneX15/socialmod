package com.takumistudios.socialmod.common;
import com.takumistudios.socialmod.common.model.*;
import com.takumistudios.socialmod.client.theme.Theme;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SeriesTemplatesTest {
    @Test void bothStylesKeepWorldVisibleAndHaveIndependentEditableCopies() {
        for(String id:SeriesTemplates.IDS) {
            var visual=SeriesTemplates.visual(id); var theme=Theme.CODEC.parse(JsonOps.INSTANCE,visual.theme).result().orElseThrow();
            assertTrue(visual.transparentWorld); assertEquals(0,theme.colors().background()); assertTrue(theme.textures().background().isEmpty());
            assertTrue((theme.colors().panel()>>>24)<255);assertTrue((theme.colors().panel()>>>24)>0);
            visual.seriesStyle="";assertEquals(id,SeriesTemplates.visual(id).seriesStyle);
            var rows=SeriesTemplates.rows(id); assertTrue(rows.templates.values().stream().allMatch(t->t.enabled));
            assertTrue(rows.templates.get("player").parts.stream().anyMatch(p->p.field.equals("status")));
        }
    }
}
