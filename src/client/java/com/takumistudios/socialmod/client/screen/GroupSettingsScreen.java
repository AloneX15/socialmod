package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.common.model.Role;
import com.takumistudios.socialmod.common.net.SnapshotDto;
import com.takumistudios.socialmod.common.net.SocialAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Ajustes del grupo: mensaje del día, descripción, mensaje fijado, color y etiqueta, canales con permisos por rol,
 * eventos y salir/disolver. Los campos que el rol no puede cambiar se muestran desactivados.
 */
public class GroupSettingsScreen extends SocialChildScreen {
    private static final int WIDTH = 300;
    private final String groupId;
    private final Map<String, EditBox> fields = new HashMap<>();
    private final Map<String, String> drafts = new HashMap<>();
    private final Ui.RowList channelRows = new Ui.RowList();
    private Role channelRole = Role.RECRUIT;
    private boolean confirmDisband;

    public GroupSettingsScreen(@Nullable Screen parent, String groupId) {
        super(parent, Component.translatable("socialmod.group_settings.title"));
        this.groupId = groupId;
    }

    @Override
    protected void saveDrafts() {
        fields.forEach((key, box) -> drafts.put(key, box.getValue()));
    }

    private EditBox field(String key, int x, int y, int w, int max, String initial, boolean enabled) {
        EditBox box = new EditBox(this.font, x, y, w, 18, Component.translatable("socialmod.group_settings." + key));
        box.setMaxLength(max);
        box.setHint(Component.translatable("socialmod.group_settings." + key).withStyle(ChatFormatting.DARK_GRAY));
        box.setValue(drafts.getOrDefault(key, initial));
        box.active = enabled;
        box.setEditable(enabled);
        fields.put(key, box);
        addRenderableWidget(box);
        return box;
    }

