package com.takumistudios.socialmod.common.model;

import com.google.gson.JsonObject;
import java.util.List;

/** Original, editable series styles. No full-screen background. */
public final class SeriesTemplates {
    public static final List<String> IDS = List.of("christmas", "dedsafio");
    public static VisualDesign visual(String id) {
        if (!IDS.contains(id) && !id.equals("clean")) throw new IllegalArgumentException("Unknown series style");
        boolean red = id.equals("dedsafio"), christmas=id.equals("christmas");
        var v = new VisualDesign(); v.seriesStyle = id; v.transparentWorld = true;
        v.widthPercent = red || christmas ? 86 : 78; v.heightPercent = red || christmas ? 80 : 74;
        v.padding = red ? 5 : 4; v.borderWidth = red ? 3 : 1;
        v.buttonColor = red ? 0xD0B8BEC6 : 0xB025303B;
        v.buttonHoverColor = red ? 0xE0E0E4EA : 0xD0405364;
        v.titleColor = red ? 0xFFFF6268 : christmas ? 0xFFFFDF87 : 0xFFF3F7FB;
        JsonObject colors = new JsonObject();
        colors.addProperty("background", "#00000000");
        colors.addProperty("panel", red ? "#7020252C" : christmas ? "#70132621" : "#70202A35");
        colors.addProperty("border", red ? "#FFD1D5DC" : christmas ? "#FFE0C28A" : "#FF71879B");
        colors.addProperty("text", "#FFF3F7FB"); colors.addProperty("muted", "#FFB8C5D2");
        colors.addProperty("accent", red ? "#FFFF6268" : christmas ? "#FFF5D474" : "#FF8DD7FF");
        colors.addProperty("highlight", red ? "#403E8AAB" : "#403C6B8A");
        v.theme.add("colors", colors);
        v.theme.add("columns", com.takumistudios.socialmod.common.model.VisualDesign.GSON.toJsonTree(List.of(
            java.util.Map.of("id","conversations","weight",1,"min_width",100),
            java.util.Map.of("id","chat","weight",red ? 5 : 3,"min_width",160),
            java.util.Map.of("id","players","weight",2,"min_width",100))));
        var toast = new JsonObject(); toast.addProperty("background", "#A020252C");
        toast.addProperty("border", red ? "#FFFF6268" : christmas ? "#FFF5D474" : "#FF8DD7FF"); v.theme.add("toast",toast);
        v.validate(); return v;
    }
    public static RowDesign rows(String id) {
        visual(id); var rows = RowDesign.defaults();
        for (var entry : rows.templates.entrySet()) {
            var row = entry.getValue(); row.enabled = true;
            row.background = 0x18101520; row.selectedBackground = 0x70405B72; row.hoverBackground = 0x403C4E60;
            for (var p : row.parts) p.color = p.field.equals("name") ? (id.equals("dedsafio") ? 0xFFFFC8CA : 0xFF8DD7FF) : 0xFFF3F7FB;
            if (entry.getKey().equals("player") || entry.getKey().equals("party")) {
                row.parts.removeIf(p -> p.field.equals("time"));
                row.parts.add(RowDesign.part("status",24,14,0,10));
            }
        }
        if(id.equals("christmas")) for(var row:rows.templates.values()) {
            row.background=0x18132621;row.selectedBackground=0x705E342C;row.hoverBackground=0x40375841;
        }
        rows.validate(); return rows;
    }
    private SeriesTemplates() { }
}
