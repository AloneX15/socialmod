package com.takumistudios.socialmod.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/** Eventos de mensajes de SocialMod. {@code conversation} es {@code dm:<a>:<b>} o {@code g:<grupo>:<canal>}. */
public final class ChatEvents {
    /** Antes de guardar y entregar un mensaje. Devolver {@code false} lo cancela. */
    public static final Event<AllowMessage> ALLOW_MESSAGE = EventFactory.createArrayBacked(AllowMessage.class,
            listeners -> (sender, conversation, text) -> {
                for (AllowMessage listener : listeners) {
                    if (!listener.allowMessage(sender, conversation, text)) {
                        return false;
                    }
                }
                return true;
            });

    /** Después de entregar un mensaje. */
    public static final Event<MessageSent> MESSAGE_SENT = EventFactory.createArrayBacked(MessageSent.class,
            listeners -> (sender, conversation, text) -> {
                for (MessageSent listener : listeners) {
                    listener.onMessageSent(sender, conversation, text);
                }
            });

    private ChatEvents() {
    }

    @FunctionalInterface
    public interface AllowMessage {
        boolean allowMessage(ServerPlayer sender, String conversation, String text);
    }

    @FunctionalInterface
    public interface MessageSent {
        void onMessageSent(ServerPlayer sender, String conversation, String text);
    }
}
