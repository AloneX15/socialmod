package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.theme.VisualText;

import com.takumistudios.socialmod.client.TagRenderer;
import com.takumistudios.socialmod.common.model.GroupIcon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Selector visual del estilo de la etiqueta de un grupo (SOCIALMOD_ERRORES 3): color con cuadro
 * saturación/brillo, barra de tono, colores rápidos y campo hexadecimal; emblema de {@link GroupIcon}; y vista
 * previa en vivo de cómo queda el nametag (nombre y, debajo, la etiqueta). No envía nada hasta pulsar Guardar.
 */
public class TagStyleScreen extends SocialChildScreen {
    /** Lo que se hace con el resultado (enviarlo al servidor o guardarlo en la pantalla de crear grupo). */
    public interface Result {
        void apply(int rgb, String icon);
    }

    private static final int WIDTH = 236;
    private static final int SQUARE = 90;
    private static final int HUE_W = 10;
    private static final int SWATCH = 13;
    private static final int ICON_CELL = 18;
    private static final int ICONS_PER_ROW = 10;
    /** Colores de chat de Minecraft: atajos conocidos para los jugadores. */
    private static final int[] PRESETS = {
            0xFFFFFF, 0xAAAAAA, 0x555555, 0x000000,
            0xFF5555, 0xAA0000, 0xFFAA00, 0xFFFF55,
            0x55FF55, 0x00AA00, 0x55FFFF, 0x00AAAA,
            0x5555FF, 0x0000AA, 0xFF55FF, 0xAA00AA};

    private final String tag;
    private final String role;
    private final Result result;
    private float hue;
    private float saturation;
    private float value;
    private String icon;
    private EditBox hex;
    private boolean updatingHex;
    private int dragging; // 0 nada, 1 cuadro, 2 tono

    public TagStyleScreen(@Nullable Screen parent, String tag, int rgb, String icon, String role, Result result) {
        super(parent, Component.translatable("socialmod.tag_style.title"));
        this.tag = tag.isEmpty() ? "TAG" : tag;
        this.role = role;
        this.result = result;
        this.icon = GroupIcon.byId(icon) == null ? GroupIcon.NONE.id() : icon;
        setRgb(rgb);
    }

    @Override
    protected boolean rebuildOnChange() {
        return false;
    }

    private int top() {
        return Math.max(Ui.TITLE_Y + 14, (this.height - 200) / 2);
    }

