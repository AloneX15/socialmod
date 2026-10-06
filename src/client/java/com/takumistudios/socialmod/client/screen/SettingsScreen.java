package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.ClientConfig;
import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.common.model.PresenceStatus;
import com.takumistudios.socialmod.common.model.Privacy;
import com.takumistudios.socialmod.common.net.SnapshotDto;
import com.takumistudios.socialmod.common.net.SocialAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Ajustes (PLAN 15): a la izquierda, estado y privacidad (se guardan en el servidor); a la derecha, preferencias
 * del cliente (toasts, sonidos, HUD, nametags, accesibilidad), que se aplican al instante.
 * Sin servidor compatible (desde ModMenu en el menú principal) solo se muestran las del cliente.
 */
public class SettingsScreen extends SocialChildScreen {
    private static final int COLUMN = 180;
    private static final int[] DURATIONS = {3, 4, 6, 8, 10, 12, 15};
    private static final int[] VOLUMES = {0, 25, 50, 70, 100};
    private static final int[] SCALES = {75, 100, 125, 150};
    private static final int[] MARGINS = {0, 40, 80, 120, 160};
    private static final int[] TOAST_MARGINS = {4, 20, 40, 80, 120, 160};
    private static final int[] TEXT_SCALES = {75, 100, 125, 150};
    /** Pestaña de preferencias del cliente (se recuerda mientras el juego está abierto). */
    private static int page;
    private EditBox customStatus;
    private String statusDraft;

    public SettingsScreen(@Nullable Screen parent) {
        super(parent, Component.translatable("socialmod.settings.title"));
    }

    @Override
    public void tick() {
        // Desde ModMenu (sin servidor) la pantalla sigue abierta con los ajustes del cliente
        if (ClientState.get().connected()) {
            super.tick();
        }
    }

    @Override
    protected void saveDrafts() {
        if (customStatus != null) {
            statusDraft = customStatus.getValue();
        }
    }

