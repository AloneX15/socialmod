package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.theme.AppearanceMode;
import com.takumistudios.socialmod.common.net.SnapshotDto;
import com.takumistudios.socialmod.common.net.SocialAction;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import java.util.*;
import java.util.function.Supplier;

/** Native screens are the single source of form controls, drafts and permission checks. */
public final class SocialComponents {
    public static final List<String> MODULES = List.of("conversations", "chat", "players", "search", "teams", "team_management", "profile", "settings", "create_group", "group", "invite", "tag", "banner", "appearance", "basic", "rows");
    public static final List<String> DATA = List.of("team_name", "team_tag", "team_icon", "player_name", "status", "conversation", "unread");
    private static int depth;
    private static boolean presenceSubscribed;
    private static final Map<Screen, Map<String, Form>> FORMS = new LinkedHashMap<>();
    private static final Map<Screen, java.lang.ref.WeakReference<Screen>> OWNERS = new WeakHashMap<>();
    private static final Map<Screen,String> SELECTIONS = new WeakHashMap<>();
    public static void selected(Screen screen,String value) { if(embedded()) SELECTIONS.put(owner(screen),value); }
    public static boolean embedded() { return depth > 0; }
    public static <T> T scoped(Supplier<T> work) { depth++; try { return work.get(); } finally { depth--; } }
    public static void scoped(Runnable work) { scoped(() -> { work.run(); return null; }); }
    public static boolean supported(Screen screen) { return screen instanceof SocialScreen || screen instanceof SocialChildScreen; }
    public static Screen owner(Screen screen) { var ref = OWNERS.get(screen); return ref == null || ref.get() == null ? screen : ref.get(); }
    public static String selection(Screen host) {
        if(SELECTIONS.containsKey(host)) return SELECTIONS.get(host);
        for (int i = 0; host != null && i < 16; i++) {
            if (host instanceof TeamScreen teams && !teams.selectedTeam().isEmpty()) return teams.selectedTeam();
            if (host instanceof SocialScreen social) return Objects.toString(social.selectedConversation(), "");
            host = host instanceof SocialChildScreen child ? child.componentParent() : null;
        }
        return Objects.toString(ClientState.get().activeConversation(), "");
    }
    public static String target(Screen host, String context, String fixed) {
        return switch (context) { case "fixed" -> fixed; case "selected" -> selection(host); default -> ClientState.get().snapshot().self.teamId; };
    }
    public static SnapshotDto.TeamView team(Screen host, String context, String fixed) {
        String id = target(host, context, fixed);
        if (id.startsWith("g:")) { int end = id.indexOf(':', 2); id = end < 0 ? id.substring(2) : id.substring(2, end); }
        String key = id;
        return ClientState.get().snapshot().teams.stream().filter(t -> t.id.equals(key)).findFirst().orElse(null);
    }
    public static String text(Screen host, String data, String context, String fixed) {
        var state = ClientState.get(); var team = team(host, context, fixed);
        String selected = context.equals("self") ? "" : context.equals("fixed") ? fixed : selection(host);
        String player = resolve(host, "profile", context, fixed);
        return switch (data) {
            case "team_name", "team_tag" -> team == null ? "" : team.name;
            case "team_icon" -> team == null ? "" : team.icon;
            case "player_name" -> player.isEmpty() ? "" : playerName(player);
            case "status" -> player.isEmpty() ? "" : player.equals(state.snapshot().self.uuid) ? state.snapshot().self.status : state.snapshot().friends.stream().filter(f -> f.uuid.equals(player)).map(f -> f.status).findFirst().orElse("offline");
            case "conversation" -> selected.isEmpty() ? "" : state.titleOf(selected);
            case "unread" -> selected.isEmpty() ? String.valueOf(state.totalUnread()) : String.valueOf(state.unreadOf(selected));
            default -> "";
        };
    }
    private static String playerName(String uuid) {
        var state = ClientState.get().snapshot(); if (state.self.uuid.equals(uuid)) return state.self.name;
        for (var f : state.friends) if (f.uuid.equals(uuid)) return f.name;
        for (var g : state.groups) for (var m : g.members) if (m.uuid.equals(uuid)) return m.name;
        return uuid;
    }
    public static Form form(Screen host, String module, String context, String fixed, String name) {
        if (!supported(host) || !ClientState.get().connected() || AppearanceMode.original()) return null;
        if (!FORMS.containsKey(host) && FORMS.size() >= 8) {
            var oldest = FORMS.keySet().iterator().next();
            for (var old : FORMS.remove(oldest).values()) old.close();
        }
        var forms = FORMS.computeIfAbsent(host, h -> new LinkedHashMap<>());
        String key = module + "/" + name;
        String resolved = resolve(host,module,context,fixed);
        Form current = forms.get(key);
        if (module.equals("basic") && !ClientState.get().snapshot().visualAdmin || module.equals("team_management") && !ClientState.get().snapshot().teamAdmin || (module.equals("banner") ? !teamBannerError(host,context,fixed).isEmpty() : module.equals("tag") && !mayEditTeam(host,context,fixed))) {
            if(current!=null) { current.close();forms.remove(key); } return null;
        }
        if (current == null || !current.target.equals(resolved)) {
            if (current != null) current.close();
            if (forms.size() >= 64 && current == null) return null;
            Screen screen = create(host, module, resolved);
            if (screen == null) { forms.remove(key); return null; }
            current = new Form(host, screen, module, resolved); forms.put(key, current);
        }
        return current;
    }
    public static String resolve(Screen host,String module,String context,String fixed) {
        if(module.equals("quick_reply") && context.equals("self")) return Objects.toString(ClientState.get().activeConversation(), "");
        if(module.equals("profile")) {
            if(context.equals("fixed"))return fixed;
            if(context.equals("selected")) { var id=com.takumistudios.socialmod.common.model.ConversationId.parse(selection(host));return id!=null && id.isDirect() && ClientState.get().selfId()!=null?id.other(ClientState.get().selfId()).toString():""; }
            return ClientState.get().snapshot().self.uuid;
        }
        if(List.of("group","invite","chat","quick_reply").contains(module)) return context.equals("fixed")?fixed:context.equals("selected")?selection(host):ClientState.get().snapshot().self.mainGroup;
        return target(host,context,fixed);
    }
    private static boolean mayEditTeam(Screen host,String context,String fixed) {
        return teamEditError(host,context,fixed).isEmpty();
    }
    /** A local explanation only; the server independently checks permissions on Save. */
    public static String teamEditError(Screen host,String context,String fixed) {
        var team=team(host,context,fixed);
        if(team==null) return "socialmod.team." + (target(host,context,fixed).isBlank() ? context.equals("self") ? "no_team" : "select_team" : "not_found");
        if(team.archived) return "socialmod.team.archived";
        return ClientState.get().snapshot().teamAdmin || team.leader.equals(ClientState.get().snapshot().self.uuid) ? "" : "socialmod.team.banner_permission";
    }
    public static String teamBannerError(Screen host, String context, String fixed) {
        String error = teamEditError(host, context, fixed);
        var team = team(host, context, fixed);
        var group = team == null ? null : ClientState.get().group(team.id);
        return error.equals("socialmod.team.banner_permission") && group != null && group.myRole.equals("vip") ? "" : error;
    }
    public static Screen create(Screen host, String module, String target) {
        String groupId = target.startsWith("g:") ? target.substring(2).split(":", 2)[0] : target;
        var group = ClientState.get().group(groupId);
        var team = ClientState.get().snapshot().teams.stream().filter(t -> t.id.equals(groupId)).findFirst().orElse(null);
        boolean teamEdit = team != null && !team.archived && (ClientState.get().snapshot().teamAdmin || team.leader.equals(ClientState.get().snapshot().self.uuid));
        return switch (module) {
            case "conversations" -> { var screen=new SocialScreen(null);screen.prepareComponent("conversations");yield screen; }
            case "players" -> {
                String conversation = target.startsWith("g:") || target.startsWith("dm:") ? target : group == null || group.channels.isEmpty() ? null : "g:" + group.id + ":" + group.channels.getFirst().name;
                var screen=new SocialScreen(conversation);screen.prepareComponent("players");yield screen;
            }
            case "chat" -> {
                String conversation = target.startsWith("g:") || target.startsWith("dm:") ? target : group == null || group.channels.isEmpty() ? null : "g:" + group.id + ":" + group.channels.getFirst().name;
                yield conversation == null || com.takumistudios.socialmod.common.model.ConversationId.parse(conversation) == null ? null : new SocialScreen(conversation);
            }
            case "advanced" -> new AdvancedCustomizationScreen(host);
            case "quick_reply" -> com.takumistudios.socialmod.common.model.ConversationId.parse(target) == null ? null : new QuickReplyScreen(target);
            case "search" -> new PlayerSearchScreen(host);
            case "teams" -> { var screen = new TeamScreen(host); screen.componentSelect(groupId); yield screen; }
            case "team_management" -> { if (!ClientState.get().snapshot().teamAdmin) yield null; var screen = new TeamManagementScreen(host); screen.componentSelect(groupId); yield screen; }
            case "profile" -> { try { yield new ProfileScreen(host, UUID.fromString(target), playerName(target)); } catch (IllegalArgumentException e) { yield null; } }
            case "settings" -> new SettingsScreen(host);
            case "create_group" -> new CreateGroupScreen(host);
            case "group" -> group == null ? null : new GroupSettingsScreen(host, groupId);
            case "invite" -> group == null ? null : new InviteScreen(host, groupId, group.party);
            case "appearance" -> new AppearanceScreen(host);
            case "basic" -> ClientState.get().snapshot().visualAdmin ? new VisualEditorScreen(host) : null;
            case "rows" -> new RowTemplateScreen(host);
            case "banner" -> !(teamEdit || team != null && !team.archived && group != null && group.myRole.equals("vip")) ? null : new BannerEditorScreen(host, team.banner, value -> ClientNet.action(SocialAction.TEAM_BANNER, team.id, com.takumistudios.socialmod.common.model.VisualDesign.GSON.toJson(value)));
            case "tag" -> !teamEdit ? null : new TagStyleScreen(host, team.name, team.color, team.icon, "", (rgb, icon) -> ClientNet.action(SocialAction.TEAM_STYLE, team.id, icon + ";" + String.format("#%06X", rgb & 0xFFFFFF)), team.banner, value -> ClientNet.action(SocialAction.TEAM_BANNER, team.id, com.takumistudios.socialmod.common.model.VisualDesign.GSON.toJson(value)));
            default -> null;
        };
    }
    public static void searchResult(com.takumistudios.socialmod.common.model.PlayerSearch.Result result) {
        var forms = FORMS.get(ClientCompat.currentScreen()); if (forms == null) return;
        for (var form : forms.values()) if (form.screen instanceof PlayerSearchScreen search) scoped(() -> search.accept(result));
    }
    public static void subscriptions(long tick) {
        Screen host=ClientCompat.currentScreen();boolean needed=false;
        var forms=FORMS.get(host);
        if(forms!=null && supported(host) && !AppearanceMode.original()) for(var form:forms.values())
            if(form.lastTick>=tick-1 && (form.module.equals("players") || form.module.equals("conversations")))needed=true;
        if(host instanceof SocialScreen) { presenceSubscribed=false;return; }
        if(needed!=presenceSubscribed) { presenceSubscribed=needed;ClientNet.action(needed?SocialAction.PANEL_OPEN:SocialAction.PANEL_CLOSE,""); }
    }
    public static void clear() { for (var forms : FORMS.values()) for (var form : forms.values()) form.close(); FORMS.clear(); OWNERS.clear(); SELECTIONS.clear();if(presenceSubscribed){presenceSubscribed=false;ClientNet.action(SocialAction.PANEL_CLOSE,"");} }
    public static final class Form {
        private final Map<AbstractWidget,Long> detached = new WeakHashMap<>();
        public void detach(AbstractWidget widget, long tick) { detached.put(widget,tick); }
        public <T> T originalControls(Supplier<T> draw, long tick) {
            var hidden = new ArrayList<AbstractWidget>();
            detached.forEach((widget,stamp) -> { if (stamp >= tick - 1 && widget.visible) { hidden.add(widget); widget.visible = false; } });
            try { return draw.get(); } finally { hidden.forEach(widget -> widget.visible = true); }
        }
        public <T> T contentOnly(Supplier<T> work) {
            var hidden = new ArrayList<AbstractWidget>();
            for (var widget : controls()) if (!(widget instanceof SocialBlockWidget) && widget.visible) { hidden.add(widget); widget.visible=false; }
            try { return work.get(); } finally { for(var widget : hidden) widget.visible=true; }
        }
        public void render(Runnable draw, long tick) { originalControls(() -> { draw.run(); return null; }, tick); }
        public final Screen screen;
        public final String module, target;
        public final Screen host;
        private long lastTick = Long.MIN_VALUE;
        private Form(Screen host, Screen screen, String module, String target) {
            this.host = host; this.screen = screen; this.module = module; this.target = target;
            int canvasWidth=screen instanceof SocialScreen || screen instanceof VisualEditorScreen ? 540 : 400;
            OWNERS.put(screen, new java.lang.ref.WeakReference<>(host));
            if (screen instanceof SocialScreen social) social.prepareComponent(module);
            scoped(() -> screen.init(canvasWidth, 340));
            if(screen instanceof SocialScreen social && social.selectedConversation()!=null) {
                var cache=ClientState.get().conversation(social.selectedConversation());if(!cache.requested) { cache.requested=true;ClientNet.history(social.selectedConversation(),0); }
            }
        }
        public void tick(long tick) { if (tick == lastTick) return; lastTick = tick; scoped(screen::tick); }
        public List<AbstractWidget> controls() { return Screens.getWidgets(screen); }
        public String key(AbstractWidget widget) {
            String explicit=com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identifier(widget);
            String base = !explicit.isEmpty()?explicit:widget.getMessage().getContents() instanceof TranslatableContents t ? t.getKey() : widget.getMessage().getString();
            int occurrence = 0;
            for (var w : controls()) { String id=com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identifier(w);String label = !id.isEmpty()?id:w.getMessage().getContents() instanceof TranslatableContents t ? t.getKey() : w.getMessage().getString(); if (base.equals(label)) occurrence++; if (w == widget) break; }
            return base + "/" + occurrence;
        }
        public AbstractWidget control(String key) { for (var widget : controls()) if (key(widget).equals(key)) return widget; return null; }
        public int[] region() { return screen instanceof SocialScreen social ? social.componentRegion(module) : new int[]{0, 0, screen.width, screen.height}; }
        public void close() { scoped(screen::removed); if(screen instanceof VisualEditorScreen)com.takumistudios.socialmod.client.theme.VisualManager.preview(null); OWNERS.remove(screen); }
    }
    private SocialComponents() { }
}
