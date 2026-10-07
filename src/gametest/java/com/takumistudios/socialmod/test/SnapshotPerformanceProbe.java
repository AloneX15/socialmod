package com.takumistudios.socialmod.test;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.storage.SocialStorage;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/** Measures the real recipient-specific snapshot path, not a synthetic DTO serializer. */
final class SnapshotPerformanceProbe {
    static void measure(SocialServer social, List<ServerPlayer> players) {
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        var allocation = bean instanceof com.sun.management.ThreadMXBean extended ? extended : null;
        if (allocation != null && allocation.isThreadAllocatedMemorySupported()) allocation.setThreadAllocatedMemoryEnabled(true);
        for (int count : List.of(50, 200)) {
            for (int i = 0; i < 10; i++) SocialStorage.gson().toJson(social.snapshots().build(players.get(i)));
            long thread = Thread.currentThread().threadId();
            long before = allocation == null ? 0 : allocation.getThreadAllocatedBytes(thread), start = System.nanoTime(), bytes = 0;
            for (int i = 0; i < count; i++) {
                String json = SocialStorage.gson().toJson(social.snapshots().build(players.get(i)));
                bytes += json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            }
            long elapsed = System.nanoTime() - start, allocated = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread) - before;
            SocialMod.LOGGER.info("[SocialMod] Snapshot benchmark: recipients={}, ms={}, allocatedBytes={}, jsonBytes={}", count, elapsed / 1_000_000.0, allocated, bytes);
            if (elapsed > 10_000_000_000L) throw new AssertionError("Snapshot batch exceeded 10 second CI budget");
        }
    }
}
