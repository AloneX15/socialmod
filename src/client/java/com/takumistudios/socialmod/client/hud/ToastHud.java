package com.takumistudios.socialmod.client.hud;

import com.takumistudios.socialmod.client.theme.VisualManager;

import com.takumistudios.socialmod.client.theme.VisualText;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.api.client.ToastData;
import com.takumistudios.socialmod.client.ClientConfig;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.Heads;
import com.takumistudios.socialmod.client.SocialKeys;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.screen.SocialScreen;
import com.takumistudios.socialmod.client.theme.Theme;
import com.takumistudios.socialmod.client.theme.ThemeManager;
import com.takumistudios.socialmod.common.net.Payloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Notificaciones flotantes (PLAN 6). Se dibujan como elemento de HUD de Fabric API, sin mixins sobre el
 * {@code ToastManager} vanilla, así conviven y se ordenan con otros HUD. Cuando no hay toasts no hace nada
 * (sin asignaciones por frame).
 */
public final class ToastHud {
    private static final int HEIGHT = 36;
    private static final int SPACING = 4;
    private static final long SLIDE_MILLIS = 220;
    private static final long FADE_MILLIS = 300;
    /** Tras recibir daño, los toasts esperan este tiempo (modo combate). */
    private static final long COMBAT_MILLIS = 5_000;

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final List<ToastData> DEFERRED = new ArrayList<>();
    private static long lastCombat;

    private static final class Active {
        ToastData data;
        int count = 1;
        long shownAt;
        long receivedAt;

        Active(ToastData data, long now) {
            this.data = data;
            this.shownAt = now;
            this.receivedAt = now;
        }
    }

    private ToastHud() {
    }

    public static void clear() {
        ACTIVE.clear();
        DEFERRED.clear();
    }

    /** Notificación nueva (del servidor o de la API). */
    public static void push(ToastData data) {
        ClientConfig config = ClientConfig.get();
        ClientState state = ClientState.get();
        if (!config.toasts.enabled) {
            return;
        }
        if (data.kind() == Payloads.NotifyKind.GROUP && (!config.toasts.showGroupMessages || ClientState.isMuted(data.conversation()))) {
            return;
        }
        // Ya está leyendo esa conversación
        if (!data.conversation().isEmpty() && data.conversation().equals(state.activeConversation())
                && ClientCompat.currentScreen() instanceof SocialScreen) {
            return;
        }
        // Estado "No molestar": sin toasts ni sonidos (solo cuenta en el HUD)
        if ("dnd".equals(state.snapshot().self.status) && data.kind().priority() < 3) {
            return;
        }
        if (config.toasts.smartDnd && busy()) {
            DEFERRED.add(data);
            return;
        }
        show(data);
    }

    private static void show(ToastData data) {
        ClientConfig config = ClientConfig.get();
        long now = System.currentTimeMillis();
        if (config.toasts.groupBySender && data.source() != null) {
            for (Active active : ACTIVE) {
                if (Objects.equals(active.data.source(), data.source()) && active.data.conversation().equals(data.conversation())
                        && active.data.kind() == data.kind()) {
                    active.count++;
                    active.data = data;
                    active.receivedAt = now;
                    active.shownAt = Math.min(active.shownAt, now - SLIDE_MILLIS);
                    sound(data.kind());
                    narrate(data, active.count);
                    return;
                }
            }
        }
        ACTIVE.add(new Active(data, now));
        // Prioridad: menciones e invitaciones arriba; a igual prioridad, el más reciente primero
        ACTIVE.sort(Comparator.<Active>comparingInt(a -> -a.data.kind().priority()).thenComparingLong(a -> -a.receivedAt));
        sound(data.kind());
        narrate(data, 1);
    }

