package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.theme.VisualText;

import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.common.net.SocialAction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Paged TEAM catalog and staff controls. Every action is revalidated by the server. */
public class TeamScreen extends SocialChildScreen {
    private int page;
    private boolean management;
    private String selected = "";
    private String loadedSelection = "";
    public String selectedTeam() { return selected; }
    public void componentSelect(String id) { selected = id; }
    private EditBox name, player;
    private String nameDraft = "", playerDraft = "";
    private int color = 0x55FF55;
    private String icon = "shield";
    private com.takumistudios.socialmod.common.model.TeamBanner banner = new com.takumistudios.socialmod.common.model.TeamBanner();
    public TeamScreen(Screen parent) { this(parent, false); }
    protected TeamScreen(Screen parent, boolean management) { super(parent, Component.translatable(management ? "socialmod.team.manage" : "socialmod.team.title")); this.management = management; }
    @Override protected void saveDrafts() { if (name != null) nameDraft = name.getValue(); if (player != null) playerDraft = player.getValue(); }
    @Override protected void init() {
        super.init();
        var state = ClientState.get().snapshot();
        int w = Math.min(320, width - 16), x = (width - w) / 2;
        management = management && state.teamAdmin;
        if (management && selected.isEmpty()) selected = state.self.teamId;
        var selectedTeam = state.teams.stream().filter(t -> t.id.equals(selected)).findFirst().orElse(null);
        boolean loadSelection = management && selectedTeam != null && !loadedSelection.equals(selected);
        if (loadSelection) {
            loadedSelection = selected; nameDraft = selectedTeam.name;
            color = selectedTeam.color; icon = selectedTeam.icon; banner = selectedTeam.banner.copy();
        }
        if (state.teamAdmin && !management) addRenderableWidget(Ui.button(Component.translatable("socialmod.team.manage"), b -> {
            saveDrafts(); var screen = new TeamManagementScreen(this); screen.componentSelect(selected); ClientCompat.setScreen(screen);
        }).bounds(x + w - 72, 4, 72, 20).build());
        int rows = Math.max(1, (height - (management ? 188 : 166)) / 36);
        if (loadSelection) page = state.teams.indexOf(selectedTeam) / rows;
        page = Math.min(page, Math.max(0, (state.teams.size() - 1) / rows));
        int y = 42;
        for (var team : state.teams.stream().skip((long) page * rows).limit(rows).toList()) {
            var button = Ui.button(Component.literal((selected.equals(team.id) ? "\u2713 " : "") + (team.archived ? "[A] " : "") + team.name + " (" + team.members + ")").withColor(team.color), b -> {
                selected = team.id;
                loadedSelection = selected;
                SocialComponents.selected(this,team.id);
                color = team.color; icon = team.icon;
                banner = team.banner.copy();
                if (management) nameDraft = team.name;
                if (!state.teamAdmin && !state.self.teamChosen && !team.archived) confirm(SocialAction.TEAM_CHOOSE, team.id, "");
                else { playerDraft = player == null ? playerDraft : player.getValue(); rebuildWidgets(); }
            }).bounds(x, y, w, 34).build();
            com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identify(button, "team_" + team.id);
            addRenderableWidget(button);
            addRenderableWidget(new BannerWidget(x + 4, y + 2, 15, 30, team.banner, "team_banner_" + team.id));
            y += 36;
        }
        addRenderableWidget(Ui.button(Component.literal("<"), b -> { saveDrafts(); page = Math.max(0, page - 1); rebuildWidgets(); }).bounds(x, y, 28, 20).build());
        addRenderableWidget(Ui.button(Component.literal(">"), b -> { saveDrafts(); page++; rebuildWidgets(); }).bounds(x + w - 28, y, 28, 20).build());
        y += 24;
        name = new StyledEditBox(font, x, y, management ? w / 2 - 2 : w - 84, 20, Component.translatable("socialmod.team.name")); name.setMaxLength(48); name.setValue(nameDraft);
        name.setHint(Ui.hint(Component.translatable("socialmod.team.name"))); addRenderableWidget(name);
        if (!management) {
            player = null;
            addRenderableWidget(Ui.button(Component.translatable("socialmod.group_settings.style"), b -> {
                saveDrafts(); ClientCompat.setScreen(new TagStyleScreen(this, nameDraft, color, icon, "", (rgb, chosen) -> { color = rgb; icon = chosen; }, banner, value -> banner = value));
            }).bounds(x + w - 80, y, 80, 20).build()); y += 24;
            var create = Ui.button(Component.translatable("socialmod.team.create"), b -> confirm(SocialAction.TEAM_CREATE, name.getValue(), icon + ";" + String.format("#%06X", color & 0xFFFFFF) + ";" + com.takumistudios.socialmod.common.model.VisualDesign.GSON.toJson(banner)))
                    .bounds(x, y, state.teamAdmin && !state.self.teamChosen ? w / 2 - 2 : w, 20).build();
            create.active = state.teamAdmin || !state.self.teamChosen; addRenderableWidget(create);
            if (state.teamAdmin && !state.self.teamChosen) addRenderableWidget(Ui.button(Component.translatable("socialmod.team.title"), b -> confirm(SocialAction.TEAM_CHOOSE, selected, "")).bounds(x + w / 2, y, w / 2, 20).build());
        } else {
            player = new StyledEditBox(font, x + w / 2 + 2, y, w / 2 - 2, 20, Component.translatable("socialmod.team.player")); player.setMaxLength(36); player.setValue(playerDraft);
            player.setHint(Ui.hint(Component.translatable("socialmod.team.player"))); addRenderableWidget(player); y += 24;
            String[] labels = {"assign", "reset", "archive", "restore", "rename"};
            SocialAction[] actions = {SocialAction.TEAM_ASSIGN, SocialAction.TEAM_RESET, SocialAction.TEAM_ARCHIVE, SocialAction.TEAM_RESTORE, SocialAction.TEAM_RENAME};
            for (int i = 0; i < actions.length; i++) {
                final SocialAction action = actions[i];
                var actionButton = Ui.button(Component.translatable("socialmod.team." + labels[i]), b -> {
                    String a = action == SocialAction.TEAM_ASSIGN || action == SocialAction.TEAM_RESET ? player.getValue() : selected;
                    String value = action == SocialAction.TEAM_ASSIGN ? selected : action == SocialAction.TEAM_RENAME ? name.getValue() : "";
                    confirm(action, a, value);
                }).bounds(x + i * w / 5, y, w / 5 - 2, 20).build();
                if (action != SocialAction.TEAM_RESET && selectedTeam == null) {
                    actionButton.active = false; actionButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("socialmod.team.select_team")));
                }
                addRenderableWidget(actionButton);
            }
            y += 24;
            EditBox limit = new StyledEditBox(font, x, y, w / 3 - 2, 20, Component.translatable("socialmod.team.max")); limit.setMaxLength(4); limit.setValue(String.valueOf(state.maxTeams)); addRenderableWidget(limit);
            addRenderableWidget(Ui.button(Component.translatable("socialmod.team.max"), b -> ClientNet.action(SocialAction.TEAM_LIMIT, limit.getValue())).bounds(x + w / 3, y, w / 3 - 2, 20).build());
            var styleButton = Ui.button(Component.translatable("socialmod.group_settings.style"), b -> {
                String targetId = selected;
                saveDrafts(); ClientCompat.setScreen(new TagStyleScreen(this, nameDraft, color, icon, "", (rgb, chosen) -> {
                    color = rgb; icon = chosen; ClientNet.action(SocialAction.TEAM_STYLE, targetId, chosen + ";" + String.format("#%06X", rgb & 0xFFFFFF));
                }));
            }).bounds(x + 2 * w / 3, y, w / 3, 20).build();
            if (selectedTeam == null) { styleButton.active = false; styleButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("socialmod.team.select_team"))); }
            addRenderableWidget(styleButton);
        }
        addRenderableWidget(Ui.button(Component.translatable("gui.back"), b -> onClose()).bounds(width / 2 - 45, height - 24, 90, 20).build());
        var target = state.teams.stream().filter(t -> t.id.equals(selected.isEmpty() ? state.self.teamId : selected)).findFirst();
        if (target.isPresent() && !target.get().archived && (state.teamAdmin || target.get().leader.equals(state.self.uuid))) {
            var team = target.get();
            addRenderableWidget(Ui.button(Component.translatable("socialmod.banner.edit"), b -> { saveDrafts(); ClientCompat.setScreen(new BannerEditorScreen(this, team.banner, value -> ClientNet.action(SocialAction.TEAM_BANNER, team.id, com.takumistudios.socialmod.common.model.VisualDesign.GSON.toJson(value)))); })
                .bounds(x, height - 48, w, 20).build());
        }
    }
    private void confirm(SocialAction action, String a, String b) {
        saveDrafts();
        ClientCompat.setScreen(new ConfirmScreen(ok -> {
            if (ok) ClientNet.action(action, a, b);
            ClientCompat.setScreen(this);
        }, Component.translatable("socialmod.team.confirm"), Component.translatable("socialmod.team.confirm_action", Component.translatable("socialmod.team.action." + action.name().toLowerCase(java.util.Locale.ROOT)), a, b)));
    }
    @Override protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var state = ClientState.get().snapshot();
        var current = state.teams.stream().filter(t -> t.id.equals(management ? selected : state.self.teamId)).findFirst();
        Ui.title(graphics, font, management ? title : current.<Component>map(t -> Component.translatable("socialmod.team.current", t.name)).orElse(title), width / 2, 8);
        Component help = management ? current.<Component>map(t -> Component.translatable("socialmod.team.editing", t.name)).orElseGet(() -> Component.translatable("socialmod.team.manage_help"))
                : Component.translatable(state.self.teamChosen ? "socialmod.team.locked" : "socialmod.team.choose_help");
        VisualText.centeredText(graphics, font, help, width / 2, 24, Ui.theme().colors().muted());
    }
}
