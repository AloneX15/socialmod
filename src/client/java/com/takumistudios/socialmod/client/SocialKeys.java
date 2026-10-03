package com.takumistudios.socialmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.takumistudios.socialmod.SocialMod;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Teclas (PLAN 7.1 y 14). Quick-Reply usa {@code Y} por defecto: {@code R} y {@code U} las usan JEI, REI y EMI.
 * Al entrar en un mundo se detectan conflictos y se avisa, en lugar de quitarle la tecla a otro mod.
 */
public final class SocialKeys {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SocialMod.MOD_ID, "main"));

    public static final KeyMapping QUICK_REPLY = new KeyMapping("key.socialmod.quick_reply", InputConstants.KEY_Y, CATEGORY);
    public static final KeyMapping OPEN_PANEL = new KeyMapping("key.socialmod.open_panel", InputConstants.KEY_K, CATEGORY);
    /** Ping para la party (PLAN 5.5). J: libre en vanilla 26.x y con Xaero (G es "Acciones rápidas" en 26.3). */
    public static final KeyMapping PING = new KeyMapping("key.socialmod.ping", InputConstants.KEY_J, CATEGORY);

    private static boolean conflictsChecked;

    private SocialKeys() {
    }

    public static void register() {
        KeyMappingHelper.registerKeyMapping(QUICK_REPLY);
        KeyMappingHelper.registerKeyMapping(OPEN_PANEL);
        KeyMappingHelper.registerKeyMapping(PING);
    }

    public static void resetConflictCheck() {
        conflictsChecked = false;
    }
    /** Teclas libres en vanilla 26.x (sin contar las combinaciones F3+) para recolocar las nuestras si chocan. */
    private static final String[] FREE_CANDIDATES = {"key.keyboard.j", "key.keyboard.h", "key.keyboard.n",
            "key.keyboard.i", "key.keyboard.v", "key.keyboard.y", "key.keyboard.u", "key.keyboard.b"};

    /** Las teclas de depuración solo actúan con F3 pulsado: no chocan con una tecla normal. */
    private static boolean realConflict(KeyMapping ours, KeyMapping other) {
        return other != ours && !other.isUnbound() && !other.getName().startsWith("key.debug.") && other.same(ours);
    }

    private static boolean inUse(Minecraft minecraft, KeyMapping probe) {
        for (KeyMapping other : minecraft.options.keyMappings) {
            if (realConflict(probe, other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Una vez por sesión: si una tecla nuestra sigue en su valor por defecto y otro mod usa la misma (p. ej. Xaero's
     * Minimap usa Y para sus ajustes), se mueve la nuestra a una libre y se avisa; nunca se toca la del otro mod.
     * Si el jugador ya la cambió a mano, solo se avisa.
     */
    public static void checkConflicts() {
        if (conflictsChecked) {
            return;
        }
        conflictsChecked = true;
        Minecraft minecraft = Minecraft.getInstance();
        List<String> conflicts = new ArrayList<>();
        List<String> moved = new ArrayList<>();
        for (KeyMapping ours : new KeyMapping[]{QUICK_REPLY, OPEN_PANEL, PING}) {
            if (ours.isUnbound()) {
                continue;
            }
            KeyMapping clash = null;
            for (KeyMapping other : minecraft.options.keyMappings) {
                if (realConflict(ours, other)) {
                    clash = other;
                    break;
                }
            }
            if (clash == null) {
                continue;
            }
            String clashName = Component.translatable(clash.getName()).getString();
            if (ours.isDefault() && relocate(minecraft, ours)) {
                moved.add(Component.translatable(ours.getName()).getString() + " → " + ours.getTranslatedKeyMessage().getString()
                        + " (" + clashName + ")");
            } else {
                conflicts.add(Component.translatable(ours.getName()).getString() + " ↔ " + clashName
                        + " (" + ours.getTranslatedKeyMessage().getString() + ")");
            }
        }
        if (!moved.isEmpty()) {
            KeyMapping.resetMapping();
            minecraft.options.save();
            SocialMod.LOGGER.info("[SocialMod] Teclas recolocadas por conflicto: {}", moved);
        }
        if (minecraft.player == null) {
            return;
        }
        if (!moved.isEmpty()) {
            minecraft.player.sendSystemMessage(Component.translatable("socialmod.keys.moved", String.join(", ", moved))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (!conflicts.isEmpty()) {
            SocialMod.LOGGER.warn("[SocialMod] Conflictos de teclas: {}", conflicts);
            minecraft.player.sendSystemMessage(Component.translatable("socialmod.keys.conflict", String.join(", ", conflicts))
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
    }

    private static boolean relocate(Minecraft minecraft, KeyMapping ours) {
        InputConstants.Key original = ours.getDefaultKey();
        for (String name : FREE_CANDIDATES) {
            ours.setKey(InputConstants.getKey(name));
            if (!inUse(minecraft, ours)) {
                return true;
            }
        }
        ours.setKey(original);
        return false;
    }
}
