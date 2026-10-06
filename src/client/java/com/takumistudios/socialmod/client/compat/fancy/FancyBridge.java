package com.takumistudios.socialmod.client.compat.fancy;

import com.takumistudios.socialmod.SocialMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/** The only entry into optional editor classes. Safe to load without either editor. */
public final class FancyBridge {
    private static boolean available, failed;
    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("fancymenu")) return;
        try { FancyBackend.register(); available = true; }
        catch (RuntimeException | LinkageError e) { disable(e); }
    }
    public static boolean available() { return available && !failed; }
    public static boolean spiffy() { return available() && FabricLoader.getInstance().isModLoaded("spiffyhud"); }
    public static void identify(AbstractWidget widget, String id) {
        if (available()) try { FancyBackend.identify(widget, id); } catch (RuntimeException | LinkageError e) { disable(e); }
    }
    public static boolean hidden(AbstractWidget widget) {
        if (!available()) return false;
        try { return FancyBackend.hidden(widget); } catch (RuntimeException | LinkageError e) { disable(e); return false; }
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
        try { return FancyBackend.replacesHud(kind); } catch (RuntimeException | LinkageError e) { disable(e); return false; }
    }
    public static java.util.concurrent.CompletableFuture<Void> christmas(Screen target) {
        if (!available()) return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("FancyMenu unavailable"));
        try { return FancyBackend.christmas(target); } catch (RuntimeException | LinkageError e) { disable(e); return java.util.concurrent.CompletableFuture.failedFuture(e); }
    }
    public static java.util.concurrent.CompletableFuture<Void> reset() {
        if (!available()) return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("FancyMenu unavailable"));
        try { return FancyBackend.reset(); } catch (RuntimeException | LinkageError e) { disable(e); return java.util.concurrent.CompletableFuture.failedFuture(e); }
    }
    public static void edit(Screen target) {
        if (available()) try { FancyBackend.edit(target); } catch (RuntimeException | LinkageError e) { disable(e); }
    }
    public static void editHud() {
        if (spiffy()) try { FancyBackend.editHud(); } catch (RuntimeException | LinkageError e) { disable(e); }
    }
    private static void disable(Throwable e) { failed = true; SocialMod.LOGGER.warn("Advanced customization unavailable; using the basic interface", e); }
    private FancyBridge() { }
}