    @Override
    protected void init() {
        super.init();
        boolean connected = ClientState.get().connected();
        int totalWidth = connected ? 2 * COLUMN + 12 : COLUMN;
        int leftX = (this.width - totalWidth) / 2;
        int clientX = connected ? leftX + COLUMN + 12 : leftX;
        int top = 28;

        if (connected) {
            SnapshotDto.Self self = ClientState.get().snapshot().self;
            int y = top;
            PresenceStatus status = PresenceStatus.byId(self.status);
            addRenderableWidget(Ui.button(label("socialmod.settings.status", Component.literal(status.symbol() + " ")
                            .append(Component.translatable("socialmod.status." + status.id()))), b -> {
                // Se lee el valor vivo: el botón se reconstruye cuando llega el snapshot y el lambda capturado
                // podría ser de la versión anterior si se pulsa dos veces seguidas
                PresenceStatus current = PresenceStatus.byId(ClientState.get().snapshot().self.status);
                PresenceStatus next = PresenceStatus.byOrdinal((current.ordinal() + 1) % 4);
                ClientState.get().setSelfStatus(next);
                ClientNet.action(SocialAction.SET_STATUS, next.id());
            }).bounds(leftX, y, COLUMN, 20).build());
            y += 22;
            customStatus = new StyledEditBox(this.font, leftX, y, COLUMN - 54, 20, Component.translatable("socialmod.settings.custom_status"));
            customStatus.setMaxLength(48);
            customStatus.setHint(Component.translatable("socialmod.settings.custom_status").withStyle(ChatFormatting.DARK_GRAY));
            customStatus.setValue(statusDraft != null ? statusDraft : self.customStatus);
            addRenderableWidget(customStatus);
            addRenderableWidget(Ui.button(Component.translatable("socialmod.group_settings.save"), b -> {
                statusDraft = null;
                ClientNet.action(SocialAction.SET_CUSTOM_STATUS, customStatus.getValue());
            }).bounds(leftX + COLUMN - 50, y, 50, 20).build());
            y += 22;
            Privacy messages = Privacy.byId(self.whoCanMessage);
            addRenderableWidget(Ui.button(label("socialmod.settings.who_messages", Component.translatable("socialmod.privacy." + messages.id())), b -> {
                String next = Privacy.byId(ClientState.get().snapshot().self.whoCanMessage).next().id();
                ClientState.get().updateSelf(s -> s.whoCanMessage = next);
                ClientNet.action(SocialAction.SET_PRIVACY_MESSAGES, next);
            }).bounds(leftX, y, COLUMN, 20).build());
            y += 22;
            Privacy statusPrivacy = Privacy.byId(self.whoSeesStatus);
            addRenderableWidget(Ui.button(label("socialmod.settings.who_status", Component.translatable("socialmod.privacy." + statusPrivacy.id())), b -> {
                String next = Privacy.byId(ClientState.get().snapshot().self.whoSeesStatus).next().id();
                ClientState.get().updateSelf(s -> s.whoSeesStatus = next);
                ClientNet.action(SocialAction.SET_PRIVACY_STATUS, next);
            }).bounds(leftX, y, COLUMN, 20).build());
            y += 22;
            addRenderableWidget(serverToggle(leftX, y, "socialmod.settings.show_dimension", self.showDimension, SocialAction.SET_SHOW_DIMENSION,
                    (s, v) -> s.showDimension = v));
            y += 22;
            addRenderableWidget(serverToggle(leftX, y, "socialmod.settings.read_receipts", self.readReceipts, SocialAction.SET_READ_RECEIPTS,
                    (s, v) -> s.readReceipts = v));
            y += 22;
            addRenderableWidget(serverToggle(leftX, y, "socialmod.settings.typing", self.typingIndicator, SocialAction.SET_TYPING_INDICATOR,
                    (s, v) -> s.typingIndicator = v));
            y += 22;
            addRenderableWidget(Ui.button(Component.translatable("socialmod.settings.export"),
                    b -> ClientNet.action(SocialAction.DATA_EXPORT, "")).bounds(leftX, y, COLUMN, 20).build());
        } else {
            customStatus = null;
        }

        ClientConfig config = ClientConfig.get();
        int y = top;
        int half = (COLUMN - 4) / 2;
        // Pestañas de las preferencias del cliente: así cabe todo con cualquier GUI Scale
        int tabW = (COLUMN - 4) / 3;
        String[] tabs = {"socialmod.settings.page.notifications", "socialmod.settings.page.interface", "socialmod.settings.page.maps"};
        for (int i = 0; i < tabs.length; i++) {
            int index = i;
            Button tab = Ui.button(Component.translatable(tabs[i]), b -> {
                page = index;
                saveDrafts();
                rebuildWidgets();
            }).bounds(clientX + i * (tabW + 2), y, tabW, 20).build();
            tab.active = page != i;
            addRenderableWidget(tab);
        }
        y += 24;
        switch (page) {
            case 0 -> {
                addRenderableWidget(toggle(clientX, y, half, "socialmod.settings.toasts", () -> config.toasts.enabled, v -> config.toasts.enabled = v));
                addRenderableWidget(cycle(clientX + half + 4, y, half, "socialmod.settings.position",
                        () -> Component.translatable(ClientConfig.cornerKey(config.toasts.position)), () -> config.toasts.position = config.toasts.position.next()));
                y += 22;
                addRenderableWidget(cycle(clientX, y, half, "socialmod.settings.duration",
                        () -> Component.literal(config.toasts.durationSeconds + " s"), () -> config.toasts.durationSeconds = next(DURATIONS, config.toasts.durationSeconds)));
                addRenderableWidget(cycle(clientX + half + 4, y, half, "socialmod.settings.max_visible",
                        () -> Component.literal(String.valueOf(config.toasts.maxVisible)), () -> config.toasts.maxVisible = config.toasts.maxVisible % 5 + 1));
                y += 22;
                addRenderableWidget(toggle(clientX, y, half, "socialmod.settings.animations", () -> config.toasts.animations, v -> config.toasts.animations = v));
                addRenderableWidget(toggle(clientX + half + 4, y, half, "socialmod.settings.smart_dnd", () -> config.toasts.smartDnd, v -> config.toasts.smartDnd = v));
                y += 22;
                addRenderableWidget(toggle(clientX, y, half, "socialmod.settings.group_toasts", () -> config.toasts.showGroupMessages, v -> config.toasts.showGroupMessages = v));
                addRenderableWidget(cycle(clientX + half + 4, y, half, "socialmod.settings.volume",
                        () -> Component.literal(config.sounds.volume + "%"), () -> config.sounds.volume = next(VOLUMES, config.sounds.volume)));
                y += 22;
                addRenderableWidget(toggle(clientX, y, half, "socialmod.settings.narrator", () -> config.accessibility.narrateToasts, v -> config.accessibility.narrateToasts = v));
                addRenderableWidget(toggle(clientX + half + 4, y, half, "socialmod.settings.ping_sound", () -> config.ping.sound, v -> config.ping.sound = v));
            }
            case 1 -> {
                addRenderableWidget(toggle(clientX, y, half, "socialmod.settings.hud", () -> config.hud.enabled, v -> config.hud.enabled = v));
                addRenderableWidget(cycle(clientX + half + 4, y, half, "socialmod.settings.hud_position",
                        () -> Component.translatable(ClientConfig.cornerKey(config.hud.position)), () -> config.hud.position = config.hud.position.next()));
                y += 22;
                addRenderableWidget(cycle(clientX, y, half, "socialmod.settings.hud_scale",
                        () -> Component.literal(config.hud.scale + "%"), () -> config.hud.scale = next(SCALES, config.hud.scale)));
                addRenderableWidget(toggle(clientX + half + 4, y, half, "socialmod.settings.party_health", () -> config.hud.partyHealth, v -> config.hud.partyHealth = v));
                y += 22;
                addRenderableWidget(toggle(clientX, y, half, "socialmod.settings.reduced_motion", () -> config.accessibility.reducedMotion, v -> config.accessibility.reducedMotion = v));
                y += 22;
                addRenderableWidget(cycle(clientX, y, half, "socialmod.settings.text_scale",
                        () -> Component.literal(config.panel.textScale + "%"), () -> config.panel.textScale = next(TEXT_SCALES, config.panel.textScale)));
                addRenderableWidget(toggle(clientX + half + 4, y, half, "socialmod.settings.high_contrast", () -> config.accessibility.highContrast, v -> config.accessibility.highContrast = v));
            }
            default -> {
                addRenderableWidget(toggle(clientX, y, half, "socialmod.settings.waypoints", () -> config.maps.waypoints, v -> config.maps.waypoints = v));
                addRenderableWidget(toggle(clientX + half + 4, y, half, "socialmod.settings.ping", () -> config.ping.enabled, v -> config.ping.enabled = v));
                y += 22;
                addRenderableWidget(cycle(clientX, y, half, "socialmod.settings.reserved",
                        () -> Component.literal(config.panel.reservedRight + " px"), () -> config.panel.reservedRight = next(MARGINS, config.panel.reservedRight)));
                addRenderableWidget(cycle(clientX + half + 4, y, half, "socialmod.settings.reserved_top",
                        () -> Component.literal(config.panel.reservedTop + " px"), () -> config.panel.reservedTop = next(MARGINS, config.panel.reservedTop)));
                y += 22;
                addRenderableWidget(cycle(clientX, y, half, "socialmod.settings.toast_margin_x",
                        () -> Component.literal(config.toasts.marginX + " px"), () -> config.toasts.marginX = next(TOAST_MARGINS, config.toasts.marginX)));
                addRenderableWidget(cycle(clientX + half + 4, y, half, "socialmod.settings.toast_margin_y",
                        () -> Component.literal(config.toasts.marginY + " px"), () -> config.toasts.marginY = next(TOAST_MARGINS, config.toasts.marginY)));
                y += 22;
                addRenderableWidget(toggle(clientX, y, half, "socialmod.settings.auto_margins", () -> config.maps.autoMargins, v -> {
                    config.maps.autoMargins = v;
                    config.maps.autoMarginsApplied = false; // al reactivarlo se vuelve a medir el minimapa
                }));
                addRenderableWidget(toggle(clientX + half + 4, y, half, "socialmod.settings.cache", () -> config.cache.enabled, v -> {
                    config.cache.enabled = v;
                    if (!v) {
                        com.takumistudios.socialmod.client.ClientCache.clearAll();
                    }
                }));
            }
        }

        addRenderableWidget(Ui.button(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private static int next(int[] values, int current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] > current) {
                return values[i];
            }
        }
        return values[0];
    }

