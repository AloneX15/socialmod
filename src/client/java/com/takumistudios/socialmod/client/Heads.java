package com.takumistudios.socialmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Cabezas de jugador con el gestor de skins de vanilla (PLAN 13): se usan las texturas que el cliente ya descargó
 * para la lista de jugadores. Sin peticiones propias a Mojang; si no hay skin, Steve/Alex según el UUID.
 */
public final class Heads {
    private Heads() {
    }

    public static PlayerSkin skin(@Nullable UUID player) {
        if (player == null) {
            return DefaultPlayerSkin.getDefaultSkin();
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(player);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(player);
    }

    public static void draw(GuiGraphicsExtractor graphics, @Nullable UUID player, int x, int y, int size) {
        PlayerFaceExtractor.extractRenderState(graphics, skin(player), x, y, size);
    }
}
