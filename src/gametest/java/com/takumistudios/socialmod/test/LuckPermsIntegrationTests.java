package com.takumistudios.socialmod.test;

import com.takumistudios.socialmod.server.PermissionBridge;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.MetaNode;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** Loaded only in the real LuckPerms profile. No production dependency on its API. */
final class LuckPermsIntegrationTests {
    static void run(GameTestHelper helper) {
        var api = LuckPermsProvider.get();
        var uuid = java.util.UUID.randomUUID();
        var loaded = api.getUserManager().loadUser(uuid, "socialmod_lp");
        int[] stage = {0};
        var contextActive = new java.util.concurrent.atomic.AtomicBoolean(true);
        var calculator = new net.luckperms.api.context.ContextCalculator<ServerPlayer>() {
            @Override public void calculate(ServerPlayer target, net.luckperms.api.context.ContextConsumer consumer) {
                if (target.getUUID().equals(uuid) && contextActive.get()) consumer.accept("socialmod-test", "allowed");
            }
        };
        api.getContextManager().registerCalculator(calculator);
        ServerPlayer[] player = {null};
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<?>> pending = new java.util.concurrent.atomic.AtomicReference<>(loaded);
        helper.succeedWhen(() -> {
            if (!pending.get().isDone()) throw helper.assertionException("Waiting for LuckPerms user data");
            pending.get().join();
            if (stage[0] == 0) {
                var user = loaded.join();
                user.data().add(PermissionNode.builder("socialmod.chat.private").value(false).build());
                user.data().add(PermissionNode.builder("socialmod.mod.history").value(true).withContext("socialmod-test", "allowed").build());
                user.data().add(MetaNode.builder("socialmod:limit.groups", "7").build());
                var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(uuid, "socialmod_lp"), false);
                player[0] = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
                var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
                new io.netty.channel.embedded.EmbeddedChannel(connection);
                helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player[0], cookie);
                com.takumistudios.socialmod.server.SocialServer.get().record(player[0]);
                pending.set(api.getUserManager().saveUser(user)); stage[0] = 1;
                throw helper.assertionException("Waiting for initial permissions");
            }
            if (stage[0] == 1) {
                if (PermissionBridge.allows(player[0], PermissionBridge.CHAT_PRIVATE)) throw helper.assertionException("LuckPerms denial ignored");
                if (!PermissionBridge.isStaff(player[0], PermissionBridge.MOD_HISTORY)) throw helper.assertionException("LuckPerms grant ignored");
                if (PermissionBridge.limit(player[0], PermissionBridge.LIMIT_GROUPS, 3) != 7) throw helper.assertionException("LuckPerms integer metadata ignored");
                contextActive.set(false); api.getContextManager().signalContextUpdate(player[0]);
                stage[0] = 2; throw helper.assertionException("Waiting for context change");
            }
            if (stage[0] == 2) {
                if (PermissionBridge.isStaff(player[0], PermissionBridge.MOD_HISTORY)) throw helper.assertionException("Old LuckPerms context cached");
                contextActive.set(true); api.getContextManager().signalContextUpdate(player[0]);
                stage[0] = 3; throw helper.assertionException("Waiting for context restoration");
            }
            if (stage[0] == 3) {
                if (!PermissionBridge.isStaff(player[0], PermissionBridge.MOD_HISTORY)) throw helper.assertionException("Restored LuckPerms context ignored");
                var user = loaded.join(); user.data().clear();
                user.data().add(PermissionNode.builder("socialmod.mod.history").value(false).build());
                user.data().add(MetaNode.builder("socialmod:limit.groups", "invalid").build());
                pending.set(api.getUserManager().saveUser(user)); stage[0] = 4;
                throw helper.assertionException("Waiting for permission revocation");
            }
            if (PermissionBridge.isStaff(player[0], PermissionBridge.MOD_HISTORY)) throw helper.assertionException("LuckPerms revocation cached");
            if (PermissionBridge.limit(player[0], PermissionBridge.LIMIT_GROUPS, 3) != 3) throw helper.assertionException("Invalid metadata must use config");
            api.getContextManager().unregisterCalculator(calculator);
        });
    }
}
