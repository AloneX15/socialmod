package com.takumistudios.socialmod.server.service;

import com.takumistudios.socialmod.api.event.PresenceEvents;
import com.takumistudios.socialmod.common.model.PresenceStatus;
import com.takumistudios.socialmod.common.model.Privacy;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.server.PermissionBridge;
import com.takumistudios.socialmod.server.ServerApiImpl;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.config.ServerConfig;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Presencia (PLAN 4.3 y 5.4). Solo se envía a quien le interesa: amigos, compañeros de grupo y quien tenga el panel
 * abierto, en lotes cada {@code batchTicks} ticks. El AFK se detecta en el servidor con el tiempo de la última acción.
 */
public final class PresenceService {
    private final SocialServer social;
    /** Jugadores conectados que están AFK. */
    private final Set<UUID> afk = new HashSet<>();
    /** Actividad aportada por la API (StatusProvider). */
    private final Map<UUID, String> activities = new HashMap<>();
    /** Pendientes de enviar: destinatario → jugadores cuyo estado cambió. */
    private final Map<UUID, Set<UUID>> pending = new HashMap<>();
    private int ticks;

    public PresenceService(SocialServer social) {
        this.social = social;
    }

    // ---------- Estado visible ----------

    /** Estado efectivo de un jugador sin tener en cuenta quién lo mira. */
    public PresenceStatus effective(UUID player) {
        ServerPlayer online = social.online(player);
        PlayerRecord record = social.storage().player(player);
        if (online == null || record == null) {
            return PresenceStatus.OFFLINE;
        }
        if (record.status == PresenceStatus.INVISIBLE) {
            return PresenceStatus.INVISIBLE;
        }
        if (record.status == PresenceStatus.ONLINE && afk.contains(player)) {
            return PresenceStatus.AWAY;
        }
        return record.status;
    }

    /**
     * Lo que {@code viewer} puede ver del estado de {@code target}. Un jugador invisible, bloqueado o con la
     * privacidad cerrada aparece como {@link PresenceStatus#OFFLINE} ("desconocido").
     */
    public PresenceStatus visibleStatus(UUID viewer, UUID target) {
        PresenceStatus status = effective(target);
        if (viewer.equals(target) || status == PresenceStatus.OFFLINE) {
            return status;
        }
        if (!canSeeDetails(viewer, target)) {
            return PresenceStatus.OFFLINE;
        }
        return status == PresenceStatus.INVISIBLE ? PresenceStatus.OFFLINE : status;
    }

    public boolean canSeeDetails(UUID viewer, UUID target) {
        PlayerRecord record = social.storage().player(target);
        if (record == null) {
            return false;
        }
        if (record.blocked.contains(viewer)) {
            return false;
        }
        return switch (record.whoSeesStatus) {
            case EVERYONE -> true;
            case FRIENDS -> record.friends.contains(viewer);
            case NOBODY -> false;
        };
    }

    public Payloads.PresenceEntry entryFor(UUID viewer, UUID target) {
        PlayerRecord record = social.storage().player(target);
        String name = record == null ? "" : record.name;
        PresenceStatus status = visibleStatus(viewer, target);
        String custom = "";
        String dimension = "";
        if (status != PresenceStatus.OFFLINE && record != null) {
            custom = !record.customStatus.isEmpty() ? record.customStatus : activities.getOrDefault(target, "");
            ServerPlayer online = social.online(target);
            if (record.showDimension && online != null) {
                dimension = online.level().dimension().identifier().toString();
            }
        }
        return new Payloads.PresenceEntry(target, name, status.ordinal(), custom, dimension);
    }

    // ---------- Suscripciones ----------

    /** Jugadores conectados con el mod a los que les interesa el estado de {@code target}. */
    public Set<UUID> interestedIn(UUID target) {
        Set<UUID> result = new LinkedHashSet<>();
        PlayerRecord record = social.storage().player(target);
        if (record != null) {
            result.addAll(record.friends);
        }
        for (Group group : social.groups().groupsOf(target)) {
            result.addAll(group.members.keySet());
        }
        for (ServerPlayer player : social.server().getPlayerList().getPlayers()) {
            SocialServer.Session session = social.session(player.getUUID());
            if (session != null && session.panelOpen) {
                result.add(player.getUUID());
            }
        }
        result.removeIf(id -> !social.hasMod(id) || social.online(id) == null);
        return result;
    }

    public void markChanged(UUID target) {
        for (UUID viewer : interestedIn(target)) {
            pending.computeIfAbsent(viewer, v -> new LinkedHashSet<>()).add(target);
        }
        if (social.hasMod(target)) {
            pending.computeIfAbsent(target, v -> new LinkedHashSet<>()).add(target);
        }
    }

    /** Al abrir el panel: el estado de todos los conectados (PLAN 4.3: solo mientras está abierto). */
    public void setPanelOpen(ServerPlayer player, boolean open) {
        SocialServer.Session session = social.session(player.getUUID());
        if (session == null || session.panelOpen == open) {
            return;
        }
        session.panelOpen = open;
        if (open) {
            Set<UUID> all = pending.computeIfAbsent(player.getUUID(), v -> new LinkedHashSet<>());
            for (ServerPlayer other : social.server().getPlayerList().getPlayers()) {
                all.add(other.getUUID());
            }
        }
    }

