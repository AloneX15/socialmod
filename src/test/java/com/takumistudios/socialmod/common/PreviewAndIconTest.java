package com.takumistudios.socialmod.common;

import com.takumistudios.socialmod.common.model.GroupIcon;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.common.text.MessageFormatter;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SOCIALMOD_ERRORES 3 (iconos) y 4 (marcadores en las notificaciones). */
class PreviewAndIconTest {
    private static Payloads.AttachmentView coords(int x, int y, int z) {
        return new Payloads.AttachmentView(false, null, "minecraft:overworld", x, y, z);
    }

    @Test
    void coordsAreRenderedInToastPreview() {
        String preview = MessageFormatter.preview("ven aquí [coords] rápido", List.of(coords(120, 64, -450)), 80);
        assertEquals("ven aquí x: 120, z: -450 rápido", preview);
        assertFalse(preview.contains("[coords]"));
    }

    @Test
    void uppercaseAndMisspelledTokensAreNormalized() {
        assertEquals("[coords] y [item]", MessageFormatter.normalizeTokens("[CORDS] y [ITEM]"));
        assertEquals("[coords]", MessageFormatter.normalizeTokens("[Coords]"));
        assertEquals("x: 1, z: 3", MessageFormatter.preview("[CORDS]", List.of(coords(1, 2, 3)), 80));
    }

    @Test
    void tokensWithoutAttachmentNeverShowRaw() {
        String preview = MessageFormatter.preview("mira [item] y [coords]", List.of(), 80);
        assertEquals("mira (item) y (coords)", preview);
    }

    @Test
    void previewIsTruncated() {
        String preview = MessageFormatter.preview("a".repeat(100), List.of(), 10);
        assertEquals(10, preview.length());
        assertTrue(preview.endsWith("…"));
    }

    @Test
    void iconsAreWhitelistedAndUnique() {
        assertEquals(GroupIcon.SWORDS, GroupIcon.byId("SWORDS"));
        assertNull(GroupIcon.byId("<script>"));
        assertNull(GroupIcon.byId(null));
        assertEquals("", GroupIcon.glyphOf("desconocido"));
        Set<String> glyphs = new HashSet<>();
        for (GroupIcon icon : GroupIcon.values()) {
            assertTrue(icon.id().matches("[a-z_]{1,16}"), "id corto para el paquete: " + icon.id());
            assertTrue(icon.glyph().codePoints().allMatch(cp -> cp < 0x10000), "solo plano básico: " + icon.id());
            assertTrue(glyphs.add(icon.glyph()), "símbolo repetido: " + icon.id());
        }
    }
}
