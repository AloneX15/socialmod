package com.takumistudios.socialmod.client.compat.fancy;

import de.keksuccino.fancymenu.customization.element.ElementRegistry;
import de.keksuccino.fancymenu.customization.layer.ScreenCustomizationLayerHandler;
import de.keksuccino.fancymenu.customization.layout.Layout;
import de.keksuccino.spiffyhud.customization.SpiffyGui;
import de.keksuccino.spiffyhud.customization.SpiffyOverlayScreen;
import java.util.List;

/** Loaded only when SpiffyHUD is installed; no foreign HUD types leak into the FancyMenu bridge. */
final class SpiffyBackend {
    static void register() {
        for (String kind : List.of("social", "party", "pings", "toasts")) ElementRegistry.register(new SocialHudElement.Builder(kind));
    }
    static boolean replacesHud(String kind) {
        var layer = ScreenCustomizationLayerHandler.getLayerOfScreen(SpiffyGui.INSTANCE.getOverlayScreen());
        return layer != null && layer.allElements.stream().anyMatch(e -> e instanceof SocialHudElement hud && hud.kind.equals(kind) && hud.shouldRender());
    }
    static void edit() { FancyBackend.edit(new SpiffyOverlayScreen(true)); }
    static String christmasLayout() {
        var hud = Layout.buildForScreen("de.keksuccino.spiffyhud.customization.SpiffyOverlayScreen");
        int offset = 0;
        for (String kind : List.of("social","party","pings","toasts")) {
            var element = new SocialHudElement.Builder(kind).buildDefaultInstance(); element.anchorPoint = de.keksuccino.fancymenu.customization.element.anchor.ElementAnchorPoints.TOP_LEFT;
            element.posOffsetX = 8; element.posOffsetY = 8 + offset; element.baseHeight = kind.equals("social") ? 40 : 100; offset += element.baseHeight + 4;
            element.setInstanceIdentifier("socialmod_christmas_"+kind); hud.serializedElements.add(element.getBuilder().serializeElementInternal(element));
        }
        return de.keksuccino.fancymenu.util.properties.PropertiesParser.serializeSetToFancyString(hud.serialize());
    }
    private SpiffyBackend() { }
}
