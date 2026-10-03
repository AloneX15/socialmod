package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.theme.Theme;
import com.takumistudios.socialmod.client.theme.ThemeManager;
import com.takumistudios.socialmod.common.model.PresenceStatus;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Utilidades de dibujo compartidas por las pantallas (estilo vanilla, colores del tema). */
public final class Ui {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private Ui() {
    }

    public static Theme theme() {
        return ThemeManager.get();
    }

    public static String trim(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    public static void panel(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
        Theme theme = theme();
        if (theme.textures().panel().isPresent()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, theme.textures().panel().get(), x1, y1, x2 - x1, y2 - y1);
            return;
        }
        graphics.fill(x1, y1, x2, y2, theme.colors().panel());
        graphics.outline(x1, y1, x2 - x1, y2 - y1, theme.colors().border());
    }

    /** Fondo de pantalla completa del panel: textura del tema o color. */
    public static void background(GuiGraphicsExtractor graphics, int width, int height) {
        Theme theme = theme();
        if (theme.textures().background().isPresent()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, theme.textures().background().get(), 0, 0, width, height);
        } else {
            graphics.fill(0, 0, width, height, theme.colors().background());
        }
    }

    // ---------- Guía de estilo (SOCIALMOD_ERRORES 2) ----------
    // Títulos de pantalla: blanco, centrados, en TITLE_Y. Cabeceras de sección: MAYÚSCULAS (las pone el código,
    // los .json van en minúsculas normales), color muted y línea. Etiquetas y botones: frase normal ("En línea").
    // Pistas de los campos: gris oscuro. Colores elegidos por los jugadores (grupos): siempre por readable().

    public static final int TITLE_Y = 10;
    public static final int TITLE = 0xFFFFFFFF;
    public static final int SUCCESS = 0xFF55FF55;
    public static final int DANGER = 0xFFFF5555;
    public static final int WARNING = 0xFFFFAA00;
    public static final int EVENT = 0xFFFF55FF;
    public static final int ROW = 12;

    public static void title(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y) {
        graphics.centeredText(font, text, centerX, y, TITLE);
    }

    public static Component hint(Component text) {
        return text.copy().withStyle(net.minecraft.ChatFormatting.DARK_GRAY);
    }

    /** Texto de sección en mayúsculas según el idioma del juego. */
    public static String upper(Component text) {
        return text.getString().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Color de un jugador o grupo legible sobre el fondo oscuro del panel: opaco y, si es muy oscuro
     * (p. ej. {@code #000080}), aclarado hasta una luminancia mínima.
     */
    public static int readable(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        double luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
        if (luminance < 0.35) {
            double t = (0.35 - luminance) / 0.35 * 0.6;
            r = (int) (r + (255 - r) * t);
            g = (int) (g + (255 - g) * t);
            b = (int) (b + (255 - b) * t);
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static void sectionHeader(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width) {
        Theme theme = theme();
        String label = upper(text) + " ";
        graphics.text(font, trim(font, label, width), x, y, theme.colors().muted());
        int lineStart = x + font.width(label) + 2;
        if (lineStart < x + width) {
            graphics.horizontalLine(lineStart, x + width, y + 4, theme.colors().border());
        }
    }

    /** Símbolo de estado: además del color tiene forma propia (accesibilidad, PLAN 7.3). */
    public static void status(GuiGraphicsExtractor graphics, Font font, PresenceStatus status, int x, int y) {
        graphics.text(font, status.symbol(), x, y, status.color());
    }

    public static String time(long epochMillis) {
        long age = System.currentTimeMillis() - epochMillis;
        return age < 86_400_000L ? TIME.format(Instant.ofEpochMilli(epochMillis)) : DATE.format(Instant.ofEpochMilli(epochMillis));
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Zona clicable de una lista, en coordenadas de contenido (y sin desplazar). */
    public record Row(int x, int y, int width, int height, Runnable onClick, Runnable onRightClick) {
        public static Row of(int x, int y, int width, int height, Runnable onClick) {
            return new Row(x, y, width, height, onClick, null);
        }
    }

    /** Lista de filas con desplazamiento, recortada a un área. Las filas se registran al dibujar. */
    public static final class RowList {
        public final List<Row> rows = new ArrayList<>();
        public int scroll;
        public int contentHeight;
        public int viewX;
        public int viewY;
        public int viewWidth;
        public int viewHeight;

        public void begin(int x, int y, int width, int height) {
            rows.clear();
            viewX = x;
            viewY = y;
            viewWidth = width;
            viewHeight = height;
        }

        public void end(int contentHeight) {
            this.contentHeight = contentHeight;
            scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewHeight)));
        }

        /** Posición en pantalla de una y de contenido. */
        public int screenY(int contentY) {
            return viewY + contentY - scroll;
        }

        public boolean isOver(double mx, double my) {
            return inside(mx, my, viewX, viewY, viewWidth, viewHeight);
        }

        public boolean scroll(double mx, double my, double amount) {
            if (!isOver(mx, my)) {
                return false;
            }
            scroll = Math.max(0, Math.min(scroll - (int) (amount * 12), Math.max(0, contentHeight - viewHeight)));
            return true;
        }

        public boolean click(double mx, double my, boolean right) {
            if (!isOver(mx, my)) {
                return false;
            }
            double localY = my - viewY + scroll;
            // Las filas más específicas (botones) se registran después: se recorren al revés
            for (int i = rows.size() - 1; i >= 0; i--) {
                Row row = rows.get(i);
                if (mx >= row.x() && mx < row.x() + row.width() && localY >= row.y() && localY < row.y() + row.height()) {
                    Runnable action = right ? row.onRightClick() : row.onClick();
                    if (action != null) {
                        action.run();
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
