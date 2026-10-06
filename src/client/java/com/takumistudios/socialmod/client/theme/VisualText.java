package com.takumistudios.socialmod.client.theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/** Font selection for every SocialMod text renderer, preserving translated text and inline styles. */
public final class VisualText {
    private static String cachedId = "";
    private static FontDescription font;
    private static FontDescription font() {
        String id = VisualManager.get().font;
        if (!id.equals(cachedId)) { cachedId = id; font = new FontDescription.Resource(Identifier.parse(id)); }
        return font;
    }
    private static Component styled(Component text) { return VisualManager.get().font.equals("minecraft:default") ? text : text.copy().withStyle(s -> s.withFont(font())); }
    private static FormattedCharSequence styled(FormattedCharSequence text) { if (VisualManager.get().font.equals("minecraft:default")) return text; return sink -> text.accept((index, style, point) -> sink.accept(index, style.withFont(font()), point)); }
    private static Component from(FormattedCharSequence text) {
        StringBuilder value = new StringBuilder(); text.accept((i, style, point) -> { value.appendCodePoint(point); return true; }); return Component.literal(value.toString());
    }
    private static void draw(GuiGraphicsExtractor g, Font f, Component value, int x, int y, int color, boolean shadow, boolean centered) {
        Component text = styled(value);
        if (!VisualManager.tracksDrawn()) { if (centered) g.centeredText(f, text, x, y, color); else g.text(f, text, x, y, color, shadow); return; }
        int width = Math.max(1, f.width(text)), left = centered ? x - width / 2 : x;
        String key = value.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : value.getString();
        var drawn = VisualManager.drawn(key, left, y, width, 9, true); var rect = VisualManager.drawnRect(drawn);
        if (rect == null) { if (centered) g.centeredText(f, text, x, y, color); else g.text(f, text, x, y, color, shadow); return; }
        g.pose().pushMatrix(); g.pose().translate(drawn.x(), drawn.y()); g.pose().scale((float) drawn.w() / width, (float) drawn.h() / 9);
        g.text(f, text, 0, 0, rect.textColor, shadow); g.pose().popMatrix();
    }
    public static void text(GuiGraphicsExtractor g, Font f, String text, int x, int y, int color) { draw(g, f, Component.literal(text), x, y, color, true, false); }
    public static void text(GuiGraphicsExtractor g, Font f, Component text, int x, int y, int color) { draw(g, f, text, x, y, color, true, false); }
    public static void text(GuiGraphicsExtractor g, Font f, FormattedCharSequence text, int x, int y, int color) { text(g, f, text, x, y, color, true); }
    public static void text(GuiGraphicsExtractor g, Font f, String text, int x, int y, int color, boolean shadow) { draw(g, f, Component.literal(text), x, y, color, shadow, false); }
    public static void text(GuiGraphicsExtractor g, Font f, Component text, int x, int y, int color, boolean shadow) { draw(g, f, text, x, y, color, shadow, false); }
    public static void text(GuiGraphicsExtractor g, Font f, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        if (!VisualManager.tracksDrawn()) { g.text(f, styled(text), x, y, color, shadow); return; }
        Component value = from(text); String key = value.getString(); int width = Math.max(1, f.width(text));
        var drawn = VisualManager.drawn(key, x, y, width, 9, true); var rect = VisualManager.drawnRect(drawn);
        if (rect == null) { g.text(f, styled(text), x, y, color, shadow); return; }
        g.pose().pushMatrix(); g.pose().translate(drawn.x(), drawn.y()); g.pose().scale((float) drawn.w() / width, (float) drawn.h() / 9);
        g.text(f, styled(text), 0, 0, rect.textColor, shadow); g.pose().popMatrix();
    }
    public static void centeredText(GuiGraphicsExtractor g, Font f, String text, int x, int y, int color) { draw(g, f, Component.literal(text), x, y, color, true, true); }
    public static void centeredText(GuiGraphicsExtractor g, Font f, Component text, int x, int y, int color) { draw(g, f, text, x, y, color, true, true); }
    public static void centeredText(GuiGraphicsExtractor g, Font f, FormattedCharSequence text, int x, int y, int color) { draw(g, f, from(text), x, y, color, true, true); }
}
