package com.takumistudios.socialmod.server.security;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Cubo de fichas por jugador para todos los paquetes C→S (PLAN 4.3). Con {@code perSecond} fichas por
 * segundo y una ráfaga máxima de {@code burst}. Los paquetes que exceden el límite se descartan.
 */
public final class RateLimiter {
    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile double perSecond;
    private volatile double burst;

    private static final class Bucket {
        double tokens;
        long last;
    }

    public RateLimiter(double perSecond, double burst) {
        this(perSecond, burst, System::currentTimeMillis);
    }

    public RateLimiter(double perSecond, double burst, LongSupplier clock) {
        this.perSecond = perSecond;
        this.burst = burst;
        this.clock = clock;
    }

    public void reconfigure(double perSecond, double burst) {
        this.perSecond = perSecond;
        this.burst = burst;
    }

    public boolean tryAcquire(UUID player) {
        return tryAcquire(player, 1.0);
    }

    public boolean tryAcquire(UUID player, double cost) {
        long now = clock.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(player, id -> {
            Bucket created = new Bucket();
            created.tokens = burst;
            created.last = now;
            return created;
        });
        synchronized (bucket) {
            double elapsed = Math.max(0, now - bucket.last) / 1000.0;
            bucket.tokens = Math.min(burst, bucket.tokens + elapsed * perSecond);
            bucket.last = now;
            if (bucket.tokens >= cost) {
                bucket.tokens -= cost;
                return true;
            }
            return false;
        }
    }

    public void forget(UUID player) {
        buckets.remove(player);
    }
}
