package com.takumistudios.socialmod.test;

import com.takumistudios.socialmod.api.client.ToastData;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.hud.ToastHud;
import com.takumistudios.socialmod.client.screen.GroupSettingsScreen;
import com.takumistudios.socialmod.client.screen.ProfileScreen;
import com.takumistudios.socialmod.client.screen.QuickReplyScreen;
import com.takumistudios.socialmod.client.screen.SettingsScreen;
import com.takumistudios.socialmod.client.screen.SocialScreen;
import com.takumistudios.socialmod.common.model.ConversationId;
import com.takumistudios.socialmod.common.net.Payloads;
import com.takumistudios.socialmod.common.net.SnapshotDto;
import com.takumistudios.socialmod.server.SocialServer;
import com.takumistudios.socialmod.server.data.Conversation;
import com.takumistudios.socialmod.server.data.Group;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

import java.util.UUID;

/**
 * Prueba de cliente: handshake real en un mundo local, creación de grupo y mensajes por comandos, capturas del panel,
 * perfil, ajustes, toasts, HUD y Quick-Reply, y persistencia del grupo y del historial tras reiniciar el mundo.
 */
public class SocialModClientGameTest implements FabricClientGameTest {
    private static void waitForChunks(TestSingleplayerContext world) {
        //? if >=26.2 {
        world.getConnection().waitForChunksRender();
        //?} else {
        /*world.getClientLevel().waitForChunksRender();
        *///?}
    }

    private static final java.util.concurrent.atomic.AtomicBoolean HANG_WATCH = new java.util.concurrent.atomic.AtomicBoolean();

