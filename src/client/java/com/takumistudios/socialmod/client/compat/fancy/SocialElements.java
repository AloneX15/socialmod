package com.takumistudios.socialmod.client.compat.fancy;

import com.takumistudios.socialmod.SocialMod;
import com.takumistudios.socialmod.client.ClientState;
import com.takumistudios.socialmod.client.compat.ClientCompat;
import com.takumistudios.socialmod.client.screen.*;
import com.takumistudios.socialmod.client.theme.AppearanceMode;
import de.keksuccino.fancymenu.customization.element.*;
import de.keksuccino.fancymenu.customization.element.editor.AbstractEditorElement;
import de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen;
import de.keksuccino.fancymenu.customization.action.*;
import de.keksuccino.fancymenu.customization.placeholder.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Optional FancyMenu v3 extension. No types from this class escape the compatibility backend. */
public final class SocialElements {
    public static void register() {
        ElementRegistry.register(new Builder("banner_view", "banner"));
        ElementRegistry.register(new Builder("team_tag_view", "team_tag"));
        ElementRegistry.register(new Builder("data", "team_name"));
        ElementRegistry.register(new Builder("control", "settings"));
        for (String module : SocialComponents.MODULES) ElementRegistry.register(new Builder("module_" + module, module));
        ActionRegistry.register(new SocialAction(false)); ActionRegistry.register(new SocialAction(true));
        ActionRegistry.register(new SocialAction("socialmod_search_players", "search"));
        ActionRegistry.register(new SocialAction("socialmod_edit_team_banner", "banner"));
        for (String module : SocialComponents.MODULES)
            if (!module.equals("search") && !module.equals("banner"))
                ActionRegistry.register(new SocialAction("socialmod_open_" + module, module));
        for (String module : List.of("advanced", "screen_editor", "hud_editor", "back", "quick_reply"))
            ActionRegistry.register(new SocialAction("socialmod_open_" + module, module));
        ActionRegistry.register(new SocialAction("socialmod_group_style", "group", "button_socialmod.group_settings.style/1", "group_style"));
        ActionRegistry.register(new SocialAction("socialmod_create_group_style", "create_group", "button_socialmod.group_settings.style/1", "create_group_style"));
        ActionRegistry.register(new SocialAction("socialmod_tag_banner", "tag", "button_socialmod.banner.title/1", "tag_banner"));
        for (String data : SocialComponents.DATA) PlaceholderRegistry.register(new DataPlaceholder(data));
    }
    private static Component label(String key) { return Component.translatable("socialmod.catalog." + key); }
    private static final Component UNAVAILABLE=label("unavailable"), CHOOSE_CONTROL=label("choose_control");
    private static Screen host() { var screen = ClientCompat.currentScreen(); if (screen instanceof LayoutEditorScreen editor) return editor.layoutTargetScreen; if (SocialComponents.supported(screen)) return screen; var editor = LayoutEditorScreen.getCurrentInstance(); return editor == null ? screen : editor.layoutTargetScreen; }
    private static boolean enabled() { return SocialComponents.supported(host()) && ClientState.get().connected() && !AppearanceMode.original(); }
    private static boolean openBanner(String context,String target) {
        Screen parent=host();
        String error=SocialComponents.teamEditError(parent,context,target);
        if(!error.isEmpty()) {
            ClientCompat.setScreen(new net.minecraft.client.gui.screens.AlertScreen(()->ClientCompat.setScreen(parent),Component.translatable("socialmod.banner.title"),Component.translatable(error)));
            return true;
        }
        Screen editor=SocialComponents.create(parent,"banner",SocialComponents.resolve(parent,"banner",context,target));
        if(editor==null)return false;
        ClientCompat.setScreen(editor);return true;
    }
    public static final class Builder extends ElementBuilder<Element, Editor> {
        final String kind, initial;
        Builder(String kind, String initial) { super("socialmod_" + kind); this.kind = kind; this.initial = initial; }
        @Override public Element buildDefaultInstance() { return new Element(this); }
        @Override public Element deserializeElement(SerializedElement data) {
            var element = buildDefaultInstance();
            element.module = bounded(data.getValue("social_module"), initial);
            element.context = bounded(data.getValue("social_context"), "self");
            element.target = bounded(data.getValue("social_target"), "");
            element.form = bounded(data.getValue("social_form"), "main");
            element.control = bounded(data.getValue("social_control"), "");
            element.hideOriginal = !"false".equals(data.getValue("social_hide_original"));
            if(data.getValue("social_show_controls")!=null)element.showControls = !"false".equals(data.getValue("social_show_controls"));
            element.showBackground = !"false".equals(data.getValue("social_show_background"));
            return element;
        }
        @Override protected SerializedElement serializeElement(Element e, SerializedElement data) {
            data.putProperty("social_show_controls", String.valueOf(e.showControls));
            data.putProperty("social_show_background", String.valueOf(e.showBackground));
            data.putProperty("social_hide_original", String.valueOf(e.hideOriginal)); data.putProperty("social_module", e.module); data.putProperty("social_context", e.context);
            data.putProperty("social_target", e.target); data.putProperty("social_form", e.form); data.putProperty("social_control", e.control); return data;
        }
        @Override public Editor wrapIntoEditorElement(Element e, LayoutEditorScreen editor) { return new Editor(e, editor); }
        @Override public Component getDisplayName(AbstractElement e) { return Component.literal("SocialMod: ").append(label(kind.startsWith("module_") ? "module." + initial : kind)); }
        @Override public Component[] getDescription(AbstractElement e) { return new Component[]{label("description")}; }
        @Override public boolean shouldShowUpInEditorElementMenu(LayoutEditorScreen editor) { return SocialComponents.supported(editor.layoutTargetScreen); }
    }
    private static String bounded(String value, String fallback) { return value == null ? fallback : value.length() <= 180 && value.chars().noneMatch(c -> c < 32) ? value : fallback; }
    public static final class Element extends AbstractElement {
        public boolean hideOriginal = true, showControls, showBackground = true;
        public String module, context = "self", target = "", form = "main", control = "";
        private final Builder socialBuilder;
        private SocialComponents.Form session;
        private AbstractWidget selectedControl;
        private BannerWidget banner;
        private Object bannerSource;
        private Component value = Component.empty();
        private int version = -1;
        private String textSource = "";
        private boolean failed, focused;
        private int scroll;
        private int[] region = {0,0,540,340};
        private final AbstractWidget input = new AbstractWidget(0,0,1,1,Component.empty()) {
            @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics,int x,int y,float delta) { }
            @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
                var w=widget();if(w!=null)w.updateNarration(out);else out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,getDisplayName());
            }
            @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice) { return Element.this.mouseClicked(e,twice); }
            @Override public boolean mouseReleased(MouseButtonEvent e) { return Element.this.mouseReleased(e); }
            @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy) { return Element.this.mouseDragged(e,dx,dy); }
            @Override public boolean mouseScrolled(double x,double y,double dx,double dy) { return Element.this.mouseScrolled(x,y,dx,dy); }
            @Override public boolean keyPressed(KeyEvent e) { return Element.this.keyPressed(e); }
            @Override public boolean charTyped(CharacterEvent e) { return Element.this.charTyped(e); }
            @Override public void setFocused(boolean value) { super.setFocused(value);Element.this.setFocused(value); }
        };
        @Override public List<net.minecraft.client.gui.components.events.GuiEventListener> getWidgetsToRegister() { return socialBuilder.kind.equals("data") || socialBuilder.kind.equals("team_tag_view") ? List.of() : List.of(input); }
        Element(Builder builder) {
            super(builder); socialBuilder = builder; module = builder.initial; showControls = !List.of("conversations","players").contains(module);
            baseWidth = builder.kind.equals("banner_view") ? 60 : builder.kind.equals("data") || builder.kind.equals("team_tag_view") ? 150 : builder.kind.equals("control") ? 160 : builder.initial.equals("basic") ? 540 : 400;
            baseHeight = builder.kind.equals("banner_view") ? 120 : builder.kind.equals("data") || builder.kind.equals("team_tag_view") || builder.kind.equals("control") ? 20 : 300;
        }
        @Override public void tick() {
            super.tick(); if (failed) return;
            try {
                input.setRectangle(Math.max(1,getAbsoluteWidth()),Math.max(1,getAbsoluteHeight()),getAbsoluteX(),getAbsoluteY());
                input.visible=enabled()&&shouldRender();input.active=input.visible&&!failed;
                if (!enabled() || !shouldRender()) { session = null; return; }
                if(!editing() && !socialBuilder.kind.equals("data") && !socialBuilder.kind.equals("team_tag_view")) {
                    var widgets=net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(host());
                    String inputId="catalog_input_"+getInstanceIdentifier();
                    if (!FancyBridge.identifier(input).equals(inputId)) FancyBridge.identify(input,inputId);
                    if(!widgets.contains(input)) widgets.addFirst(input);
                }
                if (socialBuilder.kind.equals("banner_view")) {
                    var team = SocialComponents.team(host(), context, target);
                    if (banner == null || team != bannerSource || version != ClientState.get().version()) { bannerSource = team; version = ClientState.get().version(); banner = new BannerWidget(0,0,60,120,team == null ? new com.takumistudios.socialmod.common.model.TeamBanner() : team.banner,"element_banner_" + getInstanceIdentifier()); }
                } else if (socialBuilder.kind.equals("team_tag_view")) {
                    var team = SocialComponents.team(host(), context, target);
                    value = team == null ? Component.empty() : com.takumistudios.socialmod.client.TagRenderer.line(team.name, team.color, team.icon, "");
                } else if (socialBuilder.kind.equals("data")) {
                    String source = module + "/" + context + "/" + target + "/" + SocialComponents.selection(host());
                    if (version != ClientState.get().version() || !source.equals(textSource)) { version = ClientState.get().version(); textSource = source; value = Component.literal(SocialComponents.text(host(),module,context,target)); }
                } else {
                    var next = SocialComponents.form(host(),module,context,target,form);
                    if (next != session) { session = next; scroll = 0; }
                    if (session != null) { session.tick(FancyBackend.hudTick); region = session.region(); selectedControl=isControl()?session.control(control):null; if (selectedControl != null) { input.setMessage(selectedControl.getMessage()); input.active=selectedControl.active && selectedControl.visible; } else if (isControl()) input.active=false; if (selectedControl != null && hideOriginal) session.detach(selectedControl, FancyBackend.hudTick); }
                }
            } catch (RuntimeException | LinkageError e) { fail(e); }
        }
        private void fail(Throwable e) { failed = true; session = null; input.active=input.visible=false; SocialMod.LOGGER.warn("SocialMod FancyMenu element disabled: {}",getInstanceIdentifier(),e); }
        private boolean isControl() { return socialBuilder.kind.equals("control"); }
        private boolean editing() {
            Screen current=ClientCompat.currentScreen();
            return current instanceof LayoutEditorScreen || !SocialComponents.supported(current) && LayoutEditorScreen.getCurrentInstance()!=null;
        }
        private AbstractWidget widget() { return session == null ? null : selectedControl; }
        private float scale() { return getAbsoluteWidth() / (float)Math.max(1,region[2]); }
        private double localX(double x) { return (x-getAbsoluteX()) / scale() + region[0]; }
        private double localY(double y) { return (y-getAbsoluteY()) / scale() + region[1] + scroll; }
        @Override public void render(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
            if (failed || !enabled()) return;
            int x=getAbsoluteX(), y=getAbsoluteY(), w=Math.max(1,getAbsoluteWidth()), h=Math.max(1,getAbsoluteHeight());
            graphics.enableScissor(x,y,x+w,y+h); graphics.pose().pushMatrix();
            try {
                if (socialBuilder.kind.equals("team_tag_view")) { graphics.text(Minecraft.getInstance().font,value,x,y,0xFFFFFFFF);
                } else if (socialBuilder.kind.equals("banner_view")) {
                    if (banner != null) { banner.setRectangle(w,h,x,y); banner.extractWidgetRenderState(graphics,mx,my,delta); }
                    else graphics.text(Minecraft.getInstance().font,UNAVAILABLE,x,y,0xFFAAAAAA);
                } else if (socialBuilder.kind.equals("data")) graphics.text(Minecraft.getInstance().font,value,x,y,0xFFFFFFFF);
                else if (session == null) graphics.text(Minecraft.getInstance().font,UNAVAILABLE,x,y,0xFFAAAAAA);
                else if (isControl()) {
                    var widget = widget(); if (widget == null) { graphics.text(Minecraft.getInstance().font,CHOOSE_CONTROL,x,y,0xFFAAAAAA); return; }
                    withWidgetBounds(widget, () -> FancyBridge.present(input, () -> widget.extractRenderState(graphics,mx,my,delta)));
                } else {
                    graphics.pose().translate(x,y); graphics.pose().scale(scale(),scale()); graphics.pose().translate(-region[0],-region[1]-scroll);
                    SocialComponents.scoped(() -> session.render(() -> {
                        if (session.screen instanceof SocialScreen social) {
                            social.drawComponent(module, graphics, (int)localX(mx), (int)localY(my), showBackground, showControls);
                            if (showControls) for(var widget : session.controls())
                                if (!(widget instanceof SocialBlockWidget) && !(widget instanceof SocialBackgroundWidget))
                                    widget.extractRenderState(graphics,(int)localX(mx),(int)localY(my),delta);
                        } else session.screen.extractRenderState(graphics,(int)localX(mx),(int)localY(my),delta);
                    }, FancyBackend.hudTick));
                }
            } catch (RuntimeException | LinkageError e) { fail(e); }
            finally { graphics.pose().popMatrix(); graphics.disableScissor(); }
        }
        private <T> T withWidgetBounds(AbstractWidget widget, java.util.function.Supplier<T> call) {
            int x=widget.getX(),y=widget.getY(),w=widget.getWidth(),h=widget.getHeight();
            widget.setRectangle(Math.max(1,getAbsoluteWidth()),Math.max(1,getAbsoluteHeight()),getAbsoluteX(),getAbsoluteY());
            try { return call.get(); } finally { widget.setRectangle(w,h,x,y); }
        }
        private void withWidgetBounds(AbstractWidget widget, Runnable call) { withWidgetBounds(widget, () -> { call.run(); return null; }); }
        private MouseButtonEvent mapped(MouseButtonEvent e) { return new MouseButtonEvent(localX(e.x()),localY(e.y()),e.buttonInfo()); }
        private boolean guardedInput(java.util.function.Supplier<Boolean> work) { try{return work.get();}catch(RuntimeException|LinkageError e){fail(e);return false;} }
        @Override public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
            if (!enabled() || !shouldRender() || !isMouseOver(e.x(),e.y()) || failed) return false;
            if (socialBuilder.kind.equals("banner_view")) {
                if (e.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || editing()) return false;
                return openBanner(context,target);
            }
            if (session == null) return false;
            focused = true;
            try {
                if (isControl()) { var widget=widget(); if(widget==null) return false; session.screen.setFocused(widget); return withWidgetBounds(widget, () -> SocialComponents.scoped(() -> widget.mouseClicked(e,twice))); }
                return SocialComponents.scoped(() -> session.originalControls(() -> !showControls && session.screen instanceof SocialScreen ? session.contentOnly(() -> session.screen.mouseClicked(mapped(e),twice)) : session.screen.mouseClicked(mapped(e),twice), FancyBackend.hudTick));
            } catch (RuntimeException | LinkageError error) { fail(error); return false; }
        }
        @Override public boolean mouseReleased(MouseButtonEvent e) {
            if (session==null || !focused || !enabled()) return false;
            var widget=widget(); return guardedInput(()->isControl() ? widget!=null && withWidgetBounds(widget,()->widget.mouseReleased(e)) : SocialComponents.scoped(()->session.screen.mouseReleased(mapped(e))));
        }
        @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy) {
            if(session==null || !focused || !enabled()) return false;
            var widget=widget(); return guardedInput(()->isControl() ? widget!=null && withWidgetBounds(widget,()->widget.mouseDragged(e,dx,dy)) : SocialComponents.scoped(()->session.screen.mouseDragged(mapped(e),dx/scale(),dy/scale())));
        }
        @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
            if (session==null || !enabled() || !isMouseOver(x,y) || isControl()) return false;
            if (guardedInput(()->SocialComponents.scoped(()->session.originalControls(()-> !showControls && session.screen instanceof SocialScreen ? session.contentOnly(()->session.screen.mouseScrolled(localX(x),localY(y),dx/scale(),dy)) : session.screen.mouseScrolled(localX(x),localY(y),dx/scale(),dy), FancyBackend.hudTick)))) return true;
            int maximum=Math.max(0,region[3]-(int)(getAbsoluteHeight()/scale())); scroll=Math.clamp(scroll-(int)(dy*20),0,maximum); return true;
        }
        @Override public boolean keyPressed(KeyEvent e) { return focused && session!=null && (showControls || !(session.screen instanceof SocialScreen)) && enabled() && guardedInput(()->SocialComponents.scoped(()->session.screen.keyPressed(e))); }
        @Override public boolean charTyped(CharacterEvent e) { return focused && session!=null && (showControls || !(session.screen instanceof SocialScreen)) && enabled() && guardedInput(()->SocialComponents.scoped(()->session.screen.charTyped(e))); }
        @Override public void setFocused(boolean value) { focused=value; }
        @Override public boolean isFocused() { return focused; }
        @Override public boolean isMouseOver(double x,double y) { return containsMousePosition(x,y); }
        @Override public boolean isFocusable() { return true; }
        @Override public boolean isNavigatable() { return true; }
        @Override public void onDestroyElement() { input.active=input.visible=false;session=null; banner=null; super.onDestroyElement(); }
    }
    public static final class Editor extends AbstractEditorElement<Editor,Element> {
        Editor(Element e,LayoutEditorScreen editor) { super(e,editor); }
        @Override public void init() {
            super.init();
            rightClickMenu.addClickableEntry("socialmod_config",label("configure"),(menu,entry)->{
                menu.closeMenuChain(); saveSnapshot();
                openContextMenuScreen(new Configuration(editor,element,()->{ onSettingsChanged(); }));
            });
        }
    }
    /** Native configuration dialog avoids asking pack authors to edit serialized JSON. */
    static final class Configuration extends Screen {
        private final Screen back;
        private final Element element;
        private final Runnable changed;
        private EditBox destination,formName;
        Configuration(Screen back,Element element,Runnable changed) { super(label("configure")); this.back=back; this.element=element; this.changed=changed; }
        private void capture() { if(destination!=null) { element.target=bounded(destination.getValue(),""); element.form=bounded(formName.getValue(),"main"); } }
        @Override protected void init() {
            int w=Math.min(340,width-20),x=(width-w)/2,y=32;
            var options=element.socialBuilder.kind.equals("data")?SocialComponents.DATA:SocialComponents.MODULES;
            if (!element.socialBuilder.kind.equals("banner_view") && !element.socialBuilder.kind.equals("team_tag_view")) {
                addRenderableWidget(Ui.button(label("module."+element.module),b->{capture();element.module=options.get((options.indexOf(element.module)+1)%options.size());element.control="";rebuildWidgets();}).bounds(x,y,w,20).build()); y+=24;
            }
            addRenderableWidget(Ui.button(label("context."+element.context),b->{capture();var contexts=List.of("self","selected","fixed");element.context=contexts.get((contexts.indexOf(element.context)+1)%contexts.size());rebuildWidgets();}).bounds(x,y,w,20).build()); y+=24;
            destination=new StyledEditBox(font,x,y,w,20,label("target")); destination.setMaxLength(180);destination.setValue(element.target);destination.setHint(label("target"));addRenderableWidget(destination);y+=24;
            formName=new StyledEditBox(font,x,y,w,20,label("form"));formName.setMaxLength(64);formName.setValue(element.form);formName.setHint(label("form"));addRenderableWidget(formName);y+=24;
            if (!element.isControl() && List.of("conversations","players","chat").contains(element.module)) {
                addRenderableWidget(Ui.button(Component.translatable("socialmod.catalog.show_controls", Component.translatable(element.showControls ? "gui.yes" : "gui.no")), b -> { capture();element.showControls=!element.showControls;rebuildWidgets(); }).bounds(x,y,w,20).build()); y+=24;
                addRenderableWidget(Ui.button(Component.translatable("socialmod.catalog.show_background", Component.translatable(element.showBackground ? "gui.yes" : "gui.no")), b -> { capture();element.showBackground=!element.showBackground;rebuildWidgets(); }).bounds(x,y,w,20).build()); y+=24;
            }
            if(element.isControl()) {
                var realHost=back instanceof LayoutEditorScreen editor?editor.layoutTargetScreen:SocialComponents.supported(back)?back:host();
                var session=SocialComponents.form(realHost,element.module,element.context,element.target,element.form);
                List<String> controls=session==null?List.of():session.controls().stream().filter(widget->widget.visible).map(session::key).toList();
                addRenderableWidget(Ui.button(Component.translatable("socialmod.catalog.hide_original", Component.translatable(element.hideOriginal ? "gui.yes" : "gui.no")), b -> { capture(); element.hideOriginal = !element.hideOriginal; rebuildWidgets(); }).bounds(x,y,w,20).build()); y+=24;
                addRenderableWidget(Ui.button(element.control.isEmpty() ? label("choose_control") : session != null && session.control(element.control) != null ? session.control(element.control).getMessage() : Component.literal(element.control),b->{capture();if(!controls.isEmpty())element.control=controls.get((controls.indexOf(element.control)+1)%controls.size());rebuildWidgets();}).bounds(x,y,w,20).build());
            }
            addRenderableWidget(Ui.button(Component.translatable("gui.done"),b->onClose()).bounds(x,height-26,w,20).build());
        }
        @Override public void onClose() { capture(); changed.run();ClientCompat.setScreen(back); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float delta) { Ui.background(graphics,width,height);graphics.centeredText(font,title,width/2,10,0xFFFFFFFF);super.extractRenderState(graphics,x,y,delta); }
    }
    static final class SocialAction extends Action {
        final boolean controlAction;
        final String defaultModule;
        String defaultControl="", displayKey="";
        boolean generic;
        SocialAction(boolean control) { super(control?"socialmod_form_control":"socialmod_open_module");controlAction=control; defaultModule="teams"; generic=true; }
        SocialAction(String id, String module) { super(id); controlAction=false; defaultModule=module; }
        SocialAction(String id, String module, String control, String label) { super(id); controlAction=true; defaultModule=module; defaultControl=control; displayKey=label; }
        @Override public boolean hasValue() { return true; }
        @Override public void execute(String json) {
            if(!enabled())return;
            try {
                if(json.length()>2048)return;
                var config=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                String module=read(config,"module",defaultModule),context=read(config,"context","self"),target=read(config,"target",""),form=read(config,"form","main");
                if(controlAction) {
                    var session=SocialComponents.form(host(),module,context,target,form);if(session==null)return;
                    var widget=session.control(read(config,"control",defaultControl));
                    if(widget instanceof net.minecraft.client.gui.components.Button button && button.active && button.visible) SocialComponents.scoped(()->button.onPress(new MouseButtonEvent(button.getX()+1,button.getY()+1,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0))));
                } else if(module.equals("back")) { host().onClose(); }
                else if(module.equals("screen_editor")) { FancyBridge.edit(host()); }
                else if(module.equals("hud_editor")) { FancyBridge.editHud(); }
                else if(module.equals("banner")) { openBanner(context,target); }
                else { String resolved=SocialComponents.resolve(host(),module,context,target);var screen=SocialComponents.create(host(),module,resolved);if(screen!=null)ClientCompat.setScreen(screen); }
            } catch(RuntimeException | LinkageError e) { SocialMod.LOGGER.warn("Invalid SocialMod FancyMenu action",e); }
        }
        static String read(com.google.gson.JsonObject json,String key,String fallback) { return bounded(json.has(key)?json.get(key).getAsString():null,fallback); }
        @Override public Component getDisplayName() { return Component.literal("SocialMod: ").append(label(!displayKey.isEmpty() ? "module."+displayKey : generic ? controlAction ? "control" : "open" : "module."+defaultModule)); }
        @Override public Component getDescription() { return label("description"); }
        @Override public Component getValueDisplayName() { return label("configure"); }
        @Override public String getValuePreset() { return "{\"module\":\""+defaultModule+"\",\"context\":\"self\",\"target\":\"\",\"form\":\"main\",\"control\":\""+defaultControl+"\"}"; }
        @Override public boolean canRunAsync() { return false; }
        @Override public void editValue(ActionInstance instance,ActionEditingCompletedFeedback done,ActionEditingCanceledFeedback cancelled) {
            var builder=new Builder(controlAction ? "control" : "module_"+defaultModule,defaultModule); var element=builder.buildDefaultInstance();
            try { var config=com.google.gson.JsonParser.parseString(instance.value).getAsJsonObject();element.module=read(config,"module",defaultModule);element.context=read(config,"context","self");element.target=read(config,"target","");element.form=read(config,"form","main");element.control=read(config,"control",defaultControl); } catch(RuntimeException e) { SocialMod.LOGGER.debug("Reset malformed action configuration",e); }
            Screen previous=ClientCompat.currentScreen();
            ClientCompat.setScreen(new Configuration(previous,element,()->{var config=new com.google.gson.JsonObject();config.addProperty("module",element.module);config.addProperty("context",element.context);config.addProperty("target",element.target);config.addProperty("form",element.form);config.addProperty("control",element.control);done.accept(instance,config.toString(),element.module);}));
        }
    }
    static final class DataPlaceholder extends Placeholder {
        final String data;
        DataPlaceholder(String data) { super("socialmod_data_"+data);this.data=data; }
        @Override public String getReplacementFor(DeserializedPlaceholderString input) { if(!enabled())return "";var values=input.values==null?Map.<String,String>of():input.values;return SocialComponents.text(host(),data,values.getOrDefault("context","self"),values.getOrDefault("target","")); }
        @Override public List<String> getValueNames() { return List.of("context","target"); }
        @Override public String getDisplayName() { return "SocialMod: "+label("module."+data).getString(); }
        @Override public List<String> getDescription() { return List.of(label("description").getString()); }
        @Override public String getCategory() { return "SocialMod"; }
        @Override public DeserializedPlaceholderString getDefaultPlaceholderString() { return new DeserializedPlaceholderString(id,new HashMap<>(Map.of("context","self","target","")),""); }
        @Override public boolean canRunAsync() { return false; }
    }
    private SocialElements() { }
}