    /** Estado inicial de amigos y compañeros (tras el handshake). */
    public void sendInitial(ServerPlayer player) {
        Set<UUID> targets = new LinkedHashSet<>();
        PlayerRecord record = social.record(player);
        targets.addAll(record.friends);
        for (Group group : social.groups().groupsOf(player.getUUID())) {
            targets.addAll(group.members.keySet());
        }
        targets.add(player.getUUID());
        pending.computeIfAbsent(player.getUUID(), v -> new LinkedHashSet<>()).addAll(targets);
    }

    // ---------- Ciclo de vida ----------

    public void onJoin(ServerPlayer player) {
        afk.remove(player.getUUID());
        if (ServerConfig.get().modules.presence) {
            markChanged(player.getUUID());
        }
    }

    public void onLeave(ServerPlayer player) {
        UUID id = player.getUUID();
        afk.remove(id);
        activities.remove(id);
        pending.remove(id);
        if (!ServerConfig.get().modules.presence) {
            return;
        }
        // Se envía tras la desconexión: el jugador ya no está en la lista y aparece como desconectado
        social.server().execute(() -> markChanged(id));
    }

    public void setStatus(ServerPlayer player, PresenceStatus status) {
        PlayerRecord record = social.record(player);
        PresenceStatus before = effective(player.getUUID());
        record.status = status.selectable() ? status : PresenceStatus.ONLINE;
        social.storage().markPlayersDirty();
        afk.remove(player.getUUID());
        statusChanged(player.getUUID(), before);
    }

    private void statusChanged(UUID player, PresenceStatus before) {
        PresenceStatus after = effective(player);
        markChanged(player);
        if (before != after) {
            PresenceEvents.STATUS_CHANGED.invoker().onStatusChanged(player, before.id(), after.id());
        }
    }

    public boolean isAfk(UUID player) {
        return afk.contains(player);
    }

    public void tick() {
        ServerConfig config = ServerConfig.get();
        if (!config.modules.presence) {
            pending.clear();
            return;
        }
        ticks++;
        if (ticks % 20 == 0) {
            checkAfk(config);
            pollActivities();
        }
        if (ticks % config.presence.batchTicks == 0 && !pending.isEmpty()) {
            flush();
        }
    }

    private void checkAfk(ServerConfig config) {
        if (config.presence.afkMinutes <= 0) {
            return;
        }
        long limit = config.presence.afkMinutes * 60_000L;
        long now = Util.getMillis();
        for (ServerPlayer player : social.server().getPlayerList().getPlayers()) {
            boolean idle = now - player.getLastActionTime() > limit;
            UUID id = player.getUUID();
            if (idle != afk.contains(id)) {
                PresenceStatus before = effective(id);
                if (idle) {
                    afk.add(id);
                } else {
                    afk.remove(id);
                }
                statusChanged(id, before);
            }
        }
    }

    private void pollActivities() {
        if (ServerApiImpl.statusProviders().isEmpty()) {
            return;
        }
        for (ServerPlayer player : social.server().getPlayerList().getPlayers()) {
            String activity = ServerApiImpl.activityOf(player);
            String previous = activities.get(player.getUUID());
            if (!activity.equals(previous == null ? "" : previous)) {
                if (activity.isEmpty()) {
                    activities.remove(player.getUUID());
                } else {
                    activities.put(player.getUUID(), activity);
                }
                markChanged(player.getUUID());
            }
        }
    }

    private void flush() {
        Map<UUID, Set<UUID>> batch = new HashMap<>(pending);
        pending.clear();
        for (Map.Entry<UUID, Set<UUID>> entry : batch.entrySet()) {
            ServerPlayer viewer = social.online(entry.getKey());
            if (viewer == null || !social.hasMod(viewer.getUUID())) {
                continue;
            }
            List<Payloads.PresenceEntry> entries = new ArrayList<>();
            boolean staff = PermissionBridge.isStaff(viewer, PermissionBridge.MOD_INSPECT);
            for (UUID target : entry.getValue()) {
                Payloads.PresenceEntry presence = entryFor(viewer.getUUID(), target);
                if (staff && presence.status() == PresenceStatus.OFFLINE.ordinal() && effective(target) == PresenceStatus.INVISIBLE) {
                    presence = new Payloads.PresenceEntry(target, presence.name(), PresenceStatus.INVISIBLE.ordinal(), "", "");
                }
                entries.add(presence);
                if (entries.size() >= Payloads.MAX_LIST) {
                    social.send(viewer, new Payloads.PresenceS2C(entries));
                    entries = new ArrayList<>();
                }
            }
            if (!entries.isEmpty()) {
                social.send(viewer, new Payloads.PresenceS2C(entries));
            }
        }
    }

    /** Para pruebas: aplica ya los cambios pendientes. */
    public void flushNow() {
        flush();
    }

    public static boolean privacyAllows(Privacy privacy, boolean friends) {
        return privacy == Privacy.EVERYONE || (privacy == Privacy.FRIENDS && friends);
    }
}
