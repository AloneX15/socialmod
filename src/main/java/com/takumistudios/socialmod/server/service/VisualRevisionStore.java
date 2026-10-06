package com.takumistudios.socialmod.server.service;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

/** Ordered-IO-only atomic preset persistence with bounded revision history. */
public final class VisualRevisionStore {
    private VisualRevisionStore() { }
    public static void save(Path file, String previous, String saved) throws IOException {
        Files.createDirectories(file.getParent());
        Path history = file.getParent().resolve("visual-history"); Files.createDirectories(history);
        Files.writeString(history.resolve(java.util.UUID.randomUUID() + ".json"), previous, StandardCharsets.UTF_8);
        try (var paths = Files.list(history)) {
            var ordered = paths.filter(p -> p.getFileName().toString().endsWith(".json")).sorted(java.util.Comparator.comparingLong(p -> { try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0L; } })).toList();
            for (int i = 0; i < ordered.size() - 20; i++) Files.deleteIfExists(ordered.get(i));
        }
        atomic(file.resolveSibling("visual.previous.json"), previous);
        atomic(file, saved);
    }
    private static void atomic(Path file, String text) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
