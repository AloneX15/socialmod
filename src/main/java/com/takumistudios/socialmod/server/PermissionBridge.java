package com.takumistudios.socialmod.server;

import com.takumistudios.socialmod.SocialMod;
import net.fabricmc.fabric.api.permission.v1.PermissionNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Permisos con la Fabric Permission API (LuckPerms y otros proveedores, PLAN 12). Nodos {@code socialmod:<ruta>}.
 * Sin proveedor: los nodos de jugador se conceden a todos y los de staff exigen OP nivel 2.
 * Every action queries the provider; its own cache handles changes and contexts.
 */
public final class PermissionBridge {
    public static final String CHAT_PRIVATE = "chat.private";
    public static final String CHAT_LINKS = "chat.links";
    public static final String TEAM_ADMIN = "admin.teams";
    public static final String TEAM_CREATE = "team.create";
    public static final String GROUP_CREATE = "group.create";
    public static final String PARTY_CREATE = "party.create";
    public static final String MOD_MUTE = "mod.mute";
    public static final String MOD_HISTORY = "mod.history";
    public static final String MOD_DISBAND = "mod.disband";
    public static final String MOD_INSPECT = "mod.inspect";
    public static final String MOD_REPORTS = "mod.reports";
    public static final String MOD_SPY = "mod.spy";
    public static final String MOD_BYPASS = "mod.bypass";
    public static final String ADMIN_RELOAD = "admin.reload";
    public static final String DATA_EXPORT = "data.export";
    public static final String DATA_DELETE = "data.delete";
    /** Nodos enteros que sustituyen a los límites de la config. */
    public static final String LIMIT_GROUPS = "limit.groups";
    public static final String LIMIT_FRIENDS = "limit.friends";

    private PermissionBridge() {
    }

    public static Identifier node(String path) {
        return Identifier.fromNamespaceAndPath(SocialMod.MOD_ID, path);
    }

    /** Nodo de jugador: concedido por defecto. */
    public static boolean allows(ServerPlayer player, String path) {
        return check(player, path, PermissionLevel.ALL);
    }

    /** Nodo de staff: OP nivel 2 por defecto. */
    public static boolean isStaff(ServerPlayer player, String path) {
        return check(player, path, PermissionLevel.GAMEMASTERS);
    }

    private static boolean check(ServerPlayer player, String path, PermissionLevel fallback) {
        boolean result;
        try {
            result = player.checkPermission(node(path), fallback);
        } catch (RuntimeException | LinkageError e) {
            SocialMod.warnOnce("permission_api", "Fallo en la Permission API; se deniega la acci\u00f3n", e);
            result = false;
        }
        return result;
    }

    public static @Nullable Integer intValue(ServerPlayer player, String path) {
        Integer result;
        try {
            result = player.checkPermission(PermissionNode.ofInteger(node(path)));
        } catch (RuntimeException | LinkageError e) {
            SocialMod.warnOnce("permission_api_int", "Fallo en la Permission API al leer un valor entero", e);
            result = null;
        }
        return result;
    }

    public static int limit(ServerPlayer player, String path, int configValue) {
        Integer value = intValue(player, path);
        return value != null ? Math.max(0, value) : configValue;
    }

    public static boolean has(CommandSourceStack source, String path, PermissionLevel fallback) {
        try {
            return source.checkPermission(node(path), fallback);
        } catch (RuntimeException | LinkageError e) {
            SocialMod.warnOnce("permission_api_cmd", "Fallo en la Permission API; se deniega la acci\u00f3n", e);
            return false;
        }
    }

    public static Predicate<CommandSourceStack> require(String path, PermissionLevel fallback) {
        return source -> has(source, path, fallback);
    }

    /** Retained for callers compiled against earlier releases; no local permission cache remains. */
    public static void invalidate(@Nullable UUID player) { }
}
