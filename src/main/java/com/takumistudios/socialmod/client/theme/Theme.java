package com.takumistudios.socialmod.client.theme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Tema visual del panel y los toasts (PLAN 15). Se define con anclas y pesos en vez de coordenadas absolutas,
 * para que funcione con cualquier GUI Scale e idioma. Lo aporta un resource pack en
 * {@code assets/socialmod/themes/<id>.json}; si el JSON tiene errores se usa el tema por defecto.
 */
public record Theme(String layout, List<Column> columns, Colors colors, Toast toast, Textures textures) {
    public static final Theme DEFAULT = new Theme("three_column",
            List.of(new Column("conversations", 1, 110, false), new Column("chat", 2, 160, false), new Column("players", 1, 90, true)),
            Colors.DEFAULT, new Toast(0xE0101010, 0xFF555555, 160), Textures.NONE);

    public record Column(String id, int weight, int minWidth, boolean collapsible) {
        public static final Codec<Column> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Column::id),
                Codec.intRange(1, 10).optionalFieldOf("weight", 1).forGetter(Column::weight),
                Codec.intRange(40, 600).optionalFieldOf("min_width", 90).forGetter(Column::minWidth),
                Codec.BOOL.optionalFieldOf("collapsible", false).forGetter(Column::collapsible)
        ).apply(i, Column::new));
    }

    public record Colors(int text, int muted, int accent, int unread, int background, int panel, int border, int highlight) {
        public static final Colors DEFAULT = new Colors(0xFFE0E0E0, 0xFF909090, 0xFF55FF55, 0xFFFFAA00, 0xC0000000, 0x80101010, 0xFF404040, 0x40FFFFFF);
        public static final Colors HIGH_CONTRAST = new Colors(0xFFFFFFFF, 0xFFD0D0D0, 0xFF00FF00, 0xFFFFD000, 0xF0000000, 0xF0000000, 0xFFFFFFFF, 0x80FFFFFF);

        public static final Codec<Colors> CODEC = RecordCodecBuilder.create(i -> i.group(
                Colour.COLOR.optionalFieldOf("text", DEFAULT.text).forGetter(Colors::text),
                Colour.COLOR.optionalFieldOf("muted", DEFAULT.muted).forGetter(Colors::muted),
                Colour.COLOR.optionalFieldOf("accent", DEFAULT.accent).forGetter(Colors::accent),
                Colour.COLOR.optionalFieldOf("unread", DEFAULT.unread).forGetter(Colors::unread),
                Colour.COLOR.optionalFieldOf("background", DEFAULT.background).forGetter(Colors::background),
                Colour.COLOR.optionalFieldOf("panel", DEFAULT.panel).forGetter(Colors::panel),
                Colour.COLOR.optionalFieldOf("border", DEFAULT.border).forGetter(Colors::border),
                Colour.COLOR.optionalFieldOf("highlight", DEFAULT.highlight).forGetter(Colors::highlight)
        ).apply(i, Colors::new));
    }

    public record Toast(int background, int border, int width) {
        public static final Codec<Toast> CODEC = RecordCodecBuilder.create(i -> i.group(
                Colour.COLOR.optionalFieldOf("background", 0xE0101010).forGetter(Toast::background),
                Colour.COLOR.optionalFieldOf("border", 0xFF555555).forGetter(Toast::border),
                Codec.intRange(120, 300).optionalFieldOf("width", 160).forGetter(Toast::width)
        ).apply(i, Toast::new));
    }

    /**
     * Texturas opcionales (PLAN 15): sprites del atlas de la GUI que aporta el resource pack en
     * {@code assets/<ns>/textures/gui/sprites/<ruta>.png}, con su {@code .png.mcmeta} para escalado nine-slice.
     * Se usan en lugar del color de fondo correspondiente; sin ellas se dibuja el color de siempre.
     */
    public record Textures(Optional<Identifier> background, Optional<Identifier> panel, Optional<Identifier> toast) {
        public static final Textures NONE = new Textures(Optional.empty(), Optional.empty(), Optional.empty());
        public static final Codec<Textures> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.optionalFieldOf("background").forGetter(Textures::background),
                Identifier.CODEC.optionalFieldOf("panel").forGetter(Textures::panel),
                Identifier.CODEC.optionalFieldOf("toast").forGetter(Textures::toast)
        ).apply(i, Textures::new));
    }

    public static final Codec<Theme> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("layout", "three_column").forGetter(Theme::layout),
            Column.CODEC.listOf().optionalFieldOf("columns", DEFAULT.columns).forGetter(Theme::columns),
            Colors.CODEC.optionalFieldOf("colors", Colors.DEFAULT).forGetter(Theme::colors),
            Toast.CODEC.optionalFieldOf("toast", DEFAULT.toast).forGetter(Theme::toast),
            Textures.CODEC.optionalFieldOf("textures", Textures.NONE).forGetter(Theme::textures)
    ).apply(i, Theme::new));

    /** Colores como {@code "#RRGGBB"} o {@code "#AARRGGBB"}. En una clase aparte para evitar ciclos de inicialización. */
    static final class Colour {
        static final Codec<Integer> COLOR = Codec.STRING.comapFlatMap(Theme::parseColor, value -> String.format("#%08X", value));
    }

    static DataResult<Integer> parseColor(String value) {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        try {
            if (hex.length() == 6) {
                return DataResult.success(0xFF000000 | Integer.parseUnsignedInt(hex, 16));
            }
            if (hex.length() == 8) {
                return DataResult.success((int) Long.parseLong(hex, 16));
            }
        } catch (NumberFormatException ignored) {
            // abajo
        }
        return DataResult.error(() -> "Color inválido: " + value);
    }

    public Optional<Column> column(String id) {
        return columns.stream().filter(c -> c.id().equals(id)).findFirst();
    }
}
