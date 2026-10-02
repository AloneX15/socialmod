package com.takumistudios.socialmod.server.data;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Un mensaje guardado en el historial de una conversación. */
public final class ChatMessage {
    public long id;
    public UUID sender;
    public String senderName = "";
    /** Texto ya saneado (markdown seguro como texto plano; nunca JSON de componentes). */
    public String text = "";
    public long time;
    public long editedAt;
    public boolean deleted;
    public List<Attachment> attachments = new ArrayList<>();

    /** Contenido generado por el servidor ({@code [item]} o {@code [coords]}); el cliente no puede falsificarlo. */
    public static final class Attachment {
        public static final String ITEM = "item";
        public static final String COORDS = "coords";

        public String kind;
        /** Ítem codificado con el codec de {@code ItemStackTemplate}. */
        public JsonElement item;
        public String dimension = "";
        public int x;
        public int y;
        public int z;

        public static Attachment coords(String dimension, int x, int y, int z) {
            Attachment attachment = new Attachment();
            attachment.kind = COORDS;
            attachment.dimension = dimension;
            attachment.x = x;
            attachment.y = y;
            attachment.z = z;
            return attachment;
        }

        public static Attachment item(JsonElement encoded) {
            Attachment attachment = new Attachment();
            attachment.kind = ITEM;
            attachment.item = encoded;
            return attachment;
        }
    }

    public ChatMessage() {
    }

    public ChatMessage(long id, UUID sender, String senderName, String text, long time) {
        this.id = id;
        this.sender = sender;
        this.senderName = senderName;
        this.text = text;
        this.time = time;
    }

    public ChatMessage normalize() {
        if (senderName == null) senderName = "";
        if (text == null) text = "";
        if (attachments == null) attachments = new ArrayList<>();
        attachments.removeIf(a -> a == null || a.kind == null);
        return this;
    }
}
