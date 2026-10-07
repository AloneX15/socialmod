package com.takumistudios.socialmod.client.theme;

import com.takumistudios.socialmod.common.model.SeriesPack;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Default exports contain SocialMod layouts only; the series manager offers explicit selection. */
public final class SeriesPackFiles {
    public static Path export(Path gameDirectory) throws IOException {
        var layouts=SeriesPack.layouts(gameDirectory).stream().filter(p->p.substring(p.lastIndexOf('/')+1).startsWith("socialmod_")).toList();
        return export(gameDirectory,"SocialMod","TakumiStudios","1.0",net.minecraft.SharedConstants.getCurrentVersion().id(),layouts);
    }
    public static Path export(Path root,String name,String author,String version,String minecraft,Collection<String> layouts) throws IOException {
        var contents=SeriesPack.capture(root,name,author,version,minecraft,layouts);
        contents=withVersions(contents);
        Path output=SeriesPack.safe(root,"config/socialmod/presets/socialmod-series.zip");
        SeriesPack.write(output,contents); return output;
    }
    public static SeriesPack.Contents withVersions(SeriesPack.Contents pack) {
        var versions=new java.util.TreeMap<String,String>();
        for(String id:pack.metadata().requiredMods()) net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(id).ifPresent(mod -> versions.put(id,"="+mod.getMetadata().getVersion().getFriendlyString()));
        var m=pack.metadata(); return new SeriesPack.Contents(new SeriesPack.Metadata(m.format(),m.name(),m.author(),m.version(),m.minecraft(),m.requiredMods(),versions),pack.files());
    }
    private SeriesPackFiles() { }
}
