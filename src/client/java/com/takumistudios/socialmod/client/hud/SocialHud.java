package com.takumistudios.socialmod.client.hud;

import com.takumistudios.socialmod.client.ClientConfig;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.theme.ThemeManager;
import com.takumistudios.socialmod.common.model.PresenceStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Widget social del HUD (PLAN 7.2): mensajes sin leer y estado actual. Posición y escala configurables.
 * Se oculta con F1 y cuando no hay servidor compatible. El texto solo se recalcula cuando cambia el estado.
 */
public final class SocialHud {
    private static int cachedVersion = -1;
    private static String text = "";
    private static int statusColor;
    private static String statusSymbol = "";
    private static boolean muted;

    private SocialHud() {
    }

    public static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        ClientConfig.Hud config = ClientConfig.get().hud;
        ClientState state = ClientState.get();
        if (!config.enabled || !state.connected() || ClientCompat.hudHidden() || ClientCompat.currentScreen() != null) {
            return;
        }
        if (cachedVersion != state.version()) {
            cachedVersion = state.version();
            int unread = state.totalUnread();
            PresenceStatus status = PresenceStatus.byId(state.snapshot().self.status);
            statusSymbol = status.symbol();
            statusColor = status.color();
            muted = state.snapshot().self.mutedUntil > System.currentTimeMillis();
            text = unread > 0 ? "✉ " + unread : "";
        }
        Font font = Minecraft.getInstance().font;
        float scale = config.scale / 100.0F;
        int width = (int) ((font.width(statusSymbol) + (text.isEmpty() ? 0 : 4 + font.width(text)) + (muted ? 10 : 0) + 6) * scale);
        int height = (int) (12 * scale);
        int x = config.position.right() ? graphics.guiWidth() - width - config.offsetX : config.offsetX;
        int y = config.position.bottom() ? graphics.guiHeight() - height - config.offsetY : config.offsetY;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        int innerWidth = (int) (width / scale);
        graphics.fill(0, 0, innerWidth, 12, ThemeManager.get().colors().panel());
        int cursor = 3;
        graphics.text(font, statusSymbol, cursor, 2, statusColor);
        cursor += font.width(statusSymbol) + 4;
        if (!text.isEmpty()) {
            graphics.text(font, text, cursor, 2, ThemeManager.get().colors().unread());
            cursor += font.width(text) + 4;
        }
        if (muted) {
            graphics.text(font, "⊘", cursor, 2, 0xFFFF5555);
        }
        graphics.pose().popMatrix();
    }
}
