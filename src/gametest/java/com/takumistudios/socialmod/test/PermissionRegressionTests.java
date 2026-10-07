package com.takumistudios.socialmod.test;

import com.takumistudios.socialmod.server.PermissionBridge;
import net.fabricmc.fabric.api.permission.v1.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Test provider responds only to explicitly enrolled players, without changing other tests. */
final class PermissionRegressionTests {
    private static final Map<UUID, Object> VALUES = new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean registered;
    static void set(ServerPlayer player, Object value) {
        if (!registered) {
            PermissionEvents.ON_REQUEST.register(new PermissionEvents.OnRequest() {
                @Override public <T> T handlePermissionRequest(PermissionContext context, PermissionNode<T> node) {
                    Object value = VALUES.get(context.uuid());
                    if (value == null || !node.key().getNamespace().equals("socialmod")) return null;
                    if (value instanceof RuntimeException failure) throw failure;
                    return node.cast(value);
                }
            });
            registered = true;
        }
        VALUES.put(player.getUUID(), value);
    }
    static void clear(ServerPlayer player) { VALUES.remove(player.getUUID()); }
    static void check(ServerPlayer player) {
        try {
            set(player, true);
            if (!PermissionBridge.isStaff(player, PermissionBridge.MOD_HISTORY)) throw new AssertionError("grant ignored");
            set(player, false);
            if (PermissionBridge.isStaff(player, PermissionBridge.MOD_HISTORY)) throw new AssertionError("revocation cached");
            set(player, new IllegalStateException("provider failure"));
            if (PermissionBridge.allows(player, PermissionBridge.CHAT_PRIVATE)) throw new AssertionError("failed provider granted access");
        } finally { clear(player); }
    }
}
