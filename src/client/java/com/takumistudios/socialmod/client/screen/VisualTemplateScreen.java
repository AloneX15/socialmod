package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.common.model.VisualDesign;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Explicit template chooser; selection edits a draft until published. */
public final class VisualTemplateScreen extends SocialChildScreen {
    private final Consumer<VisualDesign> select;
    public VisualTemplateScreen(Screen parent, Consumer<VisualDesign> select) { super(parent, Component.translatable("socialmod.visual.templates")); this.select = select; }
    @Override protected boolean rebuildOnChange() { return false; }
    @Override protected void init() {
        int w = Math.min(240, width - 16), x = (width - w) / 2;
        String[] names = {"compact", "sidebar", "full"};
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            addRenderableWidget(Ui.button(Component.translatable("socialmod.visual.template." + name), b -> {
                VisualDesign design = com.takumistudios.socialmod.common.model.SeriesTemplates.IDS.contains(name) ? com.takumistudios.socialmod.common.model.SeriesTemplates.visual(name) : new VisualDesign();
                if (!com.takumistudios.socialmod.common.model.SeriesTemplates.IDS.contains(name)) design.mode = name;
                select.accept(design); onClose();
            }).bounds(x, 34 + i * 25, w, 20).build());
        }
        addRenderableWidget(Ui.button(Component.translatable("gui.back"), b -> onClose()).bounds(width / 2 - 45, height - 24, 90, 20).build());
    }
    @Override protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) { Ui.title(graphics, font, title, width / 2, 10); }
}
