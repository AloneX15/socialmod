package com.takumistudios.socialmod.server.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Anti-spam de mensajes (PLAN 9): límite de mensajes en una ventana de tiempo, detección de mensajes
 * repetidos y silencio automático temporal tras varias infracciones.
 */
public final class SpamGuard {
    public enum Verdict {
        OK, TOO_FAST, REPEATED, AUTO_MUTED
    }

    public record Settings(int maxMessages, long windowMillis, int maxRepeats, long repeatWindowMillis,
                           int strikesToMute, long autoMuteMillis) {
    }

    private static final class History {
        final Deque<Long> times = new ArrayDeque<>();
        String lastText = "";
        long lastTextTime;
        int repeats;
        int strikes;
        long strikeWindowStart;
    }

    private final Map<UUID, History> histories = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile Settings settings;

    public SpamGuard(Settings settings) {
        this(settings, System::currentTimeMillis);
    }

    public SpamGuard(Settings settings, LongSupplier clock) {
        this.settings = settings;
        this.clock = clock;
    }

    public void reconfigure(Settings settings) {
        this.settings = settings;
    }

    /**
     * Comprueba un mensaje. {@link Verdict#AUTO_MUTED} indica que se superaron las infracciones y el llamador
     * debe silenciar al jugador {@link Settings#autoMuteMillis()} milisegundos.
     */
    public Verdict check(UUID player, String text) {
        Settings s = settings;
        long now = clock.getAsLong();
        History history = histories.computeIfAbsent(player, id -> new History());
        synchronized (history) {
            while (!history.times.isEmpty() && now - history.times.peekFirst() > s.windowMillis()) {
                history.times.pollFirst();
            }
            Verdict verdict = Verdict.OK;
            if (s.maxMessages() > 0 && history.times.size() >= s.maxMessages()) {
                verdict = Verdict.TOO_FAST;
            } else {
                String normalized = text.trim().toLowerCase(Locale.ROOT);
                if (normalized.equals(history.lastText) && now - history.lastTextTime < s.repeatWindowMillis()) {
                    history.repeats++;
                    if (s.maxRepeats() > 0 && history.repeats >= s.maxRepeats()) {
                        verdict = Verdict.REPEATED;
                    }
                } else {
                    history.repeats = 0;
                }
                if (verdict == Verdict.OK) {
                    history.lastText = normalized;
                    history.lastTextTime = now;
                    history.times.addLast(now);
                }
            }
            if (verdict == Verdict.OK) {
                return verdict;
            }
            // Las infracciones caducan al minuto
            if (now - history.strikeWindowStart > 60_000) {
                history.strikes = 0;
                history.strikeWindowStart = now;
            }
            history.strikes++;
            if (s.strikesToMute() > 0 && history.strikes >= s.strikesToMute()) {
                history.strikes = 0;
                history.times.clear();
                return Verdict.AUTO_MUTED;
            }
            return verdict;
        }
    }

    public void forget(UUID player) {
        histories.remove(player);
    }
}
