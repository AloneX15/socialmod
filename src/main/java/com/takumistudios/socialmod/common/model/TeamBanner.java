package com.takumistudios.socialmod.common.model;

import java.util.ArrayList;
import java.util.List;

/** Portable, bounded banner identity. Colors use Minecraft dye ids, independently of tag RGB. */
public final class TeamBanner {
    public static final int MAX_LAYERS = 6;
    public int base = 0;
    public List<Layer> layers = new ArrayList<>();
    public record Layer(String pattern, int color) { }

    public void validate() {
        if (base < 0 || base > 15 || layers == null || layers.size() > MAX_LAYERS) throw new IllegalArgumentException("Invalid banner");
        for (var layer : layers) if (layer == null || layer.pattern() == null || layer.pattern().length() > 96
                || !layer.pattern().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || layer.pattern().contains("..")
                || layer.color() < 0 || layer.color() > 15) throw new IllegalArgumentException("Invalid banner layer");
    }
    public TeamBanner copy() { validate(); var copy = new TeamBanner(); copy.base = base; copy.layers = new ArrayList<>(layers); return copy; }
    public static TeamBanner parse(String json) {
        if (json == null || json.length() > 1024) throw new IllegalArgumentException("Banner size");
        JsonBudget.checkDepth(json);
        var banner = VisualDesign.GSON.fromJson(json, TeamBanner.class);
        if (banner == null) throw new IllegalArgumentException("Empty banner");
        banner.validate(); return banner;
    }
}
