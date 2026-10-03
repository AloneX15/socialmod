package com.takumistudios.socialmod.server.integration;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.common.model.Role;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.Group;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Sincronización de grupos con claims (PLAN 12). Solo Open Parties and Claims tiene versión para 26.x; FTB Chunks y
 * Cadmus quedan pendientes (ver la wiki). Esta clase no usa la API de OPAC: delega en {@code OpacClaimsBackend},
 * que solo se carga si el mod está instalado.
 */
public final class ClaimsSync {
    private static final int INTERVAL_TICKS = 200;

    private final SocialServer social;
    private final boolean opac = FabricLoader.getInstance().isModLoaded("openpartiesandclaims");
    private OpacClaimsBackend backend;
    private boolean broken;
    private int ticks;

    public ClaimsSync(SocialServer social) {
        this.social = social;
    }

    public boolean available() {
        return opac && !broken;
    }

    public void tick() {
        if (++ticks % INTERVAL_TICKS != 0) {
            return;
        }
        syncAll();
    }

    /** Recorre los grupos enlazados (también se llama al enlazar, para no esperar 10 s). */
    public void syncAll() {
        String mode = ServerConfig.get().integrations.claimsSync;
        if (!available() || mode.equals("off")) {
            return;
        }
        try {
            if (backend == null) {
                backend = new OpacClaimsBackend(social);
            }
            // Copia: la sincronización puede sacar miembros y, en el límite, disolver grupos
            for (Group group : new java.util.ArrayList<>(social.groups().all().values())) {
                if (group.claimsLink && !group.party) {
                    backend.sync(group, mode.equals("both"));
                }
            }
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            SocialMod.warnOnce("claims_sync", "Sincronización con Open Parties and Claims desactivada (versión incompatible)", e);
        }
    }

    /** Rango de OPAC que corresponde a un rol de SocialMod. */
    static String rankFor(Role role) {
        return role == Role.OFFICER || role == Role.LEADER ? "MODERATOR" : "MEMBER";
    }
}
