package com.takumistudios.socialmod.common.text;

import com.takumistudios.socialmod.common.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Convierte el texto de un mensaje (markdown seguro + adjuntos del servidor) en un {@link Component}.
 * Lo usan el servidor (chat de sistema para jugadores vanilla) y el cliente (panel y toasts).
 * Los enlaces usan {@code OpenUrl}: el cliente vanilla pide confirmación antes de abrirlos.
 */
public final class MessageFormatter {
    public static final String ITEM_TOKEN = "[item]";
    public static final String COORDS_TOKEN = "[coords]";

    private MessageFormatter() {
    }

    /**
     * @param highlight nombres (en minúsculas) cuya mención se resalta más (p. ej. el propio jugador).
     */
    public static MutableComponent format(String text, List<Payloads.AttachmentView> attachments, boolean allowLinks, Set<String> highlight) {
        MutableComponent out = Component.empty();
        int itemIndex = 0;
        int coordsIndex = 0;
        int i = 0;
        while (i < text.length()) {
            int nextItem = text.indexOf(ITEM_TOKEN, i);
            int nextCoords = text.indexOf(COORDS_TOKEN, i);
            int next = nextItem < 0 ? nextCoords : nextCoords < 0 ? nextItem : Math.min(nextItem, nextCoords);
            if (next < 0) {
                appendMarkdown(out, text.substring(i), allowLinks, highlight);
                break;
            }
            appendMarkdown(out, text.substring(i, next), allowLinks, highlight);
            boolean isItem = next == nextItem;
            Payloads.AttachmentView attachment = isItem ? nth(attachments, true, itemIndex++) : nth(attachments, false, coordsIndex++);
            if (attachment != null) {
                out.append(isItem ? item(attachment) : coords(attachment));
            } else {
                out.append(Component.literal(isItem ? ITEM_TOKEN : COORDS_TOKEN));
            }
            i = next + (isItem ? ITEM_TOKEN.length() : COORDS_TOKEN.length());
        }
        return out;
    }

    private static Payloads.AttachmentView nth(List<Payloads.AttachmentView> attachments, boolean item, int index) {
        int seen = 0;
        for (Payloads.AttachmentView attachment : attachments) {
            if (attachment.isItem() == item) {
                if (seen == index) {
                    return attachment;
                }
                seen++;
            }
        }
        return null;
    }

    private static void appendMarkdown(MutableComponent out, String text, boolean allowLinks, Set<String> highlight) {
        if (text.isEmpty()) {
            return;
        }
        for (SafeMarkdown.Span span : SafeMarkdown.parse(text)) {
            MutableComponent part = Component.literal(span.text());
            Style style = Style.EMPTY;
            if (span.bold()) style = style.withBold(true);
            if (span.italic()) style = style.withItalic(true);
            switch (span.kind()) {
                case CODE -> style = style.withColor(ChatFormatting.GRAY).withItalic(false);
                case MENTION -> {
                    boolean self = highlight.contains(span.text().substring(1).toLowerCase(Locale.ROOT));
                    style = style.withColor(self ? ChatFormatting.GOLD : ChatFormatting.YELLOW);
                    if (self) style = style.withBold(true);
                }
                case LINK -> {
                    URI uri = safeUri(span.text());
                    if (allowLinks && uri != null) {
                        style = style.withColor(ChatFormatting.BLUE).withUnderlined(true)
                                .withClickEvent(new ClickEvent.OpenUrl(uri))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal(span.text())));
                    }
                }
                default -> {
                }
            }
            out.append(part.withStyle(style));
        }
    }

    /** Solo http/https y sin caracteres raros: nada de {@code javascript:} ni {@code file:}. */
    public static URI safeUri(String text) {
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() == null) {
                return null;
            }
            return uri;
        } catch (Exception e) {
            return null;
        }
    }

    public static MutableComponent item(Payloads.AttachmentView attachment) {
        if (attachment.item() == null) {
            return Component.literal(ITEM_TOKEN);
        }
        var stack = attachment.item().create();
        MutableComponent name = Component.literal("[").append(stack.getHoverName()).append(Component.literal(
                stack.getCount() > 1 ? " x" + stack.getCount() + "]" : "]"));
        return name.withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withHoverEvent(new HoverEvent.ShowItem(attachment.item())));
    }

    public static MutableComponent coords(Payloads.AttachmentView attachment) {
        String text = attachment.x() + ", " + attachment.y() + ", " + attachment.z();
        Component hover = Component.literal(attachment.dimension()).append("\n").append(Component.translatableWithFallback(
                "socialmod.coords.hover", "Click to copy the coordinates"));
        return Component.literal("[" + text + "]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                .withHoverEvent(new HoverEvent.ShowText(hover))
                .withClickEvent(new ClickEvent.CopyToClipboard(attachment.x() + " " + attachment.y() + " " + attachment.z())));
    }

    /** Vista previa en texto plano (toasts, lista de conversaciones, narrador). */
    public static String preview(String text, int max) {
        String plain = SafeMarkdown.plain(text);
        return plain.length() > max ? plain.substring(0, Math.max(0, max - 1)) + "…" : plain;
    }
}
