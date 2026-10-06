package com.takumistudios.socialmod.common.model;

import com.google.gson.Gson;
import java.util.*;

/** Local modpack row templates. Pixel coordinates are relative to each repeated row. */
public final class RowDesign {
    public static final Gson GSON = new Gson();
    public static final List<String> KINDS = List.of("message", "conversation", "player", "party", "toast");
    public static final List<String> FIELDS = List.of("avatar", "name", "time", "text", "team", "status", "unread", "health", "icon", "role");
    public int version = 1;
    public Map<String, Template> templates = new LinkedHashMap<>();
    public static final class Template {
        public boolean enabled;
        public int height = 28, padding = 3;
        public int background = 0, selectedBackground = 0x503A7158, hoverBackground = 0x303A7158;
        public List<Part> parts = new ArrayList<>();
    }
    public static final class Part {
        public String field = "name", font = "minecraft:default", texture = "";
        public int x = 24, y = 2, width = 120, height = 10, color = 0xFFE0E0E0, scale = 100;
        public boolean right, wrap, visible = true;
    }
    public static RowDesign defaults() {
        var design = new RowDesign();
        for (String kind : KINDS) {
            var row = new Template();
            row.parts.add(part("avatar", 2, 2, 16, 16));
            row.parts.add(part("name", 24, 2, 90, 10));
            var time = part("time", 2, 2, 36, 10); time.right = true; row.parts.add(time);
            var text = part("text", 24, 14, 0, 10); text.wrap = true; row.parts.add(text);
            if (kind.equals("party")) row.parts.add(part("health", 24, 24, 80, 4));
            design.templates.put(kind, row);
        }
        return design;
    }
    public static Part part(String field, int x, int y, int w, int h) {
        var value = new Part(); value.field = field; value.x = x; value.y = y; value.width = w; value.height = h; return value;
    }
    public static RowDesign parse(String json) {
        if (json == null || json.length() > 65536) throw new IllegalArgumentException("Row design size");
        var design = GSON.fromJson(json, RowDesign.class);
        if (design == null) throw new IllegalArgumentException("Empty row design");
        design.validate(); return design;
    }
    public RowDesign copy() { return parse(GSON.toJson(this)); }
    public void validate() {
        if (version != 1 || templates == null || templates.size() > 5 || GSON.toJson(this).length() > 65536) throw new IllegalArgumentException("Row version/count");
        for (var entry : templates.entrySet()) {
            var row = entry.getValue();
            if (!KINDS.contains(entry.getKey()) || row == null || row.height < 12 || row.height > 512 || row.padding < 0 || row.padding > 32 || row.parts == null || row.parts.size() > 32) throw new IllegalArgumentException("Invalid row");
            for (var part : row.parts) {
                if (part == null || part.field == null || !FIELDS.contains(part.field) || part.x < 0 || part.x > 2048 || part.y < 0 || part.y > 512 || part.width < 0 || part.width > 2048 || part.height < 1 || part.height > 512 || part.scale < 50 || part.scale > 200) throw new IllegalArgumentException("Invalid row part");
                resource(part.font, false); resource(part.texture, true);
            }
        }
    }
    private static void resource(String value, boolean empty) {
        if (value == null || value.length() > 256 || (!empty || !value.isEmpty()) && (!value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || value.contains(".."))) throw new IllegalArgumentException("Invalid row resource");
    }
}
