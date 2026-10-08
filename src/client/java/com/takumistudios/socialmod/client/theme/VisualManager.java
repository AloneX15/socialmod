package com.takumistudios.socialmod.client.theme;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.common.model.VisualDesign;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;
import java.util.LinkedHashMap;
import java.util.Map;

/** Applies the server design, with an isolated draft override while staff preview edits. */
public final class VisualManager {
    private static VisualDesign active = new VisualDesign(), preview;
    private static final Map<AbstractWidget, String> IDS = new java.util.WeakHashMap<>();
    private static Theme theme;
    private static VisualDesign themeSource;
    private static final java.util.Set<Screen> REGISTERED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static Screen drawing;
    private static int panelIndex;
    private static final Map<Screen, Map<String, Drawn>> DRAWN = new java.util.WeakHashMap<>();
    public record Drawn(String id, int x, int y, int w, int h, boolean text) { }
    public static boolean tracksDrawn() { return drawing != null && !(drawing instanceof com.takumistudios.socialmod.client.screen.VisualEditorScreen) && (preview != null || !get().components.isEmpty()); }
    public static void begin(Screen screen) { drawing = screen; panelIndex = 0; DRAWN.computeIfAbsent(screen, s -> new LinkedHashMap<>()).clear(); }
    public static void end() { drawing = null; }
    public static java.util.Collection<Drawn> drawn(Screen screen) { return DRAWN.getOrDefault(screen, Map.of()).values(); }
    public static Drawn drawn(String label, int x, int y, int w, int h, boolean text) {
        if ((preview == null && get().components.isEmpty()) || drawing == null || drawing instanceof com.takumistudios.socialmod.client.screen.VisualEditorScreen) return null;
        String key = text ? label : "panel/" + panelIndex++;
        if (key.length() > 100) key = java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        String id = get().mode + "/" + drawing.getClass().getSimpleName() + "/" + (text ? "text/" : "") + key;
        Drawn value = new Drawn(id, x, y, Math.max(1, w), Math.max(1, h), text);
        var rect = get().components.get(id);
        if (rect != null) value = new Drawn(id, Math.round(rect.x * drawing.width), Math.round(rect.y * drawing.height), Math.max(1, Math.round(rect.w * drawing.width)), Math.max(1, Math.round(rect.h * drawing.height)), text);
        DRAWN.get(drawing).put(id, value); return value;
    }
    public static VisualDesign.Rect drawnRect(Drawn value) { return value == null ? null : get().components.get(value.id()); }
    public static int panelInset(int fallbackHeight) { int height = drawing == null ? fallbackHeight : drawing.height; return Math.min(get().panelInset, height < 220 ? 12 : 24); }
    private static final VisualDesign ORIGINAL = new VisualDesign();
    public static VisualDesign get() { if (AppearanceMode.original()) return ORIGINAL; if (preview != null) return preview; return active; }
    public static void accept(VisualDesign design) {
        try { design.validate(); active = design.copy(); theme = null; }
        catch (RuntimeException e) { SocialMod.LOGGER.warn("Preset visual inválido; se conserva el anterior", e); }
    }
    public static void preview(VisualDesign design) { preview = design; theme = null; }
    public static void reset() { active = new VisualDesign(); preview = null; theme = null; }
    public static Theme theme(Theme fallback) {
        var source = get();
        if (source != themeSource) { themeSource = source; theme = null; }
        if (source.theme.size() == 0) return fallback;
        if (theme == null) theme = Theme.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, source.theme).result().orElse(fallback);
        return theme;
    }
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof com.takumistudios.socialmod.client.screen.VisualEditorScreen) && screen.getClass().getPackageName().startsWith("com.takumistudios.socialmod.client.screen")) {
                apply(screen);
                if (!REGISTERED.add(screen)) return;
                ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> begin(s));
                ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> end());
            }
        });
    }
    public static Map<String, AbstractWidget> widgets(Screen screen) {
        Map<String, AbstractWidget> result = new LinkedHashMap<>(); Map<String, Integer> counts = new LinkedHashMap<>();
        for (AbstractWidget widget : Screens.getWidgets(screen)) {
            String label = widget.getMessage().getContents() instanceof TranslatableContents t ? t.getKey() : widget.getMessage().getString();
            // Translation keys remain stable across languages. Dynamic player/team buttons use an explicit occurrence suffix.
            String base = get().mode + "/" + screen.getClass().getSimpleName() + "/" + (widget instanceof EditBox ? "EditBox" : widget.getClass().getSimpleName()) + "/" + label;
            int index = counts.merge(base, 1, Integer::sum); String id = base + "/" + index;
            if (id.length() > 180) id = base.substring(0, Math.min(base.length(), 160)) + "/" + index;
            result.put(id, widget); IDS.put(widget, id);
        }
        return result;
    }
    public static VisualDesign.Rect rect(AbstractWidget widget) { return get().components.get(IDS.get(widget)); }
    public static void validateControls(Screen screen) {
        var design = get();
        for (String sprite : new String[]{design.buttonTexture, design.buttonHoverTexture, design.buttonDisabledTexture, design.buttonSelectedTexture, design.inputTexture, design.inputFocusTexture}) validateSprite(sprite);
        var theme = theme(Theme.DEFAULT);
        theme.textures().background().ifPresent(id -> validateSprite(id.toString()));
        theme.textures().panel().ifPresent(id -> validateSprite(id.toString()));
        theme.textures().toast().ifPresent(id -> validateSprite(id.toString()));
        for (var rect : design.components.values()) validateSprite(rect.texture);
        var widgets = widgets(screen);
        for (var entry : widgets.entrySet()) {
            var a = entry.getValue();
            if (!a.visible || !get().components.containsKey(entry.getKey())) continue;
            if (a.getWidth() < 20 || a.getHeight() < 12 || a.getX() < 0 || a.getY() < 0 || a.getRight() > screen.width || a.getBottom() > screen.height) throw new IllegalArgumentException("Inaccessible control");
            if (!(a instanceof EditBox) && net.minecraft.client.Minecraft.getInstance().font.width(a.getMessage()) > a.getWidth() - design.padding * 2) throw new IllegalArgumentException("Clipped control text");
            for (var b : widgets.values()) if (a != b && b.visible && a.getX() < b.getRight() && a.getRight() > b.getX() && a.getY() < b.getBottom() && a.getBottom() > b.getY()) throw new IllegalArgumentException("Overlapping controls");
        }
    }
    private static void validateSprite(String sprite) {
        if (sprite.isEmpty()) return;
        var id = net.minecraft.resources.Identifier.parse(sprite);
        var path = net.minecraft.resources.Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/gui/sprites/" + id.getPath() + ".png");
        if (net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(path).isEmpty()) throw new IllegalArgumentException("Missing texture: " + sprite);
    }
    public static void apply(Screen screen) {
        if (com.takumistudios.socialmod.client.compat.fancy.FancyBridge.customized(screen)) return;
        for (var entry : widgets(screen).entrySet()) {
            var widget = entry.getValue(); var rect = get().components.get(entry.getKey()); if (rect == null) continue;
            int w = Math.max(20, Math.min(screen.width, Math.round(rect.w * screen.width)));
            int h = Math.max(12, Math.min(screen.height, Math.round(rect.h * screen.height)));
            widget.setRectangle(w, h, Math.max(0, Math.min(screen.width - w, Math.round(rect.x * screen.width))), Math.max(0, Math.min(screen.height - h, Math.round(rect.y * screen.height))));
            if (widget instanceof EditBox box) { box.setTextColor(rect.textColor); box.setTextColorUneditable(rect.textColor); }
        }
    }
}
