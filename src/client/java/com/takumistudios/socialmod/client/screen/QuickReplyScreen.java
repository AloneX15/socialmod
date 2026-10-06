package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.theme.VisualText;

import com.mojang.blaze3d.platform.InputConstants;
import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.common.model.ConversationId;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.common.net.SnapshotDto;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Respuesta Rápida (PLAN 7.1): una barra en la parte inferior, sin pausar el juego ni oscurecer el mundo.
 * {@code Tab} cambia entre las últimas 5 conversaciones (o completa una {@code @mención}), las flechas recorren
 * el historial de mensajes enviados, {@code Enter} envía y {@code Esc} cierra.
 */
public class QuickReplyScreen extends Screen {
    private static final int HEIGHT = 34;
    private String conversation;
    private EditBox input;
    private int historyIndex = -1;
    private String draft = "";

    public QuickReplyScreen(String conversation) {
        super(Component.translatable("socialmod.quick_reply.title"));
        this.conversation = conversation;
    }

    @Override
    protected void init() {
        super.init();
        int width = Math.min(360, this.width - 20);
        int left = (this.width - width) / 2;
        int top = this.height - HEIGHT - 40;
        input = new StyledEditBox(this.font, left + 4, top + 14, width - 8, 16, Component.translatable("socialmod.panel.input"));
        Payloads.HelloS2C hello = ClientState.get().hello();
        input.setMaxLength(hello == null ? 256 : hello.maxMessageLength());
        input.setValue(draft);
        input.setResponder(value -> draft = value);
        addRenderableWidget(input);
        setInitialFocus(input);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            String text = input.getValue().trim();
            if (!text.isEmpty()) {
                ClientNet.message(conversation, text);
                SocialScreen.SENT_HISTORY.remove(text);
                SocialScreen.SENT_HISTORY.addFirst(text);
            }
            onClose();
            return true;
        }
        if (key == InputConstants.KEY_TAB) {
            if (!completeMention()) {
                cycleConversation();
            }
            return true;
        }
        if (key == InputConstants.KEY_UP && !SocialScreen.SENT_HISTORY.isEmpty()) {
            historyIndex = Math.min(historyIndex + 1, SocialScreen.SENT_HISTORY.size() - 1);
            input.setValue(SocialScreen.SENT_HISTORY.get(historyIndex));
            return true;
        }
        if (key == InputConstants.KEY_DOWN && historyIndex >= 0) {
            historyIndex--;
            input.setValue(historyIndex >= 0 ? SocialScreen.SENT_HISTORY.get(historyIndex) : "");
            return true;
        }
        return super.keyPressed(event);
    }

    private void cycleConversation() {
        List<String> targets = ClientState.get().replyTargets();
        if (targets.size() < 2) {
            return;
        }
        int index = targets.indexOf(conversation);
        conversation = targets.get((index + 1) % targets.size());
    }

    private boolean completeMention() {
        String value = input.getValue();
        int cursor = input.getCursorPosition();
        int at = value.lastIndexOf('@', Math.max(0, cursor - 1));
        if (at < 0 || value.substring(at, cursor).contains(" ")) {
            return false;
        }
        String prefix = value.substring(at + 1, cursor).toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        ConversationId id = ConversationId.parse(conversation);
        if (id != null && !id.isDirect()) {
            SnapshotDto.GroupView group = ClientState.get().group(id.groupId());
            if (group != null) {
                group.members.forEach(m -> names.add(m.name));
            }
        }
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                names.add(info.getProfile().name());
            }
        }
        for (String name : names) {
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix) && !name.equalsIgnoreCase(prefix)) {
                input.setValue(value.substring(0, at + 1) + name + " " + value.substring(cursor));
                input.setCursorPosition(at + name.length() + 2);
                return true;
            }
        }
        return false;
    }

    /** Sin fondo: el mundo sigue visible. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int width = Math.min(360, this.width - 20);
        int left = (this.width - width) / 2;
        int top = this.height - HEIGHT - 40;
        graphics.fill(left, top, left + width, top + HEIGHT, Ui.theme().toast().background());
        graphics.outline(left, top, width, HEIGHT, Ui.theme().colors().border());
        String title = Component.translatable("socialmod.quick_reply.replying", ClientState.get().titleOf(conversation)).getString();
        String hint = Component.translatable("socialmod.quick_reply.hint").getString();
        VisualText.text(graphics, font, Ui.trim(font, title, width - font.width(hint) - 14), left + 4, top + 3, Ui.theme().colors().accent());
        VisualText.text(graphics, font, hint, left + width - font.width(hint) - 4, top + 3, Ui.theme().colors().muted());
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        if (!ClientState.get().connected()) {
            ClientCompat.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
