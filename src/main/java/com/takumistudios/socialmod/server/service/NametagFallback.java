package com.takumistudios.socialmod.server.service;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.common.model.GroupIcon;
import com.takumistudios.socialmod.common.model.Role;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.Group;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Identidad de grupo junto al nombre (PLAN 11).
 * <ul>
 *     <li>Clientes con el mod: se les envía la etiqueta del grupo principal de cada jugador conectado ({@code TagsS2C})
 *     y el cliente la dibuja en el nametag.</li>
 *     <li>Clientes sin el mod: fallback opcional con un team del scoreboard {@code sm_<grupo>}. Desactivado por defecto
 *     porque un jugador solo puede estar en un team y choca con TAB y similares. Nunca toca teams ajenos.</li>
 * </ul>
 */
public final class NametagFallback {
    private static final String PREFIX = "sm_";

    private final SocialServer social;

    public NametagFallback(SocialServer social) {
        this.social = social;
    }

    public Payloads.TagEntry entryFor(UUID playerId) {
        Group group = social.teams().of(playerId);
        if (group == null) {
            return new Payloads.TagEntry(playerId, "", 0, "", "");
        }
        return new Payloads.TagEntry(playerId, group.name, group.color, group.icon, "");
    }

    /** Lista completa para un cliente que acaba de hacer el handshake. */
    public void sendAll(ServerPlayer viewer) {
        List<Payloads.TagEntry> entries = new ArrayList<>();
        for (ServerPlayer player : social.server().getPlayerList().getPlayers()) {
            Payloads.TagEntry entry = entryFor(player.getUUID());
            if (!entry.tag().isEmpty()) {
                entries.add(entry);
            }
        }
        social.sendModded(viewer, new Payloads.TagsS2C(true, entries));
    }

    public void update(UUID playerId) {
        ServerPlayer player = social.online(playerId);
        if (player == null) {
            return;
        }
        Payloads.TagsS2C delta = new Payloads.TagsS2C(false, List.of(entryFor(playerId)));
        for (ServerPlayer viewer : social.server().getPlayerList().getPlayers()) {
            social.sendModded(viewer, delta);
        }
        if (ServerConfig.get().nametags.scoreboardFallback) {
            updateTeam(player);
        }
    }

    private void updateTeam(ServerPlayer player) {
        try {
            Scoreboard scoreboard = social.server().getScoreboard();
            String entry = player.getScoreboardName();
            PlayerTeam current = scoreboard.getPlayersTeam(entry);
            if (current != null && !current.getName().startsWith(PREFIX)) {
                return; // el jugador está en un team de otro mod o plugin: no se toca
            }
            Group group = social.teams().of(player.getUUID());
            if (group == null) {
                if (current != null) {
                    scoreboard.removePlayerFromTeam(entry, current);
                }
                return;
            }
            String teamName = PREFIX + group.id;
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamName);
            }
            // Vanilla solo permite prefijo en la misma línea: los clientes con el mod lo ven debajo del nombre
            String glyph = GroupIcon.glyphOf(group.icon);
            team.setPlayerPrefix(Component.literal((glyph.isEmpty() ? "" : glyph + " ") + "[" + group.name + "] ").withColor(group.color));
            if (current != team) {
                scoreboard.addPlayerToTeam(entry, team);
            }
        } catch (RuntimeException e) {
            SocialMod.warnOnce("nametag_fallback", "No se pudo actualizar el team del scoreboard", e);
        }
    }
}
