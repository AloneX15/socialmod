package com.takumistudios.socialmod.server.integration;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.common.model.Role;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.Group;
import net.minecraft.server.players.NameAndId;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Enlace grupo de SocialMod ↔ party de Open Parties and Claims del líder. Reglas para no estropear claims ajenos:
 * <ul>
 *     <li>Nunca se mete a alguien que ya está en otra party de OPAC, ni se toca la party si el líder es miembro de
 *     otra.</li>
 *     <li>Solo se quitan de la party los jugadores que añadió SocialMod ({@link Group#claimsSynced}).</li>
 *     <li>Modo {@code both}: quien entra en la party por OPAC entra al grupo como recluta (si hay sitio) y quien sale
 *     de la party, sale del grupo (solo si había llegado por la sincronización).</li>
 * </ul>
 */
final class OpacClaimsBackend {
    private final SocialServer social;

    OpacClaimsBackend(SocialServer social) {
        this.social = social;
    }

    void sync(Group group, boolean both) {
        IPartyManagerAPI parties = OpenPACServerAPI.get(social.server()).getPartyManager();
        UUID leader = group.leader();
        if (leader == null) {
            return;
        }
        IServerPartyAPI party = parties.getPartyByOwner(leader);
        if (party == null) {
            if (parties.getPartyByMember(leader) != null) {
                SocialMod.warnOnce("claims_leader_" + group.id, "El líder de " + group.name
                        + " es miembro de otra party de OPAC: no se sincroniza", null);
                return;
            }
            party = parties.createPartyForOwner(new NameAndId(leader, group.memberNames.getOrDefault(leader, "?")));
            if (party == null) {
                return;
            }
        }
        boolean changed = false;
        Set<UUID> inParty = new HashSet<>();
        party.getMemberInfoStream().forEach(member -> inParty.add(member.getUUID()));

        // Grupo → party
        for (var entry : group.members.entrySet()) {
            UUID id = entry.getKey();
            if (id.equals(leader)) {
                continue;
            }
            PartyMemberRank rank = PartyMemberRank.valueOf(ClaimsSync.rankFor(entry.getValue()));
            if (!inParty.contains(id)) {
                if (group.claimsSynced.contains(id) && both) {
                    continue; // salió de la party por OPAC: se resuelve abajo
                }
                if (parties.getPartyByMember(id) == null
                        && party.addMember(id, rank, group.memberNames.getOrDefault(id, "?")) != null) {
                    group.claimsSynced.add(id);
                    changed = true;
                }
            } else if (group.claimsSynced.contains(id)) {
                IPartyMemberAPI info = party.getMemberInfo(id);
                if (info != null && info.getRank() != rank) {
                    party.setRank(info, rank);
                }
            }
        }
        // Quitados del grupo → fuera de la party (solo los que puso SocialMod)
        for (UUID id : new ArrayList<>(group.claimsSynced)) {
            boolean inGroup = group.isMember(id);
            boolean member = inParty.contains(id) || party.getMemberInfo(id) != null;
            if (!inGroup && member) {
                party.removeMember(id);
                group.claimsSynced.remove(id);
                changed = true;
            } else if (inGroup && !member && both) {
                // Salió (o lo echaron) de la party desde OPAC
                group.claimsSynced.remove(id);
                social.groups().removeMember(group, id, "socialmod.group.left");
                changed = true;
            } else if (!inGroup) {
                group.claimsSynced.remove(id);
                changed = true;
            }
        }
        // Party → grupo (modo both)
        if (both) {
            int max = ServerConfig.get().limits.maxMembersPerGroup;
            List<IPartyMemberAPI> newcomers = new ArrayList<>();
            party.getMemberInfoStream().forEach(member -> {
                if (!member.isOwner() && !group.isMember(member.getUUID())) {
                    newcomers.add(member);
                }
            });
            for (IPartyMemberAPI member : newcomers) {
                if (group.members.size() >= max) {
                    break;
                }
                group.members.put(member.getUUID(), Role.RECRUIT);
                group.memberNames.put(member.getUUID(), member.getUsername());
                group.claimsSynced.add(member.getUUID());
                social.groups().afterExternalJoin(group, member.getUUID(), member.getUsername());
                changed = true;
            }
        }
        if (changed) {
            social.storage().markGroupsDirty();
            social.storage().audit("CLAIMS_SYNC " + group.id + " party=" + party.getId());
        }
    }
}
