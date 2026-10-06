package com.takumistudios.socialmod.test;

import de.keksuccino.fancymenu.customization.layer.ScreenCustomizationLayerHandler;
import de.keksuccino.spiffyhud.customization.SpiffyGui;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Verifies real HUD rendering, separately loaded from the FancyMenu-only profile. */
final class SpiffyIntegrationTests {
    static void assertRendered(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var layer = ScreenCustomizationLayerHandler.getLayerOfScreen(SpiffyGui.INSTANCE.getOverlayScreen());
            var element = layer.allElements.stream().filter(e -> "socialmod_christmas_social".equals(e.getInstanceIdentifier())).findFirst().orElseThrow();
            try {
                var rendered = element.getClass().getDeclaredField("hudFrames"); rendered.setAccessible(true);
                if (rendered.getLong(element) == 0) throw new AssertionError("Social HUD never rendered during gameplay");
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
    }
}
