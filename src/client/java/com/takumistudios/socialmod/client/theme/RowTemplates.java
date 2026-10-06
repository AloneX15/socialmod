package com.takumistudios.socialmod.client.theme;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.common.model.RowDesign;
import com.takumistudios.socialmod.client.Heads;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Cached local templates: no filesystem work in render or tick. */
public final class RowTemplates {
    public static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("socialmod/integration/rows.json");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "SocialMod-Templates-IO"); t.setDaemon(true); return t; });
    private static volatile RowDesign active = RowDesign.defaults();
    private static volatile long revision;
    private static boolean resourcesReady;
    private record TextKey(Component text, String font, int width) { }
    private record TextLayout(Component text, java.util.List<net.minecraft.util.FormattedCharSequence> lines) { }
    private static final Map<TextKey, TextLayout> TEXT = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<TextKey, TextLayout> entry) { return size() > 512; }
    };
    private static long cachedRevision = -1;
    public static long revision() { return revision; }
    public record Data(UUID avatar, Map<String, Component> fields, float health, boolean selected, boolean hovered) {
        public Data state(boolean selected, boolean hovered) { return new Data(avatar, fields, health, selected, hovered); }
        public static Data text(UUID avatar, String name, String text) { return new Data(avatar, Map.of("name", Component.literal(name), "text", Component.literal(text)), -1, false, false); }
    }
    public static void load() {
        IO.execute(() -> { if (!Files.isRegularFile(FILE)) return; try { if (Files.size(FILE) > 65536) throw new IllegalArgumentException("Row file exceeds size limit");
                var loaded = RowDesign.parse(Files.readString(FILE));
                Minecraft.getInstance().execute(() -> {
                    active = loaded; revision++;
                    if (resourcesReady) resourcesReloaded();
                }); } catch (Exception e) {
                SocialMod.LOGGER.warn("Invalid row templates; using defaults", e);
                try {
                    Files.move(FILE, FILE.resolveSibling("rows.json.bak"), StandardCopyOption.REPLACE_EXISTING);
                    var defaults = RowDesign.defaults(); var tmp = FILE.resolveSibling("rows.json.tmp");
                    Files.writeString(tmp,RowDesign.GSON.toJson(defaults)); Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
                } catch(java.io.IOException backupError) { SocialMod.LOGGER.warn("Could not back up invalid row templates",backupError); }
            } });
    }
    public static void resourcesReloaded() {
        resourcesReady = true; TEXT.clear(); revision++;
        try { validateResources(active); } catch(RuntimeException e) { active = RowDesign.defaults(); revision++; SocialMod.LOGGER.warn("Row resources unavailable; restoring basic rows", e); }
    }
    public static RowDesign.Template template(String kind) { return active.templates.get(kind); }
    public static RowDesign design() { return active.copy(); }
    public static void activate(RowDesign design) { design.validate(); validateResources(design); active=design.copy(); revision++; TEXT.clear(); }
    public static void validateResources(RowDesign design) {
        var resources = Minecraft.getInstance().getResourceManager();
        for(var row : design.templates.values()) for(var part : row.parts) {
            var font = Identifier.parse(part.font);
            if(resources.getResource(Identifier.fromNamespaceAndPath(font.getNamespace(),"font/"+font.getPath()+".json")).isEmpty()) throw new IllegalArgumentException("Missing font: "+font);
            if(!part.texture.isEmpty()) { var texture = Identifier.parse(part.texture); if(resources.getResource(Identifier.fromNamespaceAndPath(texture.getNamespace(),"textures/gui/sprites/"+texture.getPath()+".png")).isEmpty()) throw new IllegalArgumentException("Missing texture: "+texture); }
        }
    }
    public static CompletableFuture<Void> save(RowDesign value) {
        value.validate(); validateResources(value); var snapshot = value.copy();
        return CompletableFuture.runAsync(() -> {
            try { Files.createDirectories(FILE.getParent()); Path tmp = FILE.resolveSibling("rows.json.tmp"); Files.writeString(tmp, RowDesign.GSON.toJson(snapshot)); Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); active = snapshot; revision++; }
            catch (Exception e) { SocialMod.LOGGER.warn("Could not save row templates",e); throw new CompletionException(e); }
        }, IO);
    }
    public static boolean enabled(String kind) { var row = active.templates.get(kind); return row != null && row.enabled; }
    public static int height(String kind, int width, Data data) { return height(active.templates.get(kind), width, data); }
    public static int height(RowDesign.Template row, int width, Data data) {
        if (row == null) return 12;
        var font = Minecraft.getInstance().font; int height = row.height;
        for (var part : row.parts) if (part.visible) {
            int h = part.height;
            if (part.wrap) h = Math.max(h, Math.round(textLayout(part, data, Math.max(1, Math.round(partWidth(part, width) * 100f / part.scale))).lines.size() * (font.lineHeight + 1) * part.scale / 100f));
            height = Math.max(height, part.y + h + row.padding);
        }
        return Math.min(4096, height);
    }
    private static int partWidth(RowDesign.Part part, int width) { return Math.max(1, part.width == 0 ? width - part.x - 3 : Math.min(part.width, width)); }
    private static TextLayout textLayout(RowDesign.Part part, Data data, int width) {
        if (cachedRevision != revision) { TEXT.clear(); cachedRevision = revision; }
        var source = data.fields.getOrDefault(part.field, Component.empty());
        var key = new TextKey(source, part.font, width);
        return TEXT.computeIfAbsent(key, ignored -> {
            var styled = source.copy().withStyle(style -> style.withFont(new FontDescription.Resource(Identifier.parse(part.font))));
            return new TextLayout(styled, Minecraft.getInstance().font.split(styled, width));
        });
    }
    public static void draw(GuiGraphicsExtractor graphics, String kind, int x, int y, int width, Data data) { draw(graphics, active.templates.get(kind), x, y, width, data); }
    public static void draw(GuiGraphicsExtractor graphics, RowDesign.Template row, int x, int y, int width, Data data) {
        if (row == null || width < 1) return;
        var font = Minecraft.getInstance().font;
        boolean contrast = com.takumistudios.socialmod.client.ClientConfig.get().accessibility.highContrast;
        int height = height(row, width, data), bg = data.selected ? row.selectedBackground : data.hovered ? row.hoverBackground : row.background;
        graphics.fill(x, y, x + width, y + height, contrast ? 0xFF000000 : bg);
        for (var part : row.parts) if (part.visible) {
            int w = partWidth(part, width), px = x + (part.right ? width - part.x - w : part.x), py = y + part.y;
            if (!contrast && !part.texture.isEmpty()) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.parse(part.texture), px, py, w, part.height);
            if (part.field.equals("avatar")) { Heads.draw(graphics, data.avatar, px, py, Math.min(w, part.height)); continue; }
            if (part.field.equals("health")) { graphics.fill(px, py, px + w, py + part.height, 0xFF303030); if (data.health >= 0) graphics.fill(px, py, px + Math.round(w * Math.clamp(data.health, 0, 1)), py + part.height, part.color); continue; }
            float scale = part.scale / 100f; var text = textLayout(part, data, part.wrap ? Math.max(1,Math.round(w / scale)) : 32768);
            if (!part.wrap) graphics.enableScissor(px, py, px + w, py + part.height);
            graphics.pose().pushMatrix(); graphics.pose().translate(px, py); graphics.pose().scale(scale, scale);
            if (part.wrap) { int lineY = 0; for (var line : text.lines) { graphics.text(font, line, 0, lineY, contrast ? 0xFFFFFFFF : part.color); lineY += font.lineHeight + 1; } }
            else graphics.text(font, text.text, 0, 0, contrast ? 0xFFFFFFFF : part.color);
            graphics.pose().popMatrix();
            if (!part.wrap) graphics.disableScissor();
        }
    }
    private RowTemplates() { }
}
