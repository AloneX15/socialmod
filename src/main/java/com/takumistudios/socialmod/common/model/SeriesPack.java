package com.takumistudios.socialmod.common.model;

import com.google.gson.Gson;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;

/** Portable, bounded client design snapshots. No Minecraft or optional mod classes. */
public final class SeriesPack {
    public static final Gson GSON = new Gson();
    public static final String MANIFEST = "socialmod-series.json";
    public static final String ROWS = "config/socialmod/integration/rows.json";
    public static final String VISUAL = "config/socialmod/integration/visual.json";
    private static final long MAX_TOTAL = 64L * 1024 * 1024;
    private static final int MAX_FILE = 8 * 1024 * 1024, MAX_FILES = 2000;
    private static final Pattern LOCAL = Pattern.compile("\\[source:local\\]([^\\r\\n]*)");
    public record Metadata(int format, String name, String author, String version, String minecraft,
                           List<String> requiredMods) {
        public void validate() {
            if (format != 1 || !valid(name,80) || !valid(author,80) || !valid(version,32)
                || !valid(minecraft,32) || requiredMods == null || requiredMods.size()>8
                || requiredMods.stream().anyMatch(m -> !Set.of("socialmod","fabric-api","fancymenu","konkrete","melody","spiffyhud").contains(m)))
                throw new IllegalArgumentException("Invalid series metadata");
        }
    }
    public record Contents(Metadata metadata, Map<String,byte[]> files) { }
    public static Path folder(Path root) { return root.resolve("config/socialmod/series"); }
    private static boolean valid(String s,int max) { return s!=null && !s.isBlank() && s.length()<=max && s.chars().noneMatch(Character::isISOControl); }
    public static boolean allowed(String path) {
        if (path.contains("\\") || path.startsWith("/") || path.contains(":") || Arrays.stream(path.split("/",-1)).anyMatch(s -> s.isEmpty() || s.equals(".") || s.equals(".."))) return false;
        if (path.equals(ROWS) || path.equals(VISUAL)) return true;
        return (path.startsWith("config/fancymenu/customization/") && path.endsWith(".txt"))
            || path.startsWith("config/fancymenu/assets/") || path.startsWith("config/spiffyhud/assets/")
            || path.startsWith("config/socialmod/assets/");
    }
    public static List<String> layouts(Path root) throws IOException {
        Path dir = safe(root,"config/fancymenu/customization");
        if (!Files.exists(dir)) return List.of();
        try (var walk=Files.walk(dir)) {
            return walk.filter(p -> Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) && p.toString().endsWith(".txt"))
                .map(p -> root.relativize(p).toString().replace('\\','/')).sorted().limit(MAX_FILES+1L).toList();
        }
    }
    public static Contents capture(Path root, String name,String author,String version,String minecraft,Collection<String> layouts) throws IOException {
        var files=new TreeMap<String,byte[]>();
        for(String path:List.of(ROWS,VISUAL)) if(Files.exists(safe(root,path))) files.put(path,readFile(safe(root,path)));
        for(String path:layouts) {
            if(!allowed(path) || !path.startsWith("config/fancymenu/customization/")) throw new IOException("Invalid layout: "+path);
            files.put(path,readFile(safe(root,path)));
        }
        var required=new ArrayList<>(List.of("socialmod","fabric-api"));
        if(!layouts.isEmpty()) required.addAll(List.of("fancymenu","konkrete","melody"));
        for(String path:layouts) {
            String text=new String(files.get(path),java.nio.charset.StandardCharsets.UTF_8);
            if(text.contains("socialmod_hud_") || text.contains("spiffyhud")) if(!required.contains("spiffyhud")) required.add("spiffyhud");
            var matcher=LOCAL.matcher(text);
            while(matcher.find()) {
                String ref=matcher.group(1).strip().replace('\\','/');
                if(!allowed(ref) || ref.contains("{") || ref.startsWith("config/fancymenu/customization/")) throw new IOException("Move layout assets into config/fancymenu/assets: "+ref);
                files.put(ref,readFile(safe(root,ref)));
            }
        }
        var contents=new Contents(new Metadata(1,name,author,version,minecraft,List.copyOf(required)),files);
        validate(contents); return contents;
    }
    public static Contents read(Path archive) throws IOException {
        if(Files.size(archive)>MAX_TOTAL) throw new IOException("Series ZIP exceeds size limit");
        var files=new TreeMap<String,byte[]>(); byte[] manifest=null; long total=0; int count=0;
        try(var zip=new ZipInputStream(Files.newInputStream(archive))) {
            for(ZipEntry entry;(entry=zip.getNextEntry())!=null;) {
                if(++count>MAX_FILES+2) throw new IOException("Too many pack entries");
                String path=entry.getName();
                if(entry.isDirectory()) { if(!allowed(path+"placeholder.txt")) throw new IOException("Invalid directory: "+path); continue; }
                if(!path.equals(MANIFEST) && !path.equals("SOCIALMOD-SETUP.txt") && !allowed(path)) throw new IOException("Unsupported pack file: "+path);
                byte[] data=zip.readNBytes(MAX_FILE+1); total+=data.length;
                if(data.length>MAX_FILE || total>MAX_TOTAL) throw new IOException("Series ZIP exceeds size limit");
                if(path.equals(MANIFEST)) { if(manifest!=null || data.length>65536) throw new IOException("Invalid manifest"); manifest=data; }
                else if(!path.equals("SOCIALMOD-SETUP.txt") && files.putIfAbsent(path,data)!=null) throw new IOException("Duplicate pack entry: "+path);
            }
        }
        if(manifest==null) throw new IOException("This ZIP has no series manifest; use the updated template");
        try {
            var result=new Contents(GSON.fromJson(new String(manifest,java.nio.charset.StandardCharsets.UTF_8),Metadata.class),files);
            validate(result); return result;
        } catch(RuntimeException e) { throw new IOException("Invalid series pack: "+e.getMessage(),e); }
    }
    public static void validate(Contents contents) throws IOException {
        try {
            if(contents.metadata()==null) throw new IllegalArgumentException("Missing metadata"); contents.metadata().validate();
            if(contents.files().size()>MAX_FILES) throw new IllegalArgumentException("Too many files");
            long total=0;
            for(var file:contents.files().entrySet()) {
                if(!allowed(file.getKey()) || file.getValue()==null || file.getValue().length>MAX_FILE) throw new IllegalArgumentException("Invalid pack file: "+file.getKey());
                total+=file.getValue().length;
            }
            if(total>MAX_TOTAL || contents.files().isEmpty()) throw new IllegalArgumentException("Empty or oversized pack");
            if(contents.files().containsKey(ROWS)) RowDesign.parse(new String(contents.files().get(ROWS),java.nio.charset.StandardCharsets.UTF_8));
            if(contents.files().containsKey(VISUAL)) VisualDesign.parse(new String(contents.files().get(VISUAL),java.nio.charset.StandardCharsets.UTF_8));
            var required=contents.metadata().requiredMods();
            if(!required.containsAll(List.of("socialmod","fabric-api"))) throw new IllegalArgumentException("Missing dependency declarations");
            for(var file:contents.files().entrySet()) if(file.getKey().startsWith("config/fancymenu/customization/")) {
                if(!required.containsAll(List.of("fancymenu","konkrete","melody"))) throw new IllegalArgumentException("Missing FancyMenu dependencies");
                String text=new String(file.getValue(),java.nio.charset.StandardCharsets.UTF_8);
                if((text.contains("socialmod_hud_") || text.contains("spiffyhud")) && !required.contains("spiffyhud")) throw new IllegalArgumentException("Missing SpiffyHUD dependency");
                var matcher=LOCAL.matcher(text);
                while(matcher.find()) { String ref=matcher.group(1).strip().replace('\\','/'); if(!allowed(ref) || !contents.files().containsKey(ref)) throw new IllegalArgumentException("Missing or external layout asset: "+ref); }
            }
        } catch(RuntimeException e) { throw new IOException(e.getMessage(),e); }
    }
    public static void write(Path archive,Contents contents) throws IOException {
        validate(contents); Files.createDirectories(archive.getParent()); Path tmp=archive.resolveSibling(archive.getFileName()+".tmp");
        if(Files.isSymbolicLink(tmp) || Files.isDirectory(tmp)) throw new IOException("Unsafe temporary path");
        try {
            try(var zip=new ZipOutputStream(Files.newOutputStream(tmp))) {
                var files=new TreeMap<>(contents.files()); files.put(MANIFEST,GSON.toJson(contents.metadata()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                for(var file:files.entrySet()) { zip.putNextEntry(new ZipEntry(file.getKey())); zip.write(file.getValue()); zip.closeEntry(); }
            }
            atomic(tmp,archive);
        } finally { Files.deleteIfExists(tmp); }
    }
    private static byte[] readFile(Path p) throws IOException {
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) || Files.size(p)>MAX_FILE) throw new IOException("Missing or oversized resource: "+p);
        byte[] data=Files.readAllBytes(p); if(data.length>MAX_FILE) throw new IOException("Resource grew during export"); return data;
    }
    private record State(String profile,List<String> files,Map<String,String> baseline) { }
    private record Backup(Map<String,String> files,List<String> absent,String state) { }
    public static synchronized void install(Path root,Contents contents,String profile) throws IOException {
        validate(contents); var base=folder(root); Files.createDirectories(safe(root,"config/socialmod/series"));
        Path stateFile=base.resolve("active.json"), backupFile=base.resolve("last-backup.json");
        var previous=readState(root); var affected=new TreeSet<>(previous.files()); affected.addAll(contents.files().keySet()); affected.addAll(List.of(ROWS,VISUAL));
        var originals=new TreeMap<String,String>(); var absent=new ArrayList<String>(); long bytes=0;
        for(String path:affected) {
            Path target=safe(root,path); if(Files.exists(target)) { byte[] data=readFile(target); bytes+=data.length; originals.put(path,Base64.getEncoder().encodeToString(data)); } else absent.add(path);
        }
        if(bytes>MAX_TOTAL) throw new IOException("Backup exceeds size limit");
        byte[] state=Files.exists(stateFile)?Files.readAllBytes(stateFile):null;
        var backup=new Backup(originals,absent,state==null?null:Base64.getEncoder().encodeToString(state));
        var baseline=new TreeMap<String,String>();
        for(String path:contents.files().keySet()) {
            if(previous.files().contains(path)) { if(previous.baseline().containsKey(path)) baseline.put(path,previous.baseline().get(path)); }
            else if(originals.containsKey(path)) baseline.put(path,originals.get(path));
        }
        validateEncoded(baseline);
        writeAtomic(backupFile,GSON.toJson(backup).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            for(String path:affected) {
                Path target=safe(root,path); byte[] data=contents.files().get(path);
                if(data==null) {
                    if(!path.equals(ROWS) && !path.equals(VISUAL) && previous.baseline().containsKey(path)) {
                        byte[] original=Base64.getDecoder().decode(previous.baseline().get(path));
                        if(path.startsWith("config/fancymenu/customization/")) original=new String(original,java.nio.charset.StandardCharsets.UTF_8).replaceAll("(?m)^(\\s*is_enabled\\s*=\\s*)true\\s*$","$1false").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        writeAtomic(target,original);
                    } else Files.deleteIfExists(target);
                } else writeAtomic(target,data);
            }
            writeAtomic(stateFile,GSON.toJson(new State(profile,new ArrayList<>(contents.files().keySet()),baseline)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch(IOException|RuntimeException e) { try { restore(root); } catch(IOException restoreError) { e.addSuppressed(restoreError); } throw e; }
    }
    public static synchronized void restore(Path root) throws IOException {
        Path file=safe(root,"config/socialmod/series/last-backup.json");
        if(!Files.isRegularFile(file) || Files.size(file)>MAX_TOTAL*4) throw new IOException("No valid backup available");
        try {
            var backup=GSON.fromJson(Files.readString(file),Backup.class);
            if(backup==null || backup.files()==null || backup.absent()==null || backup.files().size()+backup.absent().size()>MAX_FILES*2) throw new IOException("Invalid backup");
            for(String path:backup.files().keySet()) { if(!allowed(path)) throw new IOException("Unsafe backup"); safe(root,path); }
            for(String path:backup.absent()) { if(!allowed(path)) throw new IOException("Unsafe backup"); safe(root,path); }
            validateEncoded(backup.files());
            byte[] restoredState=backup.state()==null?null:Base64.getDecoder().decode(backup.state());
            if(restoredState!=null && restoredState.length>MAX_TOTAL*2) throw new IOException("Oversized backup state");
            for(var entry:backup.files().entrySet()) writeAtomic(safe(root,entry.getKey()),Base64.getDecoder().decode(entry.getValue()));
            for(String path:backup.absent()) Files.deleteIfExists(safe(root,path));
            Path state=safe(root,"config/socialmod/series/active.json");
            if(restoredState==null) Files.deleteIfExists(state); else writeAtomic(state,restoredState);
        } catch(RuntimeException e) { throw new IOException("Invalid backup",e); }
    }
    private static State readState(Path root) throws IOException {
        Path p=safe(root,"config/socialmod/series/active.json");
        if(!Files.exists(p)) return new State("",List.of(),Map.of());
        try {
            if(Files.size(p)>MAX_TOTAL*2) throw new IOException("Invalid active profile");
            State s=GSON.fromJson(Files.readString(p),State.class);
            if(s==null || s.profile()==null || s.files()==null || s.baseline()==null || s.files().size()>MAX_FILES || s.baseline().size()>MAX_FILES || !s.files().containsAll(s.baseline().keySet()) || s.files().stream().anyMatch(f->!allowed(f))) throw new IOException("Invalid active profile");
            validateEncoded(s.baseline()); return s;
        } catch(RuntimeException e) { throw new IOException("Invalid active profile",e); }
    }
    public static String active(Path root) throws IOException { return readState(root).profile(); }
    private static void validateEncoded(Map<String,String> files) throws IOException {
        long total=0;
        for(var entry:files.entrySet()) {
            if(!allowed(entry.getKey()) || entry.getValue()==null || entry.getValue().length()>MAX_FILE*2) throw new IOException("Invalid backup resource");
            byte[] bytes=Base64.getDecoder().decode(entry.getValue()); total+=bytes.length;
            if(bytes.length>MAX_FILE || total>MAX_TOTAL) throw new IOException("Backup exceeds size limit");
        }
    }
    /** Rejects symlinks in every ancestor, including not-yet-created destinations. */
    public static Path safe(Path root,String relative) throws IOException {
        Path base=root.toRealPath(), target=base.resolve(relative).normalize();
        if(!target.startsWith(base)) throw new IOException("Path escapes instance");
        for(Path p=target;!p.equals(base);p=p.getParent()) if(Files.isSymbolicLink(p)) throw new IOException("Symbolic links are not supported: "+p);
        return target;
    }
    private static void atomic(Path source,Path target) throws IOException { Files.move(source,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
    private static void writeAtomic(Path target,byte[] data) throws IOException {
        Files.createDirectories(target.getParent()); Path tmp=target.resolveSibling(target.getFileName()+".tmp");
        if(Files.isSymbolicLink(tmp) || Files.isDirectory(tmp)) throw new IOException("Unsafe temporary path");
        try { Files.write(tmp,data); atomic(tmp,target); } finally { Files.deleteIfExists(tmp); }
    }
    private SeriesPack() { }
}
