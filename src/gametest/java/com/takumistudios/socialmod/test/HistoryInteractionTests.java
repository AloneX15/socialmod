package com.takumistudios.socialmod.test;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.screen.SocialScreen;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.common.net.Payloads;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.*;
import java.util.*;
/** Actual rendering and pointer input against a long conversation. */
final class HistoryInteractionTests {
    static int number(Object instance,String name) {
        try { var field=instance.getClass().getDeclaredField(name);field.setAccessible(true);return field.getInt(instance); }
        catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    static void invalidate() {
        try { var changed=ClientState.class.getDeclaredMethod("changed");changed.setAccessible(true);changed.invoke(ClientState.get()); }
        catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    static void run(ClientGameTestContext context,String conversation) {
        var previous=context.computeOnClient(client->List.copyOf(ClientState.get().conversation(conversation).messages));
        boolean more=context.computeOnClient(client->ClientState.get().conversation(conversation).hasMore);
        try {
            context.runOnClient(client->{
                var cache=ClientState.get().conversation(conversation);cache.hasMore=false;cache.messages.clear();
                for(int i=0;i<80;i++)cache.messages.add(new Payloads.MessageView(10000+i,client.player.getUUID(),"History","History line "+i,1,false,false,List.of()));
                invalidate();ClientCompat.setScreen(new SocialScreen(conversation));
            });context.waitTicks(6);
            context.runOnClient(client->{
                var screen=(SocialScreen)ClientCompat.currentScreen();int x=number(screen,"chatX")+number(screen,"chatW")-4;int y=number(screen,"historyTop");
                if(number(screen,"historyMaximum")<=0)throw new AssertionError("No scrollbar for long history");
                if(!screen.mouseClicked(new MouseButtonEvent(x,y+2,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false))throw new AssertionError("Scrollbar click ignored");
                screen.mouseDragged(new MouseButtonEvent(x,y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),0,-10);screen.mouseReleased(new MouseButtonEvent(x,y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)));
                if(number(screen,"chatScroll")!=number(screen,"historyMaximum"))throw new AssertionError("Scrollbar drag did not reach oldest messages");
            });context.waitTicks(3);context.takeScreenshot("history_01_oldest");
            int before=context.computeOnClient(client->number(ClientCompat.currentScreen(),"chatScroll"));
            context.runOnClient(client->{ClientState.get().conversation(conversation).messages.add(new Payloads.MessageView(10080,client.player.getUUID(),"History","New message",1,false,false,List.of()));invalidate();});context.waitTicks(4);
            context.runOnClient(client->{if(number(ClientCompat.currentScreen(),"chatScroll")<=before)throw new AssertionError("New message moved history reader");});
        } finally {context.runOnClient(client->{var cache=ClientState.get().conversation(conversation);cache.messages.clear();cache.messages.addAll(previous);cache.hasMore=more;invalidate();ClientCompat.setScreen(new SocialScreen(conversation));});context.waitTicks(3);}
    }
    private HistoryInteractionTests() { }
}
