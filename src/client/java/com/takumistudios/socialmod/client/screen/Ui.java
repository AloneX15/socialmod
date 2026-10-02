package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.theme.Theme;
import com.takumistudios.socialmod.client.theme.ThemeManager;
import com.takumistudios.socialmod.common.model.PresenceStatus;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
        graphics.fill(x1, y1, x2, y2, theme.colors().panel());
        graphics.outline(x1, y1, x2 - x1, y2 - y1, theme.colors().border());
    }

    public static void sectionHeader(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width) {
        Theme theme = theme();
        String label = text.getString() + " ";
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
