package com.takumistudios.socialmod.common.model;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.IOException;
/** Compatibility for removed built-in styles; user assets are retained. */
public final class RetiredStyles {
    public static boolean design(String json) {
        if (json.length() > 65536) throw new IllegalArgumentException("Visual size");
        JsonBudget.checkDepth(json);
        var value = VisualDesign.GSON.fromJson(json, VisualDesign.class);
        return value != null && value.retired();
    }
    public static boolean layout(String path) {
        return path.matches("(?:.*/)?socialmod_(?:christmas|dedsafio)(?:_hud)?\\.txt");
    }
    public static SeriesPack.Contents normalize(SeriesPack.Contents pack) {
        byte[] visual = pack.files().get(SeriesPack.VISUAL);
        if (visual == null || !design(new String(visual,StandardCharsets.UTF_8))) return pack;
        var files = new TreeMap<>(pack.files());
        files.put(SeriesPack.VISUAL,VisualDesign.GSON.toJson(new VisualDesign()).getBytes(StandardCharsets.UTF_8));
        files.put(SeriesPack.ROWS,RowDesign.GSON.toJson(RowDesign.defaults()).getBytes(StandardCharsets.UTF_8));
        files.replaceAll((path,data) -> path.startsWith("config/fancymenu/customization/") ? disabled(data) : data);
        return new SeriesPack.Contents(pack.metadata(),files);
    }
    private static byte[] disabled(byte[] data) {
        String text = new String(data,StandardCharsets.UTF_8);
        return text.replaceAll("(?m)^(\\s*is_enabled\\s*=\\s*)true\\s*$", "$1false").getBytes(StandardCharsets.UTF_8);
    }
    public static void migrate(Path root) throws IOException {
        Path visual = SeriesPack.safe(root,SeriesPack.VISUAL);
        boolean reset = Files.isRegularFile(visual) && Files.size(visual) <= 65536 && design(Files.readString(visual));
        if (reset) {
            write(visual,VisualDesign.GSON.toJson(new VisualDesign()).getBytes(StandardCharsets.UTF_8));
            write(SeriesPack.safe(root,SeriesPack.ROWS),RowDesign.GSON.toJson(RowDesign.defaults()).getBytes(StandardCharsets.UTF_8));
        }
        for (String path : SeriesPack.layouts(root)) if (layout(path) || reset && path.startsWith("config/fancymenu/customization/socialmod_")) {
            Path file=SeriesPack.safe(root,path);
            if (Files.size(file) <= 8 * 1024 * 1024) write(file,disabled(Files.readAllBytes(file)));
        }
    }
    private static void write(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName()+".migration.tmp");
        if (Files.isSymbolicLink(tmp)) throw new IOException("Unsafe migration path");
        Files.write(tmp,bytes); Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    private RetiredStyles() { }
}
