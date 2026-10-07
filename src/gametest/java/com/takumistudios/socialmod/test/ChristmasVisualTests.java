package com.takumistudios.socialmod.test;

import com.takumistudios.socialmod.client.ClientConfig;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.screen.*;
import com.takumistudios.socialmod.client.theme.ChristmasSkin;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Six original textures, states, accessible compact controls and responsive panel bounds. */
final class ChristmasVisualTests {
    static void run(ClientGameTestContext context,String conversation) {
        for(String language:new String[]{"es_es","en_us"}) {
            SocialModClientGameTest.language(context,language);
            context.runOnClient(client->ClientCompat.setScreen(new Gallery()));context.waitTicks(4);
            context.takeScreenshot("christmas_"+language+"_buttons_states");
            context.runOnClient(client->{
                var screen=(Gallery)ClientCompat.currentScreen();
                var button=screen.first;int before=screen.clicks;
                screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(button.getX()+button.getWidth()/2,button.getY()+button.getHeight()/2,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);
                if(screen.clicks!=before+1)throw new AssertionError("Illustrated button lost native click");
                screen.setFocused(button);
                if(!screen.keyPressed(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,0,0))||screen.clicks!=before+2)throw new AssertionError("Illustrated button lost keyboard activation");
                try {
                    var tooltip=net.minecraft.client.gui.components.AbstractWidget.class.getDeclaredField("tooltip");tooltip.setAccessible(true);var holder=tooltip.get(screen.first);
                    if(holder.getClass().getMethod("get").invoke(holder)==null)throw new AssertionError("Illustrated button lost tooltip");
                } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
                if(((StyledButton)button).skinRole()!=ChristmasSkin.Role.GREEN)throw new AssertionError("Button role changed after customization");
                if(ChristmasSkin.role(Component.translatable("socialmod.message.delete"))!=ChristmasSkin.Role.RED)throw new AssertionError("Delete button is not red");
                if(ChristmasSkin.edge(140,32)>140/3)throw new AssertionError("Decorations cover label center");
            });
            context.takeScreenshot("christmas_"+language+"_buttons_pressed");
        }
        boolean previous=context.computeOnClient(client->ClientConfig.get().accessibility.highContrast);
        try {
            context.runOnClient(client->{ClientConfig.get().accessibility.highContrast=true;ClientCompat.setScreen(new Gallery());if(ChristmasSkin.enabled())throw new AssertionError("High contrast retained artwork");});context.waitTicks(2);context.takeScreenshot("christmas_high_contrast");
        } finally { context.runOnClient(client->ClientConfig.get().accessibility.highContrast=previous); }
        int scale=context.computeOnClient(client->client.options.guiScale().get());
        try {
            context.runOnClient(client->{client.options.guiScale().set(2);de.keksuccino.fancymenu.util.rendering.RenderingUtils.resetGuiScale();ClientCompat.setScreen(new SocialScreen(conversation));});context.waitTicks(3);
            context.runOnClient(client->{var screen=ClientCompat.currentScreen();var controls=net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).stream().filter(w->w instanceof Button||w instanceof net.minecraft.client.gui.components.EditBox).toList();
                for(var a:controls) { if(a.getX()<0||a.getY()<0||a.getRight()>screen.width||a.getBottom()>screen.height)throw new AssertionError("Christmas control outside small GUI");
                    for(var b:controls)if(a!=b&&a.getX()<b.getRight()&&a.getRight()>b.getX()&&a.getY()<b.getBottom()&&a.getBottom()>b.getY())throw new AssertionError("Christmas controls overlap");
                }
            });context.takeScreenshot("christmas_small_panel");
        } finally { context.runOnClient(client->{client.options.guiScale().set(scale);de.keksuccino.fancymenu.util.rendering.RenderingUtils.resetGuiScale();ClientCompat.setScreen(new SocialScreen(conversation));}); }
    }
    private static final class Gallery extends SocialChildScreen {
        int clicks;Button first;
        Gallery() { super(null,Component.literal("Christmas PNGs | Navidad gráfica")); }
        @Override protected void init() {
            String[] keys={"socialmod.button.accept","socialmod.message.delete","gui.back","socialmod.panel.settings","socialmod.team.title","socialmod.visual.title"};
            int w=(width-40)/4,h=32;
            for(int row=0;row<keys.length;row++)for(int column=0;column<4;column++) {
                var button=Ui.button(Component.translatable(keys[row]),b->clicks++).selected(column==2).bounds(16+column*(w+2),46+row*43,w,h).build();
                button.setTooltipDelay(java.time.Duration.ofHours(1));button.active=column!=3;button.setFocused(column==1);addRenderableWidget(button);if(row==0&&column==0)first=button;
            }
            addRenderableWidget(Ui.button(Component.literal("<"),b->clicks++).bounds(16,315,28,20).build());
            addRenderableWidget(Ui.button(Component.literal(">"),b->clicks++).bounds(48,315,28,20).build());
        }
        @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta) { }
        @Override protected void drawContent(GuiGraphicsExtractor g,int mx,int my) {
            Ui.title(g,font,title,width/2,8);String[] states={"Normal","Hover / focus","Selected","Disabled"};int w=(width-40)/4;
            for(int i=0;i<4;i++)g.text(font,states[i],16+i*(w+2),30,0xFFFFFFFF);
        }
        @Override protected boolean rebuildOnChange() { return false; }
    }
    private ChristmasVisualTests() { }
}
