package com.takumistudios.socialmod.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Original role PNGs shared by native lists and custom row templates. */
public final class RoleIcons {
    private static final Identifier LEADER=Identifier.fromNamespaceAndPath("socialmod","textures/gui/roles/leader.png");
    private static final Identifier VIP=Identifier.fromNamespaceAndPath("socialmod","textures/gui/roles/vip.png");
    private static final Identifier MEMBER=Identifier.fromNamespaceAndPath("socialmod","textures/gui/roles/member.png");
    public static void draw(GuiGraphicsExtractor graphics, String role, int x, int y, int size) {
        var texture=role.equals("leader")?LEADER:role.equals("vip")?VIP:MEMBER;
        graphics.blit(RenderPipelines.GUI_TEXTURED,texture,x,y,0,0,size,size,724,724,724,724);
    }
    private RoleIcons() { }
}
