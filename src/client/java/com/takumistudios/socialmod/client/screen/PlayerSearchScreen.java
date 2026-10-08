package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.common.model.PlayerSearch;
import com.takumistudios.socialmod.common.net.Payloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Server directory; ids prevent stale results from replacing a newer query or another screen. */
public final class PlayerSearchScreen extends SocialChildScreen {
    private static int nextRequest;
    private EditBox query;
    private String text = "";
    private int request, page, offset;
    private long due = System.currentTimeMillis(), sentAt;
    private boolean waiting, more;
    private String status = "socialmod.search.loading";
    private List<PlayerSearch.Entry> entries = List.of();
    public PlayerSearchScreen(Screen parent) { super(parent, Component.translatable("socialmod.search.title")); }
    @Override protected boolean rebuildOnChange() { return false; }
    private int rows() { return Math.max(1, (height - 110) / 22); }
    @Override protected void init() {
        super.init();
        int w = Math.min(360, width - 20), x = (width - w) / 2;
        query = new StyledEditBox(font, x, 30, w, 20, title); query.setMaxLength(32); query.setValue(text);
        query.setHint(Component.translatable("socialmod.search.hint"));
        query.setResponder(value -> { text = value; page = offset = 0; entries = List.of(); more = false; waiting = false; request = ++nextRequest; due = System.currentTimeMillis() + 300; status = "socialmod.search.loading";
            for (var widget : net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(this)) if (widget.getY() >= 54 && widget.getY() < height - 48) widget.visible = false;
        });
        addRenderableWidget(query); setInitialFocus(query);
        int y = 54;
        for (var entry : entries.stream().skip(offset).limit(rows()).toList()) {
            var button = Ui.button(Component.literal(entry.name()).append(Component.literal(" · ")).append(Component.translatable("socialmod.status." + entry.status())), b -> {
                var self = ClientState.get().selfId(); if (self == null) return;
                String key = com.takumistudios.socialmod.common.model.ConversationId.direct(self, entry.id()).key();
                ClientState.get().conversation(key).title = entry.name();
                com.takumistudios.socialmod.client.ClientApiImpl.INSTANCE.openPrivateChat(entry.id());
            }).tooltip(Tooltip.create(Component.translatable("socialmod.search.open_dm"))).bounds(x, y, w, 20).build();
            com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identify(button, "search_player_" + entry.id());
            addRenderableWidget(button); y += 22;
        }
        var previous = addRenderableWidget(Ui.button(Component.literal("<"), b -> {
            if (offset > 0) { offset = Math.max(0, offset - rows()); rebuildWidgets(); }
            else { page--; offset = 0; send(); }
        }).bounds(x, height - 48, 35, 20).tooltip(Tooltip.create(Component.translatable("socialmod.search.previous"))).build()); previous.active = !waiting && (page > 0 || offset > 0);
        var next = addRenderableWidget(Ui.button(Component.literal(">"), b -> {
            if (offset + rows() < entries.size()) { offset += rows(); rebuildWidgets(); }
            else { page++; offset = 0; send(); }
        }).bounds(x + w - 35, height - 48, 35, 20).tooltip(Tooltip.create(Component.translatable("socialmod.search.next"))).build()); next.active = !waiting && (offset + rows() < entries.size() || more);
        addRenderableWidget(Ui.button(Component.translatable("gui.back"), b -> onClose()).bounds(x, height - 24, w, 20).build());
    }
    private void send() {
        request = ++nextRequest; waiting = true; due = 0; sentAt = System.currentTimeMillis(); status = "socialmod.search.loading";
        if (!ClientNet.send(new Payloads.PlayerSearchC2S(request, text, page))) { waiting = false; status = "socialmod.search.failed"; }
        rebuildWidgets();
    }
    public void accept(PlayerSearch.Result result) {
        if (!waiting || result.request() != request || result.page() != page) return;
        waiting = false; entries = result.entries(); more = result.more(); status = entries.isEmpty() ? "socialmod.search.empty" : ""; rebuildWidgets();
    }
    @Override public void tick() {
        super.tick(); long now = System.currentTimeMillis();
        if (due > 0 && now >= due) send();
        else if (waiting && now - sentAt > 10000) { waiting = false; status = "socialmod.search.failed"; rebuildWidgets(); }
    }
    @Override protected void drawContent(GuiGraphicsExtractor graphics, int mx, int my) {
        Ui.title(graphics, font, title, width / 2, 10);
        if (!status.isEmpty()) graphics.centeredText(font, Component.translatable(status), width / 2, height - 62, Ui.theme().colors().muted());
    }
}
