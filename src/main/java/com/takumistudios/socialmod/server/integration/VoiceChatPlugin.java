package com.takumistudios.socialmod.server.integration;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.server.data.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integración con Simple Voice Chat (PLAN 12): un grupo de voz por grupo o party de SocialMod, al que se entra desde
 * el panel ({@code ☏}) o con {@code /g voice} y {@code /party voice}. Simple Voice Chat carga esta clase por el
 * entrypoint {@code voicechat} solo si está instalado; nada más en SocialMod la referencia.
 * <p>Los grupos de voz son ocultos y con contraseña aleatoria (nunca se muestra): solo se entra desde SocialMod, que
 * comprueba antes que el jugador sea miembro. No son persistentes: Simple Voice Chat los borra al quedarse vacíos.</p>
 */
public final class VoiceChatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return SocialMod.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            VoiceBridge.set(new Backend(event.getVoicechat()));
            SocialMod.LOGGER.info("[SocialMod] Simple Voice Chat detectado: grupos de voz activados");
        });
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> VoiceBridge.set(null));
    }

    private static final class Backend implements VoiceBridge.Backend {
        private static final SecureRandom RANDOM = new SecureRandom();
        private final VoicechatServerApi api;
        /** id del grupo de voz → id del grupo de SocialMod. */
        private final Map<UUID, String> owners = new ConcurrentHashMap<>();

        Backend(VoicechatServerApi api) {
            this.api = api;
        }

        private static UUID voiceId(Group group) {
            return UUID.nameUUIDFromBytes(("socialmod:" + group.id).getBytes(StandardCharsets.UTF_8));
        }

        private static String voiceName(Group group) {
            String name = group.party ? "Party" : "[" + group.tag + "] " + group.name;
            return name.length() > 24 ? name.substring(0, 24) : name;
        }

        @Override
        public boolean join(ServerPlayer player, Group group) {
            VoicechatConnection connection = api.getConnectionOf(player.getUUID());
            if (connection == null || !connection.isInstalled()) {
                return false;
            }
            UUID id = voiceId(group);
            de.maxhenkel.voicechat.api.Group voice = api.getGroup(id);
            if (voice == null) {
                byte[] secret = new byte[12];
                RANDOM.nextBytes(secret);
                voice = api.groupBuilder()
                        .setId(id)
                        .setName(voiceName(group))
                        .setPassword(HexFormat.of().formatHex(secret))
                        .setHidden(true)
                        .setPersistent(false)
                        .setType(de.maxhenkel.voicechat.api.Group.Type.NORMAL)
                        .build();
            }
            owners.put(id, group.id);
            connection.setGroup(voice);
            return true;
        }

        @Override
        public void leave(ServerPlayer player) {
            VoicechatConnection connection = api.getConnectionOf(player.getUUID());
            if (connection != null && connection.isInGroup()) {
                connection.setGroup(null);
            }
        }

        @Override
        public @Nullable String currentGroup(UUID player) {
            VoicechatConnection connection = api.getConnectionOf(player);
            if (connection == null || !connection.isInGroup() || connection.getGroup() == null) {
                return null;
            }
            return owners.get(connection.getGroup().getId());
        }

        @Override
        public void groupRemoved(Group group) {
            UUID id = voiceId(group);
            owners.remove(id);
            api.removeGroup(id);
        }
    }
}
