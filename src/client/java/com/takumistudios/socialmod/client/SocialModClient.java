package com.takumistudios.socialmod.client;

import com.takumistudios.socialmod.client.theme.VisualManager;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.api.client.ToastData;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.hud.SocialHud;
import com.takumistudios.socialmod.client.hud.PartyHud;
import com.takumistudios.socialmod.client.compat.MapCompat;
import com.takumistudios.socialmod.client.hud.ToastHud;
import com.takumistudios.socialmod.client.screen.QuickReplyScreen;
import com.takumistudios.socialmod.client.screen.SocialScreen;
import com.takumistudios.socialmod.client.theme.ThemeManager;
import com.takumistudios.socialmod.common.net.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.jspecify.annotations.Nullable;

/**
 * Cliente (opcional): handshake, receptores, teclas, HUD, toasts, nametags y temas. Sin SocialMod en el servidor
 * (o con otro protocolo) no se envía nada y el jugador usa el chat normal (modo "solo chat").
 */
public final class SocialModClient implements ClientModInitializer {
    /** Ajuste automático de márgenes para Xaero hecho (o no necesario) en esta sesión. */
    private static boolean mapMarginsDone;

    @Override
    public void onInitializeClient() {
        ClientConfig.load();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { ClientConfig.shutdown(); com.takumistudios.socialmod.client.theme.SeriesProfiles.shutdown(); com.takumistudios.socialmod.client.theme.RowTemplates.shutdown(); com.takumistudios.socialmod.client.theme.LocalSeriesDesign.shutdown(); });
        SocialKeys.register();
        com.takumistudios.socialmod.client.compat.fancy.FancyBridge.register();
        com.takumistudios.socialmod.client.theme.RowTemplates.load();
        com.takumistudios.socialmod.client.theme.LocalSeriesDesign.load();
        VisualManager.register();

