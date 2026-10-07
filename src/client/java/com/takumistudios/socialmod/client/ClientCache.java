package com.takumistudios.socialmod.client;

import com.takumistudios.socialmod.SocialMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Caché local por servidor (PLAN 4.2): el último estado social (amigos, grupos, lista de conversaciones) en
 * {@code config/socialmod/cache/<hash del servidor>/snapshot.json}. Al entrar se carga antes de que responda el
 * servidor, así el panel abre con datos al instante; el snapshot del servidor lo sustituye en cuanto llega.
 * <p>No guarda el contenido de los mensajes, solo la vista previa de la última línea de cada conversación. La
 * escritura va en un hilo propio, se agrupa (como mucho una cada {@link #MIN_INTERVAL_MS} ms) y es atómica.
 * Con {@code cache.enabled = false} no se escribe nada y se borra lo guardado.</p>
 */
public final class ClientCache {
    private static final long MIN_INTERVAL_MS = 10_000;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SocialMod client cache");
        thread.setDaemon(true);
        return thread;
    });
    private static @Nullable Path current;
    private static @Nullable String pending;
    private static long lastWrite;

    private ClientCache() {
    }

    private static Path root() {
        return FabricLoader.getInstance().getConfigDir().resolve(SocialMod.MOD_ID).resolve("cache");
    }

    /** Identidad del servidor: dirección en multijugador, nombre del mundo en un jugador. */
    private static @Nullable String serverKey(Minecraft minecraft) {
        ServerData server = minecraft.getCurrentServer();
        if (server != null && server.ip != null) {
            return "mp:" + server.ip.toLowerCase(java.util.Locale.ROOT);
        }
        if (minecraft.getSingleplayerServer() != null) {
            return "sp:" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        }
        return null;
    }

    private static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(key.hashCode());
        }
    }

    /** Al entrar en un servidor: devuelve el snapshot guardado, si lo hay. */
    public static Optional<String> open(Minecraft minecraft) {
        flush();
        current = null;
        if (!ClientConfig.get().cache.enabled) {
            clearAll();
            return Optional.empty();
        }
        String key = serverKey(minecraft);
        if (key == null) {
            return Optional.empty();
        }
        current = root().resolve(hash(key)).resolve("snapshot.json");
        try {
            return Files.exists(current) && Files.size(current)<=com.takumistudios.socialmod.common.net.SnapshotSync.MAX_STATE ? Optional.of(Files.readString(current, StandardCharsets.UTF_8)) : Optional.empty();
        } catch (IOException e) {
            SocialMod.LOGGER.debug("[SocialMod] Caché no legible: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Nuevo snapshot del servidor: se guarda agrupado. */
    public static void store(String json) {
        if (current == null || !ClientConfig.get().cache.enabled) {
            return;
        }
        pending = json;
        if (System.currentTimeMillis() - lastWrite >= MIN_INTERVAL_MS) {
            flush();
        }
    }

    /** Escribe lo pendiente (también al salir del servidor). */
    public static void flush() {
        Path target = current;
        String json = pending;
        pending = null;
        if (target == null || json == null) {
            return;
        }
        lastWrite = System.currentTimeMillis();
        IO.execute(() -> {
            try {
                Files.createDirectories(target.getParent());
                Path temp = target.resolveSibling("snapshot.json.tmp");
                Files.writeString(temp, json, StandardCharsets.UTF_8);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                SocialMod.LOGGER.debug("[SocialMod] No se pudo guardar la caché: {}", e.getMessage());
            }
        });
    }

    /** Borra toda la caché (al desactivarla en los ajustes). */
    public static void clearAll() {
        pending = null;
        IO.execute(() -> {
            Path root = root();
            if (!Files.exists(root)) {
                return;
            }
            try (var files = Files.walk(root)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // se reintenta la próxima vez
                    }
                });
            } catch (IOException ignored) {
                // nada que borrar
            }
        });
    }
}
