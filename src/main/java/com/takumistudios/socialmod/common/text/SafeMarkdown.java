package com.takumistudios.socialmod.common.text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Subconjunto seguro de markdown (PLAN 5.1): {@code **negrita**}, {@code *cursiva*}, {@code `código`},
 * enlaces http(s) y menciones {@code @nombre}. El resultado son tramos de texto plano con banderas;
 * nunca se interpreta JSON de componentes, así que un jugador no puede inyectar eventos de clic.
 * Una barra invertida escapa el siguiente símbolo ({@code \*}).
 */
public final class SafeMarkdown {
    public static final int MAX_NAME = 16;

    private SafeMarkdown() {
    }

    public enum Kind {
        TEXT, CODE, LINK, MENTION
    }

    public record Span(String text, Kind kind, boolean bold, boolean italic) {
    }

    public static List<Span> parse(String input) {
        List<Span> spans = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean bold = false;
        boolean italic = false;
        int i = 0;
        int n = input.length();
        while (i < n) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < n && "*`\\@_".indexOf(input.charAt(i + 1)) >= 0) {
                current.append(input.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '`') {
                int close = input.indexOf('`', i + 1);
                if (close > i + 1) {
                    flush(spans, current, bold, italic);
                    spans.add(new Span(input.substring(i + 1, close), Kind.CODE, false, false));
                    i = close + 1;
                    continue;
                }
            }
            if (c == '*' && i + 1 < n && input.charAt(i + 1) == '*') {
                if (bold || input.indexOf("**", i + 2) > i + 2) {
                    flush(spans, current, bold, italic);
                    bold = !bold;
                    i += 2;
                    continue;
                }
            } else if (c == '*') {
                if (italic || hasSingleStar(input, i + 1)) {
                    flush(spans, current, bold, italic);
                    italic = !italic;
                    i++;
                    continue;
                }
            }
            if (c == '@' && (i == 0 || !isNameChar(input.charAt(i - 1)))) {
                int end = i + 1;
                while (end < n && end - i - 1 < MAX_NAME && isNameChar(input.charAt(end))) {
                    end++;
                }
                if (end > i + 1) {
                    flush(spans, current, bold, italic);
                    spans.add(new Span(input.substring(i, end), Kind.MENTION, bold, italic));
                    i = end;
                    continue;
                }
            }
            if ((c == 'h' || c == 'H') && (i == 0 || Character.isWhitespace(input.charAt(i - 1))) && startsWithUrl(input, i)) {
                int end = i;
                while (end < n && !Character.isWhitespace(input.charAt(end))) {
                    end++;
                }
                while (end > i && ".,!?;:)]}\"'".indexOf(input.charAt(end - 1)) >= 0) {
                    end--;
                }
                flush(spans, current, bold, italic);
                spans.add(new Span(input.substring(i, end), Kind.LINK, bold, italic));
                i = end;
                continue;
            }
            current.append(c);
            i++;
        }
        flush(spans, current, bold, italic);
        return spans;
    }

    /** Nombres mencionados ({@code @Alex} → {@code alex}), en minúsculas y sin repetir. */
    public static Set<String> mentions(String input) {
        Set<String> names = new LinkedHashSet<>();
        for (Span span : parse(input)) {
            if (span.kind() == Kind.MENTION) {
                names.add(span.text().substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    /** Texto sin marcas (para la action bar, el narrador o las vistas previas). */
    public static String plain(String input) {
        StringBuilder out = new StringBuilder();
        for (Span span : parse(input)) {
            out.append(span.text());
        }
        return out.toString();
    }

    public static boolean containsLink(String input) {
        for (Span span : parse(input)) {
            if (span.kind() == Kind.LINK) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSingleStar(String input, int from) {
        for (int j = from; j < input.length(); j++) {
            char c = input.charAt(j);
            if (c == '\\') {
                j++;
                continue;
            }
            if (c == '*') {
                boolean doubled = (j + 1 < input.length() && input.charAt(j + 1) == '*');
                if (!doubled) {
                    return j > from;
                }
                j++;
            }
        }
        return false;
    }

    private static boolean startsWithUrl(String input, int i) {
        String rest = input.regionMatches(true, i, "https://", 0, 8) ? "https://" : input.regionMatches(true, i, "http://", 0, 7) ? "http://" : null;
        return rest != null && input.length() > i + rest.length() + 2;
    }

    public static boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private static void flush(List<Span> spans, StringBuilder current, boolean bold, boolean italic) {
        if (!current.isEmpty()) {
            spans.add(new Span(current.toString(), Kind.TEXT, bold, italic));
            current.setLength(0);
        }
    }
}
