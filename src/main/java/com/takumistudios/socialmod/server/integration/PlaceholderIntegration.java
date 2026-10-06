package com.takumistudios.socialmod.server.integration;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.common.model.PresenceStatus;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.data.Group;
import com.takumistudios.socialmod.server.data.PlayerRecord;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.ServerPlaceholderContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiFunction;

/**
 * Placeholders para Text Placeholder API de Patbox (PLAN 12): {@code %socialmod:main_group%}, {@code %socialmod:tag%},
 * {@code %socialmod:unread%}, {@code %socialmod:status%}, {@code %socialmod:friends_online%}.
 * Esta clase solo se carga si el mod {@code placeholder-api} está instalado.
 */
public final class PlaceholderIntegration {
    private PlaceholderIntegration() {
    }

    public static void register() {
        register("main_group", (social, player) -> {
            Group group = social.groups().mainGroup(player.getUUID());
            return group == null ? Component.empty() : Component.literal(group.name).withColor(group.color);
        });
        register("tag", (social, player) -> {
            Group group = social.groups().mainGroup(player.getUUID());
            return group == null ? Component.empty() : Component.literal("[" + group.tag + "]").withColor(group.color);
        });
        register("team", (social, player) -> {
            Group team = social.teams().of(player.getUUID());
            return team == null ? Component.empty() : Component.literal(team.name).withColor(team.color);
        });
        register("unread", (social, player) -> Component.literal(String.valueOf(social.record(player).totalUnread())));
        register("status", (social, player) -> {
            PresenceStatus status = social.presence().effective(player.getUUID());
            return Component.literal(status.symbol() + " " + status.id()).withColor(status.color() & 0xFFFFFF);
        });
        register("friends_online", (social, player) -> {
            PlayerRecord record = social.record(player);
            long online = record.friends.stream()
                    .filter(id -> social.presence().visibleStatus(player.getUUID(), id) != PresenceStatus.OFFLINE).count();
            return Component.literal(String.valueOf(online));
        });
        SocialMod.LOGGER.info("[SocialMod] Integración con Text Placeholder API activada");
    }

    private static void register(String name, BiFunction<SocialServer, ServerPlayer, Component> value) {
        Placeholders.registerServer(Identifier.fromNamespaceAndPath(SocialMod.MOD_ID, name), (ServerPlaceholderContext context, String argument) -> {
            SocialServer social = SocialServer.get();
            if (social == null || !context.hasServerPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            try {
                return PlaceholderResult.value(value.apply(social, context.serverPlayer()));
            } catch (RuntimeException e) {
                SocialMod.warnOnce("placeholder_" + name, "Error en el placeholder " + name, e);
                return PlaceholderResult.invalid("Error");
            }
        });
    }
}
