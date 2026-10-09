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
    static long hudTick;
    static void register() {
        ScreenCustomization.addScreenBlacklistRule(id -> id.startsWith("com.takumistudios.socialmod.client.screen.")
            && (com.takumistudios.socialmod.client.theme.AppearanceMode.original() || com.takumistudios.socialmod.client.screen.SocialComponents.embedded()));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client ->
            {
                gameScreenOpen = com.takumistudios.socialmod.client.compat.ClientCompat.currentScreen() != null;
                hudTick++;
                if (hudTick == 1) try {
                    for (var layout : LayoutHandler.getAllLayouts()) if (layout.layoutFile != null && com.takumistudios.socialmod.common.model.RetiredStyles.layout(layout.layoutFile.getName())) layout.setEnabled(false,false);
                } catch (RuntimeException | LinkageError e) { com.takumistudios.socialmod.SocialMod.LOGGER.warn("Could not disable retired layouts",e); }
                try { com.takumistudios.socialmod.client.screen.SocialComponents.subscriptions(hudTick); }
                catch (RuntimeException e) { com.takumistudios.socialmod.SocialMod.LOGGER.warn("SocialMod component subscription failed",e); }
                if (FancyBridge.spiffy()) try { SpiffyBackend.refreshIndex(); }
                catch (RuntimeException | LinkageError e) { FancyBridge.disableSpiffy(e); }
            });
        de.keksuccino.fancymenu.customization.overlay.CustomizationOverlay.registerOverlayVisibilityController(screen ->
            !com.takumistudios.socialmod.client.screen.SocialComponents.supported(screen) || !com.takumistudios.socialmod.client.theme.AppearanceMode.original());
        SocialElements.register();
        for (String name : List.of("SocialScreen", "TeamScreen", "TeamManagementScreen", "SettingsScreen", "ProfileScreen", "CreateGroupScreen", "GroupSettingsScreen", "InviteScreen", "QuickReplyScreen", "TagStyleScreen", "BannerEditorScreen", "PlayerSearchScreen", "RowTemplateScreen"))
            UniversalScreenIdentifierRegistry.register("socialmod_" + name.replace("Screen", "").toLowerCase(java.util.Locale.ROOT), "com.takumistudios.socialmod.client.screen." + name);
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("spiffyhud")) {
            try { SpiffyBackend.register(); }
            catch (RuntimeException | LinkageError e) { FancyBridge.disableSpiffy(e); }
        }
        for (String key : List.of("team", "conversation", "unread", "status")) PlaceholderRegistry.register(new SocialPlaceholder(key));
    }
    static void identify(AbstractWidget widget, String id) { ((UniqueWidget)(Object)widget).setWidgetIdentifierFancyMenu("socialmod_" + id); }
    static void applyAppearance() { skinWidgets.clear(); skinTick = -1; }
    static boolean hidden(AbstractWidget widget) { return ((de.keksuccino.fancymenu.util.rendering.ui.widget.CustomizableWidget)(Object)widget).isHiddenFancyMenu(); }
    private static final java.util.Map<Object,com.takumistudios.socialmod.client.theme.WidgetTexture> skinTextures=new java.util.WeakHashMap<>();
    private static final java.util.Map<AbstractWidget,de.keksuccino.fancymenu.customization.element.elements.button.vanillawidget.VanillaWidgetElement> skinWidgets=new java.util.WeakHashMap<>();
    private static long skinTick=-1;
    static com.takumistudios.socialmod.client.theme.WidgetTexture skinTexture(AbstractWidget widget,boolean icon) {
        de.keksuccino.fancymenu.util.resource.RenderableResource resource;
        if(!icon) {
            var custom=(de.keksuccino.fancymenu.util.rendering.ui.widget.CustomizableWidget)(Object)widget;
            resource=!widget.active?custom.getCustomBackgroundInactiveFancyMenu():widget.isHoveredOrFocused()?custom.getCustomBackgroundHoverFancyMenu():custom.getCustomBackgroundNormalFancyMenu();
            if(resource==null)resource=custom.getCustomBackgroundNormalFancyMenu();
        } else {
            if(skinTick!=hudTick) {
                skinTick=hudTick;skinWidgets.clear();
                var layer=de.keksuccino.fancymenu.customization.layer.ScreenCustomizationLayerHandler.getLayerOfScreen(com.takumistudios.socialmod.client.compat.ClientCompat.currentScreen());
                if(layer!=null)for(var element:layer.vanillaWidgetElements)if(element.widgetMeta!=null)skinWidgets.put(element.widgetMeta.getWidget(),element);
            }
            var element=skinWidgets.get(widget);if(element==null)return null;
            var source=element.getPropertySource();var supplier=!widget.active?source.iconTextureInactive:widget.isHoveredOrFocused()?source.iconTextureHover:source.iconTextureNormal;
            resource=supplier==null?null:supplier.get();
        }
        if(resource==null||resource.getResourceLocation()==null||resource.getWidth()<1||resource.getHeight()<1||resource.getWidth()>4096||resource.getHeight()>4096||resource.getResourceLocation().equals(de.keksuccino.fancymenu.util.resource.RenderableResource.MISSING_TEXTURE_LOCATION))return null;
        return cacheSkinTexture(resource);
    }

    private static com.takumistudios.socialmod.client.theme.WidgetTexture cacheSkinTexture(de.keksuccino.fancymenu.util.resource.RenderableResource resource) {
        var texture=skinTextures.get(resource);
        if(texture==null) { texture=new com.takumistudios.socialmod.client.theme.WidgetTexture(resource.getResourceLocation(),resource.getWidth(),resource.getHeight());skinTextures.put(resource,texture); }
        return texture;
    }
    static boolean buttonBackground(AbstractWidget widget, net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        return !((de.keksuccino.fancymenu.util.rendering.ui.widget.CustomizableWidget)(Object)widget).renderCustomBackgroundFancyMenu(widget, graphics, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
    }
    static boolean customLabel(AbstractWidget widget) {
        var custom=(de.keksuccino.fancymenu.util.rendering.ui.widget.CustomizableWidget)(Object)widget;
        return custom.getCustomLabelFancyMenu()!=null || custom.getHoverLabelFancyMenu()!=null;
    }
    static net.minecraft.network.chat.Component label(AbstractWidget widget) {
        var custom = (de.keksuccino.fancymenu.util.rendering.ui.widget.CustomizableWidget)(Object)widget;
        var label = widget.isHoveredOrFocused() ? custom.getHoverLabelFancyMenu() : null;
        if (label == null) label = custom.getCustomLabelFancyMenu();
        return label == null ? widget.getMessage() : label;
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
    static void reload() { skinTextures.clear();skinWidgets.clear();skinTick=-1;ScreenCustomization.reloadFancyMenu(); }
    static com.takumistudios.socialmod.common.model.SeriesPack.Contents template(Screen target,String style) {
        com.takumistudios.socialmod.common.model.SeriesTemplates.visual(style);
        var layout = Layout.buildForScreen(target);
        // Deliberately no menuBackgrounds: the live world remains visible.
        for (String id : List.of("block_conversations","block_chat","block_players")) {
            var serialized = new de.keksuccino.fancymenu.customization.element.SerializedElement();
            serialized.putProperty("element_type","vanilla_button"); serialized.putProperty("instance_identifier","socialmod_"+id); serialized.putProperty("anchor_point","vanilla");
            layout.serializedVanillaButtonElements.add(serialized);
        }
        var files = new java.util.TreeMap<String,byte[]>();

        files.put("config/fancymenu/customization/socialmod_"+style+".txt",(de.keksuccino.fancymenu.util.properties.PropertiesParser.serializeSetToFancyString(layout.serialize())+files.keySet().stream().filter(path->path.endsWith(".png")).map(path->"\n# SocialMod skin asset: [source:local]"+path).collect(java.util.stream.Collectors.joining())).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var mods = new java.util.ArrayList<>(List.of("socialmod","fabric-api","fancymenu","konkrete","melody"));
        if (FancyBridge.spiffy()) {
            files.put("config/fancymenu/customization/socialmod_"+style+"_hud.txt",SpiffyBackend.seriesLayout(style).getBytes(java.nio.charset.StandardCharsets.UTF_8)); mods.add("spiffyhud");
        }
        files.put(com.takumistudios.socialmod.common.model.SeriesPack.ROWS,com.takumistudios.socialmod.common.model.RowDesign.GSON.toJson(com.takumistudios.socialmod.common.model.SeriesTemplates.rows(style)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        files.put(com.takumistudios.socialmod.common.model.SeriesPack.VISUAL,com.takumistudios.socialmod.common.model.VisualDesign.GSON.toJson(com.takumistudios.socialmod.common.model.SeriesTemplates.visual(style)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var metadata = new com.takumistudios.socialmod.common.model.SeriesPack.Metadata(1,"Social clean","TakumiStudios","1.0",net.minecraft.SharedConstants.getCurrentVersion().id(),mods);
        return com.takumistudios.socialmod.client.theme.SeriesPackFiles.withVersions(new com.takumistudios.socialmod.common.model.SeriesPack.Contents(metadata,files));
    }

    static java.util.concurrent.CompletableFuture<Void> install(Screen target,String style) {
        ScreenCustomization.setCustomizationForScreenEnabled(target,true);
        return com.takumistudios.socialmod.client.theme.SeriesProfiles.installTemplate(template(target,style));
    }
    static java.util.concurrent.CompletableFuture<Void> reset() {
        return com.takumistudios.socialmod.client.theme.SeriesProfiles.restore();
    }

    static final class SocialPlaceholder extends Placeholder {
        private final String key;
        private int cachedVersion = -1;
        private String cachedConversation, replacement = "";
        SocialPlaceholder(String key) { super("socialmod_" + key); this.key = key; }
        @Override public String getReplacementFor(DeserializedPlaceholderString ignored) {
            var state = ClientState.get();
            if (!state.connected()) return "";
            if (cachedVersion == state.version() && java.util.Objects.equals(cachedConversation, state.activeConversation())) return replacement;
            cachedVersion = state.version(); cachedConversation = state.activeConversation();
            replacement = switch (key) {
                case "unread" -> Integer.toString(state.totalUnread());
                case "status" -> state.snapshot().self.status;
                case "conversation" -> state.activeConversation() == null ? "" : state.titleOf(state.activeConversation());
                default -> { yield state.snapshot().teams.stream().filter(t -> t.id.equals(state.snapshot().self.teamId)).map(t -> t.name).findFirst().orElse(""); }
            };
            return replacement;
        }
        @Override public boolean canRunAsync() { return false; }
        @Override public List<String> getValueNames() { return null; }
        @Override public String getDisplayName() { return "SocialMod: " + net.minecraft.network.chat.Component.translatable(key.equals("conversation") ? "socialmod.advanced.kind.conversation" : "socialmod.advanced.field."+key).getString(); }
        @Override public List<String> getDescription() { return List.of(net.minecraft.network.chat.Component.translatable("socialmod.advanced.placeholder_description",getDisplayName()).getString()); }
        @Override public String getCategory() { return "SocialMod"; }
        @Override public DeserializedPlaceholderString getDefaultPlaceholderString() { return new DeserializedPlaceholderString(id, null, ""); }
    }
}
