package com.takumistudios.socialmod.client;

import com.takumistudios.socialmod.client.theme.VisualManager;

import com.mojang.blaze3d.vertex.PoseStack;
import com.takumistudios.socialmod.common.net.Payloads;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Etiqueta del grupo en el nametag (PLAN 11), sin mixins: una capa de render de Fabric modifica el estado de render
 * que vanilla ya preparó, antes de que vanilla envíe el nametag.
 * <ul>
 *     <li>Debajo del nombre (por defecto): se usa {@code scoreText}, la línea que vanilla dibuja bajo el nombre
 *     para el marcador "belowName" del scoreboard, en 26.1.2, 26.2 y 26.3. Si el servidor ya usa ese marcador
 *     (p. ej. la vida), la etiqueta se pone delante en la misma línea y no se pierde nada.</li>
 *     <li>Delante del nombre (opción {@code nametags.belowName = false}): {@code ⚔ [TAG] Nombre}.</li>
 * </ul>
 * Se respetan la distancia, el agacharse y la invisibilidad: si vanilla no muestra el nombre no se añade nada.
 * Coste: una búsqueda en un mapa por jugador visible y frame, sin asignaciones si el estado ya está etiquetado.
 */
public final class GroupTagLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Línea ya etiquetada en este estado (evita duplicar la etiqueta si el estado se reutiliza). */
    private static final RenderStateDataKey<Component> TAGGED = RenderStateDataKey.create(() -> "socialmod:tagged_name");

    public GroupTagLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Component name = state.nameTag;
        ClientConfig.Nametags config = ClientConfig.get().nametags;
        var visual = VisualManager.get();
        if (name == null || !ClientState.get().connected()) {
            return;
        }
        Component tagged = state.getData(TAGGED);
        if (tagged != null && (tagged == state.scoreText || tagged == state.nameTag)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(state.id);
        if (!(entity instanceof Player player)) {
            return;
        }
        Payloads.TagEntry tag = ClientState.get().tagOf(player.getUUID());
        if (tag == null) {
            return;
        }
        MutableComponent line = TagRenderer.line(tag.tag(), tag.color(), tag.icon(), tag.role());
        MutableComponent result;
        if (visual.tagBelow) {
            result = state.scoreText == null ? line : line.append(Component.literal("  ")).append(state.scoreText);
            state.scoreText = result;
        } else {
            result = line.append(Component.literal(" ")).append(name);
            state.nameTag = result;
        }
        state.setData(TAGGED, result);
    }
}
