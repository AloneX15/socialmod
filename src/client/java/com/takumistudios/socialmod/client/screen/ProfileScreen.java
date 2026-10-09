package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.theme.VisualText;

import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.Heads;
import com.takumistudios.socialmod.common.model.ConversationId;
import com.takumistudios.socialmod.common.model.PresenceStatus;
import com.takumistudios.socialmod.common.model.Role;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.common.net.SnapshotDto;
import com.takumistudios.socialmod.common.net.SocialAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Perfil rápido (PLAN 8.2): skin (3D si el jugador está cerca y cargado; si no, la cara), grupo, estado y acciones:
 * mensaje, amistad, favorito, nota, bloquear, invitar y gestión dentro del grupo si hay permisos.
 */
public class ProfileScreen extends SocialChildScreen {
    private static final int WIDTH = 260;

    private final UUID target;
    private final String name;
    private EditBox note;
    private String noteDraft;

    public ProfileScreen(@Nullable Screen parent, UUID target, String name) {
        super(parent, Component.literal(name));
        this.target = target;
        this.name = name;
    }

    private SnapshotDto.@Nullable Friend friend() {
        for (SnapshotDto.Friend friend : ClientState.get().snapshot().friends) {
            if (friend.uuid.equals(target.toString())) {
                return friend;
            }
        }
        return null;
    }

    private boolean incomingRequest() {
        return ClientState.get().snapshot().incoming.stream().anyMatch(r -> r.uuid.equals(target.toString()));
    }

    /** Grupo en el que gestionar al jugador: el del panel si es miembro, si no el grupo principal. */
    private SnapshotDto.@Nullable GroupView contextGroup() {
        if (parent instanceof SocialScreen panel && panel.selectedConversation() != null && panel.selectedConversation().startsWith("g:")) {
            ConversationId id = ConversationId.parse(panel.selectedConversation());
            SnapshotDto.GroupView group = id == null ? null : ClientState.get().group(id.groupId());
            if (group != null) {
                return group;
            }
        }
        return ClientState.get().mainGroup();
    }

    private static SnapshotDto.@Nullable Member member(SnapshotDto.GroupView group, UUID id) {
        for (SnapshotDto.Member member : group.members) {
            if (member.uuid.equals(id.toString())) {
                return member;
            }
        }
        return null;
    }

    @Override
    protected void saveDrafts() {
        if (note != null) {
            noteDraft = note.getValue();
        }
    }

