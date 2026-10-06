package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.theme.SeriesProfiles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Three small pages keep profile creation, file selection and import separate and readable. */
public final class SeriesManagerScreen extends SocialChildScreen {
    private SeriesProfiles.Catalog catalog=new SeriesProfiles.Catalog(List.of(),List.of(),List.of(),"");
    private final Set<String> selected=new TreeSet<>();
    private Path profileFile;
    private String name="",author="TakumiStudios",version="1.0",status="";
    private int page,layoutIndex,importIndex;
    private boolean busy;
    private EditBox nameBox,authorBox,versionBox;
    public SeriesManagerScreen(Screen parent) { super(parent,Component.translatable("socialmod.series.title")); refresh(true); }
    @Override protected boolean rebuildOnChange() { return false; }
    private SeriesProfiles.Profile profile() { return catalog.profiles().stream().filter(p->p.file().equals(profileFile)).findFirst().orElse(null); }
    private void refresh(boolean initial) {
        SeriesProfiles.catalog().whenComplete((value,error)->net.minecraft.client.Minecraft.getInstance().execute(()->{
            if(error!=null) { failure(error);return; } catalog=value;
            if(initial) for(String path:catalog.layouts()) if(path.substring(path.lastIndexOf('/')+1).startsWith("socialmod_")) selected.add(path);
            if(width>0)rebuildWidgets();
        }));
    }
    private void fields() { if(nameBox!=null) { name=nameBox.getValue();author=authorBox.getValue();version=versionBox.getValue(); } }
    private void switchPage() { fields();page=(page+1)%3;rebuildWidgets(); }
    private void chooseProfile() {
        fields(); var profiles=catalog.profiles(); int index=-1;
        for(int i=0;i<profiles.size();i++) if(profiles.get(i).file().equals(profileFile)) index=i;
        index++; if(index>=profiles.size()) { profileFile=null;name="";author="TakumiStudios";version="1.0"; }
        else { var p=profiles.get(index);profileFile=p.file();name=p.metadata().name();author=p.metadata().author();version=p.metadata().version();selected.clear();selected.addAll(p.layouts()); }
        rebuildWidgets();
    }
    private void failure(Throwable error) {
        while(error.getCause()!=null) error=error.getCause();
        status=Component.translatable("socialmod.series.error",error.getMessage()).getString(); busy=false; if(width>0)rebuildWidgets();
    }
    private <T> void run(CompletableFuture<T> future,java.util.function.Consumer<T> success) {
        busy=true;status=Component.translatable("socialmod.series.working").getString();rebuildWidgets();
        future.whenComplete((value,error)->minecraft.execute(()->{
            busy=false;if(error!=null) failure(error);else { status=Component.translatable("socialmod.series.done").getString();success.accept(value);refresh(false); }
        }));
    }
    private void button(String key,int x,int y,int w,Runnable action,boolean enabled) {
        var b=addRenderableWidget(Ui.button(Component.translatable(key),ignored->action.run()).bounds(x,y,w,20).build());b.active=enabled&&!busy;
    }
    @Override protected void init() {
        nameBox=authorBox=versionBox=null;
        int w=Math.min(360,width-20),x=(width-w)/2,half=(w-4)/2;
        var tab=addRenderableWidget(Ui.button(Component.translatable("socialmod.series.page",Component.translatable("socialmod.series.page."+page)),b->switchPage()).bounds(x,28,w,20).build());tab.active=!busy;
        if(page==0) {
            var p=profile();
            var picker=addRenderableWidget(Ui.button(Component.translatable("socialmod.series.profile",p==null?Component.translatable("socialmod.series.new"):Component.literal(p.metadata().name())),b->chooseProfile()).bounds(x,52,w,20).build());picker.active=!busy;
            nameBox=box(x,76,w,"socialmod.series.name",name,80);
            authorBox=box(x,100,half,"socialmod.series.author",author,80);
            versionBox=box(x+half+4,100,half,"socialmod.series.version",version,32);
            button("socialmod.series.save",x,124,half,()->{fields();run(SeriesProfiles.save(profile(),name,author,version,selected),file->profileFile=file);},true);
            button("socialmod.series.duplicate",x+half+4,124,half,()->run(SeriesProfiles.duplicate(profile(),profile().metadata().name()+" (2)"),file->{profileFile=file;name=name+" (2)";}),p!=null);
            button("socialmod.series.activate",x,148,w,()->run(SeriesProfiles.activate(profile().file()),v->{}),p!=null);
        } else if(page==1) {
            String path=catalog.layouts().isEmpty()?"":catalog.layouts().get(Math.floorMod(layoutIndex,catalog.layouts().size()));
            var picker=addRenderableWidget(Ui.button(Component.literal(path.isEmpty()?Component.translatable("socialmod.series.no_layouts").getString():Ui.trim(font,path.substring(path.lastIndexOf('/')+1),w-12)),b->{layoutIndex++;rebuildWidgets();}).bounds(x,52,w,20).build());picker.active=!busy&&!path.isEmpty();
            var toggle=addRenderableWidget(Ui.button(Component.translatable("socialmod.series.include",Component.translatable(selected.contains(path)?"gui.yes":"gui.no")),b->{if(!selected.remove(path))selected.add(path);rebuildWidgets();}).bounds(x,76,w,20).build());toggle.active=!busy&&!path.isEmpty();
            button("socialmod.series.export",x,100,w,()->run(SeriesProfiles.export(name,author,version,selected),file->status=Component.translatable("socialmod.series.exported",file.getFileName()).getString()),!name.isBlank());
            button("socialmod.series.folder",x,124,w,this::openFolder,true);
        } else {
            Path zip=catalog.imports().isEmpty()?null:catalog.imports().get(Math.floorMod(importIndex,catalog.imports().size()));
            var picker=addRenderableWidget(Ui.button(Component.literal(zip==null?Component.translatable("socialmod.series.no_zips").getString():Ui.trim(font,zip.getFileName().toString(),w-12)),b->{importIndex++;rebuildWidgets();}).bounds(x,52,w,20).build());picker.active=!busy&&zip!=null;
            button("socialmod.series.review",x,76,w,()->run(SeriesProfiles.inspect(zip),pack->ClientCompat.setScreen(new SeriesImportScreen(this,pack))),zip!=null);
            button("socialmod.series.restore",x,100,w,()->run(SeriesProfiles.restore(),v->{}),true);
            button("socialmod.series.folder",x,124,w,this::openFolder,true);
            button("socialmod.series.refresh",x,148,w,()->refresh(false),true);
        }
        button("gui.back",x,height-25,w,this::onClose,true);
    }
    private EditBox box(int x,int y,int w,String key,String value,int max) {
        var box=new StyledEditBox(font,x,y,w,20,Component.translatable(key));box.setMaxLength(max);box.setHint(Component.translatable(key));box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(key)));box.setValue(value);box.setResponder(text->{switch(key) {case "socialmod.series.name"->name=text;case "socialmod.series.author"->author=text;default->version=text;}});box.setEditable(!busy);addRenderableWidget(box);return box;
    }
    private void openFolder() {
        try { java.awt.Desktop.getDesktop().open(SeriesProfiles.root().resolve("config/socialmod/presets").toFile()); }
        catch(Exception e) { failure(e); }
    }
    public void refreshCatalog() { refresh(false); }
    @Override public void onClose() { if(busy)return;fields();super.onClose(); }
    @Override protected void drawContent(GuiGraphicsExtractor graphics,int mx,int my) {
        Ui.title(graphics,font,title,width/2,10);
        String info=page==0?Component.translatable("socialmod.series.active",catalog.profiles().stream().filter(p->p.file().getFileName().toString().equals(catalog.active())).map(p->p.metadata().name()).findFirst().orElse(Component.translatable("socialmod.series.none").getString())).getString():page==1?Component.translatable("socialmod.series.selected",selected.size()).getString():Component.translatable("socialmod.series.import_hint").getString();
        graphics.centeredText(font,Component.literal(Ui.trim(font,info,width-20)),width/2,176,Ui.theme().colors().text());
        graphics.centeredText(font,Component.literal(Ui.trim(font,status,width-20)),width/2,height-40,Ui.theme().colors().text());
    }
}
