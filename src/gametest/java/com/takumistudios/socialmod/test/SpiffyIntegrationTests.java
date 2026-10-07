package com.takumistudios.socialmod.test;

import de.keksuccino.fancymenu.customization.layer.ScreenCustomizationLayerHandler;
import de.keksuccino.spiffyhud.customization.SpiffyGui;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Verifies real HUD rendering, separately loaded from the FancyMenu-only profile. */
final class SpiffyIntegrationTests {
    private static void toggleHud(net.minecraft.client.Minecraft client) {
        //? if >=26.2 {
        client.gui.hud.toggle();
        //?} else {
        /*client.options.hideGui = !client.options.hideGui;
        *///?}
    }
    static void assertRendered(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var layer = ScreenCustomizationLayerHandler.getLayerOfScreen(SpiffyGui.INSTANCE.getOverlayScreen());
            var element = layer.allElements.stream().filter(e -> ("socialmod_"+com.takumistudios.socialmod.client.theme.LocalSeriesDesign.get().seriesStyle+"_social").equals(e.getInstanceIdentifier())).findFirst().orElseThrow();
            try {
                var rendered = element.getClass().getDeclaredField("hudFrames"); rendered.setAccessible(true);
                if (rendered.getLong(element) == 0) throw new AssertionError("Social HUD never rendered during gameplay");
                var cache = element.getClass().getDeclaredMethod("cachedRows", boolean.class); cache.setAccessible(true);
                Object first = cache.invoke(element, false), second = cache.invoke(element, false);
                if (first != second) throw new AssertionError("HUD rows were allocated twice in one tick");
                var failed = element.getClass().getDeclaredField("failed"); failed.setAccessible(true);
                failed.setBoolean(element, true);
                if (com.takumistudios.socialmod.client.compat.fancy.FancyBridge.replacesHud("social")) throw new AssertionError("Failed custom HUD suppressed native HUD");
                if (!com.takumistudios.socialmod.client.compat.fancy.FancyBridge.available()) throw new AssertionError("HUD failure disabled FancyMenu");
                failed.setBoolean(element, false);
                if (!com.takumistudios.socialmod.client.compat.ClientCompat.hudHidden()) {
                    toggleHud(client);
                    try { if (element.shouldRender()) throw new AssertionError("Custom HUD ignores F1"); }
                    finally { toggleHud(client); }
                }
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
    }
}
