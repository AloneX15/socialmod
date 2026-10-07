package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.theme.VisualManager;

import com.takumistudios.socialmod.client.theme.VisualText;
import com.takumistudios.socialmod.client.theme.RowTemplates;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import com.takumistudios.socialmod.client.ClientConfig;
import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.Heads;
import com.takumistudios.socialmod.client.compat.MapCompat;
import com.takumistudios.socialmod.client.theme.Theme;
import com.takumistudios.socialmod.common.model.ConversationId;
import com.takumistudios.socialmod.common.model.PresenceStatus;
import com.takumistudios.socialmod.common.model.Role;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.common.net.SnapshotDto;
import com.takumistudios.socialmod.common.net.SocialAction;
import com.takumistudios.socialmod.common.text.MessageFormatter;
import com.takumistudios.socialmod.common.text.SafeMarkdown;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Vista principal (PLAN 8.1): conversaciones, chat y jugadores. Los breakpoints usan el ancho escalado de la GUI
 * (PLAN 8.3): 3 columnas desde 480, 2 columnas desde 320 (jugadores en pestaña) y pestañas a pantalla completa debajo.
 * Respeta un margen reservado para minimapas (config del cliente).
 */
public class SocialScreen extends Screen {
    private int visualInset() { return Math.min(VisualManager.get().panelInset, height < 220 ? 12 : 24); }
    private static int gap() { return Math.max(2, VisualManager.get().padding); }
    private static final int ROW = 11;
    private static final int INPUT_HEIGHT = 18;
    private static int mainButtonHeight() { return VisualManager.get().seriesStyle.equals("christmas")?32:20; }
    /** Mensajes enviados (para recorrerlos con las flechas). Compartido con Quick-Reply. */
    static final List<String> SENT_HISTORY = new ArrayList<>();

    enum Layout { THREE, TWO, TABS }

    enum Tab { CONVERSATIONS, CHAT, PLAYERS }

    private @Nullable String selected;
    private Tab tab = Tab.CHAT;
    private Layout layout = Layout.THREE;
    private final Ui.RowList left = new Ui.RowList();
    private final Ui.RowList right = new Ui.RowList();
    private final Ui.RowList chatRows = new Ui.RowList();
    private int chatScroll;
    /** Botón de silenciar canal en la cabecera del chat (-1 = no se muestra). */
    private int muteX = -1;
    private int muteY;
    /** Botón de chat de voz (Simple Voice Chat) en la cabecera (-1 = no se muestra). */
    private int voiceX = -1;
    private String voiceGroupId = "";
    private boolean voiceActive;
    private EditBox input;
    private EditBox search;
    private String draft = "";
    private String searchText = "";
    private long selectedMessage = -1;
    private long editing = -1;
    private int historyIndex = -1;
    private int lastVersion = -1;
    private long lastTyping;
    private long historyRequestedBefore = -1;
    private boolean panelOpened;
    private boolean openingChild;

    // Rectángulos de las columnas (ancho 0 = oculta)
    private int convX, convW, chatX, chatW, playersX, playersW, top, bottom;
    private int frameLeft, frameTop, frameRight, frameBottom;

    private List<Line> lines = List.of();
    private int linesVersion = -1;
    private int linesWidth = -1;
    private @Nullable String linesConversation;
    private record TemplateMessage(Payloads.MessageView message, RowTemplates.Data data, int height) { }
    private List<TemplateMessage> templateMessages = List.of();
    private int templateVersion = -1, templateWidth = -1, templateHeight;
    private long templateRevision = -1;
    private String templateLanguage = "";
    private @Nullable String templateConversation;

    /** Una línea ya partida del chat. */
    private record Line(FormattedCharSequence text, long messageId, boolean header, @Nullable UUID sender,
                        @Nullable ItemStack item, Payloads.@Nullable AttachmentView coords) {
    }

    public SocialScreen(@Nullable String conversation) {
        super(Component.translatable("socialmod.panel.title"));
        this.selected = conversation;
    }

    public @Nullable String selectedConversation() {
        return selected;
    }

    // =====================================================================
    // Disposición y widgets
    // =====================================================================

    @Override
    protected void init() {
        if (!panelOpened) {
            panelOpened = true;
            ClientNet.action(SocialAction.PANEL_OPEN, "");
        }
        if (selected != null) {
            ClientState.get().setActiveConversation(selected);
            ClientState.ConversationCache cache = ClientState.get().conversation(selected);
            if (!cache.requested) {
                cache.requested = true;
                ClientNet.history(selected, 0);
            }
        }
        ClientConfig.Panel panelConfig = ClientConfig.get().panel;
        int inset = visualInset();
        int columnGap = gap() + 2 * inset;
        int leftEdge = gap() + inset;
        int rightEdge = this.width - gap() - inset - panelConfig.reservedRight;
        top = gap() + inset + panelConfig.reservedTop;
        bottom = this.height - gap() - inset;
        var visual = VisualManager.get();
        if (!visual.mode.equals("full")) {
            int areaW = Math.max(160, rightEdge - leftEdge), areaH = Math.max(120, bottom - top);
            int panelW = Math.min(areaW, Math.max(220, areaW * visual.widthPercent / 100));
            int panelH = Math.min(areaH, Math.max(180, areaH * visual.heightPercent / 100));
            if (visual.mode.equals("sidebar")) { panelW = Math.min(areaW, Math.max(220, areaW * visual.sidebarWidthPercent / 100)); leftEdge = rightEdge - panelW; }
            else { leftEdge += (areaW - panelW) / 2; rightEdge = leftEdge + panelW; }
            top += (areaH - panelH) / 2; bottom = top + panelH;
        }
        frameLeft = leftEdge; frameTop = top; frameRight = rightEdge; frameBottom = bottom;
        int available = rightEdge - leftEdge;
        if (visual.seriesStyle.equals("dedsafio")) top += 18;

        Theme theme = Ui.theme();
        Theme.Column convCol = theme.column("conversations").orElse(Theme.DEFAULT.columns().get(0));
        Theme.Column chatCol = theme.column("chat").orElse(Theme.DEFAULT.columns().get(1));
        Theme.Column playersCol = theme.column("players").orElse(Theme.DEFAULT.columns().get(2));
        int minThree = convCol.minWidth() + chatCol.minWidth() + playersCol.minWidth() + 2 * columnGap;
        int minTwo = convCol.minWidth() + chatCol.minWidth() + columnGap;
        layout = available >= Math.max(480, minThree) ? Layout.THREE : available >= Math.max(320, minTwo) ? Layout.TWO : Layout.TABS;
        if (theme.layout().equals("tabs")) layout = Layout.TABS;
        else if (theme.layout().equals("two_column") && layout == Layout.THREE) layout = Layout.TWO;


        if (layout == Layout.TABS) {
            top += 22;
            convX = chatX = playersX = leftEdge;
            convW = tab == Tab.CONVERSATIONS ? available : 0;
            chatW = tab == Tab.CHAT ? available : 0;
            playersW = tab == Tab.PLAYERS ? available : 0;
            int tabWidth = (available - 2 * gap()) / 3;
            addTab(Tab.CONVERSATIONS, "socialmod.panel.tab.conversations", leftEdge, tabWidth);
            addTab(Tab.CHAT, "socialmod.panel.tab.chat", leftEdge + tabWidth + gap(), tabWidth);
            addTab(Tab.PLAYERS, "socialmod.panel.tab.players", leftEdge + 2 * (tabWidth + gap()), tabWidth);
        } else if (layout == Layout.TWO) {
            int total = convCol.weight() + chatCol.weight();
            convW = convCol.minWidth() + Math.max(0, available - columnGap - convCol.minWidth() - chatCol.minWidth()) * convCol.weight() / total;
            convX = leftEdge;
            int mainX = convX + convW + columnGap;
            int mainW = rightEdge - mainX;
            if (tab == Tab.CONVERSATIONS) {
                tab = Tab.CHAT;
            }
            chatX = playersX = mainX;
            chatW = tab == Tab.CHAT ? mainW : 0;
            playersW = tab == Tab.PLAYERS ? mainW : 0;
            top += 22;
            int tabWidth = (mainW - gap()) / 2;
            addTab(Tab.CHAT, "socialmod.panel.tab.chat", mainX, tabWidth);
            addTab(Tab.PLAYERS, "socialmod.panel.tab.players", mainX + tabWidth + gap(), tabWidth);
        } else {
            int total = convCol.weight() + chatCol.weight() + playersCol.weight();
            int usable = available - 2 * columnGap;
            int extra = Math.max(0, usable - convCol.minWidth() - chatCol.minWidth() - playersCol.minWidth());
            convW = convCol.minWidth() + extra * convCol.weight() / total;
            playersW = playersCol.minWidth() + extra * playersCol.weight() / total;
            chatW = usable - convW - playersW;
            convX = leftEdge;
            chatX = convX + convW + columnGap;
            playersX = chatX + chatW + columnGap;
            playersW = rightEdge - playersX;
        }

        top += mainButtonHeight() + 4 + inset;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.team.title"), b -> openChild(new TeamScreen(this)))
                .bounds(leftEdge, top - mainButtonHeight() - 4 - inset, Math.min(VisualManager.get().seriesStyle.equals("christmas")?140:100, available / 3), mainButtonHeight()).build());
        if (ClientState.get().snapshot().visualAdmin) addRenderableWidget(Ui.button(Component.translatable("socialmod.visual.title"), b -> openChild(com.takumistudios.socialmod.client.compat.fancy.FancyBridge.available() ? new AdvancedCustomizationScreen(this) : new VisualEditorScreen(this)))
                .bounds(leftEdge + Math.min(VisualManager.get().seriesStyle.equals("christmas")?140:100, available / 3) + 4, top - mainButtonHeight() - 4 - inset, Math.min(VisualManager.get().seriesStyle.equals("christmas")?140:100, available / 3), mainButtonHeight()).build());

