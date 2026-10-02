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

    private static boolean conflictsChecked;

    private SocialKeys() {
    }

    public static void register() {
        KeyMappingHelper.registerKeyMapping(QUICK_REPLY);
        KeyMappingHelper.registerKeyMapping(OPEN_PANEL);
    }

    public static void resetConflictCheck() {
        conflictsChecked = false;
    }

    /** Avisa una vez por sesión si alguna de nuestras teclas coincide con otra. */
    public static void checkConflicts() {
        if (conflictsChecked) {
            return;
        }
        conflictsChecked = true;
        Minecraft minecraft = Minecraft.getInstance();
        List<String> conflicts = new ArrayList<>();
        for (KeyMapping ours : new KeyMapping[]{QUICK_REPLY, OPEN_PANEL}) {
            if (ours.isUnbound()) {
                continue;
            }
            for (KeyMapping other : minecraft.options.keyMappings) {
                if (other != ours && !other.isUnbound() && other.same(ours)) {
                    conflicts.add(Component.translatable(ours.getName()).getString() + " ↔ " + Component.translatable(other.getName()).getString()
                            + " (" + ours.getTranslatedKeyMessage().getString() + ")");
                }
            }
        }
        if (!conflicts.isEmpty() && minecraft.player != null) {
            SocialMod.LOGGER.warn("[SocialMod] Conflictos de teclas: {}", conflicts);
            minecraft.player.sendSystemMessage(Component.translatable("socialmod.keys.conflict", String.join(", ", conflicts))
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
    }
}
