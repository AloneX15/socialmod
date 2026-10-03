package com.takumistudios.socialmod.client.compat;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.client.ClientConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Compatibilidad con Xaero's Minimap y Xaero's World Map (PLAN 12), sin dependencia de compilación: todo por
 * reflexión y dentro de try/catch. Si algo falla (otra versión de Xaero con clases cambiadas), la integración se
 * desactiva para el resto de la sesión y SocialMod sigue funcionando con su comportamiento normal.
 * <ul>
 *     <li>Waypoints: el World Map no tiene waypoints propios, muestra los del Minimap. Por eso se añaden al conjunto
 *     actual del Minimap ({@code BuiltInHudModules.MINIMAP → MinimapSession → MinimapWorld → WaypointSet}) y se
 *     guardan con {@code MinimapWorldManagerIO.saveWorld}; aparecen en el minimapa y en el mapa completo.</li>
 *     <li>Posición del minimapa ({@code ModuleTransform} y {@code MinimapSession.getWidth}) para que los toasts y el
 *     HUD no lo tapen.</li>
 * </ul>
 * Comprobado con Xaero's Minimap 26.5.0/26.5.1/26.5.3 y World Map 1.46.0/1.46.1/1.46.4 (MC 26.1.2, 26.2 y 26.3).
 */
public final class MapCompat {
    private static final boolean MINIMAP = FabricLoader.getInstance().isModLoaded("xaerominimap")
            || FabricLoader.getInstance().isModLoaded("xaerominimapfair");
    private static final boolean WORLD_MAP = FabricLoader.getInstance().isModLoaded("xaeroworldmap");
    private static boolean broken;

    /** Esquina y tamaño (en píxeles escalados de la GUI) que ocupa el minimapa. */
    public record MinimapArea(boolean right, boolean bottom, int width, int height) {
    }

    private MapCompat() {
    }

    /** Cómo crear un waypoint en un mapa que se registra él mismo (JourneyMap, por su plugin). */
    @FunctionalInterface
    public interface WaypointSink {
        boolean add(String name, String dimension, int x, int y, int z, int rgb);
    }

    private static volatile @Nullable WaypointSink journeyMap;

    /** Lo llama {@link JourneyMapIntegration} cuando JourneyMap inicializa el plugin. */
    public static void registerJourneyMap(WaypointSink sink) {
        journeyMap = sink;
    }

    /** ¿Hay un mapa de Xaero instalado? (para textos y avisos). */
    public static boolean xaeroPresent() {
        return MINIMAP || WORLD_MAP;
    }

    /** ¿Se pueden crear waypoints? Xaero's Minimap (el World Map solo los muestra) o JourneyMap. */
    public static boolean waypointsAvailable() {
        return ((MINIMAP && !broken) || journeyMap != null) && ClientConfig.get().maps.waypoints;
    }

    /**
     * Crea un waypoint permanente: primero en Xaero's Minimap y, si no está o no se pudo, en JourneyMap.
     * @return {@code false} si no se pudo en ninguno: el que llama hace fallback (copiar las coordenadas).
     */
    public static boolean addWaypoint(String name, String dimension, int x, int y, int z, int rgb) {
        if (!ClientConfig.get().maps.waypoints) {
            return false;
        }
        if (MINIMAP && !broken && addXaeroWaypoint(name, dimension, x, y, z, rgb)) {
            return true;
        }
        WaypointSink sink = journeyMap;
        if (sink != null) {
            try {
                return sink.add(name, dimension, x, y, z, rgb);
            } catch (RuntimeException | LinkageError e) {
                journeyMap = null;
                SocialMod.warnOnce("journeymap_compat", "Integración con JourneyMap desactivada (versión incompatible)", e);
            }
        }
        return false;
    }

    private static @Nullable Object session() throws ReflectiveOperationException {
        Class<?> modules = Class.forName("xaero.hud.minimap.BuiltInHudModules");
        Object module = modules.getField("MINIMAP").get(null);
        return module.getClass().getMethod("getCurrentSession").invoke(module);
    }