        // Columna de conversaciones: búsqueda arriba, botones abajo
        if (com.takumistudios.socialmod.client.compat.fancy.FancyBridge.available()) {
            if (convW > 0) addRenderableWidget(new SocialBlockWidget(this, "conversations", convX, top, convW, bottom - top));
            if (chatW > 0) addRenderableWidget(new SocialBlockWidget(this, "chat", chatX, top, chatW, bottom - top));
            if (playersW > 0) addRenderableWidget(new SocialBlockWidget(this, "players", playersX, top, playersW, bottom - top));
        }
        if (convW > 0) {
            search = new StyledEditBox(this.font, convX + 3, top + 3, convW - 6, 14, Component.translatable("socialmod.panel.search"));
            search.setHint(Component.translatable("socialmod.panel.search").withStyle(ChatFormatting.DARK_GRAY));
            search.setMaxLength(32);
            search.setValue(searchText);
            search.setResponder(value -> searchText = value);
            addRenderableWidget(search);
            int buttonW = (convW - 6 - gap()) / 2;
            addRenderableWidget(Ui.button(Component.translatable("socialmod.panel.new_group"),
                    b -> openChild(new CreateGroupScreen(this))).bounds(convX + 3, bottom - mainButtonHeight() - 2, buttonW, mainButtonHeight()).build());
            addRenderableWidget(Ui.button(Component.translatable("socialmod.panel.settings"),
                    b -> openChild(new SettingsScreen(this))).bounds(convX + 3 + buttonW + gap(), bottom - mainButtonHeight() - 2, buttonW, mainButtonHeight()).build());
        } else {
            search = null;
        }

        // Columna de chat: entrada abajo y acciones del mensaje seleccionado
        if (chatW > 0) {
            Payloads.HelloS2C hello = ClientState.get().hello();
            int maxLength = hello == null ? 256 : hello.maxMessageLength();
            boolean sharing = hello == null || hello.sharingEnabled();
            int buttonsW = sharing ? 2 * 22 + gap() : 0;
            input = new StyledEditBox(this.font, chatX + 3, bottom - INPUT_HEIGHT - 3, chatW - 6 - buttonsW - 24, INPUT_HEIGHT,
                    Component.translatable("socialmod.panel.input"));
            input.setMaxLength(maxLength);
            input.setHint(Component.translatable(selected == null ? "socialmod.panel.select_conversation" : "socialmod.panel.input")
                    .withStyle(ChatFormatting.DARK_GRAY));
            input.setValue(draft);
            input.setResponder(this::onInputChanged);
            input.active = selected != null && canWrite();
            addRenderableWidget(input);
            int bx = chatX + 3 + input.getWidth() + 2;
            if (sharing) {
                addRenderableWidget(Ui.button(Component.literal("⌖"), b -> insertToken(MessageFormatter.COORDS_TOKEN))
                        .tooltip(Tooltip.create(Component.translatable("socialmod.panel.share_coords")))
                        .bounds(bx, bottom - INPUT_HEIGHT - 4, 22, 20).build());
                addRenderableWidget(Ui.button(Component.literal("✦"), b -> insertToken(MessageFormatter.ITEM_TOKEN))
                        .tooltip(Tooltip.create(Component.translatable("socialmod.panel.share_item")))
                        .bounds(bx + 22 + gap(), bottom - INPUT_HEIGHT - 4, 22, 20).build());
                bx += buttonsW;
            }
            addRenderableWidget(Ui.button(Component.literal("➤"), b -> submit())
                    .tooltip(Tooltip.create(Component.translatable("socialmod.panel.send")))
                    .bounds(bx + 2, bottom - INPUT_HEIGHT - 4, 20, 20).build());
            if (selected != null) {
                setInitialFocus(input);
            }
            addMessageActions();
        } else {
            input = null;
        }

