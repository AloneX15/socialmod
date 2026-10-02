package com.takumistudios.socialmod.server.service;

import com.takumistudios.socialmod.api.Notification;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.server.Lang;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Notificaciones (PLAN 6 y 10). Con el mod: un toast ({@code NotifyS2C}); sin el mod: action bar o chat, según la config.
 * Los textos se traducen aquí al idioma del jugador, así que sirven para ambos casos.
 */
public final class Notifier {
    private final SocialServer social;

    public Notifier(SocialServer social) {
        this.social = social;
    }

    /**
     * @param titleKey clave de idioma del título (con {@code args})
     * @param body     texto ya preparado (vista previa de un mensaje, etc.)
     */
    public void notify(ServerPlayer target, Payloads.NotifyKind kind, @Nullable UUID source, String titleKey, Object[] args,
                       String body, String conversation) {
        String title = Lang.format(target.clientInformation().language(), titleKey, args);
        if (social.hasMod(target.getUUID())) {
            social.send(target, new Payloads.NotifyS2C(kind, source, title, body, conversation));
            return;
        }
        if (kind == Payloads.NotifyKind.PRIVATE || kind == Payloads.NotifyKind.GROUP) {
            return; // el propio mensaje ya llega al chat
        }
        if (target.getChatVisibility() == ChatVisiblity.HIDDEN) {
            return;
        }
        Component line = Lang.tr(target, titleKey, args).withStyle(ChatFormatting.GOLD);
        if (!body.isEmpty()) {
            line = line.copy().append(Component.literal(": " + body).withStyle(ChatFormatting.WHITE));
        }
        if ("chat".equalsIgnoreCase(ServerConfig.get().formats.vanillaNotifications)) {
            target.sendSystemMessage(line);
        } else {
            target.sendOverlayMessage(line);
        }
    }

    /** Notificación de sistema enviada por otro mod a través de la API. */
    public void system(ServerPlayer target, Notification notification) {
        if (social.hasMod(target.getUUID())) {
            social.send(target, new Payloads.NotifyS2C(Payloads.NotifyKind.SYSTEM, notification.source(),
                    notification.title().getString(), notification.body().getString(), ""));
            return;
        }
        Component line = notification.title().copy().withStyle(ChatFormatting.GOLD).append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(notification.body());
        if ("chat".equalsIgnoreCase(ServerConfig.get().formats.vanillaNotifications)) {
            target.sendSystemMessage(line);
        } else {
            target.sendOverlayMessage(line);
        }
    }

    /** Mensaje de respuesta a una acción (éxito o error), siempre en el chat. */
    public void feedback(ServerPlayer target, boolean ok, String key, Object... args) {
        target.sendSystemMessage(Lang.tr(target, key, args).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}
