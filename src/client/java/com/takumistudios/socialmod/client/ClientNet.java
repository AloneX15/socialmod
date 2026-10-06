package com.takumistudios.socialmod.client;

import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.common.net.SocialAction;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Envío de intenciones al servidor. Sin servidor compatible no se envía nada (modo solo chat). */
public final class ClientNet {
    private ClientNet() {
    }

    public static boolean send(CustomPacketPayload payload) {
        if (!ClientState.get().connected() || !ClientPlayNetworking.canSend(payload.type())) {
            return false;
        }
        ClientPlayNetworking.send(payload);
        return true;
    }

    public static void message(String target, String text) {
        send(new Payloads.SendC2S(target, text));
    }

    public static void action(SocialAction action, String a) {
        action(action, a, "");
    }

    public static void action(SocialAction action, String a, String b) {
        if (action == SocialAction.VISUAL_PUBLISH) {
            if (b.length() > 65536) return;
            java.util.List<String> chunks = new java.util.ArrayList<>();
            for (int offset = 0; offset < b.length();) {
                int end = Math.min(b.length(), offset + 4096);
                if (end < b.length() && Character.isHighSurrogate(b.charAt(end - 1))) end--;
                chunks.add(b.substring(offset, end)); offset = end;
            }
            String upload = java.util.UUID.randomUUID().toString();
            for (int i = 0; i < chunks.size(); i++) send(new Payloads.ActionC2S(action, upload + "/" + i + "/" + chunks.size(), chunks.get(i)));
            return;
        }
        send(new Payloads.ActionC2S(action, a, b));
    }

    public static void history(String conversation, long beforeId) {
        send(new Payloads.HistoryC2S(conversation, beforeId));
    }

    public static void typing(String conversation) {
        send(new Payloads.SignalC2S(Payloads.Signal.TYPING, conversation, 0));
    }

    public static void read(String conversation, long upTo) {
        send(new Payloads.SignalC2S(Payloads.Signal.READ, conversation, upTo));
    }
}