    @Override
    protected void init() {
        fields.clear();
        SnapshotDto.GroupView group = ClientState.get().group(groupId);
        if (group == null) {
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                    .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
            return;
        }
        boolean edit = group.myPermissions.contains("edit_info");
        boolean pin = group.myPermissions.contains("pin");
        boolean channels = group.myPermissions.contains("manage_channels") && !group.party;
        boolean events = group.myPermissions.contains("manage_events");
        boolean leader = "leader".equals(group.myRole);
        int left = panelLeft(WIDTH);
        int y = 28;
        int saveX = left + WIDTH - 50;
        int boxW = WIDTH - 54;

        field("motd", left, y, boxW, 128, group.motd, edit);
        addRenderableWidget(saveButton(saveX, y, edit, () -> ClientNet.action(SocialAction.GROUP_SET_MOTD, groupId, value("motd"))));
        y += 22;
        field("description", left, y, boxW, 256, group.description, edit);
        addRenderableWidget(saveButton(saveX, y, edit, () -> ClientNet.action(SocialAction.GROUP_SET_DESCRIPTION, groupId, value("description"))));
        y += 22;
        field("pinned", left, y, boxW, 200, group.pinned, pin);
        addRenderableWidget(saveButton(saveX, y, pin, () -> ClientNet.action(SocialAction.GROUP_PIN, groupId, value("pinned"))));
        y += 22;
        if (!group.party) {
            int half = (boxW - 4) / 2;
            field("color", left, y, half - 52, 16, String.format("#%06X", group.color & 0xFFFFFF), edit);
            addRenderableWidget(saveButton(left + half - 50, y, edit, () -> ClientNet.action(SocialAction.GROUP_SET_COLOR, groupId, value("color"))));
            field("tag", left + half + 4, y, half - 52, 5, group.tag, edit);
            addRenderableWidget(saveButton(left + 2 * half - 46, y, edit, () -> ClientNet.action(SocialAction.GROUP_SET_TAG, groupId, value("tag"))));
            y += 26;

            // Canales
            field("channel", left, y, boxW - 80, 16, "", channels);
            Button role = Button.builder(Component.translatable("socialmod.role." + channelRole.id()), b -> {
                channelRole = Role.byOrdinal((channelRole.ordinal() + Role.values().length - 1) % Role.values().length);
                saveDrafts();
                rebuildWidgets();
            }).bounds(left + boxW - 78, y, 76, 18).build();
            role.active = channels;
            addRenderableWidget(role);
            addRenderableWidget(saveButton(saveX, y, channels, () -> {
                ClientNet.action(SocialAction.GROUP_CHANNEL_CREATE, groupId, value("channel") + " " + channelRole.id());
                drafts.put("channel", "");
            }, "socialmod.group_settings.add"));
            y += 22 + Math.min(4, group.channels.size()) * 11 + 4;

            // Eventos
            field("event_minutes", left, y, 40, 5, "", events);
            field("event_title", left + 44, y, boxW - 44, 64, "", events);
            addRenderableWidget(saveButton(saveX, y, events, () -> {
                ClientNet.action(SocialAction.GROUP_EVENT_CREATE, groupId, value("event_minutes") + " " + value("event_title"));
                drafts.put("event_minutes", "");
                drafts.put("event_title", "");
            }, "socialmod.group_settings.add"));
            y += 26;
        }

        int buttonW = (WIDTH - 8) / 3;
        if (!group.party) {
            addRenderableWidget(Button.builder(Component.translatable("socialmod.group_settings.set_main"),
                    b -> ClientNet.action(SocialAction.GROUP_SET_MAIN, groupId)).bounds(left, y, buttonW, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("socialmod.group_settings.leave"), b -> {
            ClientNet.action(SocialAction.GROUP_LEAVE, groupId);
            onClose();
        }).bounds(left + buttonW + 4, y, buttonW, 20).build());
        Button disband = Button.builder(Component.translatable(confirmDisband ? "socialmod.group_settings.disband_confirm" : "socialmod.group_settings.disband")
                .withStyle(ChatFormatting.RED), b -> {
            if (confirmDisband) {
                ClientNet.action(SocialAction.GROUP_DISBAND, groupId);
                onClose();
            } else {
                confirmDisband = true;
                saveDrafts();
                rebuildWidgets();
            }
        }).bounds(left + 2 * (buttonW + 4), y, buttonW, 20).build();
        disband.active = leader;
        addRenderableWidget(disband);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private String value(String key) {
        EditBox box = fields.get(key);
        return box == null ? "" : box.getValue().trim();
    }

    private Button saveButton(int x, int y, boolean active, Runnable action) {
        return saveButton(x, y, active, action, "socialmod.group_settings.save");
    }

    private Button saveButton(int x, int y, boolean active, Runnable action, String key) {
        Button button = Button.builder(Component.translatable(key), b -> {
            drafts.clear();
            action.run();
        }).bounds(x, y, 50, 18).build();
        button.active = active;
        return button;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick) || channelRows.click(event.x(), event.y(), false);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        SnapshotDto.GroupView group = ClientState.get().group(groupId);
        if (group == null) {
            graphics.centeredText(font, Component.translatable("socialmod.group_settings.gone"), this.width / 2, this.height / 2, Ui.theme().colors().muted());
            return;
        }
        int left = panelLeft(WIDTH);
        String header = group.party ? Component.translatable("socialmod.panel.party").getString() : "[" + group.tag + "] " + group.name;
        graphics.centeredText(font, Component.literal(header), this.width / 2, 12, (group.color & 0xFFFFFF) | 0xFF000000);
        if (group.party) {
            return;
        }
        // Lista de canales con botón de borrar
        int y = 28 + 22 * 3 + 26 + 22;
        channelRows.begin(left, y, WIDTH, 4 * 11);
        boolean canDelete = group.myPermissions.contains("manage_channels") && group.channels.size() > 1;
        int shown = 0;
        for (SnapshotDto.ChannelView channel : group.channels) {
            if (shown >= 4) {
                break;
            }
            int rowY = y + shown * 11;
            graphics.text(font, "#" + channel.name + "  (" + Component.translatable("socialmod.role." + channel.minRole).getString() + "+)",
                    left + 4, rowY, Ui.theme().colors().text());
            if (canDelete) {
                graphics.text(font, "✖", left + WIDTH - 60, rowY, 0xFFFF5555);
                String name = channel.name;
                channelRows.rows.add(Ui.Row.of(left + WIDTH - 62, shown * 11, 10, 11,
                        () -> ClientNet.action(SocialAction.GROUP_CHANNEL_DELETE, groupId, name)));
            }
            shown++;
        }
        channelRows.end(shown * 11);
    }
}