    /** Modo no molestar inteligente: combate (daño reciente) o una pantalla abierta. */
    private static boolean busy() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        return System.currentTimeMillis() - lastCombat < COMBAT_MILLIS || ClientCompat.currentScreen() != null;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.hurtTime > 0) {
            lastCombat = System.currentTimeMillis();
        }
        if (!DEFERRED.isEmpty() && !busy()) {
            // Resumen de lo que llegó mientras estaba ocupado
            if (DEFERRED.size() == 1) {
                show(DEFERRED.getFirst());
            } else {
                ToastData last = DEFERRED.getLast();
                show(new ToastData(Payloads.NotifyKind.SYSTEM, last.source(),
                        Component.translatable("socialmod.toast.summary", DEFERRED.size()).getString(),
                        last.title() + ": " + last.body(), last.conversation()));
            }
            DEFERRED.clear();
        }
        long duration = ClientConfig.get().toasts.durationSeconds * 1000L;
        long now = System.currentTimeMillis();
        ACTIVE.removeIf(active -> now - active.receivedAt > duration + FADE_MILLIS);
    }

    /** Toast más reciente con conversación (para la tecla de Quick-Reply). */
    public static @Nullable ToastData latestWithConversation() {
        ToastData best = null;
        long bestTime = 0;
        for (Active active : ACTIVE) {
            if (!active.data.conversation().isEmpty() && active.receivedAt > bestTime) {
                best = active.data;
                bestTime = active.receivedAt;
            }
        }
        return best;
    }

    private static void sound(Payloads.NotifyKind kind) {
        ClientConfig.Sounds sounds = ClientConfig.get().sounds;
        if (!sounds.enabled || sounds.volume <= 0) {
            return;
        }
        boolean enabled = switch (kind) {
            case PRIVATE -> sounds.privateMessages;
            case MENTION -> sounds.mentions;
            case INVITE, FRIEND -> sounds.invites;
            case EVENT -> sounds.events;
            case GROUP -> sounds.groupMessages;
            case SYSTEM -> sounds.mentions;
        };
        if (!enabled) {
            return;
        }
        String name = switch (kind) {
            case PRIVATE -> "notify.private";
            case MENTION -> "notify.mention";
            case INVITE, FRIEND -> "notify.invite";
            case EVENT -> "notify.event";
            case GROUP -> "notify.group";
            case SYSTEM -> "notify.system";
        };
        try {
            SoundEvent event = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(SocialMod.MOD_ID, name));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, sounds.volume / 100.0F));
        } catch (RuntimeException e) {
            SocialMod.warnOnce("toast_sound", "No se pudo reproducir el sonido de notificación", e);
        }
    }

    /** Accesibilidad (PLAN 7.3): lee el toast con el Narrador si está activo. */
    private static void narrate(ToastData data, int count) {
        if (!ClientConfig.get().accessibility.narrateToasts) {
            return;
        }
        var narrator = Minecraft.getInstance().getNarrator();
        if (narrator.isActive()) {
            narrator.saySystemQueued(Component.literal(data.title() + (count > 1 ? " (" + count + ")" : "") + ". " + data.body()));
        }
    }

    // ---------- Dibujo ----------

    public static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (ACTIVE.isEmpty() || ClientCompat.hudHidden()) {
            return;
        }
        ClientConfig config = ClientConfig.get();
        Theme theme = ThemeManager.get();
        Font font = Minecraft.getInstance().font;
        int width = theme.toast().width();
        long now = System.currentTimeMillis();
        long duration = config.toasts.durationSeconds * 1000L;
        ClientConfig.Corner corner = config.toasts.position;
        int shown = Math.min(ACTIVE.size(), config.toasts.maxVisible);
        for (int i = 0; i < shown; i++) {
            Active active = ACTIVE.get(i);
            long age = now - active.receivedAt;
            float alpha = 1.0F;
            float slide = 0.0F;
            if (config.toasts.animations && !ClientConfig.get().accessibility.reducedMotion) {
                long sinceShown = now - active.shownAt;
                slide = sinceShown < SLIDE_MILLIS ? 1.0F - sinceShown / (float) SLIDE_MILLIS : 0.0F;
                if (age > duration) {
                    alpha = Mth.clamp(1.0F - (age - duration) / (float) FADE_MILLIS, 0.0F, 1.0F);
                }
            } else if (age > duration) {
                continue;
            }
            int offsetX = (int) (slide * (width + config.toasts.marginX));
            var visual = VisualManager.get();
            int x = Math.round(Math.max(0, graphics.guiWidth() - width) * visual.toastX) + offsetX;
            int y = Math.round(Math.max(0, graphics.guiHeight() - shown * (HEIGHT + SPACING)) * visual.toastY) + i * (HEIGHT + SPACING);
            drawToast(graphics, font, theme, active, x, y, width, alpha, now);
        }
    }

    private static void drawToast(GuiGraphicsExtractor graphics, Font font, Theme theme, Active active, int x, int y, int width, float alpha, long now) {
        ToastData data = active.data;
        if (theme.textures().toast().isPresent()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, theme.textures().toast().get(), x, y, width, HEIGHT, alpha);
        } else {
            graphics.fill(x, y, x + width, y + HEIGHT, fade(theme.toast().background(), alpha));
        }
        graphics.outline(x, y, width, HEIGHT, fade(kindColor(data.kind(), theme), alpha));
        if (alpha > 0.3F) {
            Heads.draw(graphics, data.source(), x + 5, y + 5, 16);
        }
        int textX = x + 26;
        String title = data.title() + (active.count > 1 ? " (" + active.count + ")" : "");
        String ago = agoText(now - active.receivedAt);
        int agoWidth = font.width(ago);
        VisualText.text(graphics, font, trim(font, title, width - 34 - agoWidth), textX, y + 5, fade(theme.colors().accent(), alpha));
        VisualText.text(graphics, font, ago, x + width - agoWidth - 4, y + 5, fade(theme.colors().muted(), alpha));
        if (!data.body().isEmpty()) {
            VisualText.text(graphics, font, trim(font, data.body(), width - 30), textX, y + 15, fade(theme.colors().text(), alpha));
        }
        if (!data.conversation().isEmpty()) {
            Component hint = Component.translatable("socialmod.toast.hint", SocialKeys.QUICK_REPLY.getTranslatedKeyMessage());
            VisualText.text(graphics, font, trim(font, hint.getString(), width - 30), textX, y + 25, fade(theme.colors().muted(), alpha));
        }
    }

    private static String agoText(long millis) {
        long seconds = millis / 1000;
        return seconds < 60 ? Component.translatable("socialmod.time.seconds", Math.max(1, seconds)).getString()
                : Component.translatable("socialmod.time.minutes", seconds / 60).getString();
    }

    private static int kindColor(Payloads.NotifyKind kind, Theme theme) {
        return switch (kind) {
            case MENTION -> 0xFFFFD000;
            case INVITE, FRIEND -> 0xFF55FFFF;
            case EVENT -> 0xFFFF55FF;
            case PRIVATE -> theme.colors().accent();
            default -> theme.toast().border();
        };
    }

    public static String trim(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    /** Aplica un factor de transparencia a un color ARGB. */
    public static int fade(int color, float alpha) {
        int a = (int) (((color >>> 24) & 0xFF) * alpha);
        return (Math.max(a, alpha > 0.05F ? 4 : 0) << 24) | (color & 0xFFFFFF);
    }

    public static void onCombat() {
        lastCombat = System.currentTimeMillis();
    }
}
