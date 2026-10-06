import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;

/** Editable source for the small pixel-art UI sprites and Winter Lodge bitmap font. */
class GenerateChristmasAssets {
    static Path assets;
    static void px(BufferedImage image, int x, int y, int color) { if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) image.setRGB(x,y,color); }
    static void fill(BufferedImage image, int x, int y, int w, int h, int color) { for(int yy=y;yy<y+h;yy++) for(int xx=x;xx<x+w;xx++) px(image,xx,yy,color); }
    static void sprite(String name, int w, int h, int center, int light, boolean snow) throws Exception {
        BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        fill(image,1,1,w-2,h-2,0xFF301C19); fill(image,2,2,w-4,h-4,0xFF77482B);
        fill(image,3,3,w-6,h-6,0xFFB7894D); fill(image,4,4,w-8,h-8,center);
        fill(image,4,4,w-8,1,light); fill(image,4,h-5,w-8,1,0xFF371D20);
        for(int x=5;x<w-4;x+=6) { px(image,x,2,0xFFE1BE74); px(image,x,h-3,0xFF52311E); }
        for(int y=5;y<h-4;y+=5) { px(image,2,y,0xFFB7884D); px(image,w-3,y,0xFF513522); }
        for(int x:new int[]{2,w-4}) { fill(image,x,2,2,2,0xFFF7D58B); fill(image,x,h-4,2,2,0xFFE2B06B); }
        if(snow) { fill(image,3,0,w-6,2,0xFFF6F0DD); fill(image,5,2,3,1,0xFFC7D8DB); }
        if(name.startsWith("button")) {
            px(image,5,3,0xFF558846); px(image,6,3,0xFF2B5532); px(image,5,4,0xFFF0D28A);
            px(image,w-6,3,0xFF558846); px(image,w-7,3,0xFF2B5532); px(image,w-6,4,0xFFDF5262);
        }
        Path file=assets.resolve("textures/gui/sprites/christmas/"+name+".png"); Files.createDirectories(file.getParent()); ImageIO.write(image,"png",file.toFile());
        Files.writeString(file.resolveSibling(file.getFileName()+".mcmeta"),"{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":"+w+",\"height\":"+h+",\"border\":4}}}\n");
    }
    static void font() throws Exception {
        Map<Character,String> glyphs=new LinkedHashMap<>();
        String[] upper={
        "01110/10001/10001/11111/10001/10001/10001","11110/10001/10001/11110/10001/10001/11110","01111/10000/10000/10000/10000/10000/01111",
        "11110/10001/10001/10001/10001/10001/11110","11111/10000/10000/11110/10000/10000/11111","11111/10000/10000/11110/10000/10000/10000",
        "01111/10000/10000/10111/10001/10001/01111","10001/10001/10001/11111/10001/10001/10001","01110/00100/00100/00100/00100/00100/01110",
        "00111/00010/00010/00010/10010/10010/01100","10001/10010/10100/11000/10100/10010/10001","10000/10000/10000/10000/10000/10000/11111",
        "10001/11011/10101/10101/10001/10001/10001","10001/11001/10101/10011/10001/10001/10001","01110/10001/10001/10001/10001/10001/01110",
        "11110/10001/10001/11110/10000/10000/10000","01110/10001/10001/10001/10101/10010/01101","11110/10001/10001/11110/10100/10010/10001",
        "01111/10000/10000/01110/00001/00001/11110","11111/00100/00100/00100/00100/00100/00100","10001/10001/10001/10001/10001/10001/01110",
        "10001/10001/10001/10001/10001/01010/00100","10001/10001/10001/10101/10101/11011/10001","10001/10001/01010/00100/01010/10001/10001",
        "10001/10001/01010/00100/00100/00100/00100","11111/00001/00010/00100/01000/10000/11111"};
        String[] lower={
        "00000/00000/01110/00001/01111/10001/01111","10000/10000/10110/11001/10001/10001/11110","00000/00000/01111/10000/10000/10000/01111",
        "00001/00001/01101/10011/10001/10001/01111","00000/00000/01110/10001/11111/10000/01111","00110/01001/01000/11100/01000/01000/01000",
        "00000/01111/10001/10001/01111/00001/01110","10000/10000/10110/11001/10001/10001/10001","00100/00000/01100/00100/00100/00100/01110",
        "00010/00000/00110/00010/00010/10010/01100","10000/10000/10010/10100/11000/10100/10010","01100/00100/00100/00100/00100/00100/01110",
        "00000/00000/11010/10101/10101/10101/10101","00000/00000/10110/11001/10001/10001/10001","00000/00000/01110/10001/10001/10001/01110",
        "00000/11110/10001/10001/11110/10000/10000","00000/01111/10001/10001/01111/00001/00001","00000/00000/10111/11000/10000/10000/10000",
        "00000/00000/01111/10000/01110/00001/11110","01000/01000/11100/01000/01000/01001/00110","00000/00000/10001/10001/10001/10011/01101",
        "00000/00000/10001/10001/10001/01010/00100","00000/00000/10001/10001/10101/10101/01010","00000/00000/10001/01010/00100/01010/10001",
        "00000/10001/10001/10001/01111/00001/01110","00000/00000/11111/00010/00100/01000/11111"};
        String[] digits={"01110/10001/10011/10101/11001/10001/01110","00100/01100/00100/00100/00100/00100/01110","01110/10001/00001/00010/00100/01000/11111","11110/00001/00001/01110/00001/00001/11110","00010/00110/01010/10010/11111/00010/00010","11111/10000/10000/11110/00001/00001/11110","01110/10000/10000/11110/10001/10001/01110","11111/00001/00010/00100/01000/01000/01000","01110/10001/10001/01110/10001/10001/01110","01110/10001/10001/01111/00001/00001/01110"};
        for(int i=0;i<26;i++) { glyphs.put((char)('A'+i),upper[i]); glyphs.put((char)('a'+i),lower[i]); }
        for(int i=0;i<10;i++) glyphs.put((char)('0'+i),digits[i]);
        glyphs.put('!',"00100/00100/00100/00100/00100/00000/00100"); glyphs.put('?',"01110/10001/00001/00010/00100/00000/00100");
        glyphs.put('.',"00000/00000/00000/00000/00000/00000/00100"); glyphs.put(',',"00000/00000/00000/00000/00000/00100/01000");
        glyphs.put(':',"00000/00000/00100/00000/00000/00100/00000"); glyphs.put(';',"00000/00000/00100/00000/00000/00100/01000");
        glyphs.put('-',"00000/00000/00000/11111/00000/00000/00000"); glyphs.put('+',"00000/00100/00100/11111/00100/00100/00000");
        glyphs.put('(',"00010/00100/01000/01000/01000/00100/00010"); glyphs.put(')',"01000/00100/00010/00010/00010/00100/01000");
        glyphs.put('[',"01110/01000/01000/01000/01000/01000/01110"); glyphs.put(']',"01110/00010/00010/00010/00010/00010/01110");
        glyphs.put('/',"00001/00001/00010/00100/01000/10000/10000"); glyphs.put('_',"00000/00000/00000/00000/00000/00000/11111");
        String accents="\u00e1\u00e9\u00ed\u00f3\u00fa\u00c1\u00c9\u00cd\u00d3\u00da\u00f1\u00d1\u00fc\u00dc\u00a1\u00bf";
        String bases="aeiouAEIOUnNuU!?";
        for(int i=0;i<accents.length();i++) glyphs.put(accents.charAt(i),glyphs.get(bases.charAt(i)));
        Map<Character,Integer> widths = new HashMap<>();
        widths.put((char)0x0021,1);
        widths.put((char)0x0028,3);
        widths.put((char)0x0029,3);
        widths.put((char)0x002b,5);
        widths.put((char)0x002c,1);
        widths.put((char)0x002d,5);
        widths.put((char)0x002e,1);
        widths.put((char)0x002f,5);
        widths.put((char)0x0030,5);
        widths.put((char)0x0031,5);
        widths.put((char)0x0032,5);
        widths.put((char)0x0033,5);
        widths.put((char)0x0034,5);
        widths.put((char)0x0035,5);
        widths.put((char)0x0036,5);
        widths.put((char)0x0037,5);
        widths.put((char)0x0038,5);
        widths.put((char)0x0039,5);
        widths.put((char)0x003a,1);
        widths.put((char)0x003b,1);
        widths.put((char)0x003f,5);
        widths.put((char)0x0041,5);
        widths.put((char)0x0042,5);
        widths.put((char)0x0043,5);
        widths.put((char)0x0044,5);
        widths.put((char)0x0045,5);
        widths.put((char)0x0046,5);
        widths.put((char)0x0047,5);
        widths.put((char)0x0048,5);
        widths.put((char)0x0049,3);
        widths.put((char)0x004a,5);
        widths.put((char)0x004b,5);
        widths.put((char)0x004c,5);
        widths.put((char)0x004d,5);
        widths.put((char)0x004e,5);
        widths.put((char)0x004f,5);
        widths.put((char)0x0050,5);
        widths.put((char)0x0051,5);
        widths.put((char)0x0052,5);
        widths.put((char)0x0053,5);
        widths.put((char)0x0054,5);
        widths.put((char)0x0055,5);
        widths.put((char)0x0056,5);
        widths.put((char)0x0057,5);
        widths.put((char)0x0058,5);
        widths.put((char)0x0059,5);
        widths.put((char)0x005a,5);
        widths.put((char)0x005b,3);
        widths.put((char)0x005d,3);
        widths.put((char)0x005f,5);
        widths.put((char)0x0061,5);
        widths.put((char)0x0062,5);
        widths.put((char)0x0063,5);
        widths.put((char)0x0064,5);
        widths.put((char)0x0065,5);
        widths.put((char)0x0066,4);
        widths.put((char)0x0067,5);
        widths.put((char)0x0068,5);
        widths.put((char)0x0069,1);
        widths.put((char)0x006a,5);
        widths.put((char)0x006b,4);
        widths.put((char)0x006c,2);
        widths.put((char)0x006d,5);
        widths.put((char)0x006e,5);
        widths.put((char)0x006f,5);
        widths.put((char)0x0070,5);
        widths.put((char)0x0071,5);
        widths.put((char)0x0072,5);
        widths.put((char)0x0073,5);
        widths.put((char)0x0074,3);
        widths.put((char)0x0075,5);
        widths.put((char)0x0076,5);
        widths.put((char)0x0077,5);
        widths.put((char)0x0078,5);
        widths.put((char)0x0079,5);
        widths.put((char)0x007a,5);
        widths.put((char)0x00a1,1);
        widths.put((char)0x00bf,5);
        widths.put((char)0x00c1,5);
        widths.put((char)0x00c9,5);
        widths.put((char)0x00cd,3);
        widths.put((char)0x00d1,5);
        widths.put((char)0x00d3,5);
        widths.put((char)0x00da,5);
        widths.put((char)0x00dc,5);
        widths.put((char)0x00e1,5);
        widths.put((char)0x00e9,5);
        widths.put((char)0x00ed,2);
        widths.put((char)0x00f1,5);
        widths.put((char)0x00f3,5);
        widths.put((char)0x00fa,5);
        widths.put((char)0x00fc,5);
        int rows=(glyphs.size()+15)/16; BufferedImage image=new BufferedImage(128,rows*8,BufferedImage.TYPE_INT_ARGB);
        List<String> chars=new ArrayList<>(); StringBuilder row=new StringBuilder(); int i=0;
        for(var entry:glyphs.entrySet()) {
            int ox=i%16*8, oy=i/16*8; String[] lines=entry.getValue().split("/");
            if (entry.getKey()=='\u00a1' || entry.getKey()=='\u00bf') Collections.reverse(Arrays.asList(lines));
            int inkWidth=widths.getOrDefault(entry.getKey(),5);
            for(int y=0;y<7;y++) for(int x=0;x<5;x++) if(lines[y].charAt(x)=='1') px(image,ox+x*inkWidth/5,oy+y+1,y<4?0xFFFFFFFF:0xFFDDE5DA);
            px(image,ox+inkWidth-1,oy+7,0xFFDDE5DA);
            char c=entry.getKey();
            if("\u00e1\u00e9\u00ed\u00f3\u00fa\u00c1\u00c9\u00cd\u00d3\u00da".indexOf(c)>=0) { px(image,ox+Math.min(inkWidth-1,2),oy,0xFFFFFFFF); px(image,ox+Math.min(inkWidth-1,3),oy,0xFFFFFFFF); }
            if(c=='\u00f1'||c=='\u00d1') for(int x=0;x<Math.min(inkWidth,4);x++) px(image,ox+x,oy,0xFFFFFFFF);
            if(c=='\u00fc'||c=='\u00dc') { px(image,ox+Math.min(inkWidth-1,1),oy,0xFFFFFFFF);px(image,ox+Math.min(inkWidth-1,3),oy,0xFFFFFFFF); }
            row.append(c);i++;if(i%16==0) { chars.add(row.toString());row.setLength(0); }
        }
        if(!row.isEmpty()) { while(row.length()<16) row.append('\u0000');chars.add(row.toString()); }
        Path png=assets.resolve("textures/font/christmas.png");Files.createDirectories(png.getParent());ImageIO.write(image,"png",png.toFile());
        StringBuilder json=new StringBuilder("{\"providers\":[{\"type\":\"bitmap\",\"file\":\"socialmod:font/christmas.png\",\"ascent\":7,\"height\":8,\"chars\":[");
        for(int j=0;j<chars.size();j++) { if(j>0)json.append(',');json.append('"');for(char c:chars.get(j).toCharArray())json.append(String.format("\\u%04x",(int)c));json.append('"'); }
        json.append("]},{\"type\":\"reference\",\"id\":\"minecraft:default\"}]}\n");
        Path file=assets.resolve("font/christmas.json");Files.createDirectories(file.getParent());Files.writeString(file,json.toString());
    }
    public static void main(String[] args) throws Exception {
        assets=Path.of(args.length==0?"src/main/resources/assets/socialmod":args[0]);
        sprite("button",32,20,0xFF682837,0xFFBB5660,false);
        sprite("button_hover",32,20,0xFF8C3442,0xFFFFD88E,false);
        sprite("button_selected",32,20,0xFF245443,0xFF87C789,false);
        sprite("button_disabled",32,20,0xFF343D39,0xFF65716B,false);
        sprite("input",32,16,0xFF111E1E,0xFF304B44,false);
        sprite("input_focus",32,16,0xFF142824,0xFFE8BE74,false);
        sprite("toast",64,36,0xFF102923,0xFF445C3C,true);
        font();
    }
}
