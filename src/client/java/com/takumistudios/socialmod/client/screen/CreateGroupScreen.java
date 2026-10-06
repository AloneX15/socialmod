package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.theme.VisualText;

import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.client.TagRenderer;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.common.model.GroupIcon;
import com.takumistudios.socialmod.common.net.SocialAction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Crear un grupo (nombre, etiqueta, icono y color) o una party rápida. El servidor valida nombre, etiqueta, icono,
 * color y límites; el estilo viaja junto a la etiqueta como {@code TAG;icono;#RRGGBB}.
 */
public class CreateGroupScreen extends SocialChildScreen {
    private static final int WIDTH = 220;
    private EditBox name;
    private EditBox tag;
    private String nameDraft = "";
    private String tagDraft = "";
    private int color = 0x55FF55;
    private String icon = GroupIcon.SHIELD.id();

    public CreateGroupScreen(@Nullable Screen parent) {
        super(parent, Component.translatable("socialmod.create.title"));
    }

    @Override
    protected void saveDrafts() {
        nameDraft = name.getValue();
        tagDraft = tag.getValue();
    }

    @Override
    protected void init() {
        super.init();
        int left = panelLeft(WIDTH);
        int y = this.height / 2 - 40;
        name = new StyledEditBox(this.font, left, y, WIDTH, 20, Component.translatable("socialmod.create.name"));
        name.setMaxLength(24);
        name.setHint(Ui.hint(Component.translatable("socialmod.create.name")));
        name.setValue(nameDraft);
        addRenderableWidget(name);
        tag = new StyledEditBox(this.font, left, y + 26, 80, 20, Component.translatable("socialmod.create.tag"));
        tag.setMaxLength(5);
        tag.setHint(Ui.hint(Component.translatable("socialmod.create.tag")));
        tag.setValue(tagDraft);
        addRenderableWidget(tag);
        addRenderableWidget(Ui.button(Component.translatable("socialmod.group_settings.style"), b -> {
            saveDrafts();
            ClientCompat.setScreen(new TagStyleScreen(this, tag.getValue().trim().toUpperCase(Locale.ROOT), color, icon, "leader",
                    (rgb, chosen) -> {
                        color = rgb;
                        icon = chosen;
                    }));
        }).bounds(left, y + 52, WIDTH, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.create.create"), b -> {
            String style = tag.getValue().trim() + ";" + icon + ";" + String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
            ClientNet.action(SocialAction.GROUP_CREATE, name.getValue().trim(), style);
            onClose();
        }).bounds(left, y + 90, WIDTH / 2 - 2, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.create.party"), b -> {
            ClientNet.action(SocialAction.PARTY_CREATE, "");
            onClose();
        }).bounds(left + WIDTH / 2 + 2, y + 90, WIDTH / 2 - 2, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
        setInitialFocus(name);
    }

    @Override
    protected boolean rebuildOnChange() {
        return false;
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = panelLeft(WIDTH);
        int y = this.height / 2 - 40;
        Ui.title(graphics, font, this.title, this.width / 2, y - 22);
        VisualText.text(graphics, font, Component.translatable("socialmod.create.tag_help"), left + 86, y + 32, Ui.theme().colors().muted());
        // Vista previa en vivo de la etiqueta tal como se verá bajo el nombre
        String currentTag = tag == null || tag.getValue().isBlank() ? "TAG" : tag.getValue().trim().toUpperCase(Locale.ROOT);
        VisualText.centeredText(graphics, font, TagRenderer.panelLine(currentTag, color, icon, "leader"), this.width / 2, y + 77, 0xFFFFFFFF);
    }
}
