package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.theme.VisualText;

import com.google.gson.JsonObject;
import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.theme.PresetFiles;
import com.takumistudios.socialmod.client.theme.Theme;
import com.takumistudios.socialmod.client.theme.VisualManager;
import com.takumistudios.socialmod.common.model.VisualDesign;
import com.takumistudios.socialmod.common.net.SocialAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Visual inspector: live screen canvas, relative drag/resize, property pages, draft history and portable presets. */
public final class VisualEditorScreen extends SocialChildScreen {
    private VisualDesign draft;
    private final Deque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private Screen canvas;
    private String selected = "";
    private int page, screenIndex, resolution, category;
    private final Map<String, VisualDesign.Rect> dragGroup = new LinkedHashMap<>();
    private static final String[] CATEGORIES = {"window", "colors", "texts", "hud", "nametag", "resources", "component"};
    private float canvasScale;
    private int canvasX, canvasY, canvasW, canvasH;
    private boolean dragging, resize;
    private float dragRemainderX, dragRemainderY;
    private String status = "";
    private final List<Property> properties = new ArrayList<>();
    private record Property(String key, java.util.function.Supplier<String> read, java.util.function.Consumer<String> write) { }
    public VisualEditorScreen(Screen parent) {
        super(parent, Component.translatable("socialmod.visual.title"));
        draft = ClientState.get().snapshot().visual.copy();
        if (draft.theme.size() == 0) draft.theme = Theme.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, Ui.theme()).result().orElseThrow().getAsJsonObject();
        canvas = parent; VisualManager.preview(draft);
    }
    @Override protected boolean rebuildOnChange() { return false; }
    private void checkpoint() { undo.push(VisualDesign.GSON.toJson(draft)); if (undo.size() > 60) undo.removeLast(); redo.clear(); }
    private void refresh() { VisualManager.preview(draft); if (canvas != null) canvas.init(canvasW, canvasH); rebuildWidgets(); }
    private void history(Deque<String> from, Deque<String> to) {
        if (from.isEmpty()) return; to.push(VisualDesign.GSON.toJson(draft)); draft = VisualDesign.parse(from.pop()); refresh();
    }
    @Override protected void init() {
        int sidebar = Math.min(190, Math.max(120, width / 3));
        int availableW = Math.max(80, width - sidebar - 12), availableH = Math.max(60, height - 58);
        int[][] sizes = {{640, 360}, {480, 270}, {320, 180}};
        canvasW = sizes[resolution][0]; canvasH = sizes[resolution][1];
        canvasScale = Math.min((float) availableW / canvasW, (float) availableH / canvasH); canvasX = sidebar + 8; canvasY = 32;
        if (canvas != null && (canvas.width != canvasW || canvas.height != canvasH)) canvas.init(canvasW, canvasH);
        properties.clear();
        for (var field : VisualDesign.class.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getName().equals("version") || field.getName().equals("mode") || field.getName().equals("components") || field.getName().equals("theme")) continue;
            properties.add(new Property(field.getName(), () -> {
                try { Object value = field.get(draft); return field.getType() == int.class && (field.getName().toLowerCase(Locale.ROOT).contains("color") || field.getName().startsWith("health")) ? String.format("#%08X", value) : String.valueOf(value); }
                catch (IllegalAccessException e) { throw new IllegalStateException(e); }
            }, value -> {
                try {
                    Object parsed = field.getType() == int.class ? (value.startsWith("#") ? (int) Long.parseLong(value.substring(1), 16) : Integer.parseInt(value))
                            : field.getType() == float.class ? Float.parseFloat(value) : field.getType() == boolean.class ? Boolean.parseBoolean(value) : value;
                    field.set(draft, parsed);
                } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
            }));
        }
        themeProperties(draft.theme, "theme");
        var rect = draft.components.get(selected);
        if (rect != null) for (var field : VisualDesign.Rect.class.getFields()) properties.add(0, new Property("component." + field.getName(), () -> {
            try { return field.getType() == int.class ? String.format("#%08X", field.get(rect)) : String.valueOf(field.get(rect)); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }, value -> {
            try { field.set(rect, field.getType() == float.class ? Float.parseFloat(value) : field.getType() == int.class ? (value.startsWith("#") ? (int) Long.parseLong(value.substring(1), 16) : Integer.parseInt(value)) : value); }
            catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }));
        properties.removeIf(property -> !categoryOf(property.key).equals(CATEGORIES[category]));
        int perPage = Math.max(1, (height - 174) / 40), pages = Math.max(1, (properties.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        addRenderableWidget(Ui.button(Component.translatable("socialmod.visual.mode." + draft.mode), b -> {
            checkpoint(); draft.mode = switch (draft.mode) { case "compact" -> "sidebar"; case "sidebar" -> "full"; default -> "compact"; }; refresh();
        }).bounds(4, 28, sidebar - 8, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.visual.category." + CATEGORIES[category]), b -> { category = (category + 1) % CATEGORIES.length; page = 0; rebuildWidgets(); }).bounds(4, 50, sidebar - 8, 20).build());
        int y = 90;
        for (var property : properties.stream().skip((long) page * perPage).limit(perPage).toList()) {
            String value = property.read.get();
            if (value.equals("true") || value.equals("false")) {
                addRenderableWidget(Ui.button(Component.translatable(Boolean.parseBoolean(value) ? "options.on" : "options.off"), b -> applyProperty(property, Boolean.toString(!Boolean.parseBoolean(value))))
                        .bounds(4, y, sidebar - 8, 18).build()); y += 40; continue;
            }
            if (value.startsWith("#")) {
                addRenderableWidget(Ui.button(Component.literal(value), b -> {
                    int color = (int) Long.parseLong(value.substring(1), 16); if (value.length() == 7) color |= 0xFF000000;
                    com.takumistudios.socialmod.client.compat.ClientCompat.setScreen(new VisualColorScreen(this, color, rgb -> applyProperty(property, String.format("#%08X", rgb))));
                }).bounds(4, y, sidebar - 8, 18).build()); y += 40; continue;
            }
            if (property.key.toLowerCase(Locale.ROOT).contains("texture")) {
                addRenderableWidget(Ui.button(Component.translatable("socialmod.visual.choose_resource"), b -> com.takumistudios.socialmod.client.compat.ClientCompat.setScreen(new VisualResourceScreen(this, chosen -> applyProperty(property, chosen)))).bounds(4, y, sidebar - 8, 18).build()); y += 40; continue;
            }
            EditBox box = new EditBox(font, 4, y, sidebar - 38, 18, Component.literal(property.key)); box.setMaxLength(512); box.setValue(value); addRenderableWidget(box);
            addRenderableWidget(Ui.button(Component.literal("✓"), b -> {
                applyProperty(property, box.getValue());
            }).bounds(sidebar - 32, y, 28, 18).build()); y += 40;
        }
        addRenderableWidget(Ui.button(Component.literal("<"), b -> { page = Math.max(0, page - 1); rebuildWidgets(); }).bounds(4, height - 80, 26, 20).build());
        addRenderableWidget(Ui.button(Component.literal((page + 1) + "/" + pages), b -> { page = (page + 1) % pages; rebuildWidgets(); }).bounds(34, height - 80, sidebar - 70, 20).build());
        addRenderableWidget(Ui.button(Component.literal(">"), b -> { page = (page + 1) % pages; rebuildWidgets(); }).bounds(sidebar - 32, height - 80, 28, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.visual.undo"), b -> history(undo, redo)).bounds(4, height - 56, sidebar / 2 - 6, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.visual.redo"), b -> history(redo, undo)).bounds(sidebar / 2, height - 56, sidebar / 2 - 4, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.visual.screen"), b -> cycleScreen()).bounds(canvasX, 6, 88, 20).build());
        addRenderableWidget(Ui.button(Component.literal(canvasW + "×" + canvasH), b -> { resolution = (resolution + 1) % 3; refresh(); }).bounds(canvasX + 92, 6, 88, 20).build());
        String[] labels = {"preset", "reset", "import", "export", "publish", "rollback", "back"};
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i]; int bw = Math.max(30, (width - 8) / labels.length);
            addRenderableWidget(Ui.button(Component.translatable("socialmod.visual." + label), b -> {
                switch (label) {
                    case "preset" -> { checkpoint(); draft.components.clear(); draft.theme = new JsonObject(); draft.buttonTexture = draft.buttonHoverTexture = draft.buttonDisabledTexture = "";
                        draft.mode = switch (draft.mode) { case "compact" -> "sidebar"; case "sidebar" -> "full"; default -> "compact"; };
                        draft.widthPercent = 78; draft.heightPercent = 78; draft.sidebarWidthPercent = 40; refresh(); }
                    case "reset" -> { checkpoint(); if (selected.isEmpty()) draft = new VisualDesign(); else draft.components.remove(selected); refresh(); }
                    case "import", "export" -> files(label);
                    case "publish" -> { try { draft.validate(); VisualManager.validateControls(canvas); ClientNet.action(SocialAction.VISUAL_PUBLISH, "", VisualDesign.GSON.toJson(draft)); status = Component.translatable("socialmod.visual.sent").getString(); } catch (RuntimeException e) { status = Component.translatable("socialmod.visual.invalid").getString(); } }
                    case "rollback" -> com.takumistudios.socialmod.client.compat.ClientCompat.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(ok -> { if (ok) ClientNet.action(SocialAction.VISUAL_ROLLBACK, ""); com.takumistudios.socialmod.client.compat.ClientCompat.setScreen(this); }, Component.translatable("socialmod.visual.rollback"), Component.translatable("socialmod.visual.rollback_help")));
                    case "back" -> onClose();
                }
            }).bounds(4 + i * bw, height - 24, bw - 2, 20).build());
        }
    }
    private static String categoryOf(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (key.startsWith("component.")) return "component";
        if (key.startsWith("tag")) return "nametag";
        if (key.startsWith("hud") || key.startsWith("party") || key.startsWith("toast")) return "hud";
        if (key.equals("font")) return "texts";
        if (lower.contains("texture") || key.startsWith("resource")) return "resources";
        if (lower.contains("color") || key.startsWith("health")) return "colors";
        return "window";
    }
    private void capturePanel(com.takumistudios.socialmod.client.theme.VisualManager.Drawn panel) {
        dragGroup.clear();
        for (var entry : VisualManager.widgets(canvas).entrySet()) {
            var w = entry.getValue();
            if (w.getX() >= panel.x() && w.getY() >= panel.y() && w.getRight() <= panel.x() + panel.w() && w.getBottom() <= panel.y() + panel.h())
                capture(entry.getKey(), w.getX(), w.getY(), w.getWidth(), w.getHeight());
        }
        for (var child : VisualManager.drawn(canvas)) if (!child.id().equals(panel.id()) && child.x() >= panel.x() && child.y() >= panel.y() && child.x() + child.w() <= panel.x() + panel.w() && child.y() + child.h() <= panel.y() + panel.h())
            capture(child.id(), child.x(), child.y(), child.w(), child.h());
    }
    private void capture(String id, int x, int y, int w, int h) {
        var rect = draft.components.computeIfAbsent(id, ignored -> new VisualDesign.Rect((float)x / canvasW, (float)y / canvasH, (float)w / canvasW, (float)h / canvasH));
        dragGroup.put(id, VisualDesign.GSON.fromJson(VisualDesign.GSON.toJson(rect), VisualDesign.Rect.class));
    }
    private void applyProperty(Property property, String value) {
        String before = VisualDesign.GSON.toJson(draft);
        try { property.write.accept(value); draft.validate(); undo.push(before); redo.clear(); status = ""; refresh(); }
        catch (RuntimeException e) { draft = VisualDesign.parse(before); status = Component.translatable("socialmod.visual.invalid").getString(); refresh(); }
    }
    private void themeProperties(JsonObject object, String prefix) {
        for (var entry : object.entrySet()) {
            String path = prefix + "." + entry.getKey();
            if (entry.getValue().isJsonObject()) themeProperties(entry.getValue().getAsJsonObject(), path);
            else if (entry.getValue().isJsonArray()) {
                int index = 0; for (var element : entry.getValue().getAsJsonArray()) { if (element.isJsonObject()) themeProperties(element.getAsJsonObject(), path + "." + index); index++; }
            }
            else properties.add(new Property(path, () -> entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString() ? entry.getValue().getAsString() : entry.getValue().toString(), value -> {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) object.addProperty(entry.getKey(), value);
                else object.add(entry.getKey(), com.google.gson.JsonParser.parseString(value));
            }));
        }
    }
    private void cycleScreen() {
        screenIndex = (screenIndex + 1) % 9;
        var state = ClientState.get().snapshot();
        canvas = switch (screenIndex) {
            case 1 -> new SettingsScreen(this); case 2 -> new CreateGroupScreen(this); case 3 -> new TeamScreen(this);
            case 4 -> new ProfileScreen(this, UUID.fromString(state.self.uuid), state.self.name);
            case 5 -> state.groups.isEmpty() ? new TeamScreen(this) : new GroupSettingsScreen(this, state.groups.getFirst().id);
            case 6 -> new TagStyleScreen(this, "TEAM", 0x55FF55, "shield", "", (rgb, icon) -> { });
            case 7 -> state.groups.isEmpty() ? new TeamScreen(this) : new InviteScreen(this, state.groups.getFirst().id, false);
            case 8 -> new QuickReplyScreen(state.conversations.isEmpty() ? "" : state.conversations.getFirst().id);
            default -> new SocialScreen(null);
        };
        selected = ""; page = 0; canvas.init(canvasW, canvasH); rebuildWidgets();
    }
    private void files(String operation) {
        VisualDesign snapshot = draft.copy();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                VisualDesign imported = operation.equals("import") ? PresetFiles.load() : null;
                if (imported == null) PresetFiles.export(snapshot);
                Minecraft.getInstance().execute(() -> { if (imported != null) { checkpoint(); draft = imported; refresh(); } status = PresetFiles.DIRECTORY.toString(); });
            } catch (Exception e) {
                com.takumistudios.socialmod.SocialMod.LOGGER.warn("Error en preset visual", e);
                Minecraft.getInstance().execute(() -> status = Component.translatable("socialmod.visual.invalid").getString());
            }
        });
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (Ui.inside(event.x(), event.y(), canvasX, canvasY, Math.round(canvasW * canvasScale), Math.round(canvasH * canvasScale))) {
            double x = (event.x() - canvasX) / canvasScale, y = (event.y() - canvasY) / canvasScale;
            for (var entry : VisualManager.widgets(canvas).entrySet()) {
                AbstractWidget widget = entry.getValue(); if (!widget.isMouseOver(x, y)) continue;
                checkpoint(); dragGroup.clear(); selected = entry.getKey();
                draft.components.putIfAbsent(selected, new VisualDesign.Rect((float) widget.getX() / canvasW, (float) widget.getY() / canvasH, (float) widget.getWidth() / canvasW, (float) widget.getHeight() / canvasH));
                dragging = true; dragRemainderX = dragRemainderY = 0; resize = event.button() == 1; page = 0; category = 6; rebuildWidgets(); return true;
            }
            var drawn = new ArrayList<>(VisualManager.drawn(canvas));
            drawn.sort(Comparator.comparing(value -> !value.text()));
            for (var value : drawn) if (Ui.inside(x, y, value.x(), value.y(), value.w(), value.h())) {
                checkpoint(); selected = value.id();
                if (!value.text()) capturePanel(value); else dragGroup.clear();
                float rx = Math.max(0, Math.min(.98f, (float) value.x() / canvasW)), ry = Math.max(0, Math.min(.98f, (float) value.y() / canvasH));
                draft.components.putIfAbsent(selected, new VisualDesign.Rect(rx, ry, Math.max(.001f, Math.min(1 - rx, (float) value.w() / canvasW)), Math.max(.001f, Math.min(1 - ry, (float) value.h() / canvasH))));
                dragging = true; dragRemainderX = dragRemainderY = 0; resize = event.button() == 1; page = 0; category = 6; rebuildWidgets(); return true;
            }
            selected = ""; rebuildWidgets(); return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        var rect = draft.components.get(selected);
        if (dragging && rect != null) {
            float oldX = rect.x, oldY = rect.y;
            if (resize) { rect.w = Math.max(20f / canvasW, Math.min(1 - rect.x, rect.w + (float) dx / canvasScale / canvasW)); rect.h = Math.max(12f / canvasH, Math.min(1 - rect.y, rect.h + (float) dy / canvasScale / canvasH)); }
            else { rect.x = Math.max(0, Math.min(1 - rect.w, rect.x + (float) dx / canvasScale / canvasW + dragRemainderX)); rect.y = Math.max(0, Math.min(1 - rect.h, rect.y + (float) dy / canvasScale / canvasH + dragRemainderY)); }
            if (!resize) {
                float rawX = rect.x, rawY = rect.y;
                rect.x = Math.max(0, Math.min(1 - rect.w, Math.round(rect.x * canvasW / 4) * 4f / canvasW));
                rect.y = Math.max(0, Math.min(1 - rect.h, Math.round(rect.y * canvasH / 4) * 4f / canvasH));
                dragRemainderX = rawX - rect.x; dragRemainderY = rawY - rect.y;
                float shiftX = rect.x - oldX, shiftY = rect.y - oldY;
                for (var id : dragGroup.keySet()) {
                    var child = draft.components.get(id);
                    child.x = Math.max(0, Math.min(1 - child.w, child.x + shiftX));
                    child.y = Math.max(0, Math.min(1 - child.h, child.y + shiftY));
                }
            }
            VisualManager.apply(canvas); return true;
        }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { dragging = false; return super.mouseReleased(event); }
    @Override public void onClose() { VisualManager.preview(null); if (parent != null) parent.init(width, height); super.onClose(); }
    @Override protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Ui.title(graphics, font, title, (canvasX - 8) / 2, 8);
        int perPage = Math.max(1, (height - 174) / 40), y = 78;
        for (var property : properties.stream().skip((long) page * perPage).limit(perPage).toList()) {
            VisualText.text(graphics, font, Ui.trim(font, Component.translatable("socialmod.visual.property." + property.key).getString(), canvasX - 12), 4, y, Ui.theme().colors().muted()); y += 40;
        }
        if (canvas != null) {
            graphics.enableScissor(canvasX, canvasY, canvasX + Math.round(canvasW * canvasScale), canvasY + Math.round(canvasH * canvasScale));
            graphics.pose().pushMatrix(); graphics.pose().translate(canvasX, canvasY); graphics.pose().scale(canvasScale, canvasScale);
            VisualManager.begin(canvas);
            canvas.extractRenderState(graphics, -1, -1, 0);
            VisualManager.end();
            if (dragging) {
                for (int gx = 0; gx < canvasW; gx += 32) graphics.fill(gx, 0, gx + 1, canvasH, 0x2055FFFF);
                for (int gy = 0; gy < canvasH; gy += 32) graphics.fill(0, gy, canvasW, gy + 1, 0x2055FFFF);
            }
            for (var entry : VisualManager.widgets(canvas).entrySet()) { var widget = entry.getValue(); graphics.outline(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), selected.equals(entry.getKey()) ? 0xFFFFFF00 : 0x6055FFFF); }
            for (var value : VisualManager.drawn(canvas)) if (selected.equals(value.id())) graphics.outline(value.x(), value.y(), value.w(), value.h(), 0xFFFFFF00);
            graphics.pose().popMatrix(); graphics.disableScissor();
        }
        VisualText.text(graphics, font, Ui.trim(font, status.isEmpty() ? Component.translatable("socialmod.visual.drag_help").getString() : status, width - canvasX - 4), canvasX, height - 40, Ui.theme().colors().text());
    }
}
