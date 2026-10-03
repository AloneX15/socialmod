package com.takumistudios.socialmod.client.compat;

import com.takumistudios.socialmod.SocialMod;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Plugin de JourneyMap (API v2): JourneyMap lo carga por el entrypoint {@code journeymap} solo si está instalado y
 * registra en {@link MapCompat} cómo crear waypoints. Ninguna otra clase de SocialMod referencia la API de JourneyMap.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class JourneyMapIntegration implements IClientPlugin {
    @Override
    public String getModId() {
        return SocialMod.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI api) {
        MapCompat.registerJourneyMap((name, dimension, x, y, z, rgb) -> {
            ResourceKey<net.minecraft.world.level.Level> level = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension));
            Waypoint waypoint = WaypointFactory.createWaypoint(SocialMod.MOD_ID, new BlockPos(x, y, z), name, level, true);
            waypoint.setName(name);
            waypoint.setColor(rgb & 0xFFFFFF);
            api.addWaypoint(SocialMod.MOD_ID, waypoint);
            return true;
        });
        SocialMod.LOGGER.info("[SocialMod] JourneyMap detectado: waypoints activados");
    }
}
