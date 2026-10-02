package com.takumistudios.socialmod.server;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.api.GroupInfo;
import com.takumistudios.socialmod.api.MessageFilter;
import com.takumistudios.socialmod.api.Notification;
import com.takumistudios.socialmod.api.SocialModServerAPI;
import com.takumistudios.socialmod.api.StatusProvider;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Implementación de {@link SocialModServerAPI}. Los registros sobreviven a reinicios del servidor integrado. */
public final class ServerApiImpl implements SocialModServerAPI {
    public static final ServerApiImpl INSTANCE = new ServerApiImpl();

    private static final Map<Identifier, StatusProvider> STATUS_PROVIDERS = new LinkedHashMap<>();
    private static final List<MessageFilter> FILTERS = new CopyOnWriteArrayList<>();

    private ServerApiImpl() {
    }

    public static GroupInfo info(Group group) {
        Map<UUID, String> members = new LinkedHashMap<>();
        group.members.forEach((id, role) -> members.put(id, role.id()));
        return new GroupInfo(group.id, group.name, group.tag, group.color, group.party, Map.copyOf(members));
    }

    @Override
    public Optional<GroupInfo> getMainGroup(UUID player) {
        SocialServer social = SocialServer.get();
        if (social == null) {
            return Optional.empty();
        }
        Group group = social.groups().mainGroup(player);
        return group == null ? Optional.empty() : Optional.of(info(group));
    }

    @Override
    public Collection<GroupInfo> getGroups(UUID player) {
        SocialServer social = SocialServer.get();
        if (social == null) {
            return List.of();
        }
        return social.groups().groupsOf(player).stream().map(ServerApiImpl::info).toList();
    }

    @Override
    public boolean areFriends(UUID a, UUID b) {
        SocialServer social = SocialServer.get();
        PlayerRecord record = social == null ? null : social.storage().player(a);
        return record != null && record.friends.contains(b);
    }

    @Override
    public boolean isBlocked(UUID blocker, UUID target) {
        SocialServer social = SocialServer.get();
        PlayerRecord record = social == null ? null : social.storage().player(blocker);
        return record != null && record.blocked.contains(target);
    }

    @Override
    public void sendSystemNotification(ServerPlayer player, Notification notification) {
        SocialServer social = SocialServer.get();
        if (social != null) {
            social.notifier().system(player, notification);
        }
    }

    @Override
    public void registerStatusProvider(Identifier id, StatusProvider provider) {
        synchronized (STATUS_PROVIDERS) {
            STATUS_PROVIDERS.put(id, provider);
        }
    }

    @Override
    public void registerMessageFilter(MessageFilter filter) {
        FILTERS.add(filter);
    }

    public static Map<Identifier, StatusProvider> statusProviders() {
        return STATUS_PROVIDERS;
    }

    public static List<MessageFilter> filters() {
        return FILTERS;
    }

    /** Primera actividad no vacía de los proveedores registrados. Un proveedor que falla no afecta a los demás. */
    public static String activityOf(ServerPlayer player) {
        synchronized (STATUS_PROVIDERS) {
            for (Map.Entry<Identifier, StatusProvider> entry : STATUS_PROVIDERS.entrySet()) {
                try {
                    Optional<String> activity = entry.getValue().activity(player);
                    if (activity.isPresent() && !activity.get().isBlank()) {
                        return com.takumistudios.socialmod.common.text.TextSanitizer.clean(activity.get(), 48);
                    }
                } catch (RuntimeException e) {
                    SocialMod.warnOnce("status_provider_" + entry.getKey(), "El proveedor de estado " + entry.getKey() + " falló", e);
                }
            }
        }
        return "";
    }
}
