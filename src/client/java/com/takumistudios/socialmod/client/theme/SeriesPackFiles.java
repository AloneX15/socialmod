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
        Path output=SeriesPack.safe(root,"config/socialmod/presets/socialmod-series.zip");
        SeriesPack.write(output,contents); return output;
    }
    private SeriesPackFiles() { }
}
