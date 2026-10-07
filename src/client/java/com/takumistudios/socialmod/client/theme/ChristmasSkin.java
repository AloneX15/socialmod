package com.takumistudios.socialmod.client.theme;

import com.takumistudios.socialmod.client.ClientConfig;
import com.takumistudios.socialmod.client.compat.fancy.FancyBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import java.util.List;

/** Supplied PNGs; horizontal slices preserve bows and snow across control sizes. */
public final class ChristmasSkin {
    public record Texture(Identifier id,int width,int height) { }
    public enum Role {
        GREEN("green","candy",false),RED("red","creeper",false),WOOD("wood","gingerbread",false),
        ICE("ice","snowman",true),GOLD("gold","tree",true),PURPLE("purple","sword",false);
        public final String asset,icon; public final boolean darkText; final Texture texture;
        Role(String asset,String icon,boolean dark) { this.asset=asset;this.icon=icon;darkText=dark;texture=new Texture(id("buttons/"+asset),887,asset.equals("wood")||asset.equals("ice")?295:296); }
    }
    public static final List<String> ICONS=List.of("sword","gingerbread","crafting_gift","tree","creeper","santa","snowman","candy");
    private static final java.util.Map<String,Texture> ICON_TEXTURES=new java.util.HashMap<>();
    static { for(String icon:ICONS) ICON_TEXTURES.put(icon,new Texture(id("icons/"+icon),128,128)); }
    public static String local(String kind,String name) { return "config/fancymenu/assets/socialmod/christmas_graphic/"+kind+"/"+name+".png"; }
    public static Identifier id(String name) { return Identifier.fromNamespaceAndPath("socialmod","textures/christmas_graphic/"+name+".png"); }
    public static boolean enabled() { return VisualManager.get().seriesStyle.equals("christmas") && !ClientConfig.get().accessibility.highContrast; }
    public static Role role(Component label) {
        if(!(label.getContents() instanceof TranslatableContents translated))return switch(label.getString()) { case "➤"->Role.GREEN;case "⌖","✦"->Role.ICE;default->Role.WOOD; };
        String key=translated.getKey();
        if(key.contains("delete")||key.contains("deny")||key.contains("leave")||key.contains("kick")||(key.contains("block")&&!key.contains("unblock"))||key.contains("report"))return Role.RED;
        if(key.contains("save")||key.contains("accept")||key.contains("confirm")||key.contains("invite")||key.contains("new_group")||key.contains("new_party")||key.contains("create")||key.contains("send")||key.contains("unblock"))return Role.GREEN;
        if(key.contains("visual")||key.contains("advanced")||key.contains("series")||key.contains("template"))return Role.PURPLE;
        if(key.contains("team"))return Role.GOLD;
        if(key.contains("setting")||key.contains("share")||key.contains("copy")||key.contains("edit")||key.equals("⌖")||key.equals("✦"))return Role.ICE;
        return Role.WOOD;
    }
    public static String icon(Component label,Role role) {
        String key=label.getContents() instanceof TranslatableContents t?t.getKey():label.getString();
        return key.equals("✦")||key.contains("share_item")?"crafting_gift":key.equals("⌖")||key.contains("share_coords")?"tree":role.icon;
    }
    public static int edge(int width,int height) { return Math.min(Math.max(1,width/3),Math.max(1,Math.round(220f*height/296f))); }
    public static int labelPadding(int width,int height) { return edge(width,height)+3; }
    public static void button(GuiGraphicsExtractor g,AbstractWidget widget,Role role,boolean selected,boolean pressed) {
        Texture texture=FancyBridge.skinTexture(widget,false);if(texture==null)texture=FancyBridge.skinAsset("buttons",role.asset);if(texture==null)texture=role.texture;
        int tint=!widget.active?0x99777777:pressed?0xFFC0C0C0:widget.isHoveredOrFocused()?0xFFFFFFFF:0xFFE0E0E0;
        sliced(g,texture,widget.getX(),widget.getY(),widget.getWidth(),widget.getHeight(),tint);
        if(selected||widget.isFocused())g.outline(widget.getX()+edge(widget.getWidth(),widget.getHeight()),widget.getY()+widget.getHeight()/4,Math.max(1,widget.getWidth()-2*edge(widget.getWidth(),widget.getHeight())),Math.max(1,widget.getHeight()/2),selected?0xFFFFDF87:0xFFFFFFFF);
    }
    public static void sliced(GuiGraphicsExtractor g,Texture texture,int x,int y,int w,int h,int tint) {
        int sourceEdge=Math.min(texture.width()/3,Math.round(texture.width()*220f/887f)),edge=edge(w,h);
        g.blit(RenderPipelines.GUI_TEXTURED,texture.id(),x,y,0,0,edge,h,sourceEdge,texture.height(),texture.width(),texture.height(),tint);
        if(w>2*edge)g.blit(RenderPipelines.GUI_TEXTURED,texture.id(),x+edge,y,sourceEdge,0,w-2*edge,h,texture.width()-2*sourceEdge,texture.height(),texture.width(),texture.height(),tint);
        g.blit(RenderPipelines.GUI_TEXTURED,texture.id(),x+w-edge,y,texture.width()-sourceEdge,0,edge,h,sourceEdge,texture.height(),texture.width(),texture.height(),tint);
    }
    public static void icon(GuiGraphicsExtractor g,String name,int x,int y,int size) { var custom=FancyBridge.skinAsset("icons",name);drawIcon(g,custom==null?ICON_TEXTURES.getOrDefault(name,ICON_TEXTURES.get("tree")):custom,x,y,size,0xFFFFFFFF); }
    public static void buttonIcon(GuiGraphicsExtractor g,AbstractWidget widget,String name,int x,int y,int size) {
        var custom=FancyBridge.skinTexture(widget,true);if(custom==null)custom=FancyBridge.skinAsset("icons",name);
        drawIcon(g,custom==null?ICON_TEXTURES.getOrDefault(name,ICON_TEXTURES.get("tree")):custom,x,y,size,widget.active?0xFFFFFFFF:0x99777777);
    }
    private static void drawIcon(GuiGraphicsExtractor g,Texture texture,int x,int y,int size,int tint) { g.blit(RenderPipelines.GUI_TEXTURED,texture.id(),x,y,0,0,size,size,texture.width(),texture.height(),texture.width(),texture.height(),tint); }
    /** Thin trim; the panel interior remains translucent. */
    public static void frame(GuiGraphicsExtractor g,int x,int y,int w,int h) {
        g.fill(x,y,x+w,y+2,0xFFE6F2F3);g.fill(x,y+2,x+w,y+3,0xFFAD333B);g.fill(x,y+h-1,x+w,y+h,0xFFA88754);
        for(int offset=8;offset<w-8;offset+=24)g.fill(x+offset,y,x+offset+8,y+2,0xFFC2DFEC);
    }
    private ChristmasSkin() { }
}
