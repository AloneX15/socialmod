package com.takumistudios.socialmod.common.model;
import java.util.List;
/** Neutral editable preset. */
public final class SeriesTemplates {
    public static final List<String> IDS = List.of("clean");
    public static VisualDesign visual(String id) {
        if (!IDS.contains(id)) throw new IllegalArgumentException("Unknown series style");
        var visual = new VisualDesign(); visual.seriesStyle = id; visual.transparentWorld = true;
        var colors = new com.google.gson.JsonObject(); colors.addProperty("background", "#00000000"); colors.addProperty("panel", "#70202A35"); visual.theme.add("colors", colors); return visual;
    }
    public static RowDesign rows(String id) { visual(id); var rows = RowDesign.defaults(); rows.templates.values().forEach(row -> row.enabled = true); return rows; }
    private SeriesTemplates() { }
}
