package com.takumistudios.socialmod.server.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Historial reciente de una conversación. Se carga bajo demanda y se recorta según la retención
 * configurada (por defecto 30 días o los últimos 500 mensajes).
 */
public final class Conversation {
    public String id;
    public long nextId = 1;
    /** Mensajes en orden cronológico (el más antiguo primero). */
    public List<ChatMessage> messages = new ArrayList<>();

    public Conversation() {
    }

    public Conversation(String id) {
        this.id = id;
    }

    public ChatMessage find(long messageId) {
        // Los ids son crecientes: búsqueda binaria
        int low = 0;
        int high = messages.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long current = messages.get(mid).id;
            if (current < messageId) {
                low = mid + 1;
            } else if (current > messageId) {
                high = mid - 1;
            } else {
                return messages.get(mid);
            }
        }
        return null;
    }

    public ChatMessage last() {
        return messages.isEmpty() ? null : messages.getLast();
    }

    /** Mensajes con id menor que {@code beforeId} (0 = desde el final), del más antiguo al más nuevo. */
    public List<ChatMessage> page(long beforeId, int limit) {
        int end = messages.size();
        if (beforeId > 0) {
            end = 0;
            while (end < messages.size() && messages.get(end).id < beforeId) {
                end++;
            }
        }
        int start = Math.max(0, end - limit);
        return new ArrayList<>(messages.subList(start, end));
    }

    /** Aplica la retención. Devuelve {@code true} si se borró algo. */
    public boolean applyRetention(int maxMessages, long minTime) {
        int before = messages.size();
        if (minTime > 0) {
            messages.removeIf(m -> m.time < minTime);
        }
        if (maxMessages > 0 && messages.size() > maxMessages) {
            messages.subList(0, messages.size() - maxMessages).clear();
        }
        return messages.size() != before;
    }

    public Conversation normalize() {
        if (messages == null) messages = new ArrayList<>();
        messages.removeIf(m -> m == null);
        messages.forEach(ChatMessage::normalize);
        messages.sort((x, y) -> Long.compare(x.id, y.id));
        long max = messages.isEmpty() ? 0 : messages.getLast().id;
        if (nextId <= max) nextId = max + 1;
        return this;
    }
}