    @Override
    protected void init() {
        super.init();
        int left = panelLeft(WIDTH);
        int top = top();
        hex = new StyledEditBox(this.font, left + SQUARE + HUE_W + 10, top + 4 * SWATCH + 22, 64, 18, Component.translatable("socialmod.group_settings.color"));
        hex.setMaxLength(7);
        hex.setHint(Ui.hint(Component.translatable("socialmod.group_settings.color")));
        hex.setValue(hexOf(rgb()));
        hex.setResponder(text -> {
            if (updatingHex) {
                return;
            }
            Integer parsed = parseHex(text);
            if (parsed != null) {
                setRgb(parsed);
            }
        });
        addRenderableWidget(hex);
        int buttonsY = iconsY() + 2 * ICON_CELL + 32;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.group_settings.save"), b -> {
            result.apply(rgb(), icon);
            onClose();
        }).bounds(left, buttonsY, WIDTH / 2 - 2, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + WIDTH / 2 + 2, buttonsY, WIDTH / 2 - 2, 20).build());
    }

    private int iconsY() {
        return top() + SQUARE + 16;
    }

    // ---------- Color ----------

    private int rgb() {
        return hsv(hue, saturation, value);
    }

    private void setRgb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        value = max;
        saturation = max == 0 ? 0 : delta / max;
        if (delta == 0) {
            return; // gris: se conserva el tono anterior para que la barra no salte
        }
        float h;
        if (max == r) {
            h = ((g - b) / delta) % 6;
        } else if (max == g) {
            h = (b - r) / delta + 2;
        } else {
            h = (r - g) / delta + 4;
        }
        hue = ((h / 6f) % 1f + 1f) % 1f;
    }

    static int hsv(float h, float s, float v) {
        float c = v * s;
        float hp = (h % 1f) * 6f;
        float x = c * (1 - Math.abs(hp % 2 - 1));
        float r = 0;
        float g = 0;
        float b = 0;
        switch ((int) hp) {
            case 0 -> { r = c; g = x; }
            case 1 -> { r = x; g = c; }
            case 2 -> { g = c; b = x; }
            case 3 -> { g = x; b = c; }
            case 4 -> { r = x; b = c; }
            default -> { r = c; b = x; }
        }
        float m = v - c;
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    private static String hexOf(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private static @Nullable Integer parseHex(String text) {
        String clean = text.trim().startsWith("#") ? text.trim().substring(1) : text.trim();
        return clean.matches("[0-9A-Fa-f]{6}") ? Integer.parseInt(clean, 16) : null;
    }

    private void syncHex() {
        if (hex != null) {
            updatingHex = true;
            hex.setValue(hexOf(rgb()));
            updatingHex = false;
        }
    }

    // ---------- Ratón ----------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mx = event.x();
        double my = event.y();
        int left = panelLeft(WIDTH);
        int top = top();
        if (Ui.inside(mx, my, left, top, SQUARE, SQUARE)) {
            dragging = 1;
            pickSquare(mx, my);
            return true;
        }
        if (Ui.inside(mx, my, left + SQUARE + 4, top, HUE_W, SQUARE)) {
            dragging = 2;
            pickHue(my);
            return true;
        }
        int swatchX = left + SQUARE + HUE_W + 10;
        for (int i = 0; i < PRESETS.length; i++) {
            if (Ui.inside(mx, my, swatchX + (i % 4) * (SWATCH + 2), top + (i / 4) * (SWATCH + 2), SWATCH, SWATCH)) {
                setRgb(PRESETS[i]);
                syncHex();
                return true;
            }
        }
        GroupIcon[] icons = GroupIcon.values();
        for (int i = 0; i < icons.length; i++) {
            if (Ui.inside(mx, my, left + (i % ICONS_PER_ROW) * (ICON_CELL + 6), iconsY() + (i / ICONS_PER_ROW) * ICON_CELL, ICON_CELL, ICON_CELL)) {
                icon = icons[i].id();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == 1) {
            pickSquare(event.x(), event.y());
            return true;
        }
        if (dragging == 2) {
            pickHue(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = 0;
        return super.mouseReleased(event);
    }

    private void pickSquare(double mx, double my) {
        int left = panelLeft(WIDTH);
        int top = top();
        saturation = clamp01((float) (mx - left) / (SQUARE - 1));
        value = 1f - clamp01((float) (my - top) / (SQUARE - 1));
        syncHex();
    }

    private void pickHue(double my) {
        hue = Math.min(0.999f, clamp01((float) (my - top()) / (SQUARE - 1)));
        syncHex();
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // ---------- Dibujo ----------

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Ui.title(graphics, font, this.title, this.width / 2, Ui.TITLE_Y);
        int left = panelLeft(WIDTH);
        int top = top();
        Ui.panel(graphics, left - 6, top - 6, left + WIDTH + 6, iconsY() + 2 * ICON_CELL + 26);

        // Cuadro saturación (horizontal) / brillo (vertical): columnas de 2 px con degradado hacia negro
        for (int x = 0; x < SQUARE; x += 2) {
            int top0 = 0xFF000000 | hsv(hue, x / (float) (SQUARE - 1), 1f);
            graphics.fillGradient(left + x, top, left + x + 2, top + SQUARE, top0, 0xFF000000);
        }
        graphics.outline(left - 1, top - 1, SQUARE + 2, SQUARE + 2, Ui.theme().colors().border());
        int cx = left + Math.round(saturation * (SQUARE - 1));
        int cy = top + Math.round((1 - value) * (SQUARE - 1));
        graphics.outline(cx - 2, cy - 2, 5, 5, value > 0.5f ? 0xFF000000 : 0xFFFFFFFF);

        // Barra de tono: seis tramos con degradado
        int hueX = left + SQUARE + 4;
        for (int i = 0; i < 6; i++) {
            int y1 = top + i * SQUARE / 6;
            int y2 = top + (i + 1) * SQUARE / 6;
            graphics.fillGradient(hueX, y1, hueX + HUE_W, y2, 0xFF000000 | hsv(i / 6f, 1, 1), 0xFF000000 | hsv(((i + 1) % 6) / 6f, 1, 1));
        }
        int hy = top + Math.round(hue * (SQUARE - 1));
        graphics.fill(hueX - 1, hy - 1, hueX + HUE_W + 1, hy + 1, 0xFFFFFFFF);

        // Colores rápidos
        int swatchX = left + SQUARE + HUE_W + 10;
        for (int i = 0; i < PRESETS.length; i++) {
            int sx = swatchX + (i % 4) * (SWATCH + 2);
            int sy = top + (i / 4) * (SWATCH + 2);
            graphics.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | PRESETS[i]);
            boolean current = (rgb() & 0xFFFFFF) == PRESETS[i];
            graphics.outline(sx - 1, sy - 1, SWATCH + 2, SWATCH + 2, current ? 0xFFFFFFFF : Ui.theme().colors().border());
        }
        VisualText.text(graphics, font, Ui.upper(Component.translatable("socialmod.tag_style.color")), swatchX, top + 4 * SWATCH + 11, Ui.theme().colors().muted());
        graphics.fill(swatchX + 68, top + 4 * SWATCH + 22, swatchX + 86, top + 4 * SWATCH + 40, 0xFF000000 | rgb());

        // Emblemas
        int iconsY = iconsY();
        VisualText.text(graphics, font, Ui.upper(Component.translatable("socialmod.tag_style.icon")), left, iconsY - 11, Ui.theme().colors().muted());
        GroupIcon[] icons = GroupIcon.values();
        for (int i = 0; i < icons.length; i++) {
            int ix = left + (i % ICONS_PER_ROW) * (ICON_CELL + 6);
            int iy = iconsY + (i / ICONS_PER_ROW) * ICON_CELL;
            boolean selected = icons[i].id().equals(icon);
            if (selected) {
                graphics.fill(ix, iy, ix + ICON_CELL, iy + ICON_CELL - 2, Ui.theme().colors().highlight());
            }
            if (Ui.inside(mouseX, mouseY, ix, iy, ICON_CELL, ICON_CELL)) {
                graphics.outline(ix, iy, ICON_CELL, ICON_CELL - 2, Ui.theme().colors().border());
            }
            String glyph = icons[i] == GroupIcon.NONE ? "∅" : icons[i].glyph();
            VisualText.centeredText(graphics, font, glyph, ix + ICON_CELL / 2, iy + 4, selected ? 0xFF000000 | Ui.readable(rgb()) : Ui.theme().colors().text());
        }

        // Vista previa del nametag: nombre arriba y la etiqueta debajo, con el fondo translúcido de vanilla
        int previewY = iconsY + 2 * ICON_CELL + 6;
        VisualText.text(graphics, font, Ui.upper(Component.translatable("socialmod.tag_style.preview")), left, previewY, Ui.theme().colors().muted());
        String name = minecraft != null && minecraft.player != null ? minecraft.player.getGameProfile().name() : "Steve";
        MutableComponent tagLine = TagRenderer.line(tag, rgb(), icon, role);
        int centerX = left + WIDTH / 2 + 30;
        nameplate(graphics, Component.literal(name), centerX, previewY - 2);
        nameplate(graphics, tagLine, centerX, previewY + 9);
    }

    private void nameplate(GuiGraphicsExtractor graphics, Component text, int centerX, int y) {
        int w = font.width(text);
        graphics.fill(centerX - w / 2 - 1, y - 1, centerX + w / 2 + 1, y + 9, 0x40000000);
        VisualText.centeredText(graphics, font, text, centerX, y, 0xFFFFFFFF);
    }
}
