package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.common.model.TeamBanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

/** Cached front-face textures preserve Minecraft patterns and scale with the widget and GUI pose. */
public final class BannerWidget extends AbstractWidget {
    private final Identifier[] textures;
    private final int[] colors;
    public BannerWidget(int x, int y, int w, int h, TeamBanner value, String id) {
        super(x, y, w, h, Component.translatable("socialmod.banner.title"));
        var banner = value.copy();
        var images = new java.util.ArrayList<Identifier>(); var tints = new java.util.ArrayList<Integer>();
        images.add(Identifier.withDefaultNamespace("textures/entity/banner/base.png")); tints.add(DyeColor.byId(banner.base).getTextureDiffuseColor());
        var level = Minecraft.getInstance().level;
        if (level != null) {
            var registry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN);
            for (var layer : banner.layers) registry.getOptional(Identifier.parse(layer.pattern())).ifPresent(pattern -> {
                var asset = pattern.assetId(); images.add(Identifier.fromNamespaceAndPath(asset.getNamespace(), "textures/entity/banner/" + asset.getPath() + ".png")); tints.add(DyeColor.byId(layer.color()).getTextureDiffuseColor());
            });
        }
        textures = images.toArray(Identifier[]::new); colors = tints.stream().mapToInt(Integer::intValue).toArray();
        com.takumistudios.socialmod.client.compat.fancy.FancyBridge.identify(this, id);
    }
    @Override public boolean isMouseOver(double x, double y) { return false; }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) { return false; }
    @Override protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mx, int my, float partial) {
        if (!visible || com.takumistudios.socialmod.client.compat.fancy.FancyBridge.hidden(this)) return;
        com.takumistudios.socialmod.client.compat.fancy.FancyBridge.buttonBackground(this, graphics);
        int w = Math.max(1, Math.min(getWidth(), getHeight() / 2)), h = w * 2;
        int x = getX() + (getWidth() - w) / 2, y = getY() + (getHeight() - h) / 2;
        for (int i = 0; i < textures.length; i++) graphics.blit(RenderPipelines.GUI_TEXTURED, textures[i], x, y, 1, 1, w, h, 20, 40, 64, 64, colors[i] | 0xFF000000);
    }
}