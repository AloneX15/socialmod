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
        boolean connected = ClientState.get().connected();
        int totalWidth = connected ? 2 * COLUMN + 12 : COLUMN;
        int leftX = (this.width - totalWidth) / 2;
        int clientX = connected ? leftX + COLUMN + 12 : leftX;
        int top = 28;

        if (connected) {
            SnapshotDto.Self self = ClientState.get().snapshot().self;
            int y = top;
            PresenceStatus status = PresenceStatus.byId(self.status);
            addRenderableWidget(Button.builder(label("socialmod.settings.status", Component.literal(status.symbol() + " ")
                            .append(Component.translatable("socialmod.status." + status.id()))), b -> {
                PresenceStatus next = PresenceStatus.byOrdinal((status.ordinal() + 1) % 4);
                ClientNet.action(SocialAction.SET_STATUS, next.id());
            }).bounds(leftX, y, COLUMN, 20).build());
            y += 22;
            customStatus = new EditBox(this.font, leftX, y, COLUMN - 54, 20, Component.translatable("socialmod.settings.custom_status"));
            customStatus.setMaxLength(48);
            customStatus.setHint(Component.translatable("socialmod.settings.custom_status").withStyle(ChatFormatting.DARK_GRAY));
            customStatus.setValue(statusDraft != null ? statusDraft : self.customStatus);
            addRenderableWidget(customStatus);
            addRenderableWidget(Button.builder(Component.translatable("socialmod.group_settings.save"), b -> {
                statusDraft = null;
                ClientNet.action(SocialAction.SET_CUSTOM_STATUS, customStatus.getValue());
            }).bounds(leftX + COLUMN - 50, y, 50, 20).build());
            y += 22;
            Privacy messages = Privacy.byId(self.whoCanMessage);
            addRenderableWidget(Button.builder(label("socialmod.settings.who_messages", Component.translatable("socialmod.privacy." + messages.id())),
                    b -> ClientNet.action(SocialAction.SET_PRIVACY_MESSAGES, messages.next().id())).bounds(leftX, y, COLUMN, 20).build());
            y += 22;
            Privacy statusPrivacy = Privacy.byId(self.whoSeesStatus);
            addRenderableWidget(Button.builder(label("socialmod.settings.who_status", Component.translatable("socialmod.privacy." + statusPrivacy.id())),
                    b -> ClientNet.action(SocialAction.SET_PRIVACY_STATUS, statusPrivacy.next().id())).bounds(leftX, y, COLUMN, 20).build());
            y += 22;
            addRenderableWidget(serverToggle(leftX, y, "socialmod.settings.show_dimension", self.showDimension, SocialAction.SET_SHOW_DIMENSION));
            y += 22;
            addRenderableWidget(serverToggle(leftX, y, "socialmod.settings.read_receipts", self.readReceipts, SocialAction.SET_READ_RECEIPTS));
            y += 22;
            addRenderableWidget(serverToggle(leftX, y, "socialmod.settings.typing", self.typingIndicator, SocialAction.SET_TYPING_INDICATOR));
            y += 22;
            addRenderableWidget(Button.builder(Component.translatable("socialmod.settings.export"),
                    b -> ClientNet.action(SocialAction.DATA_EXPORT, "")).bounds(leftX, y, COLUMN, 20).build());
        } else {
            customStatus = null;
        }

        ClientConfig config = ClientConfig.get();
        int y = top;
        int half = (COLUMN - 4) / 2;
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
        addRenderableWidget(toggle(clientX, y, half, "socialmod.settings.hud", () -> config.hud.enabled, v -> config.hud.enabled = v));
        addRenderableWidget(cycle(clientX + half + 4, y, half, "socialmod.settings.hud_position",
                () -> Component.translatable(ClientConfig.cornerKey(config.hud.position)), () -> config.hud.position = config.hud.position.next()));
        y += 22;
        addRenderableWidget(cycle(clientX, y, half, "socialmod.settings.hud_scale",
                () -> Component.literal(config.hud.scale + "%"), () -> config.hud.scale = next(SCALES, config.hud.scale)));
        addRenderableWidget(toggle(clientX + half + 4, y, half, "socialmod.settings.nametags", () -> config.nametags.showGroupTags, v -> config.nametags.showGroupTags = v));
        y += 22;
        addRenderableWidget(toggle(clientX, y, half, "socialmod.settings.narrator", () -> config.accessibility.narrateToasts, v -> config.accessibility.narrateToasts = v));
        addRenderableWidget(toggle(clientX + half + 4, y, half, "socialmod.settings.high_contrast", () -> config.accessibility.highContrast, v -> config.accessibility.highContrast = v));
        y += 22;
        addRenderableWidget(cycle(clientX, y, COLUMN, "socialmod.settings.reserved",
                () -> Component.literal(config.panel.reservedRight + " px"), () -> config.panel.reservedRight = next(MARGINS, config.panel.reservedRight)));

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
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

    private Button serverToggle(int x, int y, String key, boolean value, SocialAction action) {
        return Button.builder(label(key, value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF),
                b -> ClientNet.action(action, String.valueOf(!value))).bounds(x, y, COLUMN, 20).build();
    }

    private Button toggle(int x, int y, int w, String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        return Button.builder(label(key, getter.getAsBoolean() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF), b -> {
            setter.accept(!getter.getAsBoolean());
            ClientConfig.save();
            saveDrafts();
            rebuildWidgets();
        }).bounds(x, y, w, 20).build();
    }

    private Button cycle(int x, int y, int w, String key, Supplier<Component> value, Runnable advance) {
        return Button.builder(label(key, value.get()), b -> {
            advance.run();
            ClientConfig.save();
            saveDrafts();
            rebuildWidgets();
        }).bounds(x, y, w, 20).build();
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.centeredText(font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        boolean connected = ClientState.get().connected();
        int totalWidth = connected ? 2 * COLUMN + 12 : COLUMN;
        int leftX = (this.width - totalWidth) / 2;
        if (connected) {
            graphics.text(font, Component.translatable("socialmod.settings.server_section"), leftX, 20, Ui.theme().colors().accent());
            graphics.text(font, Component.translatable("socialmod.settings.client_section"), leftX + COLUMN + 12, 20, Ui.theme().colors().accent());
        } else {
            graphics.text(font, Component.translatable("socialmod.settings.client_section"), leftX, 20, Ui.theme().colors().accent());
        }
    }
}