    /** Waypoint en el conjunto actual de Xaero's Minimap; solo en la dimensión actual (el "mundo" de Xaero es por dimensión). */
    private static boolean addXaeroWaypoint(String name, String dimension, int x, int y, int z, int rgb) {
        Minecraft minecraft = Minecraft.getInstance();
        // El "mundo" actual del minimapa es por dimensión: solo se añade si las coordenadas son de la dimensión actual
        if (minecraft.level == null || !minecraft.level.dimension().identifier().toString().equals(dimension)) {
            return false;
        }
        try {
            Object session = session();
            if (session == null) {
                return false;
            }
            Object worldManager = session.getClass().getMethod("getWorldManager").invoke(session);
            Object world = worldManager.getClass().getMethod("getCurrentWorld").invoke(worldManager);
            if (world == null) {
                return false;
            }
            Object set = world.getClass().getMethod("getCurrentWaypointSet").invoke(world);
            if (set == null) {
                return false;
            }
            Class<?> waypointClass = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            Constructor<?> constructor = waypointClass.getConstructor(int.class, int.class, int.class, String.class, String.class,
                    int.class, int.class, boolean.class);
            String initials = name.isEmpty() ? "S" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            Object waypoint = constructor.newInstance(x, y, z, name, initials, nearestColorIndex(rgb), 0, false);
            set.getClass().getMethod("add", waypointClass).invoke(set, waypoint);
            Object io = session.getClass().getMethod("getWorldManagerIO").invoke(session);
            Method save = io.getClass().getMethod("saveWorld", world.getClass());
            save.invoke(io, world);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            disable(e);
            return false;
        }
    }

    /** Zona que ocupa el minimapa, o {@code null} si no hay minimapa visible o no se puede leer. */
    public static @Nullable MinimapArea minimapArea() {
        if (!MINIMAP || broken) {
            return null;
        }
        try {
            Object session = session();
            if (session == null) {
                return null;
            }
            Class<?> modules = Class.forName("xaero.hud.minimap.BuiltInHudModules");
            Object module = modules.getField("MINIMAP").get(null);
            Object transform = module.getClass().getMethod("getUsedTransform").invoke(module);
            boolean right = transform.getClass().getField("fromRight").getBoolean(transform);
            boolean bottom = transform.getClass().getField("fromBottom").getBoolean(transform);
            int offsetX = Math.abs(transform.getClass().getField("x").getInt(transform));
            int offsetY = Math.abs(transform.getClass().getField("y").getInt(transform));
            double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            int size = (int) session.getClass().getMethod("getWidth", double.class).invoke(session, guiScale);
            // Margen extra: el minimapa dibuja coordenadas, bioma, etc. debajo del mapa
            return new MinimapArea(right, bottom, size + offsetX + 4, size + offsetY + 40);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            disable(e);
            return null;
        }
    }

    /**
     * Primera vez con el minimapa instalado: si comparte esquina con los toasts o el HUD social, se apartan
     * (se guarda en client.json y el jugador lo puede cambiar luego en Ajustes). Devuelve {@code true} si terminó.
     */
    public static boolean applyAutoMargins() {
        ClientConfig config = ClientConfig.get();
        if (!config.maps.autoMargins || config.maps.autoMarginsApplied || !MINIMAP) {
            return true;
        }
        MinimapArea area = minimapArea();
        if (area == null) {
            return broken; // sin sesión todavía: se vuelve a intentar
        }
        if (config.toasts.position.right() == area.right() && config.toasts.position.bottom() == area.bottom()) {
            config.toasts.marginY = Math.max(config.toasts.marginY, area.height());
        }
        if (config.hud.position.right() == area.right() && config.hud.position.bottom() == area.bottom()) {
            config.hud.offsetY = Math.max(config.hud.offsetY, area.height());
        }
        if (area.right()) {
            config.panel.reservedRight = Math.max(config.panel.reservedRight, Math.min(160, area.width()));
        }
        config.maps.autoMarginsApplied = true;
        ClientConfig.save();
        SocialMod.LOGGER.info("[SocialMod] Xaero's Minimap detectado: márgenes ajustados para no taparlo");
        return true;
    }

    /** Índice de {@code WaypointColor} (los 16 colores de chat, mismo orden) más cercano al color del grupo. */
    static int nearestColorIndex(int rgb) {
        int[] palette = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
                0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
        int best = 10;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            long dr = ((rgb >> 16) & 0xFF) - ((palette[i] >> 16) & 0xFF);
            long dg = ((rgb >> 8) & 0xFF) - ((palette[i] >> 8) & 0xFF);
            long db = (rgb & 0xFF) - (palette[i] & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static void disable(Throwable e) {
        broken = true;
        SocialMod.warnOnce("xaero_compat", "Integración con Xaero desactivada (versión incompatible)", e);
    }
}
