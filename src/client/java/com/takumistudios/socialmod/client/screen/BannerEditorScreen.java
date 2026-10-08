package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.common.model.TeamBanner;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import java.util.List;
import java.util.function.Consumer;

/** A bounded draft; only Save invokes the callback. */
public final class BannerEditorScreen extends SocialChildScreen {
    private final TeamBanner draft;
    private final Consumer<TeamBanner> result;
    private List<String> patterns = List.of("minecraft:stripe_center");
    private int page;
    public BannerEditorScreen(Screen parent, TeamBanner initial, Consumer<TeamBanner> result) {
        super(parent, Component.translatable("socialmod.banner.title")); draft = initial.copy(); this.result = result;
    }
    @Override protected boolean rebuildOnChange() { return false; }
    private Component color(int dye) { return Component.translatable("color.minecraft." + DyeColor.byId(dye).getName()); }
    @Override protected void init() {
        super.init();
        if (minecraft.level != null) patterns = minecraft.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN)
            .keySet().stream().filter(id -> !id.getPath().equals("base")).map(Object::toString).sorted().toList();
        int w = Math.min(340, width - 16), x = (width - w) / 2, controls = w - 76;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.banner.base", color(draft.base)), b -> { draft.base = (draft.base + 1) % 16; rebuildWidgets(); }).bounds(x, 30, controls, 20).build());
        int rows = Math.max(1, Math.min(6, (height - 110) / 24)); page = Math.min(page, Math.max(0, (draft.layers.size() - 1) / rows));
        int y = 54;
        for (int i = page * rows; i < Math.min(draft.layers.size(), (page + 1) * rows); i++) {
            final int index = i; var layer = draft.layers.get(i);
            String path = net.minecraft.resources.Identifier.parse(layer.pattern()).getPath();
            var pattern = Component.translatable("block.minecraft.banner." + path + "." + DyeColor.byId(layer.color()).getName());
            int pw = Math.max(50, controls - 116);
            addRenderableWidget(Ui.button(pattern, b -> { int next = (patterns.indexOf(layer.pattern()) + 1) % patterns.size(); draft.layers.set(index, new TeamBanner.Layer(patterns.get(next), layer.color())); rebuildWidgets(); }).tooltip(net.minecraft.client.gui.components.Tooltip.create(pattern)).bounds(x, y, pw, 20).build());
            addRenderableWidget(Ui.button(color(layer.color()), b -> { draft.layers.set(index, new TeamBanner.Layer(layer.pattern(), (layer.color() + 1) % 16)); rebuildWidgets(); }).bounds(x + pw + 2, y, 50, 20).build());
            var up = addRenderableWidget(Ui.button(Component.literal("↑"), b -> { java.util.Collections.swap(draft.layers, index, index - 1); rebuildWidgets(); }).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("socialmod.banner.up"))).bounds(x + pw + 54, y, 18, 20).build()); up.active = index > 0;
            var down = addRenderableWidget(Ui.button(Component.literal("↓"), b -> { java.util.Collections.swap(draft.layers, index, index + 1); rebuildWidgets(); }).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("socialmod.banner.down"))).bounds(x + pw + 74, y, 18, 20).build()); down.active = index + 1 < draft.layers.size();
            addRenderableWidget(Ui.button(Component.literal("×"), b -> { draft.layers.remove(index); rebuildWidgets(); }).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("socialmod.banner.remove"))).bounds(x + pw + 94, y, 18, 20).build()); y += 24;
        }
        addRenderableWidget(new BannerFrameWidget(x + controls + 4, 30, 72, Math.max(40, height - 80), title, "banner_container", false));
        addRenderableWidget(new BannerWidget(x + controls + 8, 34, 64, Math.max(32, height - 88), draft, "banner_preview"));
        var add = addRenderableWidget(Ui.button(Component.translatable("socialmod.banner.add"), b -> { draft.layers.add(new TeamBanner.Layer(patterns.getFirst(), 15)); page = (draft.layers.size() - 1) / rows; rebuildWidgets(); }).bounds(x, height - 52, controls - 44, 20).build()); add.active = draft.layers.size() < TeamBanner.MAX_LAYERS && !patterns.isEmpty();
        addRenderableWidget(Ui.button(Component.literal("<"), b -> { page = Math.max(0, page - 1); rebuildWidgets(); }).bounds(x + controls - 42, height - 52, 20, 20).build());
        addRenderableWidget(Ui.button(Component.literal(">"), b -> { page = Math.min(Math.max(0, (draft.layers.size() - 1) / rows), page + 1); rebuildWidgets(); }).bounds(x + controls - 20, height - 52, 20, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.group_settings.save"), b -> { result.accept(draft.copy()); onClose(); }).bounds(x, height - 26, w / 2 - 2, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("gui.cancel"), b -> onClose()).bounds(x + w / 2 + 2, height - 26, w / 2 - 2, 20).build());
        for (var widget : net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(this)) if (widget instanceof net.minecraft.client.gui.components.Button && widget.getY() >= 54 && widget.getY() < y) {
            int layer = page * rows + (widget.getY() - 54) / 24;
            String kind = widget.getX() == x ? "pattern" : widget.getX() == x + Math.max(50, controls - 116) + 2 ? "color" : widget.getX() == x + Math.max(50, controls - 116) + 54 ? "up" : widget.getX() == x + Math.max(50, controls - 116) + 74 ? "down" : "remove";
            com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identify(widget, "banner_layer_" + layer + "_" + kind);
        }
    }
    @Override protected void drawContent(GuiGraphicsExtractor graphics, int mx, int my) { Ui.title(graphics, font, title, width / 2, 10); }
}