    /** Diagnóstico: si salir del mundo tarda más de 15 s, vuelca la pila de todos los hilos al log. */
    private static void startHangWatchdog() {
        HANG_WATCH.set(true);
        Thread watchdog = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                try {
                    Thread.sleep(15_000);
                } catch (InterruptedException e) {
                    return;
                }
                if (!HANG_WATCH.get()) {
                    return;
                }
                StringBuilder dump = new StringBuilder("HANGDUMP salir del mundo tarda más de ").append(15 * (i + 1)).append(" s\n");
                for (var entry : Thread.getAllStackTraces().entrySet()) {
                    Thread thread = entry.getKey();
                    dump.append("HANGDUMP \"").append(thread.getName()).append("\" ").append(thread.getState()).append('\n');
                    for (StackTraceElement element : entry.getValue()) {
                        dump.append("HANGDUMP     at ").append(element).append('\n');
                    }
                }
                com.takumistudios.socialmod.SocialMod.LOGGER.warn(dump.toString());
            }
        }, "SocialMod hang watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static final class ChristmasStatesScreen extends com.takumistudios.socialmod.client.screen.SocialChildScreen {
        ChristmasStatesScreen() { super(null, net.minecraft.network.chat.Component.translatable("socialmod.visual.states")); }
        @Override protected void init() {
            String[] states = {"normal", "hover", "selected", "disabled"};
            for (int i = 0; i < states.length; i++) {
                var button = com.takumistudios.socialmod.client.screen.Ui.button(net.minecraft.network.chat.Component.translatable("socialmod.visual.state." + states[i]), b -> { }).selected(i == 2).bounds(width / 2 - 90, 50 + i * 28, 180, 20).build();
                button.active = i != 2 && i != 3; addRenderableWidget(button);
                if (i == 1) setInitialFocus(button);
            }
        }
        @Override protected void drawContent(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            com.takumistudios.socialmod.client.screen.Ui.panel(graphics, width / 2 - 100, 42, width / 2 + 100, 162);
            com.takumistudios.socialmod.client.screen.Ui.title(graphics, font, title, width / 2, 8);
        }
    }
    private static void checkChristmasLayout(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = com.takumistudios.socialmod.client.compat.ClientCompat.currentScreen();
            for (var widget : net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen)) {
                if (widget.visible && (widget.getX() < 0 || widget.getY() < 0 || widget.getRight() > screen.width || widget.getBottom() > screen.height)) throw new AssertionError("Christmas control outside viewport: " + widget.getMessage());
            }
        });
    }
    static void language(ClientGameTestContext context, String code) {
        var finished = new java.util.concurrent.atomic.AtomicBoolean();
        context.runOnClient(client -> {
            client.getLanguageManager().setSelected(code); client.options.languageCode = code;
            client.reloadResourcePacks().thenRun(() -> finished.set(true));
        });
        context.waitFor(client -> finished.get(), 400);
        // A completed reload future can still leave the loading overlay fading out.
        //? if >=26.2 {
        context.waitFor(client -> client.gui.overlay() == null, 400);
        //?} else {
        /*context.waitFor(client -> client.getOverlay() == null, 400);
        *///?}
        context.waitTicks(3);
    }
    private static void christmasDocumentation(ClientGameTestContext context, TestSingleplayerContext world, String conversation) {
        String originalLanguage = context.computeOnClient(client -> client.getLanguageManager().getSelected());
        var previous = context.computeOnClient(client -> ClientState.get().snapshot().visual.copy());
        for (String code : new String[]{"es_es", "en_us"}) {
            language(context, code);
            context.waitFor(client -> ClientState.get().snapshot().visualAdmin, 100);
            context.setScreen(() -> new SocialScreen(conversation)); context.waitTicks(5);
            context.takeScreenshot("christmas_" + code + "_01_open_editor");
            context.clickScreenButton("socialmod.visual.title");
            if (com.takumistudios.socialmod.client.compat.fancy.FancyBridge.available()) context.clickScreenButton("socialmod.advanced.basic");
            context.clickScreenButton("socialmod.visual.preset"); context.waitTicks(3);
            context.takeScreenshot("christmas_" + code + "_02_templates");
            context.clickScreenButton("socialmod.visual.template.christmas"); context.waitTicks(5);
            context.takeScreenshot("christmas_" + code + "_03_preview");
            context.clickScreenButton("socialmod.visual.category.window"); context.waitTicks(3);
            context.takeScreenshot("christmas_" + code + "_04_colors");
            context.clickScreenButton("socialmod.visual.publish");
            context.waitFor(client -> ClientState.get().snapshot().visual.decoration.equals("christmas"), 100);
            context.waitTicks(5);
            context.takeScreenshot("christmas_" + code + "_05_publish");
            context.clickScreenButton("socialmod.visual.back"); context.waitTicks(5);
            context.takeScreenshot("christmas_" + code + "_06_panel");
            context.runOnClient(client -> {
                var style = net.minecraft.network.chat.Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.parse("socialmod:christmas")));
                for (char c : "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789\u00e1\u00e9\u00ed\u00f3\u00fa\u00f1\u00fc\u00a1\u00bf".toCharArray()) {
                    String value = String.valueOf(c);
                    if (client.font.width(value) != client.font.width(net.minecraft.network.chat.Component.literal(value).withStyle(style))) throw new AssertionError("Christmas font changed native cursor metrics for " + value);
                }
            });
            context.setScreen(() -> new SettingsScreen(new SocialScreen(conversation))); context.waitTicks(5);
            context.takeScreenshot("christmas_" + code + "_07_settings");
            context.setScreen(ChristmasStatesScreen::new); context.waitTicks(3);
            context.takeScreenshot("christmas_" + code + "_08_button_states");
            context.setScreen(() -> new com.takumistudios.socialmod.client.screen.VisualEditorScreen(new SocialScreen(conversation)));
            context.runOnClient(client -> {
                var editor = com.takumistudios.socialmod.client.compat.ClientCompat.currentScreen();
                var resolution = net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(editor).stream().filter(w -> w.getMessage().getString().equals("640\u00d7360")).findFirst().orElseThrow();
                ((net.minecraft.client.gui.components.Button) resolution).onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0, new net.minecraft.client.input.MouseButtonInfo(0, 0)));
            });
            context.waitTicks(3); context.takeScreenshot("christmas_" + code + "_09_medium_preview");
            context.runOnClient(client -> {
                var editor = com.takumistudios.socialmod.client.compat.ClientCompat.currentScreen();
                var resolution = net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(editor).stream().filter(w -> w.getMessage().getString().equals("480\u00d7270")).findFirst().orElseThrow();
                ((net.minecraft.client.gui.components.Button) resolution).onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0, new net.minecraft.client.input.MouseButtonInfo(0, 0)));
            });
            context.waitTicks(3); context.takeScreenshot("christmas_" + code + "_10_small_preview");
            context.clickScreenButton("socialmod.visual.back"); checkChristmasLayout(context);
            context.runOnClient(client -> {
                try {
                    var design = ClientState.get().snapshot().visual.copy();
                    java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("socialmod-christmas-");
                    com.takumistudios.socialmod.client.theme.PresetFiles.export(design, directory);
                    try (var zip = new java.util.zip.ZipFile(directory.resolve("series.zip").toFile())) {
                        if (zip.getEntry("assets/socialmod/font/christmas.json") == null || zip.getEntry("assets/socialmod/textures/gui/sprites/christmas/panel.png") == null) throw new AssertionError("Christmas assets missing from export");
                    }
                    java.nio.file.Files.copy(directory.resolve("series.zip"), directory.resolve("import.zip"));
                    var loaded = com.takumistudios.socialmod.client.theme.PresetFiles.load(directory);
                    if (!loaded.font.equals("socialmod:christmas") || !java.nio.file.Files.exists(directory.resolve("assets/socialmod/textures/gui/sprites/christmas/panel.png"))) throw new AssertionError("Christmas import failed");
                    com.takumistudios.socialmod.client.theme.PresetFiles.export(loaded, java.nio.file.Path.of("christmas-export"));
                } catch (java.io.IOException e) { throw new AssertionError("Christmas pack export/import failed", e); }
            });
            if (code.equals("es_es")) {
                context.setScreen(() -> new com.takumistudios.socialmod.client.screen.VisualEditorScreen(new SocialScreen(conversation)));
                context.clickScreenButton("socialmod.visual.preset"); context.clickScreenButton("socialmod.visual.template.full");
                context.clickScreenButton("socialmod.visual.publish");
                context.waitFor(client -> ClientState.get().snapshot().visual.decoration.equals("none"), 100);
                context.clickScreenButton("socialmod.visual.rollback"); context.clickScreenButton("gui.yes");
                context.waitFor(client -> ClientState.get().snapshot().visual.font.equals("socialmod:christmas"), 100);
                context.waitFor(client -> com.takumistudios.socialmod.client.compat.ClientCompat.currentScreen() instanceof SocialScreen, 100);
            }
        }
        context.setScreen(() -> null);
        world.getServer().runOnServer(server -> SocialServer.get().visuals().publish(com.takumistudios.socialmod.common.model.VisualDesign.GSON.toJson(previous), "test cleanup"));
        context.waitFor(client -> ClientState.get().snapshot().visual.decoration.equals(previous.decoration), 100);
        language(context, originalLanguage);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("socialmod.test.christmasPack"))
            context.waitFor(client -> com.takumistudios.socialmod.client.theme.RowTemplates.enabled("message")
                && com.takumistudios.socialmod.client.theme.LocalSeriesDesign.get()!=null,200);
        TestWorldSave save;
        String[] groupKey = new String[1];
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            save = world.getWorldSave();
            waitForChunks(world);
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");
            context.waitFor(client -> ClientState.get().connected(), 200);

            world.getServer().runCommand("execute as @p run g create TF Team Forest");
            world.getServer().runCommand("execute as @p run g channel create comercio");
            world.getServer().runCommand("execute as @p run g pin Reunión el sábado a las 18:00");
            world.getServer().runCommand("execute as @p run status text Construyendo la base");
            world.getServer().runCommand("execute as @p run g hola **equipo**, nos vemos en [coords]");
            world.getServer().runCommand("execute as @p run g alguien tiene diamantes? @TF https://example.com");
            context.waitFor(client -> ClientState.get().mainGroup() != null, 200);

            groupKey[0] = context.computeOnClient(client -> {
                SnapshotDto.GroupView group = ClientState.get().mainGroup();
                return ConversationId.group(group.id, "general").key();
            });
            context.setScreen(() -> new SocialScreen(groupKey[0]));
            context.waitFor(client -> {
                ClientState.ConversationCache cache = ClientState.get().existing(groupKey[0]);
                return cache != null && cache.messages.size() >= 2;
            }, 200);
            context.waitTicks(10);
            context.takeScreenshot("socialmod_panel");
            context.runOnClient(client -> {
                var binding = com.takumistudios.socialmod.client.SocialKeys.OPEN_PANEL;
                var original = com.mojang.blaze3d.platform.InputConstants.getKey(binding.saveString());
                try {
                    for (String keyName : new String[]{"key.keyboard.k", "key.keyboard.h"}) {
                        var assigned = com.mojang.blaze3d.platform.InputConstants.getKey(keyName);
                        int key = assigned.getValue();
                        binding.setKey(assigned);
                        net.minecraft.client.KeyMapping.resetMapping();
                        com.takumistudios.socialmod.client.SocialModClient.openPanel(groupKey[0]);
                        var panel = com.takumistudios.socialmod.client.compat.ClientCompat.currentScreen();
                        if (!(panel instanceof SocialScreen)) throw new AssertionError("Panel did not open");
                        if (!panel.keyPressed(new net.minecraft.client.input.KeyEvent(key, 0, 0))) throw new AssertionError("Assigned key not consumed");
                        if (com.takumistudios.socialmod.client.compat.ClientCompat.currentScreen() != null) throw new AssertionError("Assigned key did not close panel");
                    }
                } finally { binding.setKey(original); net.minecraft.client.KeyMapping.resetMapping(); }
            });

            UUID self = context.computeOnClient(client -> ClientState.get().selfId());
            String name = context.computeOnClient(client -> client.player.getGameProfile().name());
            context.setScreen(() -> new ProfileScreen(new SocialScreen(groupKey[0]), self, name));
            context.waitTicks(5);
            context.takeScreenshot("socialmod_profile");

            String groupId = context.computeOnClient(client -> ClientState.get().mainGroup().id);
            context.setScreen(() -> new GroupSettingsScreen(null, groupId));
            context.waitTicks(5);
            context.takeScreenshot("socialmod_group_settings");

            context.setScreen(() -> new SettingsScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("socialmod_settings");

            context.setScreen(() -> null);
            context.runOnClient(client -> {
                ToastHud.push(new ToastData(Payloads.NotifyKind.PRIVATE, self, "Alex", "¿Nos vemos en x:120, z:-450?", groupKey[0]));
                ToastHud.push(new ToastData(Payloads.NotifyKind.MENTION, null, "Steve te ha mencionado en Team Forest", "@TF ¡a minar!", groupKey[0]));
            });
            context.waitTicks(10);
            context.takeScreenshot("socialmod_toasts");

            context.setScreen(() -> new QuickReplyScreen(groupKey[0]));
            context.waitTicks(5);
            context.takeScreenshot("socialmod_quick_reply");
            context.setScreen(() -> null);

            // ---------- SOCIALMOD_ERRORES ----------
            // 1: el estado elegido vuelve en el snapshot y el botón puede seguir rotando
            for (String status : new String[]{"away", "dnd", "online"}) {
                context.runOnClient(client -> com.takumistudios.socialmod.client.ClientNet.action(
                        com.takumistudios.socialmod.common.net.SocialAction.SET_STATUS, status));
                context.waitFor(client -> status.equals(ClientState.get().snapshot().self.status), 100);
            }
            // 4: la vista previa de la conversación no muestra marcadores sin resolver
            String preview = context.computeOnClient(client -> ClientState.get().snapshot().conversations.stream()
                    .filter(v -> v.id.equals(groupKey[0])).map(v -> v.preview).findFirst().orElse(""));
            world.getServer().runCommand("execute as @p run g voy a [CORDS]");
            context.waitFor(client -> ClientState.get().snapshot().conversations.stream()
                    .anyMatch(v -> v.id.equals(groupKey[0]) && v.preview.contains("x: ")), 200);
            if (preview.contains("[coords]")) {
                throw new AssertionError("La vista previa muestra [coords] sin resolver: " + preview);
            }
            // 3: emblema y color, etiqueta con icono y selector visual
            world.getServer().runCommand("execute as @p run g icon swords");
            world.getServer().runCommand("execute as @p run g color #3366FF");
            world.getServer().runCommand("execute as @p run socialteam create confirm Forest TEAM");
            context.waitFor(client -> {
                Payloads.TagEntry tag = ClientState.get().tagOf(client.player.getUUID());
                return tag != null && tag.tag().equals("Forest TEAM");
            }, 200);
            context.setScreen(() -> new com.takumistudios.socialmod.client.screen.TagStyleScreen(null, "TF", 0x3366FF, "swords", "leader", (rgb, icon) -> { }));
            context.waitTicks(5);
            context.takeScreenshot("socialmod_tag_style");
            context.setScreen(() -> new com.takumistudios.socialmod.client.screen.CreateGroupScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("socialmod_create_group");
            context.setScreen(() -> null);

            context.setScreen(() -> new com.takumistudios.socialmod.client.screen.TeamScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("socialmod_team");
            context.runOnClient(client -> ClientState.get().snapshot().teamAdmin = true);
            context.setScreen(() -> new com.takumistudios.socialmod.client.screen.TeamScreen(null));
            context.clickScreenButton("socialmod.team.manage");
            context.waitTicks(3);
            context.takeScreenshot("socialmod_team_admin");
            context.runOnClient(client -> ClientState.get().snapshot().teamAdmin = false);

            for (String mode : new String[]{"compact", "sidebar", "full"}) {
                world.getServer().runOnServer(server -> {
                    var visual = SocialServer.get().visuals().design().copy(); visual.mode = mode;
                    SocialServer.get().visuals().publish(com.takumistudios.socialmod.common.model.VisualDesign.GSON.toJson(visual), "test");
                });
                context.waitFor(client -> ClientState.get().snapshot().visual.mode.equals(mode), 100);
                context.setScreen(() -> new SocialScreen(groupKey[0]));
                context.waitTicks(5);
                context.takeScreenshot("socialmod_mode_" + mode);
            }
            context.setScreen(() -> new com.takumistudios.socialmod.client.screen.VisualEditorScreen(new SocialScreen(groupKey[0])));
            context.waitTicks(5);
            context.takeScreenshot("socialmod_visual_editor");
            context.runOnClient(client -> {
                var screen = com.takumistudios.socialmod.client.compat.ClientCompat.currentScreen();
                var widgets = net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen);
                var field = widgets.stream().filter(w -> w instanceof net.minecraft.client.gui.components.EditBox && w.getMessage().getString().equals("widthPercent")).findFirst().orElseThrow();
                ((net.minecraft.client.gui.components.EditBox) field).setValue("88");
                var apply = widgets.stream().filter(w -> w instanceof net.minecraft.client.gui.components.Button && w.getY() == field.getY()).findFirst().orElseThrow();
                ((net.minecraft.client.gui.components.Button) apply).onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0, new net.minecraft.client.input.MouseButtonInfo(0, 0)));
                if (com.takumistudios.socialmod.client.theme.VisualManager.get().widthPercent != 88) throw new AssertionError("Visual integer property did not apply");
            });
            context.clickScreenButton("socialmod.visual.undo");
            context.runOnClient(client -> { if (com.takumistudios.socialmod.client.theme.VisualManager.get().widthPercent != 78) throw new AssertionError("Visual undo failed"); });
            context.clickScreenButton("socialmod.visual.redo");
            context.runOnClient(client -> { if (com.takumistudios.socialmod.client.theme.VisualManager.get().widthPercent != 88) throw new AssertionError("Visual redo failed"); });

            context.runOnClient(client -> {
                try {
                    java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("socialmod-preset-test-");
                    var design = com.takumistudios.socialmod.client.theme.VisualManager.get().copy();
                    com.takumistudios.socialmod.client.theme.PresetFiles.export(design, directory);
                    var loaded = com.takumistudios.socialmod.client.theme.PresetFiles.load(directory);
                    if (loaded.widthPercent != 88) throw new AssertionError("Preset JSON round trip failed");
                    java.nio.file.Files.copy(directory.resolve("series.zip"), directory.resolve("import.zip"));
                    var zipped = com.takumistudios.socialmod.client.theme.PresetFiles.load(directory);
                    if (zipped.widthPercent != 88) throw new AssertionError("Preset ZIP round trip failed");
                } catch (java.io.IOException e) { throw new AssertionError("Preset files failed", e); }
            });
            context.runOnClient(client -> com.takumistudios.socialmod.client.theme.VisualManager.preview(null));
            context.setScreen(() -> null);

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                server.getPlayerList().op(new net.minecraft.server.players.NameAndId(player.getGameProfile()), java.util.Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER), java.util.Optional.of(false));
                com.takumistudios.socialmod.server.PermissionBridge.invalidate(player.getUUID());
                SocialServer.get().snapshots().send(player);
            });
            christmasDocumentation(context, world, groupKey[0]);

            // Ping de party (fase 3): el servidor lo valida y el cliente lo muestra
            world.getServer().runCommand("execute as @p run party create");
            context.waitFor(client -> ClientState.get().inParty(), 200);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                SocialServer.get().party().ping(player, player.getBlockX() + 5, player.getBlockY(), player.getBlockZ() + 5);
            });
            context.waitFor(client -> !com.takumistudios.socialmod.client.PartyClient.pings().isEmpty(), 100);
            context.waitTicks(10);
            context.takeScreenshot("socialmod_ping");

            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("fancymenu")) FancyIntegrationTests.run(context, groupKey[0]);

            // Xaero (solo con -Pxaero): waypoint real en el minimapa
            boolean xaero = context.computeOnClient(client -> com.takumistudios.socialmod.client.compat.MapCompat.waypointsAvailable());
            if (xaero) {
                context.waitTicks(40);
                boolean added = context.computeOnClient(client -> com.takumistudios.socialmod.client.compat.MapCompat.addWaypoint(
                        "SocialMod", client.level.dimension().identifier().toString(), 10, 70, 10, 0x3366FF));
                if (!added) {
                    throw new AssertionError("No se pudo crear el waypoint en Xaero's Minimap");
                }
                boolean measured = context.computeOnClient(client -> com.takumistudios.socialmod.client.compat.MapCompat.minimapArea() != null);
                if (!measured) {
                    throw new AssertionError("No se pudo leer la posición del minimapa de Xaero");
                }
                context.takeScreenshot("socialmod_xaero");
            }
            startHangWatchdog();
        }
        HANG_WATCH.set(false);

        // Reinicio: el grupo y el historial deben persistir (StorageBackend "file")
        try (TestSingleplayerContext reopened = save.open()) {
            waitForChunks(reopened);
            boolean groupPersisted = reopened.getServer().computeOnServer(server -> {
                SocialServer social = SocialServer.get();
                return social != null && social.groups().all().values().stream().anyMatch(g -> g.tag.equals("TF") && g.channel("comercio") != null);
            });
            if (!groupPersisted) {
                throw new AssertionError("El grupo no persistió tras reiniciar el mundo");
            }
            reopened.getServer().runOnServer(server -> SocialServer.get().storage().withConversation(groupKey[0], conversation -> { }));
            context.waitTicks(20);
            boolean teamPersisted = reopened.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var team = SocialServer.get().teams().of(player.getUUID());
                return team != null && team.name.equals("Forest TEAM") && SocialServer.get().record(player).teamChosen;
            });
            if (!teamPersisted) throw new AssertionError("TEAM assignment did not survive world restart");
            boolean historyPersisted = reopened.getServer().computeOnServer(server -> {
                Conversation conversation = SocialServer.get().storage().cachedConversation(groupKey[0]);
                return conversation != null && conversation.messages.size() >= 2;
            });
            if (!historyPersisted) {
                throw new AssertionError("El historial del grupo no persistió tras reiniciar el mundo");
            }
            Group group = reopened.getServer().computeOnServer(server -> SocialServer.get().groups().all().values().iterator().next());
            if (!group.pinned.startsWith("Reunión")) {
                throw new AssertionError("El mensaje fijado no persistió");
            }
        }
    }
}
