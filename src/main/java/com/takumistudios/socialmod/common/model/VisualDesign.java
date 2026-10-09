package com.takumistudios.socialmod.common.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Portable visual settings. Relative widget rectangles survive resolution and GUI scale changes. */
public final class VisualDesign {
    public static final Gson GSON = new Gson();
    private static final Pattern RESOURCE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    public int version = 1;
    public String mode = "compact";
    public int widthPercent = 78, heightPercent = 78;
    public int sidebarWidthPercent = 40;
    public JsonObject theme = new JsonObject();
    public String resourcePackUrl = "", resourcePackSha1 = "";
    public int healthBackground = 0xFF3A0000, healthHigh = 0xFFE03030, healthLow = 0xFFFF0000;
    public String font = "minecraft:default";
    public String buttonTexture = "", buttonHoverTexture = "", buttonDisabledTexture = "";
    public int buttonColor = 0xE0202020, buttonHoverColor = 0xFF404040, buttonDisabledColor = 0x80303030;
    public int titleColor = 0xFFFFFFFF, successColor = 0xFF55FF55, dangerColor = 0xFFFF5555, warningColor = 0xFFFFAA00;
    public int eventColor = 0xFFFF55FF;
    public int borderWidth = 1, padding = 4;
    public boolean tagBelow = true, tagIcon = true, tagBrackets = true;
    public float hudX = .98f, hudY = .02f, partyX = .02f, partyY = .3f, toastX = .98f, toastY = .02f;
    public int hudScale = 100;
    public String decoration = "none";
    public String seriesStyle = "";
    public boolean transparentWorld;
    public int panelInset = 0;
    public String inputTexture = "", inputFocusTexture = "", buttonSelectedTexture = "";
    public Map<String, Rect> components = new LinkedHashMap<>();
    public static final class Rect {
        public float x, y, w, h;
        public int textColor = 0xFFE0E0E0;
        public String texture = "";
        public Rect() { }
        public Rect(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    }
    public static VisualDesign parse(String json) {
        if (json == null || json.length() > 65536) throw new IllegalArgumentException("Visual size");
        JsonBudget.checkDepth(json);
        VisualDesign result = GSON.fromJson(json, VisualDesign.class);
        if (result == null) throw new IllegalArgumentException("Empty visual design");
        result.validate(); return result;
    }
    public boolean retired() { return "christmas".equals(seriesStyle) || "dedsafio".equals(seriesStyle) || "christmas".equals(decoration) || "socialmod:christmas".equals(font); }
    public VisualDesign copy() { return parse(GSON.toJson(this)); }
    public void validate() {
        if (GSON.toJson(this).length() > 65536) throw new IllegalArgumentException("Visual size");
        if (retired()) {
            VisualDesign defaults = new VisualDesign();
            try { for (var field : VisualDesign.class.getFields()) if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) field.set(this,field.get(defaults)); }
            catch (IllegalAccessException ex) { throw new IllegalStateException("Cannot reset retired design",ex); }
        }
        if (!java.util.List.of("none").contains(decoration)) throw new IllegalArgumentException("Decoration");
        if (seriesStyle == null || !java.util.List.of("", "clean").contains(seriesStyle)) throw new IllegalArgumentException("Series style");
        if (version != 1 || !java.util.List.of("compact", "sidebar", "full").contains(mode)
                || sidebarWidthPercent < 25 || sidebarWidthPercent > 75 || widthPercent < 35 || widthPercent > 100 || heightPercent < 45 || heightPercent > 100
                || panelInset < 0 || panelInset > 24 || borderWidth < 0 || borderWidth > 8 || padding < 0 || padding > 24 || hudScale < 50 || hudScale > 200
                || components == null || components.size() > 200 || theme == null) throw new IllegalArgumentException("Invalid visual design");
        if (resourcePackUrl == null || resourcePackSha1 == null) throw new IllegalArgumentException("Resource pack");
        if (!resourcePackUrl.isEmpty()) {
            java.net.URI uri = java.net.URI.create(resourcePackUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || !resourcePackSha1.matches("[a-fA-F0-9]{40}")) throw new IllegalArgumentException("Resource pack URL/hash");
        }
        if (com.takumistudios.socialmod.client.theme.Theme.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, theme).result().isEmpty()) throw new IllegalArgumentException("Invalid theme");
        resource(font, false); resource(buttonTexture, true); resource(buttonHoverTexture, true); resource(buttonDisabledTexture, true); resource(buttonSelectedTexture, true); resource(inputTexture, true); resource(inputFocusTexture, true);
        for (float n : new float[]{hudX, hudY, partyX, partyY, toastX, toastY}) if (!Float.isFinite(n) || n < 0 || n > 1) throw new IllegalArgumentException("Invalid anchor");
        for (var entry : components.entrySet()) {
            Rect r = entry.getValue();
            if (entry.getKey().length() > 180 || r == null || !Float.isFinite(r.x) || !Float.isFinite(r.y)
                    || !Float.isFinite(r.w) || !Float.isFinite(r.h) || r.x < 0 || r.y < 0 || r.w <= 0 || r.h <= 0
                    || r.x + r.w > 1.001 || r.y + r.h > 1.001) throw new IllegalArgumentException("Widget outside screen");
            resource(r.texture, true);
        }
    }
    private static void resource(String value, boolean optional) {
        if (value == null || value.length() > 256 || (!optional || !value.isEmpty()) && (!RESOURCE.matcher(value).matches() || value.contains(".."))) throw new IllegalArgumentException("Invalid resource");
    }
}
