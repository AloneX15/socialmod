package com.takumistudios.socialmod.client.compat.fancy;

import com.takumistudios.socialmod.SocialMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/** The only entry into optional editor classes. Safe to load without either editor. */
public final class FancyBridge {
    private static boolean available, failed, spiffyFailed;
    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("fancymenu")) return;
        try { FancyBackend.register(); available = true; }
        catch (RuntimeException | LinkageError e) { disable(e); }
    }
    public static boolean available() { return available && !failed; }
    public static boolean spiffy() { return available() && !spiffyFailed && FabricLoader.getInstance().isModLoaded("spiffyhud"); }
    public static void identify(AbstractWidget widget, String id) {
        if (available()) try { FancyBackend.identify(widget, id); } catch (RuntimeException | LinkageError e) { disable(e); }
    }
    public static boolean hidden(AbstractWidget widget) {
        if (!available()) return false;
        try { return FancyBackend.hidden(widget); } catch (RuntimeException | LinkageError e) { disable(e); return false; }
    }
    public static com.takumistudios.socialmod.client.theme.ChristmasSkin.Texture skinTexture(AbstractWidget widget,boolean icon) {
        if(!available())return null;
        try { return FancyBackend.skinTexture(widget,icon); } catch(RuntimeException|LinkageError e) { disable(e);return null; }
    }
    public static com.takumistudios.socialmod.client.theme.ChristmasSkin.Texture skinAsset(String kind,String name) {
        if(!available())return null;
        try { return FancyBackend.skinAsset(kind,name); } catch(RuntimeException|LinkageError e) { disable(e);return null; }
    }
    public static boolean buttonBackground(AbstractWidget widget, net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        if (!available()) return false;
        try { return FancyBackend.buttonBackground(widget, graphics); } catch (RuntimeException | LinkageError e) { disable(e); return false; }
    }
    public static boolean customLabel(AbstractWidget widget) {
        if (!available()) return false;
        try { return FancyBackend.customLabel(widget); } catch(RuntimeException | LinkageError e) { disable(e); return false; }
    }
    public static float labelScale(AbstractWidget widget) {
        if (!available()) return 1;
        try { return FancyBackend.labelScale(widget); } catch(RuntimeException | LinkageError e) { disable(e); return 1; }
    }
    public static boolean labelShadow(AbstractWidget widget) {
        if (!available()) return false;
        try { return FancyBackend.labelShadow(widget); } catch(RuntimeException | LinkageError e) { disable(e); return false; }
    }
    public static boolean customized(Screen screen) {
        if (!available()) return false;
        try { return FancyBackend.customized(screen); } catch (RuntimeException | LinkageError e) { disable(e); return false; }
    }
    public static boolean background(Screen screen) {
        if (!available()) return false;
        try { return FancyBackend.background(screen); } catch (RuntimeException | LinkageError e) { disable(e); return false; }
    }
    public static boolean replacesHud(String kind) {
        if (!spiffy()) return false;
        try { return FancyBackend.replacesHud(kind); } catch (RuntimeException | LinkageError e) { disableSpiffy(e); return false; }
    }
    public static java.util.concurrent.CompletableFuture<Void> install(Screen target,String style) {
        if (!available()) return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("FancyMenu unavailable"));
        try { return FancyBackend.install(target,style); } catch (RuntimeException | LinkageError e) { disable(e); return java.util.concurrent.CompletableFuture.failedFuture(e); }
    }
    public static com.takumistudios.socialmod.common.model.SeriesPack.Contents template(Screen target,String style) {
        if(!available()) throw new IllegalStateException("FancyMenu unavailable");
        return FancyBackend.template(target,style);
    }
    public static java.util.concurrent.CompletableFuture<Void> reset() {
        if (!available()) return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("FancyMenu unavailable"));
        try { return FancyBackend.reset(); } catch (RuntimeException | LinkageError e) { disable(e); return java.util.concurrent.CompletableFuture.failedFuture(e); }
    }
    public static void edit(Screen target) {
        if (available()) try { FancyBackend.edit(target); } catch (RuntimeException | LinkageError e) { disable(e); }
    }
    public static void editHud() {
        if (spiffy()) try { FancyBackend.editHud(); } catch (RuntimeException | LinkageError e) { disableSpiffy(e); }
    }
    public static void reload() {
        if(available()) try { FancyBackend.reload(); } catch(RuntimeException|LinkageError e) { disable(e); }
    }
    static void disableSpiffy(Throwable e) {
        if (spiffyFailed) return;
        spiffyFailed = true;
        SocialMod.LOGGER.warn("SpiffyHUD integration unavailable; restoring the basic HUD", e);
    }
    private static void disable(Throwable e) { failed = true; SocialMod.LOGGER.warn("Advanced customization unavailable; using the basic interface", e); }
    private FancyBridge() { }
}
