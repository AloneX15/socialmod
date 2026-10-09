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
    private static Object indexedLayer;
    private static List<?> indexedElements = List.of();
    private static final java.util.Map<String, List<SocialHudElement>> byKind = new java.util.HashMap<>();

    /** Check structural changes once per client tick; visibility is evaluated at render time. */
    static void refreshIndex() {
        var layer = ScreenCustomizationLayerHandler.getLayerOfScreen(SpiffyGui.INSTANCE.getOverlayScreen());
        if (layer == indexedLayer && (layer == null || indexedElements.equals(layer.allElements))) return;
        indexedLayer = layer;
        indexedElements = layer == null ? List.of() : List.copyOf(layer.allElements);
        byKind.clear();
        for (Object element : indexedElements) if (element instanceof SocialHudElement hud) {
            byKind.computeIfAbsent(hud.kind, ignored -> new java.util.ArrayList<>()).add(hud);
        }
    }
    static boolean replacesHud(String kind) {
        if (indexedLayer == null) refreshIndex();
        var elements = byKind.get(kind);
        if (elements != null) for (SocialHudElement element : elements) if (element.shouldRender()) return true;
        return false;
    }
    static void edit() { FancyBackend.edit(new SpiffyOverlayScreen(true)); }
    static String seriesLayout(String style) {
        var hud = Layout.buildForScreen("de.keksuccino.spiffyhud.customization.SpiffyOverlayScreen");
        for (String kind : List.of("social","party","pings","toasts")) {
            var element = new SocialHudElement.Builder(kind).buildDefaultInstance();
            element.anchorPoint = kind.equals("social") || kind.equals("toasts") ? de.keksuccino.fancymenu.customization.element.anchor.ElementAnchorPoints.TOP_RIGHT : de.keksuccino.fancymenu.customization.element.anchor.ElementAnchorPoints.TOP_LEFT;
            element.baseWidth = 170; element.baseHeight = kind.equals("social") ? 32 : 64;
            element.posOffsetX = kind.equals("social") || kind.equals("toasts") ? -178 : 8;
            element.posOffsetY = switch(kind) { case "social" -> 8; case "toasts" -> 44; case "party" -> 8; default -> 76; };

            element.setInstanceIdentifier("socialmod_"+style+"_"+kind); hud.serializedElements.add(element.getBuilder().serializeElementInternal(element));
        }
        return de.keksuccino.fancymenu.util.properties.PropertiesParser.serializeSetToFancyString(hud.serialize());
    }
    private SpiffyBackend() { }
}
