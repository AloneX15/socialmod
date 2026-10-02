package com.takumistudios.socialmod.common.text;

/**
 * Limpieza de todo texto que llega de un jugador (mensajes, estados, nombres de grupo...).
 * Quita caracteres de control, códigos de formato {@code §}, caracteres invisibles y de dirección
 * (usados para suplantar nombres), une las líneas y recorta a la longitud máxima.
 */
public final class TextSanitizer {
    private TextSanitizer() {
    }

    public static String clean(String input, int maxCodePoints) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(input.length(), maxCodePoints * 2));
        int count = 0;
        boolean lastSpace = true;
        for (int i = 0; i < input.length() && count < maxCodePoints; ) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§') {
                // Código de formato heredado: se quitan el símbolo y el carácter del código (§c, §l...)
                if (i < input.length()) {
                    i += Character.charCount(input.codePointAt(i));
                }
                continue;
            }
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                if (!lastSpace) {
                    out.append(' ');
                    count++;
                    lastSpace = true;
                }
                continue;
            }
            if (!isAllowed(cp)) {
                continue;
            }
            out.appendCodePoint(cp);
            count++;
            lastSpace = false;
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == ' ') {
            end--;
        }
        out.setLength(end);
        return out.toString();
    }

    static boolean isAllowed(int cp) {
        if (cp < 0x20 || cp == 0x7F || (cp >= 0x80 && cp < 0xA0)) {
            return false; // control
        }
        if (cp == '§') {
            return false; // códigos de formato heredados
        }
        if (cp >= 0x200B && cp <= 0x200F) {
            return false; // zero-width y marcas de dirección
        }
        if (cp >= 0x202A && cp <= 0x202E) {
            return false; // overrides de dirección (suplantación)
        }
        if (cp >= 0x2066 && cp <= 0x2069) {
            return false; // aislamientos de dirección
        }
        if (cp == 0xFEFF || cp == 0x00AD) {
            return false; // BOM y guion invisible
        }
        if (cp >= 0xE000 && cp <= 0xF8FF) {
            return false; // uso privado: glifos de resource packs
        }
        int type = Character.getType(cp);
        return type != Character.UNASSIGNED && type != Character.SURROGATE && type != Character.FORMAT;
    }

    /** Longitud en puntos de código (lo que ve el jugador), no en unidades UTF-16. */
    public static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    /** Nombres de grupo, etiquetas, canales: solo caracteres seguros y sin espacios dobles. */
    public static String cleanName(String input, int max) {
        return clean(input, max);
    }
}
