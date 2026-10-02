package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.Heads;
import com.takumistudios.socialmod.common.net.SnapshotDto;
import com.takumistudios.socialmod.common.net.SocialAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Invitar a un grupo o party: escribir un nombre o elegir un jugador conectado que no sea miembro. */
public class InviteScreen extends SocialChildScreen {
    private static final int WIDTH = 220;
    private final String groupId;
    private final boolean party;
    private final Ui.RowList rows = new Ui.RowList();
    private EditBox name;
    private String nameDraft = "";

    public InviteScreen(@Nullable Screen parent, String groupId, boolean party) {
        super(parent, Component.translatable("socialmod.invite.title"));
        this.groupId = groupId;
        this.party = party;
    }

    @Override
    protected void saveDrafts() {
        nameDraft = name.getValue();
    }

    @Override
    protected void init() {
        int left = panelLeft(WIDTH);
        name = new EditBox(this.font, left, 30, WIDTH - 64, 20, Component.translatable("socialmod.invite.name"));
        name.setMaxLength(16);
        name.setHint(Component.translatable("socialmod.invite.name").withStyle(ChatFormatting.DARK_GRAY));
        name.setValue(nameDraft);
        name.setResponder(value -> nameDraft = value);
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.translatable("socialmod.invite.send"), b -> invite(name.getValue().trim()))
                .bounds(left + WIDTH - 60, 30, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
        setInitialFocus(name);
    }

    private void invite(String target) {
        if (target.matches("[A-Za-z0-9_]{1,16}") || target.length() == 36) {
            ClientNet.action(SocialAction.GROUP_INVITE, groupId, target);
            name.setValue("");
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick) || rows.click(event.x(), event.y(), false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        return rows.scroll(mx, my, scrollY) || super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.centeredText(font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        int left = panelLeft(WIDTH);
        int top = 58;
        int bottom = this.height - 36;
        Ui.panel(graphics, left - 2, top - 2, left + WIDTH + 2, bottom + 2);
        SnapshotDto.GroupView group = ClientState.get().group(groupId);
        List<String> members = new ArrayList<>();
        if (group != null) {
            group.members.forEach(m -> members.add(m.uuid));
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        List<PlayerInfo> online = connection == null ? List.of() : new ArrayList<>(connection.getListedOnlinePlayers());
        online.sort(Comparator.comparing(info -> info.getProfile().name().toLowerCase(Locale.ROOT)));
        rows.begin(left, top, WIDTH, bottom - top);
        graphics.enableScissor(left, top, left + WIDTH, bottom);
        int y = 0;
        String filter = nameDraft.toLowerCase(Locale.ROOT);
        for (PlayerInfo info : online) {
            UUID id = info.getProfile().id();
            String playerName = info.getProfile().name();
            if (members.contains(id.toString()) || id.equals(ClientState.get().selfId()) || !playerName.toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            int rowY = rows.screenY(y);
            if (Ui.inside(mouseX, mouseY, left, rowY - 1, WIDTH, 12)) {
                graphics.fill(left, rowY - 1, left + WIDTH, rowY + 11, Ui.theme().colors().highlight());
            }
            Heads.draw(graphics, id, left + 3, rowY, 8);
            graphics.text(font, playerName, left + 14, rowY, Ui.theme().colors().text());
            graphics.text(font, "+", left + WIDTH - 10, rowY, Ui.theme().colors().accent());
            rows.rows.add(Ui.Row.of(left, y, WIDTH, 12, () -> invite(playerName)));
            y += 12;
        }
        graphics.disableScissor();
        rows.end(y);
        if (y == 0) {
            graphics.centeredText(font, Component.translatable("socialmod.invite.nobody"), this.width / 2, top + 10, Ui.theme().colors().muted());
        }
        if (party) {
            graphics.text(font, Component.translatable("socialmod.invite.party_hint"), left, bottom + 6, Ui.theme().colors().muted());
        }
    }
}
