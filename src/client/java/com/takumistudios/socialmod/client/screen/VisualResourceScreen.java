package com.takumistudios.socialmod.client.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Consumer;

/** Resource-pack sprite browser with thumbnails and bounded pages. */
public final class VisualResourceScreen extends SocialChildScreen {
    private final Consumer<String> select;
    private List<Identifier> sprites = List.of();
    private int page;
    public VisualResourceScreen(Screen parent, Consumer<String> select) { super(parent, Component.translatable("socialmod.visual.choose_resource")); this.select = select; }
    @Override protected boolean rebuildOnChange() { return false; }
    @Override protected void init() {
        sprites = minecraft.getResourceManager().listResources("textures/gui/sprites", id -> id.getPath().endsWith(".png")).keySet().stream()
                .map(id -> Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring("textures/gui/sprites/".length(), id.getPath().length() - 4))).sorted(java.util.Comparator.comparing(Identifier::toString)).toList();
        int rows = Math.max(1, (height - 80) / 26);
        page = Math.min(page, Math.max(0, (sprites.size() - 1) / rows));
        for (int i = 0; i < rows && page * rows + i < sprites.size(); i++) {
            var id = sprites.get(page * rows + i);
            addRenderableWidget(Ui.button(Component.literal(Ui.trim(font, id.toString(), width - 64)), b -> { select.accept(id.toString()); onClose(); }).bounds(34, 30 + i * 26, width - 42, 22).build());
        }
        addRenderableWidget(Ui.button(Component.literal("<"), b -> { page = Math.max(0, page - 1); rebuildWidgets(); }).bounds(8, height - 46, 28, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.visual.no_texture"), b -> { select.accept(""); onClose(); }).bounds(40, height - 46, width - 80, 20).build());
        addRenderableWidget(Ui.button(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(width - 36, height - 46, 28, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("gui.back"), b -> onClose()).bounds(width / 2 - 45, height - 24, 90, 20).build());
    }
    @Override protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Ui.title(graphics, font, title, width / 2, 8);
        int rows = Math.max(1, (height - 80) / 26);
        for (int i = 0; i < rows && page * rows + i < sprites.size(); i++) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprites.get(page * rows + i), 8, 30 + i * 26, 22, 22);
    }
}