        ClientPlayNetworking.registerGlobalReceiver(Payloads.HelloS2C.TYPE, (payload, context) -> guarded("hello", () -> {
            ClientState.get().onHello(payload);
            if (ClientState.get().connected()) ClientNet.action(com.takumistudios.socialmod.common.net.SocialAction.VISUAL_PERSONAL_ORIGINAL, String.valueOf(com.takumistudios.socialmod.client.theme.AppearanceMode.personal()));
            if (payload.protocol() != Payloads.PROTOCOL_VERSION) {
                SocialMod.LOGGER.warn("[SocialMod] Protocolo del servidor {} (cliente {}): modo solo chat", payload.protocol(), Payloads.PROTOCOL_VERSION);
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.PlayerSearchS2C.TYPE, (payload, context) -> guarded("player_search", () -> {
            if (ClientCompat.currentScreen() instanceof com.takumistudios.socialmod.client.screen.PlayerSearchScreen screen) screen.accept(payload.result());
            com.takumistudios.socialmod.client.screen.SocialComponents.searchResult(payload.result());
        }));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.SnapshotS2C.TYPE, (payload, context) ->
                guarded("snapshot", () -> {
                    ClientState.get().onSnapshotFrame(payload.json());
                    VisualManager.accept(ClientState.get().snapshot().visual);
                    com.takumistudios.socialmod.client.theme.AppearanceMode.refresh();
                }));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.PresenceS2C.TYPE, (payload, context) ->
                guarded("presence", () -> ClientState.get().onPresence(payload.entries())));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.MessagesS2C.TYPE, (payload, context) ->
                guarded("messages", () -> ClientState.get().onMessages(payload)));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.SignalS2C.TYPE, (payload, context) ->
                guarded("signal", () -> ClientState.get().onSignal(payload)));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.PartyS2C.TYPE, (payload, context) ->
                guarded("party", () -> PartyClient.onParty(payload)));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.PingS2C.TYPE, (payload, context) ->
                guarded("ping", () -> PartyClient.onPing(payload)));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.TagsS2C.TYPE, (payload, context) ->
                guarded("tags", () -> ClientState.get().onTags(payload)));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.NotifyS2C.TYPE, (payload, context) ->
                guarded("notify", () -> ToastHud.push(new ToastData(payload.kind(), payload.source(), payload.title(), payload.body(), payload.conversation()))));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            com.takumistudios.socialmod.client.theme.AppearanceMode.join(client);
            ClientState.get().reset();
            PartyClient.reset();
            ToastHud.clear();
            VisualManager.reset();
            SocialKeys.resetConflictCheck();
            // Handshake: solo si el servidor registró nuestro canal (tiene SocialMod)
            if (ClientPlayNetworking.canSend(Payloads.HelloC2S.TYPE)) {
                // Caché local (PLAN 4.2): datos al instante mientras llega el snapshot del servidor
                ClientCache.open(client).ifPresent(json -> ClientState.get().onSnapshot(json));
                ClientPlayNetworking.send(new Payloads.HelloC2S(Payloads.PROTOCOL_VERSION));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientState.get().reset();
            PartyClient.reset();
            ToastHud.clear();
            VisualManager.reset();
            com.takumistudios.socialmod.client.theme.AppearanceMode.disconnect();
            ClientCache.flush();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> guarded("tick", () -> onTick(client)));

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(SocialMod.MOD_ID, "toasts"), ToastHud::extractRenderState);
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(SocialMod.MOD_ID, "social_hud"), SocialHud::extractRenderState);
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(SocialMod.MOD_ID, "party_hud"), PartyHud::extractRenderState);

        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                @SuppressWarnings("unchecked")
                RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) avatar;
                helper.register(new GroupTagLayer(parent));
            }
        });

        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(ThemeManager.ID, new ThemeManager());
    }

    private static void guarded(String name, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            SocialMod.warnOnce("client_" + name, "Error en el cliente (" + name + ")", e);
        }
    }

    private static void onTick(Minecraft client) {
        ToastHud.tick();
        PartyClient.tick(client);
        if (client.player == null) {
            return;
        }
        if (ClientState.get().connected()) {
            SocialKeys.checkConflicts();
            if (!mapMarginsDone && client.player.tickCount % 40 == 0) {
                mapMarginsDone = MapCompat.applyAutoMargins();
            }
        }
        while (SocialKeys.PING.consumeClick()) {
            PartyClient.sendPing(client);
        }
        while (SocialKeys.OPEN_PANEL.consumeClick()) {
            if (ClientCompat.currentScreen() instanceof SocialScreen panel) panel.onClose();
            else openPanel(null);
        }
        while (SocialKeys.QUICK_REPLY.consumeClick()) {
            String target = quickReplyTarget();
            if (client.hasShiftDown()) {
                openPanel(target);
            } else if (target != null && canChat(true)) {
                ClientCompat.setScreen(new QuickReplyScreen(target));
            }
        }
    }

    private static @Nullable String quickReplyTarget() {
        ToastData latest = ToastHud.latestWithConversation();
        if (latest != null) {
            return latest.conversation();
        }
        var targets = ClientState.get().replyTargets();
        return targets.isEmpty() ? null : targets.getFirst();
    }

    /**
     * Restricciones de chat (PLAN 9): con el chat oculto en las opciones o restringido por la cuenta Microsoft,
     * la UI de chat no se abre.
     */
    private static boolean canChat(boolean feedback) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean allowed = minecraft.options.chatVisibility().get() != ChatVisiblity.HIDDEN
                && minecraft.computeChatAbilities().canSendMessages();
        if (!allowed && feedback && minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.translatable("socialmod.panel.chat_disabled").withStyle(ChatFormatting.RED));
        }
        return allowed;
    }

    public static void openPanel(@Nullable String conversation) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientState.get().connected()) {
            if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(Component.translatable("socialmod.panel.not_available").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        if (!canChat(true)) {
            return;
        }
        if (ClientCompat.currentScreen() instanceof SocialScreen panel) {
            panel.select(conversation);
            return;
        }
        SocialScreen screen = new SocialScreen(null);
        ClientCompat.setScreen(screen);
        if (conversation != null) {
            screen.select(conversation);
        }
    }
}
