package com.takumistudios.socialmod.client.compat.fancy;

import com.takumistudios.socialmod.client.ClientState;
import de.keksuccino.fancymenu.customization.ScreenCustomization;
import de.keksuccino.fancymenu.customization.layer.ScreenCustomizationLayerHandler;
import de.keksuccino.fancymenu.customization.layout.Layout;
import de.keksuccino.fancymenu.customization.layout.LayoutHandler;
import de.keksuccino.fancymenu.customization.placeholder.*;
import de.keksuccino.fancymenu.customization.screen.identifier.*;
import de.keksuccino.fancymenu.util.rendering.ui.widget.UniqueWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import java.util.List;

final class FancyBackend {
    static boolean gameScreenOpen;
    static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client ->
            gameScreenOpen = com.takumistudios.socialmod.client.compat.ClientCompat.currentScreen() != null);
        // The hub opens the full editor; the editing toolbar must not cover game controls.
        de.keksuccino.fancymenu.customization.overlay.CustomizationOverlay.registerOverlayVisibilityController(screen ->
            !(screen instanceof com.takumistudios.socialmod.client.screen.SocialScreen)
                && !(screen instanceof com.takumistudios.socialmod.client.screen.SocialChildScreen));
        for (String name : List.of("SocialScreen", "TeamScreen", "TeamManagementScreen", "SettingsScreen", "ProfileScreen", "CreateGroupScreen", "GroupSettingsScreen", "InviteScreen", "QuickReplyScreen", "TagStyleScreen", "AdvancedCustomizationScreen", "RowTemplateScreen"))
            UniversalScreenIdentifierRegistry.register("socialmod_" + name.replace("Screen", "").toLowerCase(java.util.Locale.ROOT), "com.takumistudios.socialmod.client.screen." + name);
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("spiffyhud")) SpiffyBackend.register();
        for (String key : List.of("team", "conversation", "unread", "status")) PlaceholderRegistry.register(new SocialPlaceholder(key));
    }
    static void identify(AbstractWidget widget, String id) { ((UniqueWidget)(Object)widget).setWidgetIdentifierFancyMenu("socialmod_" + id); }
    static boolean hidden(AbstractWidget widget) { return ((de.keksuccino.fancymenu.util.rendering.ui.widget.CustomizableWidget)(Object)widget).isHiddenFancyMenu(); }
    static boolean buttonBackground(AbstractWidget widget, net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        return !((de.keksuccino.fancymenu.util.rendering.ui.widget.CustomizableWidget)(Object)widget).renderCustomBackgroundFancyMenu(widget, graphics, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
    }
    static boolean customLabel(AbstractWidget widget) {
        var custom=(de.keksuccino.fancymenu.util.rendering.ui.widget.CustomizableWidget)(Object)widget;
        return custom.getCustomLabelFancyMenu()!=null || custom.getHoverLabelFancyMenu()!=null;
    }
    static float labelScale(AbstractWidget widget) { return ((de.keksuccino.fancymenu.util.rendering.ui.widget.CustomizableWidget)(Object)widget).resolveLabelScaleFancyMenu(); }
    static boolean labelShadow(AbstractWidget widget) { return ((de.keksuccino.fancymenu.util.rendering.ui.widget.CustomizableWidget)(Object)widget).isLabelShadowFancyMenu(); }
    static boolean customized(Screen screen) { return ScreenCustomization.isCustomizationEnabledForScreen(screen); }
    static boolean background(Screen screen) {
        var layer = ScreenCustomizationLayerHandler.getLayerOfScreen(screen);
        return customized(screen) && layer != null && !layer.layoutBase.menuBackgrounds.isEmpty();
    }
    static boolean replacesHud(String kind) { return SpiffyBackend.replacesHud(kind); }
    static void edit(Screen target) {
        ScreenCustomization.setCustomizationForScreenEnabled(target, true);
        String id = ScreenIdentifierHandler.getIdentifierOfScreen(target);
        var layouts = LayoutHandler.getEnabledLayoutsForScreenIdentifier(id, false);
        Layout layout = layouts.isEmpty() ? Layout.buildForScreen(target) : layouts.getFirst();
        if (layouts.isEmpty()) LayoutHandler.addLayout(layout, true);
        LayoutHandler.openLayoutEditor(layout, target);
    }
    static void editHud() { SpiffyBackend.edit(); }
    private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor(r -> { var thread = new Thread(r,"SocialMod-Fancy-IO"); thread.setDaemon(true); return thread; });
    static java.util.concurrent.CompletableFuture<Void> christmas(Screen target) {
        var layout = Layout.buildForScreen(target);
        var background = new de.keksuccino.fancymenu.customization.background.backgrounds.image.ImageMenuBackgroundBuilder().buildDefaultInstance();
        background.textureSupplier.set(de.keksuccino.fancymenu.util.resource.ResourceSupplier.image("socialmod:textures/gui/sprites/christmas/background.png"));
        background.showBackground.set(true); layout.menuBackgrounds.add(background);
        for (String id : List.of("block_conversations","block_chat","block_players")) {
            var serialized = new de.keksuccino.fancymenu.customization.element.SerializedElement();
            serialized.putProperty("element_type","vanilla_button"); serialized.putProperty("instance_identifier","socialmod_"+id); serialized.putProperty("anchor_point","vanilla");
            layout.serializedVanillaButtonElements.add(serialized);
        }
        String text = de.keksuccino.fancymenu.util.properties.PropertiesParser.serializeSetToFancyString(layout.serialize());
        String hudText = FancyBridge.spiffy() ? SpiffyBackend.christmasLayout() : null;
        var rows = com.takumistudios.socialmod.common.model.RowDesign.defaults();
        for (var row : rows.templates.values()) { row.enabled=true; row.background=0xD0102923; row.selectedBackground=0xD0503B21; for(var part:row.parts) { part.font="socialmod:christmas"; part.color=part.field.equals("name") ? 0xFFFFD166 : 0xFFF8EBCB; } }
        var saveRows = com.takumistudios.socialmod.client.theme.RowTemplates.save(rows);
        var saveVisual = com.takumistudios.socialmod.client.theme.LocalSeriesDesign.save(com.takumistudios.socialmod.common.model.VisualPresets.christmas());
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                var dir = LayoutHandler.LAYOUT_DIR.toPath(); java.nio.file.Files.createDirectories(dir);
                var screenFile=dir.resolve("socialmod_christmas.txt"); var hudFile=dir.resolve("socialmod_christmas_hud.txt");
                writeNewLayout(screenFile, text);
                if (FancyBridge.spiffy()) writeNewLayout(hudFile, hudText);
            } catch(java.io.IOException e) { com.takumistudios.socialmod.SocialMod.LOGGER.warn("Could not install Christmas layouts",e); throw new java.util.concurrent.CompletionException(e); }
        },IO).thenCombine(saveRows,(a,b)->a).thenCombine(saveVisual,(a,b)->a).thenCompose(ignored -> {
            var ready = new java.util.concurrent.CompletableFuture<Void>();
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                try {
                    ScreenCustomization.setCustomizationForScreenEnabled(target,true);
                    ScreenCustomization.reloadFancyMenu();
                    for (var installed : LayoutHandler.getAllLayouts())
                        if (installed.layoutFile != null && (installed.layoutFile.getName().equals("socialmod_christmas.txt") || installed.layoutFile.getName().equals("socialmod_christmas_hud.txt"))) installed.setEnabled(true,false);
                    ready.complete(null);
                } catch (RuntimeException | LinkageError e) { ready.completeExceptionally(e); }
            });
            return ready;
        });
    }
    static java.util.concurrent.CompletableFuture<Void> reset() {
        for (var layout : LayoutHandler.getAllLayouts())
            if (layout.layoutFile != null && (layout.layoutFile.getName().equals("socialmod_christmas.txt") || layout.layoutFile.getName().equals("socialmod_christmas_hud.txt"))) layout.setEnabled(false,false);
        ScreenCustomization.reloadFancyMenu();
        return com.takumistudios.socialmod.client.theme.LocalSeriesDesign.reset().thenCombine(
            com.takumistudios.socialmod.client.theme.RowTemplates.save(com.takumistudios.socialmod.common.model.RowDesign.defaults()), (a,b) -> null);
    }
    private static void writeNewLayout(java.nio.file.Path path, String content) throws java.io.IOException {
        if (java.nio.file.Files.exists(path)) return; // Preserve creator edits when reapplying the template.
        var tmp = path.resolveSibling(path.getFileName() + ".tmp");
        java.nio.file.Files.writeString(tmp, content);
        java.nio.file.Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
    static final class SocialPlaceholder extends Placeholder {
        private final String key;
        SocialPlaceholder(String key) { super("socialmod_" + key); this.key = key; }
        @Override public String getReplacementFor(DeserializedPlaceholderString ignored) {
            var state = ClientState.get();
            if (!state.connected()) return "";
            return switch (key) {
                case "unread" -> Integer.toString(state.totalUnread());
                case "status" -> state.snapshot().self.status;
                case "conversation" -> state.activeConversation() == null ? "" : state.titleOf(state.activeConversation());
                default -> { yield state.snapshot().teams.stream().filter(t -> t.id.equals(state.snapshot().self.teamId)).map(t -> t.name).findFirst().orElse(""); }
            };
        }
        @Override public boolean canRunAsync() { return false; }
        @Override public List<String> getValueNames() { return null; }
        @Override public String getDisplayName() { return "SocialMod: " + net.minecraft.network.chat.Component.translatable(key.equals("conversation") ? "socialmod.advanced.kind.conversation" : "socialmod.advanced.field."+key).getString(); }
        @Override public List<String> getDescription() { return List.of(net.minecraft.network.chat.Component.translatable("socialmod.advanced.placeholder_description",getDisplayName()).getString()); }
        @Override public String getCategory() { return "SocialMod"; }
        @Override public DeserializedPlaceholderString getDefaultPlaceholderString() { return new DeserializedPlaceholderString(id, null, ""); }
    }
}
