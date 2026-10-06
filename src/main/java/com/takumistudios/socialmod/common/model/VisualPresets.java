package com.takumistudios.socialmod.common.model;

/** Bundled series templates. Callers receive independent editable copies. */
public final class VisualPresets {
    private static final VisualDesign CHRISTMAS = loadChristmas();
    private VisualPresets() { }
    public static java.util.List<String> assetPaths() throws java.io.IOException {
        try (var input = VisualPresets.class.getResourceAsStream("/assets/socialmod/presets/christmas-assets.json")) {
            if (input == null) throw new java.io.IOException("Christmas asset manifest missing");
            var paths = VisualDesign.GSON.fromJson(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), String[].class);
            return java.util.List.of(paths);
        }
    }
    private static VisualDesign loadChristmas() {
        try (var input = VisualPresets.class.getResourceAsStream("/assets/socialmod/presets/christmas.json")) {
            if (input == null) throw new java.io.IOException("Christmas preset missing");
            return VisualDesign.parse(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException | RuntimeException e) {
            com.takumistudios.socialmod.SocialMod.LOGGER.warn("Christmas template unavailable; using default", e);
            return new VisualDesign();
        }
    }
    public static VisualDesign christmas() { return CHRISTMAS.copy(); }
}
