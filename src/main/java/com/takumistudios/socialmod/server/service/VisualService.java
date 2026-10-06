package com.takumistudios.socialmod.server.service;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.common.model.VisualDesign;
import com.takumistudios.socialmod.server.SocialServer;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Server-authoritative visual preset. Failed loads retain the last valid design. */
public final class VisualService {
    private final SocialServer social;
    private VisualDesign design = new VisualDesign();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("socialmod/visual.json");
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "SocialMod-Visual-IO"); t.setDaemon(true); return t; });
    public VisualService(SocialServer social) { this.social = social; reload(); }
    private static final java.util.UUID PACK_ID = java.util.UUID.nameUUIDFromBytes("socialmod:series".getBytes(StandardCharsets.UTF_8));
    public VisualDesign design() { return design; }
    public void sendPack(net.minecraft.server.level.ServerPlayer player) {
        if (!design.resourcePackUrl.isEmpty()) player.connection.send(new net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket(
                PACK_ID, design.resourcePackUrl, design.resourcePackSha1, true,
                java.util.Optional.of(net.minecraft.network.chat.Component.translatable("socialmod.visual.pack_required"))));
    }
    public void reload() {
        try { if (Files.exists(file)) design = VisualDesign.parse(Files.readString(file)); }
        catch (Exception e) {
            SocialMod.LOGGER.warn("No se pudo cargar el preset visual; se conserva el anterior", e);
            try { Files.copy(file, file.resolveSibling("visual.json.bak"), StandardCopyOption.REPLACE_EXISTING); }
            catch (java.io.IOException backupError) { SocialMod.LOGGER.warn("No se pudo respaldar el preset visual", backupError); }
        }
    }
    public void reloadAsync() {
        io.execute(() -> {
            try {
                if (!Files.exists(file)) return;
                VisualDesign next = VisualDesign.parse(Files.readString(file));
                social.server().execute(() -> {
                    boolean packChanged = !design.resourcePackUrl.equals(next.resourcePackUrl) || !design.resourcePackSha1.equals(next.resourcePackSha1);
                    design = next;
                    for (var player : social.server().getPlayerList().getPlayers()) {
                        if (packChanged) { player.connection.send(new net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket(java.util.Optional.of(PACK_ID))); sendPack(player); }
                        social.snapshots().send(player);
                    }
                });
            } catch (Exception e) { SocialMod.LOGGER.warn("No se pudo recargar el preset visual; se conserva el anterior", e); }
        });
    }
    private final java.util.Map<java.util.UUID, Upload> uploads = new java.util.HashMap<>();
    private static final class Upload {
        String id; int total, next; long expires; final StringBuilder json = new StringBuilder();
    }
    public void forget(java.util.UUID player) { uploads.remove(player); }
    /** Bounded ordered chunks keep each C2S packet below Minecraft's 32767-byte limit. */
    public void receive(net.minecraft.server.level.ServerPlayer player, String header, String chunk) {
        String[] parts = header.split("/", -1); if (parts.length != 3 || chunk.length() > 4096) return;
        int index, total;
        try { java.util.UUID.fromString(parts[0]); index = Integer.parseInt(parts[1]); total = Integer.parseInt(parts[2]); }
        catch (IllegalArgumentException e) { return; }
        if (total < 1 || total > 17 || index < 0 || index >= total) return;
        Upload upload = uploads.get(player.getUUID());
        if (index == 0) {
            upload = new Upload(); upload.id = parts[0]; upload.total = total; upload.expires = System.currentTimeMillis() + 30000;
            uploads.put(player.getUUID(), upload);
        }
        if (upload == null || upload.expires < System.currentTimeMillis() || !upload.id.equals(parts[0]) || upload.total != total || upload.next != index || upload.json.length() + chunk.length() > 65536) {
            uploads.remove(player.getUUID()); return;
        }
        upload.json.append(chunk); upload.next++;
        if (upload.next == total) {
            uploads.remove(player.getUUID());
            try { publish(upload.json.toString(), player.getGameProfile().name()); social.notifier().feedback(player, true, "socialmod.visual.saved"); }
            catch (IllegalArgumentException e) { social.notifier().feedback(player, false, "socialmod.visual.invalid"); }
        }
    }
    public void rollback(String actor) {
        io.execute(() -> {
            try {
                Path previous = file.resolveSibling("visual.previous.json");
                if (!Files.exists(previous)) return;
                String json = Files.readString(previous);
                VisualDesign.parse(json);
                social.server().execute(() -> publish(json, actor + " rollback"));
            } catch (Exception e) { SocialMod.LOGGER.warn("No se pudo restaurar el preset visual", e); }
        });
    }
    public void publish(String json, String actor) {
        VisualDesign next = VisualDesign.parse(json);
        boolean packChanged = !next.resourcePackUrl.equals(design.resourcePackUrl) || !next.resourcePackSha1.equals(design.resourcePackSha1);
        String previous = VisualDesign.GSON.toJson(design);
        design = next;
        if (packChanged) for (var player : social.server().getPlayerList().getPlayers()) {
            player.connection.send(new net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket(java.util.Optional.of(PACK_ID)));
            sendPack(player);
        }
        String saved = VisualDesign.GSON.toJson(next);
        io.execute(() -> {
            try {
                VisualRevisionStore.save(file, previous, saved);
            } catch (Exception e) { SocialMod.LOGGER.error("No se pudo guardar el preset visual", e); }
        });
        social.storage().audit("VISUAL_PUBLISH " + actor);
        for (var player : social.server().getPlayerList().getPlayers()) social.snapshots().send(player);
    }
    public void persistConfig() { com.takumistudios.socialmod.server.config.ServerConfig.persist(io); }
    public void close() {
        io.shutdown();
        try { if (!io.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) SocialMod.LOGGER.warn("Guardado visual pendiente al cerrar"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); SocialMod.LOGGER.warn("Guardado visual interrumpido", e); }
    }
}
