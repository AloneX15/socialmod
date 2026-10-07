package com.takumistudios.socialmod.client.theme;

import com.takumistudios.socialmod.common.model.SeriesPack;
import com.takumistudios.socialmod.common.model.RowDesign;
import com.takumistudios.socialmod.common.model.VisualDesign;
import com.takumistudios.socialmod.client.compat.fancy.FancyBridge;
import net.minecraft.client.Minecraft;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Serialized disk operations; cached snapshots returned to the client thread. */
public final class SeriesProfiles {
    private static volatile boolean stopping;
    private static volatile Thread worker;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"SocialMod-Series-Profiles-IO");t.setDaemon(true);worker=t;return t;});
    public record Profile(Path file,SeriesPack.Metadata metadata,List<String> layouts) { }
    public record Catalog(List<Profile> profiles,List<String> layouts,List<Path> imports,String active) { }
    public static Path root() { return Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize(); }
    private interface Work<T> { T run() throws Exception; }
    private static <T> CompletableFuture<T> io(Work<T> work) {
        java.util.function.Supplier<T> task=()->{try{return work.run();}catch(Exception e){throw new CompletionException(e);}};
        if(IO.isShutdown() && Thread.currentThread()==worker) { try { return CompletableFuture.completedFuture(task.get()); } catch(RuntimeException e) { return CompletableFuture.failedFuture(e); } }
        try { return CompletableFuture.supplyAsync(task,IO); } catch(RejectedExecutionException e) {
            // Shutdown may race the admission check above; accepted operations still
            // finish their dependent disk steps on this worker.
            if(Thread.currentThread()==worker) { try { return CompletableFuture.completedFuture(task.get()); } catch(RuntimeException failure) { return CompletableFuture.failedFuture(failure); } }
            return CompletableFuture.failedFuture(e);
        }
    }
    public static CompletableFuture<Catalog> catalog() {
        Path root=root(); return io(()->{
            Path profiles=SeriesPack.safe(root,"config/socialmod/series/profiles"),imports=SeriesPack.safe(root,"config/socialmod/presets");
            Files.createDirectories(profiles); Files.createDirectories(imports);
            var result=new ArrayList<Profile>();
            try(var files=Files.list(profiles)) {
                for(Path file:files.filter(p->p.toString().endsWith(".zip") && Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).sorted().limit(100).toList()) {
                    try { var pack=SeriesPack.read(file); result.add(new Profile(file,pack.metadata(),pack.files().keySet().stream().filter(p->p.startsWith("config/fancymenu/customization/")).toList())); }
                    catch(java.io.IOException e) { com.takumistudios.socialmod.SocialMod.LOGGER.warn("Invalid series profile: {}",file,e); }
                }
            }
            List<Path> zips; try(var files=Files.list(imports)) { zips=files.filter(p->p.toString().endsWith(".zip") && Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).sorted().limit(100).toList(); }
            return new Catalog(List.copyOf(result),SeriesPack.layouts(root),zips,SeriesPack.active(root));
        });
    }
    public static CompletableFuture<Path> save(Profile previous,String name,String author,String version,Collection<String> selection) {
        Path root=root(); String mc=net.minecraft.SharedConstants.getCurrentVersion().id(); var layouts=List.copyOf(selection);
        return io(()->{
            Path path=previous==null?SeriesPack.safe(root,"config/socialmod/series/profiles/"+UUID.randomUUID()+".zip"):previous.file();
            var pack=SeriesPackFiles.withVersions(SeriesPack.capture(root,name,author,version,mc,layouts)); SeriesPack.write(path,pack); return path;
        });
    }
    public static CompletableFuture<Path> duplicate(Profile profile,String name) {
        Path root=root(); return io(()->{
            var pack=SeriesPack.read(profile.file()); var m=pack.metadata();
            var copy=new SeriesPack.Contents(new SeriesPack.Metadata(1,name,m.author(),m.version(),m.minecraft(),m.requiredMods(),m.versionRequirements()),pack.files());
            Path path=SeriesPack.safe(root,"config/socialmod/series/profiles/"+UUID.randomUUID()+".zip"); SeriesPack.write(path,copy); return path;
        });
    }
    public static CompletableFuture<SeriesPack.Contents> inspect(Path zip) { return io(()->SeriesPack.read(zip)); }
    public static List<String> missing(SeriesPack.Contents pack) {
        var missing=new ArrayList<String>();
        for(String id:pack.metadata().requiredMods()) {
            var mod=FabricLoader.getInstance().getModContainer(id);
            if(mod.isEmpty()) { missing.add(id); continue; }
            String range=pack.metadata().versionRequirements().get(id);
            if(range!=null) try {
                if(!net.fabricmc.loader.api.metadata.version.VersionPredicate.parse(range).test(mod.get().getMetadata().getVersion())) missing.add(id+" "+range+" ("+mod.get().getMetadata().getVersion().getFriendlyString()+")");
            } catch(net.fabricmc.loader.api.VersionParsingException e) { missing.add(id+" "+range); }
        }
        if(!pack.metadata().minecraft().equals(net.minecraft.SharedConstants.getCurrentVersion().id())) missing.add("Minecraft "+pack.metadata().minecraft());
        try { if(pack.files().containsKey(SeriesPack.ROWS)) RowTemplates.validateResources(RowDesign.parse(new String(pack.files().get(SeriesPack.ROWS),java.nio.charset.StandardCharsets.UTF_8))); }
        catch(RuntimeException e) { missing.add(e.getMessage()); }
        return List.copyOf(missing);
    }
    public static CompletableFuture<Void> activate(Path zip) {
        return inspect(zip).thenCompose(pack->activate(pack,zip.getFileName().toString()));
    }
    public static CompletableFuture<Path> importPack(SeriesPack.Contents pack) {
        var missing=missing(pack); if(!missing.isEmpty()) return CompletableFuture.failedFuture(new IllegalArgumentException(String.join(", ",missing)));
        Path root=root();
        return io(()->{
            Path file=SeriesPack.safe(root,"config/socialmod/series/profiles/"+UUID.randomUUID()+".zip");
            SeriesPack.write(file,pack); SeriesPack.install(root,pack,file.getFileName().toString()); return file;
        }).thenCompose(file->reload().thenApply(v->file));
    }
    private static CompletableFuture<Void> activate(SeriesPack.Contents pack,String id) {
        Path root=root();
        // Resource lookups are read-only. Keep the disk transaction on the single worker,
        // so shutdown can drain it without waiting for a client-thread continuation.
        return io(()->{
            var missing=missing(pack);if(!missing.isEmpty())throw new IllegalArgumentException(String.join(", ",missing));
            SeriesPack.install(root,pack,id);return null;
        }).thenCompose(v->reload());
    }
    public static CompletableFuture<Void> restore() { Path root=root(); return io(()->{SeriesPack.restore(root);return null;}).thenCompose(v->reload()); }
    private static CompletableFuture<Void> reload() {
        Path root=root(); return io(()->{
            Path rows=SeriesPack.safe(root,SeriesPack.ROWS),visual=SeriesPack.safe(root,SeriesPack.VISUAL);
            return new Object[]{Files.exists(rows)?RowDesign.parse(Files.readString(rows)):RowDesign.defaults(),Files.exists(visual)?VisualDesign.parse(Files.readString(visual)):null};
        }).thenCompose(values->{
            if(stopping) return CompletableFuture.completedFuture(null);
            var ready=new CompletableFuture<Void>(); Minecraft.getInstance().execute(()->{
                try { RowTemplates.activate((RowDesign)values[0]); LocalSeriesDesign.activate((VisualDesign)values[1]); FancyBridge.reload(); ready.complete(null); }
                catch(RuntimeException e) { ready.completeExceptionally(e); }
            }); return ready;
        });
    }
    public static CompletableFuture<Path> export(String name,String author,String version,Collection<String> selection) {
        Path root=root(); String mc=net.minecraft.SharedConstants.getCurrentVersion().id(); var layouts=List.copyOf(selection);
        return io(()->SeriesPackFiles.export(root,name,author,version,mc,layouts));
    }
    public static CompletableFuture<Void> installTemplate(SeriesPack.Contents pack) { return importPack(pack).thenApply(file -> null); }
    public static void shutdown() {
        stopping=true; IO.shutdown();
        try { if (!IO.awaitTermination(30,TimeUnit.SECONDS)) com.takumistudios.socialmod.SocialMod.LOGGER.error("Series profile operations still pending at client shutdown"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    private SeriesProfiles() { }
}
