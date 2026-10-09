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
    private static void seriesDocumentation(ClientGameTestContext context,String conversation) {
        for(String style:com.takumistudios.socialmod.common.model.SeriesTemplates.IDS) {
            context.runOnClient(client -> com.takumistudios.socialmod.client.theme.VisualManager.preview(com.takumistudios.socialmod.common.model.SeriesTemplates.visual(style)));
            context.setScreen(() -> new SocialScreen(conversation)); context.waitTicks(5);
            context.takeScreenshot("series_native_"+style);
        }
        context.runOnClient(client -> com.takumistudios.socialmod.client.theme.VisualManager.preview(null));
        context.setScreen(() -> null);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var state = ClientState.get(); state.reset();
            state.setActiveConversation("cache-active"); var active = state.conversation("cache-active");
            for (int i = 0; i < 1000; i++) state.conversation("cache-test-" + i);
            if (state.existing("cache-active") != active || state.existing("cache-test-0") != null) throw new AssertionError("Conversation cache did not evict old entries or preserve active conversation");
            int retained = 0; for (int i = 0; i < 1000; i++) if (state.existing("cache-test-" + i) != null) retained++;
            if (retained != 127) throw new AssertionError("Conversation cache exceeded 128 entries: " + retained);
            active.messages.add(new com.takumistudios.socialmod.common.net.Payloads.MessageView(1,java.util.UUID.randomUUID(),"Test","Keep history",1,false,false,java.util.List.of()));
            active.requested=true;
            state.onMessages(new com.takumistudios.socialmod.common.net.Payloads.MessagesS2C("cache-active","",com.takumistudios.socialmod.common.net.Payloads.MessagesMode.UNAVAILABLE,true,java.util.List.of()));
            if(active.requested || active.messages.size()!=1)throw new AssertionError("Read failure discarded cached history or blocked retry");
            state.reset();
        });
        if (Boolean.getBoolean("socialmod.test.seriesPack"))
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
            if (com.takumistudios.socialmod.client.compat.fancy.FancyBridge.installed()) FancyIntegrationTests.prepare(context);
            HistoryInteractionTests.run(context,groupKey[0]);
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
            seriesDocumentation(context, groupKey[0]);

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

            BannerSearchAppearanceTests.run(context, world, groupKey[0]);
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
        ProfileShutdownTests.run(context);
    }
}
