package com.takumistudios.socialmod.server.service;

import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.Group;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Funciones de la party en el mundo:
 * <ul>
 *     <li>Vida de los compañeros en el HUD (PLAN 5.2): cada 10 ticks se calcula el estado de cada party con alguien
 *     conectado con el mod y solo se envía a quien le cambió (vida redondeada a medio corazón).</li>
 *     <li>Ping en el mundo (PLAN 5.5): un punto que los compañeros ven 10 s. Se valida dimensión, distancia (como
 *     mucho {@link #MAX_PING_DISTANCE} bloques) y frecuencia (uno cada {@link #PING_COOLDOWN_MS} ms). Los compañeros sin
 *     el mod reciben un mensaje de chat con las coordenadas.</li>
 * </ul>
 */
public final class PartyService {
    public static final int MAX_PING_DISTANCE = 256;
    public static final long PING_COOLDOWN_MS = 1500;
    private static final int INTERVAL_TICKS = 10;

    private final SocialServer social;
    /** Último estado enviado a cada jugador, para enviar solo cambios. */
    private final Map<UUID, List<Payloads.PartyMember>> lastSent = new HashMap<>();
    private final Map<UUID, Long> lastPing = new HashMap<>();
    private int ticks;

    public PartyService(SocialServer social) {
        this.social = social;
    }

    public void tick() {
        if (++ticks % INTERVAL_TICKS != 0) {
            return;
        }
        ServerConfig.Modules modules = ServerConfig.get().modules;
        if (!modules.parties || !modules.partyHud) {
            return;
        }
        lastSent.keySet().removeIf(id -> social.online(id) == null);
        for (ServerPlayer viewer : social.server().getPlayerList().getPlayers()) {
            if (!social.hasMod(viewer.getUUID())) {
                continue;
            }
            Group party = social.groups().partyOf(viewer.getUUID());
            List<Payloads.PartyMember> members = party == null ? List.of() : members(party, viewer);
            if (!Objects.equals(lastSent.get(viewer.getUUID()), members)) {
                lastSent.put(viewer.getUUID(), members);
                social.sendModded(viewer, new Payloads.PartyS2C(members));
            }
        }
    }

    private List<Payloads.PartyMember> members(Group party, ServerPlayer viewer) {
        List<Payloads.PartyMember> result = new ArrayList<>();
        for (UUID id : party.members.keySet()) {
            if (id.equals(viewer.getUUID())) {
                continue;
            }
            ServerPlayer member = social.online(id);
            String name = party.memberNames.getOrDefault(id, "?");
            if (member == null) {
                continue;
            }
            boolean visible = member.isAlive() && member.level() == viewer.level();
            // Medio corazón de resolución: menos paquetes y no da más información que la barra de vida vanilla
            float health = visible ? Math.round(member.getHealth()) : -1;
            result.add(new Payloads.PartyMember(id, name, health, Math.round(member.getMaxHealth())));
            if (result.size() >= 8) {
                break;
            }
        }
        return result;
    }

    public void forget(UUID player) {
        lastSent.remove(player);
        lastPing.remove(player);
    }

    public void ping(ServerPlayer player, int x, int y, int z) {
        ServerConfig.Modules modules = ServerConfig.get().modules;
        if (!modules.parties || !modules.ping) {
            social.notifier().feedback(player, false, "socialmod.error.module_disabled");
            return;
        }
        Group party = social.groups().partyOf(player.getUUID());
        if (party == null) {
            social.notifier().feedback(player, false, "socialmod.party.none");
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastPing.get(player.getUUID());
        if (previous != null && now - previous < PING_COOLDOWN_MS) {
            return;
        }
        double dx = x + 0.5 - player.getX();
        double dy = y + 0.5 - player.getY();
        double dz = z + 0.5 - player.getZ();
        if (dx * dx + dy * dy + dz * dz > (double) MAX_PING_DISTANCE * MAX_PING_DISTANCE) {
            return;
        }
        lastPing.put(player.getUUID(), now);
        String dimension = player.level().dimension().identifier().toString();
        String name = player.getGameProfile().name();
        Payloads.PingS2C ping = new Payloads.PingS2C(player.getUUID(), name, dimension, x, y, z, party.color);
        for (UUID id : party.members.keySet()) {
            ServerPlayer member = social.online(id);
            if (member == null || member.level() != player.level()) {
                continue;
            }
            if (id.equals(player.getUUID()) || social.hasMod(id)) {
                social.sendModded(member, ping);
            } else if (!social.friends().blockedEitherWay(id, player.getUUID())) {
                member.sendSystemMessage(vanillaPing(name, x, y, z));
            }
        }
    }

    private static Component vanillaPing(String name, int x, int y, int z) {
        String coords = x + " " + y + " " + z;
        return Component.literal("[Party] ").withStyle(ChatFormatting.BLUE)
                .append(Component.translatableWithFallback("socialmod.ping.chat", "%s marked a point: %s", name,
                        Component.literal("[" + coords + "]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                                .withClickEvent(new ClickEvent.CopyToClipboard(coords))
                                .withHoverEvent(new HoverEvent.ShowText(Component.translatableWithFallback(
                                        "socialmod.coords.hover", "Click to copy the coordinates")))))
                        .withStyle(ChatFormatting.GRAY));
    }
}