        // Columna de jugadores: acciones del grupo
        if (playersW > 0) {
            SnapshotDto.GroupView group = selectedGroup();
            if (group != null) {
                int buttonW = (playersW - 6 - gap()) / 2;
                Button invite = Ui.button(Component.translatable("socialmod.panel.invite"),
                        b -> openChild(new InviteScreen(this, group.id, group.party))).bounds(playersX + 3, bottom - mainButtonHeight() - 2, buttonW, mainButtonHeight()).build();
                invite.active = group.myPermissions.contains("invite");
                addRenderableWidget(invite);
                addRenderableWidget(Ui.button(Component.translatable(group.party ? "socialmod.panel.party_settings" : "socialmod.panel.group_settings"),
                        b -> openChild(new GroupSettingsScreen(this, group.id))).bounds(playersX + 3 + buttonW + gap(), bottom - mainButtonHeight() - 2, buttonW, mainButtonHeight()).build());
            } else {
                addRenderableWidget(Ui.button(Component.translatable("socialmod.panel.new_party"),
                        b -> ClientNet.action(SocialAction.PARTY_CREATE, "")).bounds(playersX + 3, bottom - mainButtonHeight() - 2, playersW - 6, mainButtonHeight()).build());
            }
        }
        lastVersion = -1;
    }

    private void addTab(Tab target, String key, int x, int w) {
        Button button = Ui.button(Component.translatable(key), b -> {
            tab = target;
            rebuildWidgets();
        }).selected(tab == target).bounds(x, top - 22, w, 20).build();
        button.active = tab != target;
        addRenderableWidget(button);
    }

    /** Botones de acción cuando hay un mensaje seleccionado (editar, borrar, reportar, copiar, enlace). */
    private void addMessageActions() {
        Payloads.MessageView message = selectedMessageView();
        if (message == null) {
            return;
        }
        UUID self = ClientState.get().selfId();
        boolean own = message.sender().equals(self);
        Payloads.HelloS2C hello = ClientState.get().hello();
        long window = hello == null ? 120_000 : hello.editWindowSeconds() * 1000L;
        boolean inWindow = System.currentTimeMillis() - message.time() <= window;
        List<Button> buttons = new ArrayList<>();
        if (own && inWindow && !message.deleted()) {
            buttons.add(Ui.button(Component.translatable("socialmod.message.edit"), b -> {
                editing = message.id();
                draft = message.text();
                selectedMessage = -1;
                rebuildWidgets();
            }).build());
            buttons.add(Ui.button(Component.translatable("socialmod.message.delete"), b -> {
                ClientNet.send(new Payloads.MessageOpC2S(Payloads.MessageOp.DELETE, selected, message.id(), ""));
                selectedMessage = -1;
                rebuildWidgets();
            }).build());
        }
        if (!own && !message.deleted()) {
            buttons.add(Ui.button(Component.translatable("socialmod.message.report"), b -> {
                ClientNet.action(SocialAction.REPORT_MESSAGE, selected, String.valueOf(message.id()));
                selectedMessage = -1;
                rebuildWidgets();
            }).build());
        }
        if (!message.deleted()) {
            buttons.add(Ui.button(Component.translatable("socialmod.message.copy"), b -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(SafeMarkdown.plain(message.text()));
                selectedMessage = -1;
                rebuildWidgets();
            }).build());
            Payloads.AttachmentView coords = coordsOf(message);
            if (coords != null && MapCompat.waypointsAvailable()) {
                buttons.add(Ui.button(Component.translatable("socialmod.message.waypoint"), b -> {
                    addWaypoint(message, coords);
                    selectedMessage = -1;
                    rebuildWidgets();
                }).build());
            }
            URI link = firstLink(message.text());
            Payloads.HelloS2C info = ClientState.get().hello();
            if (link != null && (info == null || info.linksAllowed())) {
                buttons.add(Ui.button(Component.translatable("socialmod.message.open_link"),
                        b -> ConfirmLinkScreen.confirmLinkNow(this, link)).build());
            }
        }
        buttons.add(Ui.button(Component.literal("✕"), b -> {
            selectedMessage = -1;
            rebuildWidgets();
        }).build());
        int x = chatX + 3;
        int y = bottom - INPUT_HEIGHT - 28;
        for (Button button : buttons) {
            int w = button.getMessage().getString().equals("✕") ? 16 : Math.min(70, this.font.width(button.getMessage()) + 10);
            button.setRectangle(w, 16, x, y);
            x += w + 2;
            addRenderableWidget(button);
        }
    }

    private static @Nullable URI firstLink(String text) {
        for (SafeMarkdown.Span span : SafeMarkdown.parse(text)) {
            if (span.kind() == SafeMarkdown.Kind.LINK) {
                URI uri = MessageFormatter.safeUri(span.text());
                if (uri != null) {
                    return uri;
                }
            }
        }
        return null;
    }

    private void openChild(Screen screen) {
        openingChild = true;
        draft = input == null ? draft : input.getValue();
        com.takumistudios.socialmod.client.compat.ClientCompat.setScreen(screen);
    }

    // =====================================================================
    // Selección y envío
    // =====================================================================

    public void select(@Nullable String conversation) {
        if (conversation != null && conversation.equals(selected)) {
            return;
        }
        selected = conversation;
        selectedMessage = -1;
        editing = -1;
        chatScroll = 0;
        historyRequestedBefore = -1;
        draft = "";
        ClientState.get().setActiveConversation(conversation);
        if (conversation != null) {
            ClientState.ConversationCache cache = ClientState.get().conversation(conversation);
            if (!cache.requested) {
                cache.requested = true;
                ClientNet.history(conversation, 0);
            }
            markRead();
            if (layout != Layout.THREE) {
                tab = Tab.CHAT;
            }
        }
        rebuildWidgets();
    }

    private void markRead() {
        if (selected != null && ClientState.get().markRead(selected)) {
            ClientNet.read(selected, ClientState.get().lastMessageId(selected));
        }
    }

    private boolean canWrite() {
        ConversationId conversation = selected == null ? null : ConversationId.parse(selected);
        if (conversation != null && !conversation.isDirect()) {
            var group = selectedGroup();
            if (group == null || group.channels.stream().noneMatch(c -> c.name.equals(conversation.channel()) && c.canWrite)) return false;
        }
        Payloads.HelloS2C hello = ClientState.get().hello();
        if (hello == null || ClientState.get().snapshot().self.mutedUntil > System.currentTimeMillis()) {
            return false;
        }
        return Minecraft.getInstance().computeChatAbilities().canSendMessages();
    }

    private void onInputChanged(String value) {
        draft = value;
        long now = System.currentTimeMillis();
        if (selected != null && !value.isEmpty() && now - lastTyping > 3000) {
            lastTyping = now;
            ClientNet.typing(selected);
        }
    }

    private void insertToken(String token) {
        if (input != null) {
            input.insertText(token);
            setFocused(input);
        }
    }

    private void submit() {
        if (input == null || selected == null) {
            return;
        }
        String text = input.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        if (editing >= 0) {
            ClientNet.send(new Payloads.MessageOpC2S(Payloads.MessageOp.EDIT, selected, editing, text));
            editing = -1;
        } else {
            ClientNet.message(selected, text);
            SENT_HISTORY.remove(text);
            SENT_HISTORY.addFirst(text);
            while (SENT_HISTORY.size() > 50) {
                SENT_HISTORY.removeLast();
            }
        }
        historyIndex = -1;
        draft = "";
        input.setValue("");
        chatScroll = 0;
    }

    // =====================================================================
    // Entrada
    // =====================================================================

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (com.takumistudios.socialmod.client.SocialKeys.OPEN_PANEL.matches(event)) {
            onClose();
            return true;
        }
        int key = event.key();
        if (input != null && getFocused() == input) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                submit();
                return true;
            }
            if (key == InputConstants.KEY_TAB) {
                completeMention();
                return true;
            }
            if (key == InputConstants.KEY_UP && !SENT_HISTORY.isEmpty()) {
                historyIndex = Math.min(historyIndex + 1, SENT_HISTORY.size() - 1);
                input.setValue(SENT_HISTORY.get(historyIndex));
                return true;
            }
            if (key == InputConstants.KEY_DOWN && historyIndex >= 0) {
                historyIndex--;
                input.setValue(historyIndex >= 0 ? SENT_HISTORY.get(historyIndex) : "");
                return true;
            }
            if (key == InputConstants.KEY_ESCAPE && editing >= 0) {
                editing = -1;
                input.setValue("");
                return true;
            }
        }
        return super.keyPressed(event);
    }

    /** Autocompleta {@code @nombre} con miembros del grupo o jugadores conectados. */
    private void completeMention() {
        String value = input.getValue();
        int cursor = input.getCursorPosition();
        int at = value.lastIndexOf('@', Math.max(0, cursor - 1));
        if (at < 0 || value.substring(at, cursor).contains(" ")) {
            return;
        }
        String prefix = value.substring(at + 1, cursor).toLowerCase(Locale.ROOT);
        for (String name : mentionCandidates()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix) && !name.equalsIgnoreCase(prefix)) {
                input.setValue(value.substring(0, at + 1) + name + " " + value.substring(cursor));
                input.setCursorPosition(at + name.length() + 2);
                return;
            }
        }
    }

    List<String> mentionCandidates() {
        List<String> names = new ArrayList<>();
        SnapshotDto.GroupView group = selectedGroup();
        if (group != null) {
            if (!group.party) {
                names.add(group.tag);
            }
            group.members.forEach(m -> names.add(m.name));
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                names.add(info.getProfile().name());
            }
        }
        return names;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (com.takumistudios.socialmod.client.compat.fancy.FancyBridge.available()) {
            // In 26.x vanilla routes the first hovered component. A passive block underneath
            // another moved block must not capture its click; real controls retain priority.
            var block = topBlock(event.x(), event.y());
            boolean control = children().stream().anyMatch(child -> !(child instanceof SocialBlockWidget)
                && child.isMouseOver(event.x(), event.y())
                && (!(child instanceof net.minecraft.client.gui.components.AbstractWidget widget)
                    || widget.visible && widget.active && !com.takumistudios.socialmod.client.compat.fancy.FancyBridge.hidden(widget)));
            if (block != null && !control && clickBlock(block.kind(), event)) return true;
            return super.mouseClicked(event, doubleClick);
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        boolean rightClick = event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
        double mx = event.x();
        double my = event.y();
        if (blockVisible("chat") && voiceX >= 0 && Ui.inside(mx, my, voiceX - 1, muteY - 1, 10, 10)) {
            ClientNet.action(voiceActive ? SocialAction.VOICE_LEAVE : SocialAction.VOICE_JOIN, voiceGroupId);
            return true;
        }
        if (blockVisible("chat") && muteX >= 0 && selected != null && Ui.inside(mx, my, muteX - 1, muteY - 1, 10, 10)) {
            ClientState.toggleMuted(selected);
            return true;
        }
        if (convW > 0 && blockVisible("conversations") && left.click(mx, my, rightClick)) {
            return true;
        }
        if (playersW > 0 && blockVisible("players") && right.click(mx, my, rightClick)) {
            return true;
        }
        return chatW > 0 && blockVisible("chat") && chatRows.click(mx, my, rightClick);
    }

    private SocialBlockWidget topBlock(double x, double y) {
        var children = children();
        for (int i = children.size() - 1; i >= 0; i--) if (children.get(i) instanceof SocialBlockWidget block && block.visible && !com.takumistudios.socialmod.client.compat.fancy.FancyBridge.hidden(block) && Ui.inside(x, y, block.getX(), block.getY(), block.getWidth(), block.getHeight())) return block;
        return null;
    }
    private boolean clickBlock(String kind, MouseButtonEvent event) {
        double x = event.x(), y = event.y(); boolean rightClick = event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
        if (kind.equals("conversations")) return left.click(x, y, rightClick);
        if (kind.equals("players")) return right.click(x, y, rightClick);
        if (voiceX >= 0 && Ui.inside(x,y,voiceX-1,muteY-1,10,10)) { ClientNet.action(voiceActive ? SocialAction.VOICE_LEAVE : SocialAction.VOICE_JOIN,voiceGroupId); return true; }
        if (muteX >= 0 && selected != null && Ui.inside(x,y,muteX-1,muteY-1,10,10)) { ClientState.toggleMuted(selected); return true; }
        return chatRows.click(x,y,rightClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (com.takumistudios.socialmod.client.compat.fancy.FancyBridge.available()) {
            var block = topBlock(mx,my); if (block == null) return super.mouseScrolled(mx,my,scrollX,scrollY);
            if (block.kind().equals("conversations")) return left.scroll(mx,my,scrollY);
            if (block.kind().equals("players")) return right.scroll(mx,my,scrollY);
            if (chatRows.isOver(mx,my)) { chatScroll=Math.max(0,chatScroll+(int)(scrollY*12)); return true; }
            return false;
        }

        if (blockVisible("conversations") && left.scroll(mx, my, scrollY) || blockVisible("players") && right.scroll(mx, my, scrollY)) {
            return true;
        }
        if (blockVisible("chat") && chatRows.isOver(mx, my)) {
            chatScroll = Math.max(0, chatScroll + (int) (scrollY * 12));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public void tick() {
        ClientState state = ClientState.get();
        if (state.version() != lastVersion) {
            lastVersion = state.version();
            if (selected != null && state.unreadOf(selected) > 0) {
                markRead();
            }
        }
        if (!state.connected()) {
            onClose();
        }
    }

    @Override
    public void removed() {
        ClientState.get().setActiveConversation(null);
        // Al abrir una pantalla hija (perfil, ajustes...) el panel sigue "abierto" para la presencia
        if (!openingChild) {
            ClientNet.action(SocialAction.PANEL_CLOSE, "");
            panelOpened = false;
        }
        openingChild = false;
    }

    @Override
    public void added() {
        super.added();
        ClientState.get().setActiveConversation(selected);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =====================================================================
    // Dibujo
    // =====================================================================

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!VisualManager.get().transparentWorld && VisualManager.get().mode.equals("full")) super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Theme theme = Ui.theme();
        if (VisualManager.get().mode.equals("full")) Ui.background(graphics, this.width, this.height);
        else Ui.panel(graphics, frameLeft - 3, frameTop - 3, frameRight + 3, frameBottom + 3);
        if (VisualManager.get().seriesStyle.equals("dedsafio")) {
            for(int inset=0;inset<3;inset++) graphics.outline(frameLeft-4+inset,frameTop-4+inset,frameRight-frameLeft+8-2*inset,frameBottom-frameTop+8-2*inset,0xFFD1D5DC);
            int badgeX=(frameLeft+frameRight)/2-8, badgeY=Math.max(1,frameTop-22);
            graphics.fill(badgeX,badgeY,badgeX+16,frameTop,0xE0151820);
            graphics.outline(badgeX,badgeY,16,frameTop-badgeY,0xFFD1D5DC);
            for(int i=0;i<5;i++) { graphics.fill(badgeX+3+i,badgeY+5+i,badgeX+4+i,badgeY+7+i,0xFFB52635); graphics.fill(badgeX+12-i,badgeY+5+i,badgeX+13-i,badgeY+7+i,0xFFB52635); }
            for(int x=frameLeft;x<frameRight;x+=48) graphics.fill(x,frameTop-3,Math.min(x+16,frameRight),frameTop,0xFFB52635);
            var team=ClientState.get().snapshot().teams.stream().filter(t -> t.id.equals(ClientState.get().snapshot().self.teamId)).findFirst();
            String label=team.map(t -> t.name).orElse(title.getString());
            VisualText.centeredText(graphics,font,Component.literal(Ui.trim(font,label,frameRight-frameLeft-12)),(frameLeft+frameRight)/2,frameTop+4,Ui.title());
        }
        if (!com.takumistudios.socialmod.client.compat.fancy.FancyBridge.available()) {
        if (convW > 0) {
            Ui.panel(graphics, convX, top, convX + convW, bottom);
            drawConversations(graphics, mouseX, mouseY);
        }
        if (chatW > 0) {
            Ui.panel(graphics, chatX, top, chatX + chatW, bottom);
            drawChat(graphics, mouseX, mouseY);
        }
        if (playersW > 0) {
            Ui.panel(graphics, playersX, top, playersX + playersW, bottom);
            drawPlayers(graphics, mouseX, mouseY);
        }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private boolean blockVisible(String kind) {
        for (var child : children()) if (child instanceof SocialBlockWidget block && block.kind().equals(kind)) return block.visible && !com.takumistudios.socialmod.client.compat.fancy.FancyBridge.hidden(block);
        return !com.takumistudios.socialmod.client.compat.fancy.FancyBridge.available();
    }
    public void drawBlock(String kind, GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h) {
        int oldTop = top, oldBottom = bottom; top = y; bottom = y + h;
        try {
            Ui.panel(graphics, x, y, x + w, y + h);
            switch (kind) {
                case "conversations" -> { convX = x; convW = w; drawConversations(graphics, mouseX, mouseY); }
                case "chat" -> { chatX = x; chatW = w; drawChat(graphics, mouseX, mouseY); }
                case "players" -> { playersX = x; playersW = w; drawPlayers(graphics, mouseX, mouseY); }
                default -> throw new IllegalArgumentException("Unknown block");
            }
        } finally { top = oldTop; bottom = oldBottom; }
    }

    // ---------- Conversaciones ----------

    private void drawConversations(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Theme theme = Ui.theme();
        ClientState state = ClientState.get();
        SnapshotDto snapshot = state.snapshot();
        int x = convX + 3;
        int w = convW - 6;
        int viewTop = top + 20;
        int viewBottom = bottom - mainButtonHeight() - 5;
        left.begin(convX, viewTop, convW, viewBottom - viewTop);
        graphics.enableScissor(convX, viewTop, convX + convW, viewBottom);
        int y = 0;
        String filter = searchText.toLowerCase(Locale.ROOT);

        // Solicitudes e invitaciones
        if (!snapshot.incoming.isEmpty() || !snapshot.groupInvites.isEmpty()) {
            Ui.sectionHeader(graphics, font, Component.translatable("socialmod.panel.section.requests"), x, left.screenY(y), w);
            y += ROW;
            for (SnapshotDto.NameRef request : snapshot.incoming) {
                y = requestRow(graphics, x, w, y, Component.translatable("socialmod.panel.friend_request", request.name).getString(),
                        () -> ClientNet.action(SocialAction.FRIEND_ACCEPT, request.uuid), () -> ClientNet.action(SocialAction.FRIEND_DENY, request.uuid));
            }
            for (SnapshotDto.Invite invite : snapshot.groupInvites) {
                y = requestRow(graphics, x, w, y, Component.translatable("socialmod.panel.group_invite", invite.groupName).getString(),
                        () -> ClientNet.action(SocialAction.GROUP_ACCEPT, invite.groupId), () -> ClientNet.action(SocialAction.GROUP_DECLINE, invite.groupId));
            }
            y += 3;
        }

        // Directos
        Ui.sectionHeader(graphics, font, Component.translatable("socialmod.panel.section.direct"), x, left.screenY(y), w);
        y += ROW;
        UUID self = state.selfId();
        List<String> shownDirect = new ArrayList<>();
        for (SnapshotDto.ConversationView view : snapshot.conversations) {
            if (!view.id.startsWith("dm:") || !view.title.toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            ConversationId id = ConversationId.parse(view.id);
            UUID other = id == null || self == null ? null : id.other(self);
            shownDirect.add(view.id);
            y = conversationRow(graphics, x, w, y, view.id, other, other == null ? PresenceStatus.OFFLINE : state.statusOf(other),
                    view.title, view.preview, view.unread, mouseX, mouseY);
        }
        // Amigos sin conversación reciente: favoritos primero
        if (self != null) {
            List<SnapshotDto.Friend> friends = new ArrayList<>(snapshot.friends);
            friends.sort(Comparator.<SnapshotDto.Friend, Boolean>comparing(f -> !f.favorite)
                    .thenComparing(f -> PresenceStatus.byId(f.status) == PresenceStatus.OFFLINE)
                    .thenComparing(f -> f.name.toLowerCase(Locale.ROOT)));
            for (SnapshotDto.Friend friend : friends) {
                UUID friendId = UUID.fromString(friend.uuid);
                String key = ConversationId.direct(self, friendId).key();
                if (shownDirect.contains(key) || !friend.name.toLowerCase(Locale.ROOT).contains(filter)) {
                    continue;
                }
                String preview = friend.customStatus.isEmpty() ? (friend.favorite ? "★" : "") : "\"" + friend.customStatus + "\"";
                y = conversationRow(graphics, x, w, y, key, friendId, PresenceStatus.byId(friend.status), friend.name, preview, 0, mouseX, mouseY);
            }
        }
        if (shownDirect.isEmpty() && snapshot.friends.isEmpty()) {
            VisualText.text(graphics, font, Ui.trim(font, Component.translatable("socialmod.panel.no_direct").getString(), w), x + 2, left.screenY(y), theme.colors().muted());
            y += ROW;
        }
        y += 3;

        // Grupos y party
        Ui.sectionHeader(graphics, font, Component.translatable("socialmod.panel.section.groups"), x, left.screenY(y), w);
        y += ROW;
        for (SnapshotDto.GroupView group : snapshot.groups) {
            if (!group.name.toLowerCase(Locale.ROOT).contains(filter) && !group.tag.toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            int unread = 0;
            for (SnapshotDto.ChannelView channel : group.channels) {
                unread += state.unreadOf(ConversationId.group(group.id, channel.name).key());
            }
            String label = group.party ? Component.translatable("socialmod.panel.party").getString()
                    : emblem(group) + "[" + group.tag + "] " + group.name;
            int rowY = left.screenY(y);
            String firstChannel = group.channels.isEmpty() ? "general" : group.channels.getFirst().name;
            String firstKey = ConversationId.group(group.id, firstChannel).key();
            boolean groupSelected = selected != null && selected.startsWith("g:" + group.id + ":");
            if (groupSelected && group.channels.size() <= 1) {
                graphics.fill(convX + 1, rowY - 1, convX + convW - 1, rowY + ROW - 1, theme.colors().highlight());
            }
            VisualText.text(graphics, font, Ui.trim(font, label, w - 24), x + 2, rowY, Ui.readable(group.color));
            if (unread > 0) {
                String count = "(" + unread + ")";
                VisualText.text(graphics, font, count, x + w - font.width(count), rowY, theme.colors().unread());
            }
            left.rows.add(Ui.Row.of(convX, y, convW, ROW, () -> select(firstKey)));
            y += ROW;
            if (group.channels.size() > 1) {
                for (SnapshotDto.ChannelView channel : group.channels) {
                    String key = ConversationId.group(group.id, channel.name).key();
                    int cy = left.screenY(y);
                    if (key.equals(selected)) {
                        graphics.fill(convX + 1, cy - 1, convX + convW - 1, cy + ROW - 1, theme.colors().highlight());
                    }
                    boolean channelMuted = ClientState.isMuted(key);
                    VisualText.text(graphics, font, Ui.trim(font, (channelMuted ? "⊘ #" : "#") + channel.name, w - 30), x + 12, cy,
                            channelMuted ? theme.colors().muted() : theme.colors().text());
                    int channelUnread = state.unreadOf(key);
                    if (channelUnread > 0) {
                        String count = "(" + channelUnread + ")";
                        VisualText.text(graphics, font, count, x + w - font.width(count), cy, channelMuted ? theme.colors().muted() : theme.colors().unread());
                    }
                    left.rows.add(Ui.Row.of(convX, y, convW, ROW, () -> select(key)));
                    y += ROW;
                }
            }
        }
        if (snapshot.groups.isEmpty()) {
            VisualText.text(graphics, font, Ui.trim(font, Component.translatable("socialmod.panel.no_groups").getString(), w), x + 2, left.screenY(y), theme.colors().muted());
            y += ROW;
        }
        graphics.disableScissor();
        left.end(y + 4);
    }

    private int requestRow(GuiGraphicsExtractor graphics, int x, int w, int y, String label, Runnable accept, Runnable deny) {
        Theme theme = Ui.theme();
        int rowY = left.screenY(y);
        VisualText.text(graphics, font, Ui.trim(font, label, w - 24), x + 2, rowY, theme.colors().text());
        VisualText.text(graphics, font, "✔", x + w - 20, rowY, Ui.success());
        VisualText.text(graphics, font, "✖", x + w - 8, rowY, Ui.danger());
        left.rows.add(Ui.Row.of(x + w - 22, y, 10, ROW, accept));
        left.rows.add(Ui.Row.of(x + w - 10, y, 10, ROW, deny));
        return y + ROW;
    }

    private int conversationRow(GuiGraphicsExtractor graphics, int x, int w, int y, String key, @Nullable UUID other, PresenceStatus status,
                                String title, String preview, int unread, int mouseX, int mouseY) {
        Theme theme = Ui.theme();
        int rowY = left.screenY(y);
        int height = 2 * ROW;
        if (RowTemplates.enabled("conversation")) {
            var data = new RowTemplates.Data(other, Map.of("name", Component.literal(title), "text", Component.literal(preview), "unread", Component.literal(unread > 0 ? "" + unread : ""), "status", Component.literal(status.symbol())), -1, key.equals(selected), false);
            height = RowTemplates.height("conversation", w, data); RowTemplates.draw(graphics, "conversation", x, rowY, w, data.state(key.equals(selected), Ui.inside(mouseX, mouseY, x, rowY, w, height)));
            Runnable profile = other == null ? null : () -> openChild(new ProfileScreen(this, other, title));
            left.rows.add(new Ui.Row(convX, y, convW, height, () -> select(key), profile)); return y + height;
        }
        if (key.equals(selected)) {
            graphics.fill(convX + 1, rowY - 1, convX + convW - 1, rowY + height - 1, theme.colors().highlight());
        }
        Heads.draw(graphics, other, x + 1, rowY + 1, 16);
        Ui.status(graphics, font, status, x + 20, rowY);
        int textX = x + 28;
        int countWidth = unread > 0 ? font.width("(" + unread + ")") + 2 : 0;
        VisualText.text(graphics, font, Ui.trim(font, title, w - 28 - countWidth), textX, rowY, unread > 0 ? theme.colors().unread() : theme.colors().text());
        if (unread > 0) {
            VisualText.text(graphics, font, "(" + unread + ")", x + w - countWidth + 2, rowY, theme.colors().unread());
        }
        if (!preview.isEmpty()) {
            VisualText.text(graphics, font, Ui.trim(font, preview, w - 28), textX, rowY + ROW - 1, theme.colors().muted());
        }
        Runnable openProfile = other == null ? null : () -> openChild(new ProfileScreen(this, other, title));
        left.rows.add(new Ui.Row(convX, y, convW, height, () -> select(key), openProfile));
        return y + height;
    }

    // ---------- Chat ----------

    /** Emblema del grupo (PLAN 5.2) seguido de un espacio, o vacío. */
    private static String emblem(SnapshotDto.GroupView group) {
        String glyph = com.takumistudios.socialmod.common.model.GroupIcon.glyphOf(group.icon);
        return glyph.isEmpty() ? "" : glyph + " ";
    }

    private SnapshotDto.@Nullable GroupView selectedGroup() {
        if (selected == null || !selected.startsWith("g:")) {
            return null;
        }
        ConversationId id = ConversationId.parse(selected);
        return id == null ? null : ClientState.get().group(id.groupId());
    }

    private Payloads.@Nullable MessageView selectedMessageView() {
        if (selected == null || selectedMessage < 0) {
            return null;
        }
        ClientState.ConversationCache cache = ClientState.get().existing(selected);
        if (cache == null) {
            return null;
        }
        for (Payloads.MessageView message : cache.messages) {
            if (message.id() == selectedMessage) {
                return message;
            }
        }
        return null;
    }

    private void drawChat(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        muteX = -1;
        voiceX = -1;
        Theme theme = Ui.theme();
        ClientState state = ClientState.get();
        int x = chatX + 4;
        int w = chatW - 8;
        int y = top + 4;
        if (selected == null) {
            VisualText.centeredText(graphics, font, Component.translatable("socialmod.panel.select_conversation"), chatX + chatW / 2, (top + bottom) / 2, theme.colors().muted());
            drawNotice(graphics, x, bottom - INPUT_HEIGHT - 18, w);
            chatRows.begin(chatX, top, 0, 0);
            return;
        }
        // Cabecera: título, canal, fijado
        SnapshotDto.GroupView group = selectedGroup();
        String title = state.titleOf(selected);
        if (group != null) {
            ConversationId id = ConversationId.parse(selected);
            String header = group.party ? Component.translatable("socialmod.panel.party").getString()
                    : emblem(group) + Component.translatable("socialmod.panel.group_header", group.name, id == null ? "" : id.channel()).getString();
            boolean voice = state.snapshot().voice;
            VisualText.text(graphics, font, Ui.trim(font, header, w - (voice ? 24 : 12)), x, y, Ui.readable(group.color));
            // Chat de voz del grupo (Simple Voice Chat en el servidor): ☏ entra/sale
            voiceX = -1;
            if (voice) {
                boolean inVoice = group.id.equals(state.snapshot().voiceGroup);
                voiceX = x + w - 20;
                voiceGroupId = group.id;
                voiceActive = inVoice;
                VisualText.text(graphics, font, "☏", voiceX, y, inVoice ? Ui.success() : theme.colors().muted());
                if (Ui.inside(mouseX, mouseY, voiceX - 1, y - 1, 10, 10)) {
                    graphics.setTooltipForNextFrame(font, Component.translatable(inVoice ? "socialmod.voice.leave" : "socialmod.voice.join"), mouseX, mouseY);
                }
            }
            // Silenciar este canal solo en este cliente (PLAN 5.2)
            boolean muted = ClientState.isMuted(selected);
            muteX = x + w - 8;
            muteY = y;
            VisualText.text(graphics, font, muted ? "⊘" : "♪", muteX, muteY, muted ? Ui.danger() : theme.colors().muted());
            if (Ui.inside(mouseX, mouseY, muteX - 1, muteY - 1, 10, 10)) {
                graphics.setTooltipForNextFrame(font, Component.translatable(muted ? "socialmod.panel.unmute_channel" : "socialmod.panel.mute_channel"), mouseX, mouseY);
            }
        } else {
            muteX = -1;
            ConversationId id = ConversationId.parse(selected);
            UUID self = state.selfId();
            UUID other = id == null || self == null ? null : id.other(self);
            if (other != null) {
                Ui.status(graphics, font, state.statusOf(other), x, y);
                Payloads.PresenceEntry presence = state.presenceOf(other);
                String extra = presence == null || presence.customStatus().isEmpty() ? "" : "  \"" + presence.customStatus() + "\"";
                VisualText.text(graphics, font, Ui.trim(font, title + extra, w - 10), x + 8, y, theme.colors().text());
            } else {
                VisualText.text(graphics, font, Ui.trim(font, title, w), x, y, theme.colors().text());
            }
        }
        y += ROW;
        if (group != null && VisualManager.get().seriesStyle.equals("dedsafio")) {
            VisualText.text(graphics,font,Ui.trim(font,Component.translatable("socialmod.panel.motd",group.motd.isEmpty()?Component.translatable("socialmod.panel.no_motd").getString():group.motd).getString(),w),x,y,theme.colors().muted()); y+=ROW;
        }
        if (group != null && !group.pinned.isEmpty()) {
            VisualText.text(graphics, font, Ui.trim(font, Component.translatable("socialmod.panel.pinned", group.pinned).getString(), w), x, y, theme.colors().unread());
            y += ROW;
        }
        if (editing >= 0) {
            VisualText.text(graphics, font, Ui.trim(font, Component.translatable("socialmod.panel.editing").getString(), w), x, y, theme.colors().accent());
            y += ROW;
        }
        graphics.horizontalLine(chatX + 1, chatX + chatW - 2, y, theme.colors().border());
        y += 3;

        // Mensajes (de abajo arriba)
        int actionsHeight = selectedMessage >= 0 ? 20 : 0;
        int listBottom = bottom - INPUT_HEIGHT - 8 - actionsHeight - ROW;
        int listTop = y;
        // Escala de texto propia (PLAN 7.3): el texto del chat se ajusta en unidades sin escalar y se dibuja escalado
        ClientState.ConversationCache cache = state.conversation(selected);
        if (RowTemplates.enabled("message")) drawTemplateMessages(graphics, mouseX, mouseY, listTop, listBottom, cache);
        else {
        float textScale = ClientConfig.get().panel.textScale / 100.0F;
        rebuildLines((int) ((w - 12) / textScale));
        int lineHeight = Math.round((font.lineHeight + 1) * textScale);
        int contentHeight = lines.size() * lineHeight;
        int viewHeight = listBottom - listTop;
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        chatScroll = Mth.clamp(chatScroll, 0, maxScroll);

        if (chatScroll >= maxScroll && cache.hasMore && !cache.messages.isEmpty()) {
            long before = cache.messages.getFirst().id();
            if (historyRequestedBefore != before) {
                historyRequestedBefore = before;
                ClientNet.history(selected, before);
            }
        }
        chatRows.begin(chatX, listTop, chatW, viewHeight);
        chatRows.scroll = 0;
        graphics.enableScissor(chatX + 1, listTop, chatX + chatW - 1, listBottom);
        int lineY = listBottom - contentHeight + chatScroll;
        ItemStack hoverItem = null;
        Payloads.AttachmentView hoverCoords = null;
        for (Line line : lines) {
            if (lineY + lineHeight >= listTop && lineY <= listBottom) {
                if (line.messageId() == selectedMessage) {
                    graphics.fill(chatX + 1, lineY - 1, chatX + chatW - 1, lineY + lineHeight - 1, theme.colors().highlight());
                }
                int textX = x + Math.round(10 * textScale);
                if (line.header()) {
                    Heads.draw(graphics, line.sender(), x, lineY, Math.round(8 * textScale));
                }
                if (line.item() != null && line.header() == false && Ui.inside(mouseX, mouseY, chatX, lineY, chatW, lineHeight)) {
                    hoverItem = line.item();
                }
                if (line.coords() != null && Ui.inside(mouseX, mouseY, chatX, lineY, chatW, lineHeight)) {
                    hoverCoords = line.coords();
                }
                if (textScale == 1.0F) {
                    VisualText.text(graphics, font, line.text(), textX, lineY, theme.colors().text());
                } else {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(textX, lineY);
                    graphics.pose().scale(textScale, textScale);
                    VisualText.text(graphics, font, line.text(), 0, 0, theme.colors().text());
                    graphics.pose().popMatrix();
                }
                long messageId = line.messageId();
                chatRows.rows.add(new Ui.Row(chatX, lineY - listTop, chatW, lineHeight, () -> selectMessage(messageId), () -> selectMessage(messageId)));
            }
            lineY += lineHeight;
        }
        if (cache.messages.isEmpty()) {
            VisualText.centeredText(graphics, font, Component.translatable(cache.requested ? "socialmod.panel.empty" : "socialmod.panel.loading"),
                    chatX + chatW / 2, (listTop + listBottom) / 2, theme.colors().muted());
        }
        graphics.disableScissor();
        chatRows.end(viewHeight);

        if (hoverItem != null) {
            graphics.setTooltipForNextFrame(font, hoverItem, mouseX, mouseY);
        } else if (hoverCoords != null) {
            graphics.setTooltipForNextFrame(font, coordsTooltip(hoverCoords), mouseX, mouseY);
        }
        }

        // Pie: escribiendo / leído / aviso de transparencia
        int footerY = listBottom + 1;
        String footer = typingText(cache);
        if (footer.isEmpty() && group == null) {
            UUID self = state.selfId();
            Payloads.MessageView last = cache.last();
            if (last != null && last.sender().equals(self) && cache.readUpTo >= last.id()) {
                footer = "✔ " + Component.translatable("socialmod.panel.read").getString();
            }
        }
        if (!footer.isEmpty()) {
            VisualText.text(graphics, font, Ui.trim(font, footer, w), x, footerY, theme.colors().muted());
        } else {
            drawNotice(graphics, x, footerY, w);
        }
        if (!canWrite() && input != null) {
            input.setHint(Component.translatable(state.snapshot().self.mutedUntil > System.currentTimeMillis()
                    ? "socialmod.panel.muted" : "socialmod.panel.chat_disabled").withStyle(ChatFormatting.RED));
        }


    }

    private void drawTemplateMessages(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int listTop, int listBottom, ClientState.ConversationCache cache) {
        var state = ClientState.get();
        int width = Math.max(1, chatW - 8);
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (templateVersion != state.version() || templateWidth != width || templateRevision != RowTemplates.revision()
                || !java.util.Objects.equals(templateConversation,selected) || !templateLanguage.equals(language)) {
            var hello = state.hello(); boolean links = hello == null || hello.linksAllowed();
            String selfName = state.snapshot().self.name;
            var rows = new ArrayList<TemplateMessage>(); int total = 0;
            for (var message : cache.messages) {
                Component body = message.deleted() ? Component.translatable("socialmod.message.deleted") : MessageFormatter.format(message.text(), message.attachments(), links, Set.of(selfName));
                if (message.edited()) body = body.copy().append(Component.translatable("socialmod.message.edited"));
                var tag = state.tagOf(message.sender());
                var data = new RowTemplates.Data(message.sender(), Map.of("name", Component.literal(message.senderName()), "time", Component.literal(Ui.time(message.time())), "text", body, "team", Component.literal(tag == null ? "" : tag.tag())), -1, false, false);
                int height = RowTemplates.height("message",width,data); rows.add(new TemplateMessage(message,data,height)); total += height;
            }
            templateMessages = rows; templateHeight = total; templateVersion = state.version(); templateWidth = width;
            templateRevision = RowTemplates.revision(); templateConversation = selected; templateLanguage = language;
        }
        int total = templateHeight;
        int viewHeight = Math.max(1, listBottom - listTop), maxScroll = Math.max(0, total - viewHeight); chatScroll = Mth.clamp(chatScroll, 0, maxScroll);
        if (chatScroll >= maxScroll && cache.hasMore && !cache.messages.isEmpty()) { long before = cache.messages.getFirst().id(); if (historyRequestedBefore != before) { historyRequestedBefore = before; ClientNet.history(selected, before); } }
        chatRows.begin(chatX, listTop, chatW, viewHeight); chatRows.scroll = 0;
        graphics.enableScissor(chatX + 1, listTop, chatX + chatW - 1, listBottom);
        int y = listBottom - total + chatScroll;
        for (var row : templateMessages) {
            if (y + row.height >= listTop && y <= listBottom) {
                RowTemplates.draw(graphics, "message", chatX + 4, y, width, row.data.state(row.message.id() == selectedMessage, Ui.inside(mouseX, mouseY, chatX, y, chatW, row.height)));
                long id = row.message.id(); chatRows.rows.add(new Ui.Row(chatX, y - listTop, chatW, row.height, () -> selectMessage(id), () -> selectMessage(id)));
                if (Ui.inside(mouseX, mouseY, chatX, y, chatW, row.height)) for (var attachment : row.message.attachments()) {
                    if (attachment.isItem() && attachment.item() != null) graphics.setTooltipForNextFrame(font, attachment.item().create(), mouseX, mouseY);
                    else if (!attachment.isItem()) graphics.setTooltipForNextFrame(font, coordsTooltip(attachment), mouseX, mouseY);
                }
            }
            y += row.height;
        }
        graphics.disableScissor(); chatRows.end(viewHeight);
    }

    /** Aviso de transparencia (PLAN 4.2) y del modo spy si el servidor lo activó (PLAN 9). */
    private void drawNotice(GuiGraphicsExtractor graphics, int x, int y, int w) {
        Payloads.HelloS2C hello = ClientState.get().hello();
        Component notice = hello != null && hello.spyActive()
                ? Component.translatable("socialmod.panel.notice_spy").withStyle(ChatFormatting.GOLD)
                : Component.translatable("socialmod.panel.notice");
        VisualText.text(graphics, font, Ui.trim(font, notice.getString(), w), x, y, hello != null && hello.spyActive() ? Ui.warning() : Ui.theme().colors().muted());
    }

    private String typingText(ClientState.ConversationCache cache) {
        long now = System.currentTimeMillis();
        List<String> names = new ArrayList<>();
        cache.typing.values().removeIf(t -> t.until() < now);
        cache.typing.values().forEach(t -> names.add(t.name()));
        if (names.isEmpty()) {
            return "";
        }
        return Component.translatable(names.size() == 1 ? "socialmod.panel.typing_one" : "socialmod.panel.typing_many", String.join(", ", names)).getString();
    }

    /** Sin integración con mapas: distancia y dirección desde la posición actual (PLAN 5.5). */
    private Component coordsTooltip(Payloads.AttachmentView coords) {
        Minecraft minecraft = Minecraft.getInstance();
        MutableComponent text = Component.literal(coords.x() + ", " + coords.y() + ", " + coords.z() + "  (" + coords.dimension() + ")");
        if (minecraft.player != null && minecraft.level != null && minecraft.level.dimension().identifier().toString().equals(coords.dimension())) {
            double dx = coords.x() + 0.5 - minecraft.player.getX();
            double dz = coords.z() + 0.5 - minecraft.player.getZ();
            int distance = (int) Math.sqrt(dx * dx + dz * dz);
            String[] directions = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            double angle = Math.toDegrees(Math.atan2(-dx, dz));
            String direction = directions[Math.floorMod((int) Math.round(angle / 45.0), 8)];
            text.append("\n").append(Component.translatable("socialmod.coords.distance", distance, direction));
        }
        text.append("\n").append(Component.translatable(MapCompat.waypointsAvailable() ? "socialmod.coords.waypoint_hint" : "socialmod.coords.copy_hint")
                .withStyle(ChatFormatting.GRAY));
        return text;
    }

    private static Payloads.@Nullable AttachmentView coordsOf(Payloads.MessageView message) {
        for (Payloads.AttachmentView attachment : message.attachments()) {
            if (!attachment.isItem()) {
                return attachment;
            }
        }
        return null;
    }

    /** Waypoint en Xaero's Minimap (se ve también en el World Map); si no se puede, copia las coordenadas. */
    private void addWaypoint(Payloads.MessageView message, Payloads.AttachmentView coords) {
        Minecraft minecraft = Minecraft.getInstance();
        SnapshotDto.GroupView group = selectedGroup();
        int color = group != null ? group.color : 0x55FF55;
        if (MapCompat.addWaypoint(message.senderName(), coords.dimension(), coords.x(), coords.y(), coords.z(), color)) {
            if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(Component.translatable("socialmod.coords.waypoint_added", message.senderName())
                        .withStyle(ChatFormatting.GREEN));
            }
            return;
        }
        minecraft.keyboardHandler.setClipboard(coords.x() + " " + coords.y() + " " + coords.z());
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.translatable("socialmod.coords.waypoint_failed").withStyle(ChatFormatting.YELLOW));
        }
    }

    private void selectMessage(long id) {
        if (selectedMessage == id) {
            // Segundo clic sobre unas coordenadas: waypoint en el mapa (Xaero) o copiarlas al portapapeles
            Payloads.MessageView message = selectedMessageView();
            Payloads.AttachmentView coords = message == null ? null : coordsOf(message);
            if (coords != null) {
                if (MapCompat.waypointsAvailable()) {
                    addWaypoint(message, coords);
                } else {
                    Minecraft.getInstance().keyboardHandler.setClipboard(coords.x() + " " + coords.y() + " " + coords.z());
                }
            }
            return;
        }
        selectedMessage = id;
        rebuildWidgets();
    }

    private void rebuildLines(int width) {
        ClientState state = ClientState.get();
        if (linesVersion == state.version() && linesWidth == width && selected != null && selected.equals(linesConversation)) {
            return;
        }
        linesVersion = state.version();
        linesWidth = width;
        linesConversation = selected;
        List<Line> result = new ArrayList<>();
        ClientState.ConversationCache cache = state.existing(selected);
        if (cache == null) {
            lines = result;
            return;
        }
        Theme theme = Ui.theme();
        Payloads.HelloS2C hello = state.hello();
        boolean links = hello == null || hello.linksAllowed();
        String selfName = state.snapshot().self.name.toLowerCase(Locale.ROOT);
        UUID previousSender = null;
        long previousTime = 0;
        for (Payloads.MessageView message : cache.messages) {
            boolean header = !message.sender().equals(previousSender) || message.time() - previousTime > 120_000;
            if (header) {
                MutableComponent headerText = Component.literal(message.senderName().isEmpty() ? "?" : message.senderName())
                        .withColor(theme.colors().accent() & 0xFFFFFF)
                        .append(Component.literal("  " + Ui.time(message.time())).withColor(theme.colors().muted() & 0xFFFFFF));
                result.add(new Line(headerText.getVisualOrderText(), message.id(), true, message.sender(), null, null));
            }
            previousSender = message.sender();
            previousTime = message.time();
            MutableComponent body;
            if (message.deleted()) {
                body = Component.translatable("socialmod.message.deleted").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
            } else {
                body = MessageFormatter.format(message.text(), message.attachments(), links, Set.of(selfName));
                if (message.edited()) {
                    body.append(Component.literal(" ").append(Component.translatable("socialmod.message.edited")).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            ItemStack item = null;
            Payloads.AttachmentView coords = null;
            for (Payloads.AttachmentView attachment : message.attachments()) {
                if (attachment.isItem() && attachment.item() != null && item == null) {
                    item = attachment.item().create();
                } else if (!attachment.isItem() && coords == null) {
                    coords = attachment;
                }
            }
            for (FormattedCharSequence part : font.split(body, Math.max(40, width))) {
                result.add(new Line(part, message.id(), false, message.sender(), item, coords));
            }
        }
        lines = result;
    }

    // ---------- Jugadores ----------

    private void drawPlayers(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Theme theme = Ui.theme();
        ClientState state = ClientState.get();
        int x = playersX + 3;
        int w = playersW - 6;
        int viewTop = top + 3;
        int viewBottom = bottom - mainButtonHeight() - 5;
        right.begin(playersX, viewTop, playersW, viewBottom - viewTop);
        graphics.enableScissor(playersX, viewTop, playersX + playersW, viewBottom);
        int y = 0;
        String filter = searchText.toLowerCase(Locale.ROOT);

        SnapshotDto.GroupView group = selectedGroup();
        if (group != null) {
            Ui.sectionHeader(graphics, font, Component.translatable("socialmod.panel.section.members", group.members.size()), x, right.screenY(y), w);
            y += ROW;
            List<SnapshotDto.Member> members = new ArrayList<>(group.members);
            members.sort(Comparator.<SnapshotDto.Member>comparingInt(m -> roleOrder(m.role)).thenComparing(m -> !m.online).thenComparing(m -> m.name));
            for (SnapshotDto.Member member : members) {
                if (!member.name.toLowerCase(Locale.ROOT).contains(filter)) {
                    continue;
                }
                int rowY = right.screenY(y);
                Role role = Role.byId(member.role);
                PresenceStatus status = PresenceStatus.byId(member.status);
                if (RowTemplates.enabled("player")) {
                    UUID id = UUID.fromString(member.uuid);
                    var data = new RowTemplates.Data(id, Map.of("name", Component.literal(member.name), "role", Component.literal(role == null ? "?" : role.letter()), "status", Component.literal(status.symbol())), -1, false, false);
                    int height = RowTemplates.height("player", w, data); RowTemplates.draw(graphics, "player", x, rowY, w, data.state(false, Ui.inside(mouseX, mouseY, x, rowY, w, height)));
                    right.rows.add(Ui.Row.of(playersX, y, playersW, height, () -> openChild(new ProfileScreen(this, id, member.name)))); y += height; continue;
                }
                VisualText.text(graphics, font, "[" + (role == null ? "?" : role.letter()) + "]", x, rowY, theme.colors().muted());
                Ui.status(graphics, font, status, x + 16, rowY);
                VisualText.text(graphics, font, Ui.trim(font, member.name, w - 26), x + 24, rowY, member.online ? theme.colors().text() : theme.colors().muted());
                UUID id = UUID.fromString(member.uuid);
                right.rows.add(Ui.Row.of(playersX, y, playersW, ROW, () -> openChild(new ProfileScreen(this, id, member.name))));
                y += ROW;
            }
            if (!group.events.isEmpty()) {
                y += 3;
                Ui.sectionHeader(graphics, font, Component.translatable("socialmod.panel.section.events"), x, right.screenY(y), w);
                y += ROW;
                for (SnapshotDto.EventView event : group.events) {
                    long minutes = Math.max(0, (event.startsAt - System.currentTimeMillis()) / 60_000);
                    VisualText.text(graphics, font, Ui.trim(font, event.title + " (" + Component.translatable("socialmod.time.in_minutes", minutes).getString() + ")", w),
                            x, right.screenY(y), Ui.event());
                    y += ROW;
                }
            }
            y += 3;
        }

        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        List<PlayerInfo> online = connection == null ? List.of() : new ArrayList<>(connection.getListedOnlinePlayers());
        online.sort(Comparator.comparing(info -> info.getProfile().name().toLowerCase(Locale.ROOT)));
        Ui.sectionHeader(graphics, font, Component.translatable("socialmod.panel.section.online", online.size()), x, right.screenY(y), w);
        y += ROW;
        for (PlayerInfo info : online) {
            String name = info.getProfile().name();
            if (!name.toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            UUID id = info.getProfile().id();
            int rowY = right.screenY(y);
            PresenceStatus status = state.statusOf(id);
            if (RowTemplates.enabled("player")) {
                var tag = state.tagOf(id);
                var data = new RowTemplates.Data(id, Map.of("name", Component.literal(name), "team", Component.literal(tag == null ? "" : tag.tag()), "status", Component.literal(state.statusOf(id).symbol())), -1, false, false);
                int height = RowTemplates.height("player", w, data); RowTemplates.draw(graphics, "player", x, rowY, w, data.state(false, Ui.inside(mouseX, mouseY, x, rowY, w, height)));
                right.rows.add(Ui.Row.of(playersX, y, playersW, height, () -> openChild(new ProfileScreen(this, id, name)))); y += height; continue;
            }
            Heads.draw(graphics, id, x, rowY, 8);
            if (status != PresenceStatus.OFFLINE) {
                Ui.status(graphics, font, status, x + 10, rowY);
            } else {
                VisualText.text(graphics, font, "·", x + 11, rowY, theme.colors().muted());
            }
            String label = (state.isFriend(id) ? "★ " : "") + name;
            VisualText.text(graphics, font, Ui.trim(font, label, w - 20), x + 18, rowY, theme.colors().text());
            Payloads.TagEntry tag = state.tagOf(id);
            if (tag != null && playersW > 110) {
                String glyph = com.takumistudios.socialmod.common.model.GroupIcon.glyphOf(tag.icon());
                String tagText = (glyph.isEmpty() ? "" : glyph + " ") + "[" + tag.tag() + "]";
                int tagW = font.width(tagText);
                if (font.width(label) + tagW + 22 < w) {
                    VisualText.text(graphics, font, tagText, x + w - tagW, rowY, Ui.readable(tag.color()));
                }
            }
            right.rows.add(Ui.Row.of(playersX, y, playersW, ROW, () -> openChild(new ProfileScreen(this, id, name))));
            y += ROW;
        }
        graphics.disableScissor();
        right.end(y + 4);
    }

    private static int roleOrder(String role) {
        Role parsed = Role.byId(role);
        return parsed == null ? 9 : parsed.ordinal();
    }
}
