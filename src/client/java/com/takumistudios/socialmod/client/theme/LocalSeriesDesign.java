package com.takumistudios.socialmod.client.theme;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.common.model.VisualDesign;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.concurrent.*;

/** Optional appearance packaged with client layouts, independent of server publication. */
public final class LocalSeriesDesign {
    public static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("socialmod/integration/visual.json");
    private static volatile VisualDesign active;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r,"SocialMod-Series-Design-IO"); t.setDaemon(true); return t; });
    public static VisualDesign get() { return active; }
    public static void load() { IO.execute(() -> { if (!Files.isRegularFile(FILE)) return; try { if (Files.size(FILE) > 65_536) throw new IllegalArgumentException("Local design exceeds size limit"); active = VisualDesign.parse(Files.readString(FILE)); } catch(Exception e) {
                SocialMod.LOGGER.warn("Invalid local series appearance; keeping basic appearance",e);
                try { Files.move(FILE,FILE.resolveSibling("visual.json.bak"),StandardCopyOption.REPLACE_EXISTING); }
                catch(java.io.IOException backupError) { SocialMod.LOGGER.warn("Could not back up invalid local appearance",backupError); }
            } }); }
    public static CompletableFuture<Void> save(VisualDesign value) {
        value.validate(); var snapshot = value.copy();
        return CompletableFuture.runAsync(() -> { try { Files.createDirectories(FILE.getParent()); var tmp=FILE.resolveSibling("visual.json.tmp"); Files.writeString(tmp,VisualDesign.GSON.toJson(snapshot)); Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); active=snapshot; } catch(Exception e) { SocialMod.LOGGER.warn("Could not save local appearance",e); throw new CompletionException(e); } },IO);
    }
    public static void clear() { active = null; }
    public static CompletableFuture<Void> reset() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (Files.exists(FILE)) Files.move(FILE, FILE.resolveSibling("visual.json.disabled"), StandardCopyOption.REPLACE_EXISTING);
                active = null;
            } catch (java.io.IOException e) { throw new CompletionException(e); }
        }, IO);
    }
    private LocalSeriesDesign() { }
}
