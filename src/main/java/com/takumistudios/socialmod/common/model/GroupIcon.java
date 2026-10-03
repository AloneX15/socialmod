package com.takumistudios.socialmod.common.model;

/**
 * Emblema de un grupo (PLAN 5.2): un símbolo Unicode del plano básico, que la fuente de Minecraft dibuja sin
 * texturas extra y que también ven los clientes vanilla en el fallback de teams. El servidor solo acepta ids de esta
 * lista, así nadie puede meter texto arbitrario ni caracteres que rompan el nametag.
 */
public enum GroupIcon {
    NONE("none", ""),
    SHIELD("shield", "⛨"),
    SWORDS("swords", "⚔"),
    PICKAXE("pickaxe", "⛏"),
    CROWN("crown", "♛"),
    STAR("star", "★"),
    HEART("heart", "❤"),
    SKULL("skull", "☠"),
    FLAG("flag", "⚑"),
    LIGHTNING("lightning", "⚡"),
    SUN("sun", "☀"),
    MOON("moon", "☾"),
    SNOW("snow", "❄"),
    FLOWER("flower", "✿"),
    MUSIC("music", "♫"),
    DIAMOND("diamond", "♦"),
    ANCHOR("anchor", "⚓"),
    PEACE("peace", "☮"),
    YIN_YANG("yin_yang", "☯"),
    SPARKLE("sparkle", "✦");

    private final String id;
    private final String glyph;

    GroupIcon(String id, String glyph) {
        this.id = id;
        this.glyph = glyph;
    }

    public String id() {
        return id;
    }

    /** Símbolo que se dibuja; vacío para {@link #NONE}. */
    public String glyph() {
        return glyph;
    }

    /** {@code null} si el id no está en la lista. */
    public static GroupIcon byId(String id) {
        if (id != null) {
            for (GroupIcon icon : values()) {
                if (icon.id.equalsIgnoreCase(id.trim())) {
                    return icon;
                }
            }
        }
        return null;
    }

    /** Para dibujar: ids desconocidos (datos viejos o de otra versión) se muestran sin icono. */
    public static String glyphOf(String id) {
        GroupIcon icon = byId(id);
        return icon == null ? "" : icon.glyph;
    }
}
