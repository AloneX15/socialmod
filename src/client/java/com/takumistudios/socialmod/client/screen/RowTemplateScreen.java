package com.takumistudios.socialmod.client.screen;

import com.takumistudios.socialmod.client.theme.RowTemplates;
import com.takumistudios.socialmod.common.model.RowDesign;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Detailed row canvas opened from the advanced customization hub. */
public final class RowTemplateScreen extends SocialChildScreen {
    private RowDesign draft = RowTemplates.design();
    private final Deque<RowDesign> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final Map<String, EditBox> inputs = new LinkedHashMap<>();
    private int kind, selected, page, canvasX, canvasY, canvasBottom;
    private float zoom = 1;
    private boolean dragging;
    private final Map<String,String> pending = new HashMap<>();
    @Override protected void saveDrafts() { inputs.forEach((key,box) -> pending.put(key,box.getValue())); }
    @Override public void resize(int width,int height) { saveDrafts(); super.resize(width,height); }
    private RowTemplates.Data sample;
    private String status = "";
    public RowTemplateScreen(Screen parent) { super(parent, Component.translatable("socialmod.advanced.rows")); }
    @Override protected boolean rebuildOnChange() { return false; }
    private RowDesign.Template row() { return draft.templates.computeIfAbsent(RowDesign.KINDS.get(kind), k -> RowDesign.defaults().templates.get(k)); }
    private RowDesign.Part part() { return row().parts.get(Math.min(selected, row().parts.size() - 1)); }
    private void remember(RowDesign value) { undo.push(value); while (undo.size() > 40) undo.removeLast(); redo.clear(); }
    private void checkpoint() { remember(draft.copy()); }
    @Override protected void init() {
        sample = new RowTemplates.Data(minecraft.getUser().getProfileId(),Map.of(
            "name",Component.translatable("socialmod.advanced.sample.name"), "text",Component.translatable("socialmod.advanced.sample.text"),
            "team",Component.literal("Forest TEAM"), "time",Component.literal("18:30"), "unread",Component.literal("3"),
            "status",Component.translatable("socialmod.status.online"), "icon",Component.literal("\u25c6"), "role",Component.literal("L")), .8f,false,false);
        inputs.clear(); canvasX = Math.min(185, width / 2); canvasY = 78;
        int lw = Math.max(90, canvasX - 12);
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.kind", Component.translatable("socialmod.advanced.kind."+RowDesign.KINDS.get(kind))), b -> { if (!apply()) return; kind = (kind + 1) % RowDesign.KINDS.size(); selected = 0; rebuildWidgets(); }).bounds(6, 30, lw, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.enabled", Component.translatable(row().enabled?"gui.yes":"gui.no")), b -> { if (!apply()) return; checkpoint(); row().enabled = !row().enabled; rebuildWidgets(); }).bounds(canvasX, 30, Math.max(110, width - canvasX - 6), 20).build());
        if (row().parts.isEmpty()) row().parts.add(RowDesign.part("name", 2, 2, 80, 10));
        var part = part();
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.field."+part.field).append(" ["+(selected+1)+"/"+row().parts.size()+"]"), b -> { if (!apply()) return; selected = (selected + 1) % row().parts.size(); rebuildWidgets(); }).bounds(6, 54, lw, 20).build());
        String[][] pages = {{"x","y","width"},{"height","scale","color"},{"font","texture","rowHeight"},{"background","selectedBackground","hoverBackground"}};
        String[] keys = pages[page];
        int y = 88;
        for (String key : keys) {
            var field = new StyledEditBox(font, 6, y, lw, 16, Component.translatable("socialmod.advanced.property."+key));
            field.setValue(pending.getOrDefault(key,read(key))); field.setMaxLength(256); addRenderableWidget(field); inputs.put(key, field); y += 24;
        }
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.properties", page + 1), b -> { if (!apply()) return; page = (page + 1) % 4; rebuildWidgets(); }).bounds(6, y + 2, lw, 20).build());
        int rx = canvasX, rw = Math.max(100, width - rx - 6), by = Math.min(height - 125, 195); canvasBottom = by - 8;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.field", Component.translatable("socialmod.advanced.field."+part.field)), b -> { if (!apply()) return; checkpoint(); part().field = RowDesign.FIELDS.get((RowDesign.FIELDS.indexOf(part().field) + 1) % RowDesign.FIELDS.size()); rebuildWidgets(); }).bounds(rx, by, rw, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.wrap", Component.translatable(part.wrap?"gui.yes":"gui.no")), b -> { if (!apply()) return; checkpoint(); part().wrap = !part().wrap; rebuildWidgets(); }).bounds(rx, by + 24, rw, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.anchor", Component.translatable(part.right?"socialmod.advanced.right":"socialmod.advanced.left")), b -> { if (!apply()) return; checkpoint(); part().right = !part().right; rebuildWidgets(); }).bounds(rx, by + 48, rw, 20).build());
        int bw = Math.max(50, (width - 24) / 5), bottom = height - 25;
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.add"), b -> { if (row().parts.size() < 32 && apply()) { checkpoint(); row().parts.add(RowDesign.part("name", 2, 2, 80, 10)); selected = row().parts.size() - 1; rebuildWidgets(); } }).bounds(4, bottom - 24, bw, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.remove"), b -> { if (row().parts.size() > 1 && apply()) { checkpoint(); row().parts.remove(selected); selected = 0; rebuildWidgets(); } }).bounds(8 + bw, bottom - 24, bw, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.visible", Component.translatable(part.visible?"gui.yes":"gui.no")), b -> { if (!apply()) return; checkpoint(); part().visible = !part().visible; rebuildWidgets(); }).bounds(12 + bw * 2, bottom - 24, bw * 2, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.visual.undo"), b -> { if (!undo.isEmpty()) { pending.clear(); redo.push(draft.copy()); draft = undo.pop(); rebuildWidgets(); } }).bounds(4, bottom, bw, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.visual.redo"), b -> { if (!redo.isEmpty()) { pending.clear(); var next = redo.pop(); undo.push(draft.copy()); while (undo.size() > 40) undo.removeLast(); draft = next; rebuildWidgets(); } }).bounds(8 + bw, bottom, bw, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.apply"), b -> { apply(); rebuildWidgets(); }).bounds(12 + bw * 2, bottom, bw, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("socialmod.advanced.save"), b -> { if (apply()) RowTemplates.save(draft).whenComplete((v,e) -> minecraft.execute(() -> { status = e == null ? Component.translatable("socialmod.advanced.saved").getString() : e.getMessage(); rebuildWidgets(); })); }).bounds(16 + bw * 3, bottom, bw, 20).build());
        addRenderableWidget(Ui.button(Component.translatable("gui.back"), b -> onClose()).bounds(20 + bw * 4, bottom, bw, 20).build());
    }
    private String read(String key) {
        var p = part(); var r = row();
        return switch(key) { case "x" -> ""+p.x; case "y" -> ""+p.y; case "width" -> ""+p.width; case "height" -> ""+p.height; case "scale" -> ""+p.scale; case "color" -> String.format("#%08X",p.color); case "font" -> p.font; case "texture" -> p.texture; case "rowHeight" -> ""+r.height; case "background" -> String.format("#%08X",r.background); case "selectedBackground" -> String.format("#%08X",r.selectedBackground); default -> String.format("#%08X",r.hoverBackground); };
    }
    private boolean apply() {
        var before = draft.copy();
        String invalidKey="";
        try {
            var p = part(); var r = row();
            for (var entry : inputs.entrySet()) { String key = entry.getKey(), v = entry.getValue().getValue(); invalidKey=key;
                switch(key) { case "x" -> p.x=Integer.parseInt(v); case "y" -> p.y=Integer.parseInt(v); case "width" -> p.width=Integer.parseInt(v); case "height" -> p.height=Integer.parseInt(v); case "scale" -> p.scale=Integer.parseInt(v); case "font" -> p.font=v; case "texture" -> p.texture=v; case "rowHeight" -> r.height=Integer.parseInt(v); case "color" -> p.color=(int)Long.parseLong(v.replace("#",""),16); case "background" -> r.background=(int)Long.parseLong(v.replace("#",""),16); case "selectedBackground" -> r.selectedBackground=(int)Long.parseLong(v.replace("#",""),16); case "hoverBackground" -> r.hoverBackground=(int)Long.parseLong(v.replace("#",""),16); }
                draft.validate(); if(key.equals("font") || key.equals("texture")) RowTemplates.validateResources(draft);
            }
            draft.validate(); RowTemplates.validateResources(draft); if (!RowDesign.GSON.toJson(before).equals(RowDesign.GSON.toJson(draft))) { remember(before); } pending.clear(); status=""; return true;
        } catch (RuntimeException e) { draft=before; saveDrafts(); status=Component.translatable("socialmod.advanced.invalid_property",Component.translatable("socialmod.advanced.property."+invalidKey)).getString(); return false; }
    }
    @Override protected void drawContent(GuiGraphicsExtractor graphics, int mx, int my) {
        Ui.title(graphics,font,title,width/2,10);
        int rowWidth = Math.max(80, width - canvasX - 8); zoom = Math.max(.1f,Math.min(Math.min(2, rowWidth / 180f),(canvasBottom-canvasY)/(float)Math.max(1,row().height))); int logicalWidth=Math.round(rowWidth/zoom);
        graphics.enableScissor(canvasX,canvasY,width-6,canvasBottom);
        graphics.pose().pushMatrix(); try { graphics.pose().translate(canvasX,canvasY); graphics.pose().scale(zoom,zoom);
        RowTemplates.draw(graphics,row(),0,0,logicalWidth,sample);
        var p=part(); int pw=p.width==0 ? logicalWidth-p.x-3 : p.width;
        graphics.outline(p.right ? logicalWidth-p.x-pw : p.x,p.y,Math.max(1,pw),p.height,0xFFFFD166); } finally { graphics.pose().popMatrix(); graphics.disableScissor(); }
        int ly=88; for(var key:inputs.keySet()) { graphics.text(font,Component.translatable("socialmod.advanced.property."+key),6,ly-10,Ui.theme().colors().muted()); ly+=24; }
        graphics.text(font,Component.literal(status),canvasX,55,Ui.warning());
    }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        int logicalWidth=Math.round((width-canvasX-8)/zoom);
        if (e.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && e.y()>=canvasY && e.y()<canvasBottom && e.x()>=canvasX) {
            double lx=(e.x()-canvasX)/zoom,ly=(e.y()-canvasY)/zoom;
            for(int i=row().parts.size()-1;i>=0;i--) { var p=row().parts.get(i); int w=p.width==0?logicalWidth-p.x-3:p.width; int x=p.right?logicalWidth-p.x-w:p.x;
                if(lx>=x && lx<x+w && ly>=p.y && ly<p.y+p.height) { if (!apply()) return true; checkpoint(); selected=i; dragging=true; rebuildWidgets(); return true; }
            }
        }
        return super.mouseClicked(e,twice);
    }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { if(dragging) { part().x=Math.clamp(part().x+(int)Math.round((part().right?-dx:dx)/zoom),0,2048); part().y=Math.clamp(part().y+(int)Math.round(dy/zoom),0,512); return true; } return super.mouseDragged(e,dx,dy); }
    @Override public boolean mouseReleased(MouseButtonEvent e) { if(dragging) { dragging=false; rebuildWidgets(); return true; } return super.mouseReleased(e); }
}
