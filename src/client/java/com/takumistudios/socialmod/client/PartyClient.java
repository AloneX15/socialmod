package com.takumistudios.socialmod.client;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.common.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Party en el mundo, lado cliente: vida de los compañeros (la dibuja {@code PartyHud}) y pings (PLAN 5.5).
 * El ping se ve como un haz de partículas en el punto marcado durante {@link #PING_MILLIS} ms y como una línea
 * con distancia y dirección en el HUD. Partículas en vez de render propio: funcionan igual en 26.1.2–26.3, no
 * necesitan mixins ni eventos de render, y respetan los shaders y los ajustes de partículas del jugador.
 */
public final class PartyClient {
    public static final long PING_MILLIS = 10_000;
    private static final double PICK_DISTANCE = 256;

    public record ActivePing(Payloads.PingS2C ping, long until) {
    }

    private static List<Payloads.PartyMember> members = List.of();
    private static final List<ActivePing> PINGS = new ArrayList<>();
    private static int ticks;

    private PartyClient() {
    }

    public static List<Payloads.PartyMember> members() {
        return members;
    }

    public static List<ActivePing> pings() {
        return PINGS;
    }

    public static void reset() {
        members = List.of();
        PINGS.clear();
    }

    public static void onParty(Payloads.PartyS2C payload) {
        members = List.copyOf(payload.members());
    }

    public static void onPing(Payloads.PingS2C payload) {
        PINGS.removeIf(p -> p.ping().source().equals(payload.source()));
        PINGS.add(new ActivePing(payload, System.currentTimeMillis() + PING_MILLIS));
        ClientConfig config = ClientConfig.get();
        if (config.ping.sound && config.sounds.enabled && config.sounds.volume > 0) {
            try {
                SoundEvent event = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(SocialMod.MOD_ID, "ping"));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, config.sounds.volume / 100.0F));
            } catch (RuntimeException e) {
                SocialMod.warnOnce("ping_sound", "No se pudo reproducir el sonido del ping", e);
            }
        }
    }

    /** Tecla de ping: marca el bloque al que se mira (hasta 256 bloques). */
    public static void sendPing(Minecraft minecraft) {
        if (minecraft.player == null || !ClientConfig.get().ping.enabled || !ClientState.get().connected()) {
            return;
        }
        if (!ClientState.get().inParty()) {
            minecraft.player.sendOverlayMessage(Component.translatable("socialmod.party.none").withStyle(ChatFormatting.GRAY));
            return;
        }
        HitResult hit = minecraft.player.pick(PICK_DISTANCE, 1.0F, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            minecraft.player.sendOverlayMessage(Component.translatable("socialmod.ping.nothing").withStyle(ChatFormatting.GRAY));
            return;
        }
        BlockPos pos = block.getBlockPos();
        if (ClientPlayNetworking.canSend(Payloads.PingC2S.TYPE)) {
            ClientPlayNetworking.send(new Payloads.PingC2S(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    public static void tick(Minecraft minecraft) {
        long now = System.currentTimeMillis();
        Iterator<ActivePing> iterator = PINGS.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().until() < now) {
                iterator.remove();
            }
        }
        if (PINGS.isEmpty() || minecraft.level == null || ++ticks % 4 != 0) {
            return;
        }
        String dimension = minecraft.level.dimension().identifier().toString();
        for (ActivePing active : PINGS) {
            Payloads.PingS2C ping = active.ping();
            if (!ping.dimension().equals(dimension)) {
                continue;
            }
            double x = ping.x() + 0.5;
            double z = ping.z() + 0.5;
            // Haz vertical de 8 bloques sobre el bloque marcado
            for (int i = 0; i < 4; i++) {
                double y = ping.y() + 1.0 + i * 2.0 + (ticks % 8) * 0.25;
                minecraft.level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.02, 0.0);
            }
        }
    }
}
