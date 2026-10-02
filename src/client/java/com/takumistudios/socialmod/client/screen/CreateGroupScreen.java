package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.ClientNet;
import com.takumistudios.socialmod.common.net.SocialAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Crear un grupo (nombre y etiqueta) o una party rápida. El servidor valida nombre, etiqueta y límites. */
public class CreateGroupScreen extends SocialChildScreen {
    private static final int WIDTH = 220;
    private EditBox name;
    private EditBox tag;
    private String nameDraft = "";
    private String tagDraft = "";

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
        int left = panelLeft(WIDTH);
        int y = this.height / 2 - 40;
        name = new EditBox(this.font, left, y, WIDTH, 20, Component.translatable("socialmod.create.name"));
        name.setMaxLength(24);
        name.setHint(Component.translatable("socialmod.create.name").withStyle(ChatFormatting.DARK_GRAY));
        name.setValue(nameDraft);
        addRenderableWidget(name);
        tag = new EditBox(this.font, left, y + 26, 80, 20, Component.translatable("socialmod.create.tag"));
        tag.setMaxLength(5);
        tag.setHint(Component.translatable("socialmod.create.tag").withStyle(ChatFormatting.DARK_GRAY));
        tag.setValue(tagDraft);
        addRenderableWidget(tag);
        addRenderableWidget(Button.builder(Component.translatable("socialmod.create.create"), b -> {
            ClientNet.action(SocialAction.GROUP_CREATE, name.getValue().trim(), tag.getValue().trim());
            onClose();
        }).bounds(left, y + 56, WIDTH / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("socialmod.create.party"), b -> {
            ClientNet.action(SocialAction.PARTY_CREATE, "");
            onClose();
        }).bounds(left + WIDTH / 2 + 2, y + 56, WIDTH / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
        setInitialFocus(name);
    }

    @Override
    protected boolean rebuildOnChange() {
        return false;
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.centeredText(font, this.title, this.width / 2, this.height / 2 - 62, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("socialmod.create.tag_help"), panelLeft(WIDTH) + 86, this.height / 2 - 8, Ui.theme().colors().muted());
    }
}
