package com.takumistudios.socialmod.client;

import com.takumistudios.socialmod.client.theme.VisualManager;

import com.takumistudios.socialmod.client.screen.Ui;
import com.takumistudios.socialmod.common.model.GroupIcon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Formato único de la etiqueta de grupo: lo usan el nametag ({@link GroupTagLayer}), el perfil, la lista de jugadores
 * y la vista previa en vivo de los ajustes del grupo, así lo que se ve al editar es lo que se ve en el mundo.
 */
public final class TagRenderer {
    private TagRenderer() {
    }

    /** {@code ⚔ [TAG] · Oficial}. El rol va en gris para que el color del grupo destaque. */
    public static MutableComponent line(String tag, int color, String icon, String role) {
        ClientConfig.Nametags config = ClientConfig.get().nametags;
        MutableComponent out = Component.empty();
        String glyph = VisualManager.get().tagIcon ? GroupIcon.glyphOf(icon) : "";
        if (!glyph.isEmpty()) {
            out.append(Component.literal(glyph + " ").withColor(color & 0xFFFFFF));
        }
        out.append(Component.literal(VisualManager.get().tagBrackets ? "[" + tag + "]" : tag).withColor(color & 0xFFFFFF));
        if (config.showRole && role != null && !role.isEmpty()) {
            out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("socialmod.role." + role).withStyle(ChatFormatting.GRAY));
        }
        return out.withStyle(style -> style.withFont(new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.parse(VisualManager.get().font))));
    }

    /** Versión para el panel: mismo texto pero con el color ajustado para leerse sobre el fondo oscuro. */
    public static MutableComponent panelLine(String tag, int color, String icon, String role) {
        return line(tag, Ui.readable(color), icon, role);
    }
}
