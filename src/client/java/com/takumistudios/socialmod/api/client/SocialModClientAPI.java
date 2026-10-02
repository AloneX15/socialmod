package com.takumistudios.socialmod.api.client;

import com.takumistudios.socialmod.client.ClientApiImpl;

import java.util.UUID;

/**
 * API de cliente de SocialMod (PLAN 16). Usar solo desde el hilo del cliente.
 * Las aperturas de pantalla no hacen nada si el servidor no tiene SocialMod (modo solo chat).
 */
public interface SocialModClientAPI {
    static SocialModClientAPI get() {
        return ClientApiImpl.INSTANCE;
    }

    void openPanel();

    void openPrivateChat(UUID target);

    void openGroupChat(String groupId);

    void showToast(ToastData toast);

    /** {@code true} si el servidor actual tiene SocialMod con un protocolo compatible. */
    boolean isConnected();
}
