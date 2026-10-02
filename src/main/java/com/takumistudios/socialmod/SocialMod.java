package com.takumistudios.socialmod;

import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.server.Lang;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.command.SocialCommands;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.net.ServerNet;
import com.takumistudios.socialmod.server.service.DatapackFilters;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Punto de entrada común. Todo el estado del servidor vive en {@link SocialServer}; cada evento va protegido con
 * try/catch para que un error de SocialMod nunca tumbe el servidor (PLAN 14).
 */
public final class SocialMod implements ModInitializer {
    public static final String MOD_ID = "socialmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitialize() {
        ServerConfig.load();
        Lang.load();
        Payloads.register();
        ServerNet.register();
        CommandRegistrationCallback.EVENT.register(SocialCommands::register);
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(DatapackFilters.ID, new DatapackFilters());

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                SocialServer.start(server);
            } catch (RuntimeException e) {
                LOGGER.error("[SocialMod] No se pudo iniciar; el servidor sigue sin SocialMod", e);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SocialServer social = SocialServer.get();
            if (social != null) {
                try {
                    social.stop();
                } catch (RuntimeException e) {
                    LOGGER.error("[SocialMod] Error al guardar los datos", e);
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            SocialServer social = SocialServer.get();
            if (social != null) {
                try {
                    social.tick();
                } catch (RuntimeException e) {
                    warnOnce("tick", "Error en el tick de SocialMod", e);
                }
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SocialServer social = SocialServer.get();
            if (social != null) {
                try {
                    social.onJoin(handler.getPlayer());
                } catch (RuntimeException e) {
                    warnOnce("join", "Error al procesar la conexión de un jugador", e);
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SocialServer social = SocialServer.get();
            if (social != null) {
                try {
                    social.onLeave(handler.getPlayer());
                } catch (RuntimeException e) {
                    warnOnce("leave", "Error al procesar la desconexión de un jugador", e);
                }
            }
        });

        if (FabricLoader.getInstance().isModLoaded("placeholder-api") && ServerConfig.get().modules.placeholders) {
            try {
                com.takumistudios.socialmod.server.integration.PlaceholderIntegration.register();
            } catch (RuntimeException | LinkageError e) {
                warnOnce("placeholders", "No se pudo activar la integración con Text Placeholder API", e);
            }
        }
        LOGGER.info("SocialMod inicializado (protocolo {})", Payloads.PROTOCOL_VERSION);
    }

    /** Registra un aviso una sola vez por clave (sin llenar el log). */
    public static void warnOnce(String key, String message, Throwable error) {
        if (WARNED.add(key)) {
            LOGGER.warn("[SocialMod] {} (este aviso no se repetirá)", message, error);
        }
    }
}