    @Override
    protected void init() {
        super.init();
        ClientState state = ClientState.get();
        boolean self = target.equals(state.selfId());
        int left = panelLeft(WIDTH);
        int y = this.height / 2 - 10;
        List<Button> row1 = new ArrayList<>();
        List<Button> row2 = new ArrayList<>();
        if (!self) {
            UUID me = state.selfId();
            if (me != null) {
                String key = ConversationId.direct(me, target).key();
                row1.add(Ui.button(Component.translatable("socialmod.profile.message"), b -> {
                    if (parent instanceof SocialScreen panel) {
                        com.takumistudios.socialmod.client.compat.ClientCompat.setScreen(panel);
                        panel.select(key);
                    } else {
                        com.takumistudios.socialmod.client.compat.ClientCompat.setScreen(new SocialScreen(key));
                    }
                }).build());
            }
            SnapshotDto.Friend friend = friend();
            if (friend != null) {
                row1.add(Ui.button(Component.translatable(friend.favorite ? "socialmod.profile.unfavorite" : "socialmod.profile.favorite"),
                        b -> ClientNet.action(SocialAction.FRIEND_FAVORITE, target.toString())).build());
                row1.add(Ui.button(Component.translatable("socialmod.profile.remove_friend"),
                        b -> ClientNet.action(SocialAction.FRIEND_REMOVE, target.toString())).build());
            } else if (incomingRequest()) {
                row1.add(Ui.button(Component.translatable("socialmod.profile.accept_friend"),
                        b -> ClientNet.action(SocialAction.FRIEND_ACCEPT, target.toString())).build());
            } else if (state.hasOutgoingRequest(target)) {
                row1.add(Ui.button(Component.translatable("socialmod.profile.cancel_request"),
                        b -> ClientNet.action(SocialAction.FRIEND_DENY, target.toString())).build());
            } else if (!state.isBlocked(target)) {
                row1.add(Ui.button(Component.translatable("socialmod.profile.add_friend"),
                        b -> ClientNet.action(SocialAction.FRIEND_REQUEST, target.toString())).build());
            }
            row1.add(Ui.button(Component.translatable(state.isBlocked(target) ? "socialmod.profile.unblock" : "socialmod.profile.block"),
                    b -> ClientNet.action(state.isBlocked(target) ? SocialAction.UNBLOCK : SocialAction.BLOCK, target.toString())).build());

            Payloads.HelloS2C hello = state.hello();
            if (hello == null || hello.partiesEnabled()) {
                row2.add(Ui.button(Component.translatable("socialmod.profile.invite_party"),
                        b -> ClientNet.action(SocialAction.PARTY_INVITE, target.toString())).build());
            }
            SnapshotDto.GroupView group = contextGroup();
            if (group != null) {
                SnapshotDto.Member member = member(group, target);
                if (member == null && group.myPermissions.contains("invite")) {
                    row2.add(Ui.button(Component.translatable("socialmod.profile.invite_group", group.party ? "Party" : group.tag),
                            b -> ClientNet.action(SocialAction.GROUP_INVITE, group.id, target.toString())).build());
                } else if (member != null && !group.party) {
                    Role mine = Role.byId(group.myRole);
                    Role theirs = Role.byId(member.role);
                    boolean isTeam = state.snapshot().teams.stream().anyMatch(t -> t.id.equals(group.id));
                    if (isTeam && mine == Role.LEADER && theirs != null && theirs != Role.LEADER)
                        row2.add(Ui.button(Component.translatable(theirs == Role.VIP ? "socialmod.profile.revoke_vip" : "socialmod.profile.grant_vip"),
                            b -> ClientNet.action(theirs == Role.VIP ? SocialAction.TEAM_REVOKE_VIP : SocialAction.TEAM_GRANT_VIP, group.id, target.toString())).build());
                    boolean outranks = !isTeam && mine != null && theirs != null && mine.outranks(theirs);
                    if (outranks && group.myPermissions.contains("manage_roles")) {
                        row2.add(Ui.button(Component.translatable("socialmod.profile.promote"),
                                b -> ClientNet.action(SocialAction.GROUP_PROMOTE, group.id, target.toString())).build());
                        row2.add(Ui.button(Component.translatable("socialmod.profile.demote"),
                                b -> ClientNet.action(SocialAction.GROUP_DEMOTE, group.id, target.toString())).build());
                    }
                    if (outranks && group.myPermissions.contains("kick")) {
                        row2.add(Ui.button(Component.translatable("socialmod.profile.kick"),
                                b -> ClientNet.action(SocialAction.GROUP_KICK, group.id, target.toString())).build());
                    }
                    if (!isTeam && mine == Role.LEADER) {
                        row2.add(Ui.button(Component.translatable("socialmod.profile.transfer"),
                                b -> ClientNet.action(SocialAction.GROUP_TRANSFER, group.id, target.toString())).build());
                    }
                }
            }
            if (friend != null) {
                note = new StyledEditBox(this.font, left, y + 52, WIDTH - 64, 18, Component.translatable("socialmod.profile.note"));
                note.setMaxLength(128);
                note.setHint(Component.translatable("socialmod.profile.note").withStyle(ChatFormatting.DARK_GRAY));
                note.setValue(noteDraft != null ? noteDraft : friend.note);
                addRenderableWidget(note);
                addRenderableWidget(Ui.button(Component.translatable("socialmod.profile.save_note"), b -> {
                    noteDraft = null;
                    ClientNet.action(SocialAction.FRIEND_NOTE, target.toString(), note.getValue());
                }).bounds(left + WIDTH - 60, y + 51, 60, 20).build());
            } else {
                note = null;
            }
        }
        layoutRow(row1, left, y);
        layoutRow(row2, left, y + 24);
        addRenderableWidget(Ui.button(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private void layoutRow(List<Button> buttons, int left, int y) {
        if (buttons.isEmpty()) {
            return;
        }
        int gap = 3;
        int w = (WIDTH - gap * (buttons.size() - 1)) / buttons.size();
        int x = left;
        for (Button button : buttons) {
            button.setRectangle(w, 20, x, y);
            addRenderableWidget(button);
            x += w + gap;
        }
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ClientState state = ClientState.get();
        int left = panelLeft(WIDTH);
        int top = this.height / 2 - 80;
        Ui.panel(graphics, left - 6, top - 6, left + WIDTH + 6, this.height / 2 + 80);
        Minecraft minecraft = Minecraft.getInstance();
        Player entity = minecraft.level == null ? null : minecraft.level.getPlayerByUUID(target);
        if (entity != null) {
            // Render 3D solo mientras la pantalla está abierta (PLAN 13)
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, left, top, left + 50, top + 64, 28, 0.0625F, mouseX, mouseY, entity);
        } else {
            Heads.draw(graphics, target, left + 9, top + 12, 32);
        }
        int textX = left + 58;
        VisualText.text(graphics, font, name, textX, top + 6, Ui.title());
        Payloads.TagEntry tag = state.tagOf(target);
        SnapshotDto.GroupView shared = state.sharedMainGroupOf(target);
        if (tag != null && !tag.tag().isEmpty()) {
            VisualText.text(graphics, font, com.takumistudios.socialmod.client.TagRenderer.panelLine(tag.tag(), tag.color(), tag.icon(), ""), textX, top + 18, Ui.theme().colors().text());
        } else if (shared != null) {
            SnapshotDto.Member member = member(shared, target);
            String role = member == null ? "" : " · " + Component.translatable("socialmod.role." + member.role).getString();
            VisualText.text(graphics, font, Ui.trim(font, shared.name + role, WIDTH - 60), textX, top + 18, Ui.readable(shared.color));
        } else if (tag != null) {
            VisualText.text(graphics, font, com.takumistudios.socialmod.client.TagRenderer.panelLine(tag.tag(), tag.color(), tag.icon(), tag.role()), textX, top + 18, Ui.theme().colors().text());
        }
        PresenceStatus status = state.statusOf(target);
        if (target.equals(state.selfId())) {
            status = PresenceStatus.byId(state.snapshot().self.status);
        }
        Ui.status(graphics, font, status, textX, top + 30);
        VisualText.text(graphics, font, Component.translatable("socialmod.status." + status.id()), textX + 10, top + 30, Ui.theme().colors().text());
        Payloads.PresenceEntry presence = state.presenceOf(target);
        if (presence != null && !presence.customStatus().isEmpty()) {
            VisualText.text(graphics, font, Ui.trim(font, "\"" + presence.customStatus() + "\"", WIDTH - 60), textX, top + 42, Ui.theme().colors().muted());
        }
        if (presence != null && !presence.dimension().isEmpty()) {
            VisualText.text(graphics, font, Ui.trim(font, presence.dimension(), WIDTH - 60), textX, top + 54, Ui.theme().colors().muted());
        }
        if (target.equals(state.selfId())) {
            VisualText.text(graphics, font, Component.translatable("socialmod.profile.self"), left, this.height / 2 - 10, Ui.theme().colors().muted());
        }
    }
}
