package com.takumistudios.socialmod.common;

import com.takumistudios.socialmod.common.text.TextSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextSanitizerTest {
    @Test
    void removesControlAndFormattingCodes() {
        assertEquals("hola mundo", TextSanitizer.clean("§chola\u0000 \u0007mundo§r", 100));
    }

    @Test
    void removesDirectionOverridesAndInvisibleCharacters() {
        assertEquals("Alexadmin", TextSanitizer.clean("Alex‮admin​", 100));
        assertEquals("ab", TextSanitizer.clean("a﻿b­", 100));
    }

    @Test
    void collapsesWhitespaceAndNewlines() {
        assertEquals("a b c", TextSanitizer.clean("  a\n\n b\t\tc  ", 100));
    }

    @Test
    void capsByCodePoints() {
        assertEquals("abc", TextSanitizer.clean("abcdef", 3));
        String huge = "x".repeat(1_000_000);
        assertEquals(256, TextSanitizer.clean(huge, 256).length(), "un texto de 1 MB se recorta");
    }

    @Test
    void removesPrivateUseGlyphs() {
        assertEquals("ok", TextSanitizer.clean("ok", 10));
    }
}
