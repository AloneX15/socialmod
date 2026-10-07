package com.takumistudios.socialmod.test;

import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.screen.*;
import com.takumistudios.socialmod.common.model.RowDesign;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import java.util.*;

final class RowEditorRegressionTests {
    private static Object field(Object screen,String name) throws ReflectiveOperationException { var f=RowTemplateScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen); }
    @SuppressWarnings("unchecked") private static Map<String,EditBox> inputs(Object screen) throws ReflectiveOperationException { return (Map<String,EditBox>)field(screen,"inputs"); }
    private static void press(RowTemplateScreen screen,String key) {
        for(var widget:net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen)) if(widget.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals(key)) {
            ((Button)widget).onPress(new net.minecraft.client.input.MouseButtonEvent(0,0,new net.minecraft.client.input.MouseButtonInfo(0,0)));return;
        }
        throw new AssertionError("Missing row editor button: "+key);
    }
    static void run(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var parent=ClientCompat.currentScreen(); var editor=new RowTemplateScreen(parent);ClientCompat.setScreen(editor);
            try {
                inputs(editor).get("x").setValue("invalid"); press(editor,"socialmod.advanced.properties");
                if((int)field(editor,"page")!=0 || !inputs(editor).get("x").getValue().equals("invalid")) throw new AssertionError("Invalid input lost or navigation proceeded");
                press(editor,"socialmod.advanced.kind");if((int)field(editor,"kind")!=0)throw new AssertionError("Invalid row switched");
                inputs(editor).get("x").setValue("17");press(editor,"socialmod.advanced.kind");
                var draft=(RowDesign)field(editor,"draft");if(draft.templates.get("message").parts.getFirst().x!=17)throw new AssertionError("Valid input was not applied before switch");
                inputs(editor).get("x").setValue("bad-resize");editor.resize(editor.width,editor.height);
                if(!inputs(editor).get("x").getValue().equals("bad-resize"))throw new AssertionError("Resize discarded pending fields");
                for(int i=0;i<65;i++) { inputs(editor).get("x").setValue(""+(20+i));press(editor,"socialmod.advanced.apply"); }
                if(((Deque<?>)field(editor,"undo")).size()>40)throw new AssertionError("Undo history is unbounded");
                press(editor,"socialmod.visual.undo");press(editor,"socialmod.visual.redo");
            } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
            ClientCompat.setScreen(parent);
        });
    }
    private RowEditorRegressionTests() { }
}
