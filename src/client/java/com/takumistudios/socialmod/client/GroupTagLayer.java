package com.takumistudios.socialmod.client;

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
 * Etiqueta del grupo junto al nombre del jugador (PLAN 11), sin mixins: una capa de render de Fabric añade
 * {@code [TAG]} delante del nametag que vanilla ya decidió mostrar. Así se respetan la distancia, el agacharse y la
 * invisibilidad (si vanilla no muestra el nombre, no hay nada que etiquetar y no se revela a nadie).
 * Las capas se procesan antes de que vanilla envíe el nametag, por eso basta con modificar el estado de render.
 */
public final class GroupTagLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Nametag ya etiquetado en este estado (evita duplicar la etiqueta si el estado se reutiliza). */
    private static final RenderStateDataKey<Component> TAGGED = RenderStateDataKey.create(() -> "socialmod:tagged_name");

    public GroupTagLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Component name = state.nameTag;
        if (name == null || !ClientConfig.get().nametags.showGroupTags || !ClientState.get().connected()) {
            return;
        }
        if (state.getData(TAGGED) == name) {
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
        MutableComponent tagged = Component.literal("[" + tag.tag() + "] ").withColor(tag.color() & 0xFFFFFF).append(name);
        state.nameTag = tagged;
        state.setData(TAGGED, tagged);
    }
}