    private static Component label(String key, Component value) {
        return Component.translatable(key).append(": ").append(value);
    }

    /** Opción guardada en el servidor: se aplica en local al instante y el snapshot posterior la confirma. */
    private Button serverToggle(int x, int y, String key, boolean value, SocialAction action, BiConsumer<SnapshotDto.Self, Boolean> local) {
        return Ui.button(label(key, value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF), b -> {
            ClientState.get().updateSelf(s -> local.accept(s, !value));
            ClientNet.action(action, String.valueOf(!value));
        }).bounds(x, y, COLUMN, 20).build();
    }

    private Button toggle(int x, int y, int w, String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        return Ui.button(label(key, getter.getAsBoolean() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF), b -> {
            setter.accept(!getter.getAsBoolean());
            ClientConfig.save();
            saveDrafts();
            rebuildWidgets();
        }).bounds(x, y, w, 20).build();
    }

    private Button cycle(int x, int y, int w, String key, Supplier<Component> value, Runnable advance) {
        return Ui.button(label(key, value.get()), b -> {
            advance.run();
            ClientConfig.save();
            saveDrafts();
            rebuildWidgets();
        }).bounds(x, y, w, 20).build();
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Ui.title(graphics, font, this.title, this.width / 2, Ui.TITLE_Y);
        boolean connected = ClientState.get().connected();
        int totalWidth = connected ? 2 * COLUMN + 12 : COLUMN;
        int leftX = (this.width - totalWidth) / 2;
        if (connected) {
            Ui.sectionHeader(graphics, font, Component.translatable("socialmod.settings.server_section"), leftX, 20, COLUMN);
            Ui.sectionHeader(graphics, font, Component.translatable("socialmod.settings.client_section"), leftX + COLUMN + 12, 20, COLUMN);
        } else {
            Ui.sectionHeader(graphics, font, Component.translatable("socialmod.settings.client_section"), leftX, 20, COLUMN);
        }
    }
}
