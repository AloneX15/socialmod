package com.takumistudios.socialmod.server.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Filtro de palabras configurable (PLAN 9): lista de palabras (sin distinguir mayúsculas y como palabra completa)
 * y expresiones regulares. En modo {@link Mode#CENSOR} sustituye por asteriscos; en {@link Mode#BLOCK} rechaza el mensaje.
 */
public final class WordFilter {
    public enum Mode {
        CENSOR, BLOCK
    }

    public record Result(String text, boolean matched, boolean blocked) {
    }

    private final List<Pattern> patterns;
    private final Mode mode;
    private final List<String> errors = new ArrayList<>();

    public WordFilter(Collection<String> words, Collection<String> regexes, Mode mode) {
        this.mode = mode;
        List<Pattern> compiled = new ArrayList<>();
        for (String word : words) {
            if (word != null && !word.isBlank()) {
                compiled.add(Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word.trim().toLowerCase(Locale.ROOT)) + "(?![\\p{L}\\p{N}])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            }
        }
        for (String regex : regexes) {
            if (regex == null || regex.isBlank()) {
                continue;
            }
            try {
                compiled.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            } catch (PatternSyntaxException e) {
                errors.add(regex + ": " + e.getDescription());
            }
        }
        this.patterns = List.copyOf(compiled);
    }

    public static WordFilter empty() {
        return new WordFilter(List.of(), List.of(), Mode.CENSOR);
    }

    /** Expresiones que no compilaron (se ignoran y se avisan en el log). */
    public List<String> errors() {
        return errors;
    }

    public boolean isEmpty() {
        return patterns.isEmpty();
    }

    public Result apply(String text) {
        boolean matched = false;
        String result = text;
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(result);
            if (!matcher.find()) {
                continue;
            }
            matched = true;
            if (mode == Mode.BLOCK) {
                return new Result(text, true, true);
            }
            StringBuilder out = new StringBuilder();
            matcher.reset();
            while (matcher.find()) {
                matcher.appendReplacement(out, Matcher.quoteReplacement("*".repeat(Math.max(1, matcher.group().length()))));
            }
            matcher.appendTail(out);
            result = out.toString();
        }
        return new Result(result, matched, false);
    }
}
