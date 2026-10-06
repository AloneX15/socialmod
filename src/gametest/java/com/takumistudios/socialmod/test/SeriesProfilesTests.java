package com.takumistudios.socialmod.test;

import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.screen.*;
import com.takumistudios.socialmod.client.theme.*;
import com.takumistudios.socialmod.common.model.SeriesPack;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.util.concurrent.CompletableFuture;

/** Real optional editor reload, profile activation, backup restoration and small-GUI review. */
final class SeriesProfilesTests {
    static void run(ClientGameTestContext context) {
        var catalog=await(context,context.computeOnClient(c->SeriesProfiles.catalog()));
        var selection=catalog.layouts().stream().filter(p->p.substring(p.lastIndexOf('/')+1).startsWith("socialmod_")).toList();
        var saved=await(context,context.computeOnClient(c->SeriesProfiles.save(null,"Winter","Test author","1.0",selection)));
        var current=await(context,context.computeOnClient(c->SeriesProfiles.catalog()));
        var profile=current.profiles().stream().filter(p->p.file().equals(saved)).findFirst().orElseThrow();
        var duplicate=await(context,context.computeOnClient(c->SeriesProfiles.duplicate(profile,"Winter copy")));
        if(saved.equals(duplicate)) throw new AssertionError("Profile duplication replaced the source");
        await(context,context.computeOnClient(c->SeriesProfiles.activate(saved)));
        context.waitTicks(3);
        var activated=await(context,context.computeOnClient(c->SeriesProfiles.catalog()));
        if(!activated.active().equals(saved.getFileName().toString())) throw new AssertionError("Profile not activated");
        await(context,context.computeOnClient(c->SeriesProfiles.restore()));
        var pack=await(context,context.computeOnClient(c->SeriesProfiles.inspect(saved)));
        if(pack.files().keySet().stream().anyMatch(p->p.endsWith("options.txt"))) throw new AssertionError("Unrelated preferences exported");
        await(context,context.computeOnClient(c->SeriesProfiles.importPack(pack)));
        await(context,context.computeOnClient(c->SeriesProfiles.restore()));
        int scale=context.computeOnClient(c->c.options.guiScale().get());
        for(String language:new String[]{"es_es","en_us"}) {
            SocialModClientGameTest.language(context,language);
            context.runOnClient(c->{c.options.guiScale().set(2);de.keksuccino.fancymenu.util.rendering.RenderingUtils.resetGuiScale();ClientCompat.setScreen(new SeriesManagerScreen(new SocialScreen(null)));});
            context.waitTicks(5);assertWidgets(context);
            context.takeScreenshot("series_"+language+"_01_profiles");
            for(int i=0;i<2;i++) {
                context.runOnClient(c->{var screen=ClientCompat.currentScreen();var tab=net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).getFirst();
                    screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(tab.getX()+3,tab.getY()+3,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);});
                context.waitTicks(2);assertWidgets(context);context.takeScreenshot("series_"+language+"_0"+(i+2)+"_page");
            }
            context.runOnClient(c->ClientCompat.setScreen(new SeriesImportScreen(ClientCompat.currentScreen(),pack)));
            context.waitTicks(2);assertWidgets(context);context.takeScreenshot("series_"+language+"_04_review");
        }
        context.runOnClient(c->{c.options.guiScale().set(scale);de.keksuccino.fancymenu.util.rendering.RenderingUtils.resetGuiScale();ClientCompat.setScreen(new SocialScreen(null));});
    }
    private static void assertWidgets(ClientGameTestContext context) {
        context.runOnClient(c->{var screen=ClientCompat.currentScreen();var widgets=net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen);
            for(var a:widgets) {
                if(a.getX()<0||a.getY()<0||a.getRight()>screen.width||a.getBottom()>screen.height)throw new AssertionError("Series control outside small GUI");
                for(var b:widgets)if(a!=b&&a.getX()<b.getRight()&&a.getRight()>b.getX()&&a.getY()<b.getBottom()&&a.getBottom()>b.getY())throw new AssertionError("Series controls overlap");
            }
        });
    }
    private static <T> T await(ClientGameTestContext context,CompletableFuture<T> future) {context.waitFor(c->future.isDone());return future.join();}
    private SeriesProfilesTests() { }
}
