package com.takumistudios.socialmod.common.model;

/** Bounds nesting before recursive JSON model deserialization/serialization. */
public final class JsonBudget {
    public static void checkDepth(String json) {
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 64) throw new IllegalArgumentException("JSON nesting exceeds 64 levels"); }
            else if (c == '}' || c == ']') depth--;
        }
    }
    private JsonBudget() { }
}
