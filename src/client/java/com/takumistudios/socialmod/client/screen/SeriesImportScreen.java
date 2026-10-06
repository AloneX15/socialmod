package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.common.model.SeriesPack;
import com.takumistudios.socialmod.client.theme.SeriesProfiles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Nothing is installed until the creator reviews metadata and presses Install. */
public final class SeriesImportScreen extends SocialChildScreen {
    private final SeriesPack.Contents pack;
    private final List<String> files,missing;
    private int offset;
    private boolean busy,installed;
    private String status="";
    public SeriesImportScreen(Screen parent,SeriesPack.Contents pack) {
        super(parent,Component.translatable("socialmod.series.review"));this.pack=pack;
        files=pack.files().keySet().stream().sorted().toList();missing=SeriesProfiles.missing(pack);
    }
    @Override protected boolean rebuildOnChange() { return false; }
    @Override protected void init() {
        int w=Math.min(380,width-20),x=(width-w)/2,half=(w-4)/2;
        var previous=addRenderableWidget(Ui.button(Component.translatable("socialmod.series.previous"),b->{offset=Math.max(0,offset-5);rebuildWidgets();}).bounds(x,height-72,half,20).build());previous.active=!busy&&offset>0;
        var next=addRenderableWidget(Ui.button(Component.translatable("socialmod.series.next"),b->{offset+=5;rebuildWidgets();}).bounds(x+half+4,height-72,half,20).build());next.active=!busy&&offset+5<files.size();
        var install=addRenderableWidget(Ui.button(Component.translatable("socialmod.series.install"),b->{
            busy=true;status=Component.translatable("socialmod.series.working").getString();rebuildWidgets();
            SeriesProfiles.importPack(pack).whenComplete((file,error)->minecraft.execute(()->{
                busy=false;
                if(error==null) { installed=true;status=Component.translatable("socialmod.series.done").getString(); }
                else { Throwable failure=error;while(failure.getCause()!=null)failure=failure.getCause();status=Component.translatable("socialmod.series.error",failure.getMessage()).getString(); }
                rebuildWidgets();
            }));
        }).bounds(x,height-48,half,20).build());install.active=!busy&&!installed&&missing.isEmpty();
        var back=addRenderableWidget(Ui.button(Component.translatable("gui.back"),b->onClose()).bounds(x+half+4,height-48,half,20).build());back.active=!busy;
    }
    @Override public void onClose() { if(!busy) { if(parent instanceof SeriesManagerScreen manager)manager.refreshCatalog();super.onClose(); } }
    @Override protected void drawContent(GuiGraphicsExtractor g,int mx,int my) {
        Ui.title(g,font,title,width/2,10);
        var m=pack.metadata(); int x=10;
        line(g,m.name()+" · "+m.author()+" · "+m.version()+" · MC "+m.minecraft(),x,32);
        line(g,Component.translatable("socialmod.series.requires",String.join(", ",m.requiredMods())).getString(),x,46);
        line(g,Component.translatable(missing.isEmpty()?"socialmod.series.ready":"socialmod.series.missing",String.join(", ",missing)).getString(),x,60);
        for(int i=offset;i<Math.min(files.size(),offset+5);i++) line(g,files.get(i),x,78+(i-offset)*12);
        line(g,Component.translatable("socialmod.series.backup_hint").getString(),x,height-88);
        line(g,status,x,height-18);
    }
    private void line(GuiGraphicsExtractor g,String text,int x,int y) { g.text(font,Component.literal(Ui.trim(font,text,width-20)),x,y,Ui.theme().colors().text(),false); }
}
