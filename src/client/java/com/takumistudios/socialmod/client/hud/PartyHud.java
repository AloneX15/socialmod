package com.takumistudios.socialmod.client.hud;

import com.takumistudios.socialmod.client.ClientConfig;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.PartyClient;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.screen.Ui;
import com.takumistudios.socialmod.client.theme.ThemeManager;
import com.takumistudios.socialmod.common.net.Payloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Party en el HUD (PLAN 5.2 y 5.5): vida de cada compañero y pings activos con distancia y dirección.
 * Va en el borde izquierdo a media altura: no choca con los minimapas (esquinas superiores), el chat (abajo a la
 * izquierda) ni la barra de objetos. Se oculta con F1, con una pantalla abierta y sin party.
 */
public final class PartyHud {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int BAR = 40;

    private PartyHud() {
    }

    public static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        ClientConfig config = ClientConfig.get();
        if (!ClientState.get().connected() || ClientCompat.hudHidden() || ClientCompat.currentScreen() != null) {
            return;
        }
        var members = config.hud.partyHealth ? PartyClient.members() : java.util.List.<Payloads.PartyMember>of();
        var pings = PartyClient.pings();
        if (members.isEmpty() && pings.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int rows = members.size() + pings.size();
        int x = 4;
        int y = graphics.guiHeight() / 2 - rows * 6;
        int width = 4 + 70 + BAR;
        graphics.fill(x - 2, y - 2, x + width, y + rows * 12, ThemeManager.get().colors().panel());
        for (Payloads.PartyMember member : members) {
            graphics.text(font, Ui.trim(font, member.name(), 66), x, y + 2, ThemeManager.get().colors().text());
            int barX = x + 70;
            graphics.fill(barX, y + 4, barX + BAR, y + 7, 0xFF3A0000);
            if (member.health() >= 0 && member.maxHealth() > 0) {
                float ratio = Math.max(0f, Math.min(1f, member.health() / member.maxHealth()));
                int color = ratio > 0.5f ? 0xFFE03030 : ratio > 0.25f ? Ui.WARNING : 0xFFFF0000;
                graphics.fill(barX, y + 4, barX + Math.round(BAR * ratio), y + 7, color);
            } else {
                graphics.fill(barX, y + 4, barX + BAR, y + 7, 0xFF404040);
            }
            y += 12;
        }
        Player player = minecraft.player;
        String dimension = minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
        for (PartyClient.ActivePing active : pings) {
            Payloads.PingS2C ping = active.ping();
            String where;
            if (player != null && ping.dimension().equals(dimension)) {
                double dx = ping.x() + 0.5 - player.getX();
                double dz = ping.z() + 0.5 - player.getZ();
                int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                // Ángulo hacia el punto relativo a donde mira el jugador (0 = delante)
                double target = Math.toDegrees(Math.atan2(-dx, dz));
                double relative = target - player.getYRot();
                where = ARROWS[Math.floorMod((int) Math.round(relative / 45.0), 8)] + " " + distance + " m";
            } else {
                where = "x: " + ping.x() + ", z: " + ping.z();
            }
            Component line = Component.literal("◆ ").withColor(Ui.readable(ping.color()) & 0xFFFFFF)
                    .append(Component.literal(Ui.trim(font, ping.name(), 50) + "  " + where));
            graphics.text(font, line, x, y + 2, ThemeManager.get().colors().text());
            y += 12;
        }
    }
}
