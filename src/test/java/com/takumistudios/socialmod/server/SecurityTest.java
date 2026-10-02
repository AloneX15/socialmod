package com.takumistudios.socialmod.server;

import com.takumistudios.socialmod.server.security.RateLimiter;
import com.takumistudios.socialmod.server.security.SpamGuard;
import com.takumistudios.socialmod.server.security.WordFilter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityTest {
    private final AtomicLong clock = new AtomicLong(10_000);
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    void rateLimiterAllowsBurstThenRefills() {
        RateLimiter limiter = new RateLimiter(10, 20, clock::get);
        int accepted = 0;
        for (int i = 0; i < 1000; i++) {
            if (limiter.tryAcquire(PLAYER)) {
                accepted++;
            }
        }
        assertEquals(20, accepted, "solo la ráfaga");
        assertTrue(limiter.tryAcquire(OTHER), "cada jugador tiene su propio cubo");
        clock.addAndGet(500);
        int refilled = 0;
        while (limiter.tryAcquire(PLAYER)) {
            refilled++;
        }
        assertEquals(5, refilled, "10 por segundo durante medio segundo");
    }

    private SpamGuard guard() {
        return new SpamGuard(new SpamGuard.Settings(5, 4000, 3, 30_000, 3, 60_000), clock::get);
    }

    @Test
    void spamGuardLimitsSpeed() {
        SpamGuard guard = guard();
        for (int i = 0; i < 5; i++) {
            assertEquals(SpamGuard.Verdict.OK, guard.check(PLAYER, "mensaje " + i));
        }
        assertEquals(SpamGuard.Verdict.TOO_FAST, guard.check(PLAYER, "otro"));
        clock.addAndGet(5000);
        assertEquals(SpamGuard.Verdict.OK, guard.check(PLAYER, "ya puedo"));
    }

    @Test
    void spamGuardDetectsRepeatsAndAutoMutes() {
        SpamGuard guard = guard();
        assertEquals(SpamGuard.Verdict.OK, guard.check(PLAYER, "hola"));
        clock.addAndGet(1000);
        assertEquals(SpamGuard.Verdict.OK, guard.check(PLAYER, "HOLA"));
        clock.addAndGet(1000);
        assertEquals(SpamGuard.Verdict.OK, guard.check(PLAYER, "hola"));
        clock.addAndGet(1000);
        assertEquals(SpamGuard.Verdict.REPEATED, guard.check(PLAYER, "hola"));
        clock.addAndGet(1000);
        assertEquals(SpamGuard.Verdict.REPEATED, guard.check(PLAYER, "hola"));
        clock.addAndGet(1000);
        assertEquals(SpamGuard.Verdict.AUTO_MUTED, guard.check(PLAYER, "hola"), "tercera infracción: silencio automático");
    }

    @Test
    void wordFilterCensorsWholeWords() {
        WordFilter filter = new WordFilter(List.of("tonto"), List.of(), WordFilter.Mode.CENSOR);
        WordFilter.Result result = filter.apply("eres TONTO, pero no tontorrón");
        assertTrue(result.matched());
        assertEquals("eres *****, pero no tontorrón", result.text());
    }

    @Test
    void wordFilterBlocksAndIgnoresBadRegex() {
        WordFilter filter = new WordFilter(List.of(), List.of("disc(o|0)rd\\.gg", "([invalid"), WordFilter.Mode.BLOCK);
        assertEquals(1, filter.errors().size());
        assertTrue(filter.apply("únete a discord.gg/abc").blocked());
        assertFalse(filter.apply("hola").blocked());
    }
}
