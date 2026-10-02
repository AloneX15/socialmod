package com.takumistudios.socialmod.common;

import com.takumistudios.socialmod.common.text.SafeMarkdown;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeMarkdownTest {
    @Test
    void boldItalicAndCode() {
        List<SafeMarkdown.Span> spans = SafeMarkdown.parse("hola **mundo** y *tú* con `code`");
        assertEquals("hola ", spans.get(0).text());
        assertTrue(spans.get(1).bold());
        assertEquals("mundo", spans.get(1).text());
        assertTrue(spans.stream().anyMatch(s -> s.italic() && s.text().equals("tú")));
        assertTrue(spans.stream().anyMatch(s -> s.kind() == SafeMarkdown.Kind.CODE && s.text().equals("code")));
    }

    @Test
    void unclosedMarkersStayLiteral() {
        assertEquals("2 * 3 = 6 y **sin cerrar", SafeMarkdown.plain("2 * 3 = 6 y **sin cerrar"));
        assertEquals("a `b", SafeMarkdown.plain("a `b"));
    }

    @Test
    void escapes() {
        List<SafeMarkdown.Span> spans = SafeMarkdown.parse("\\*no cursiva\\* y \\@nadie");
        assertEquals(1, spans.size());
        assertEquals("*no cursiva* y @nadie", spans.getFirst().text());
        assertFalse(spans.getFirst().italic());
    }

    @Test
    void mentionsAreCollectedLowercase() {
        assertEquals(Set.of("alex", "tf"), SafeMarkdown.mentions("hey @Alex mira @TF, correo@ejemplo.com no cuenta"));
    }

    @Test
    void linksAreDetectedAndTrimmed() {
        List<SafeMarkdown.Span> spans = SafeMarkdown.parse("mira https://example.com/a?b=1. ¡ya!");
        assertTrue(spans.stream().anyMatch(s -> s.kind() == SafeMarkdown.Kind.LINK && s.text().equals("https://example.com/a?b=1")));
        assertFalse(SafeMarkdown.containsLink("javascript:alert(1)"));
    }

    @Test
    void jsonIsNeverInterpreted() {
        String json = "{\"text\":\"x\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/op me\"}}";
        assertEquals(json, SafeMarkdown.plain(json), "el JSON de componentes se muestra como texto");
    }
}
