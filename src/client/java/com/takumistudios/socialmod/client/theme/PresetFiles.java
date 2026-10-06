package com.takumistudios.socialmod.client.theme;

import com.takumistudios.socialmod.common.model.VisualDesign;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

/** Portable pack IO. Asset files live inside the preset directory and are never executable. */
public final class PresetFiles {
    public static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("socialmod/presets");
    public static VisualDesign load() throws java.io.IOException { return load(DIRECTORY); }
    public static VisualDesign load(Path directory) throws java.io.IOException {
        Path archive = directory.resolve("import.zip");
        if (Files.exists(archive)) {
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                ZipEntry entry = zip.getEntry("socialmod-visual.json");
                if (entry == null || entry.getSize() > 65536) throw new java.io.IOException("Invalid preset archive");
                String json;
                try (var stream = zip.getInputStream(entry)) { byte[] bytes = stream.readNBytes(65537); if (bytes.length > 65536) throw new java.io.IOException("Oversized preset"); json = new String(bytes, StandardCharsets.UTF_8); }
                VisualDesign design = VisualDesign.parse(json);
                Path assets = directory.resolve("assets").toAbsolutePath().normalize();
                long total = 0; int count = 0;
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry asset = entries.nextElement(); if (asset.isDirectory() || !asset.getName().startsWith("assets/")) continue;
                    if (++count > 2000) throw new java.io.IOException("Too many assets");
                    Path target = directory.toAbsolutePath().resolve(asset.getName()).normalize();
                    if (!target.startsWith(assets) || asset.getName().contains("\\")) throw new java.io.IOException("Invalid asset path");
                    try (var stream = zip.getInputStream(asset)) {
                        byte[] bytes = stream.readNBytes(8 * 1024 * 1024 + 1); total += bytes.length;
                        if (bytes.length > 8 * 1024 * 1024 || total > 64 * 1024 * 1024) throw new java.io.IOException("Assets too large");
                        Files.createDirectories(target.getParent());
                        if (!target.getParent().toRealPath().startsWith(assets.toRealPath()) || Files.isSymbolicLink(target)) throw new java.io.IOException("Asset symlink");
                        Files.write(target, bytes);
                    }
                }
                return design;
            }
        }
        Path file = directory.resolve("series.json"); return VisualDesign.parse(Files.readString(file));
    }
    public static void export(VisualDesign design) throws java.io.IOException { export(design, DIRECTORY); }
    public static void export(VisualDesign design, Path directory) throws java.io.IOException {
        design.validate(); Files.createDirectories(directory);
        Files.writeString(directory.resolve("series.json"), VisualDesign.GSON.toJson(design), StandardCharsets.UTF_8);
        Path target = directory.resolve("series.zip"), tmp = directory.resolve("series.zip.tmp");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp))) {
            add(zip, "socialmod-visual.json", VisualDesign.GSON.toJson(design));
            var packFormat = SharedConstants.getCurrentVersion().packVersion(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
            String format = "[" + packFormat.major() + "," + packFormat.minor() + "]";
            add(zip, "pack.mcmeta", "{\"pack\":{\"description\":\"SocialMod series — TakumiStudios\",\"min_format\":" + format + ",\"max_format\":" + format + "}}");
            add(zip, "assets/socialmod/themes/default.json", VisualDesign.GSON.toJson(design.theme));
            Path assets = directory.resolve("assets");
            if (design.decoration.equals("christmas") || design.font.equals("socialmod:christmas")) {
                for (String path : com.takumistudios.socialmod.common.model.VisualPresets.assetPaths()) {
                    String name = "assets/socialmod/" + path;
                    Path override = assets.resolve("socialmod").resolve(path);
                    if (Files.isRegularFile(override, LinkOption.NOFOLLOW_LINKS) && override.toRealPath().startsWith(assets.toRealPath())) continue;
                    try (var input = PresetFiles.class.getResourceAsStream("/" + name)) {
                        if (input == null) throw new java.io.IOException("Missing bundled Christmas asset: " + path);
                        zip.putNextEntry(new ZipEntry(name)); input.transferTo(zip); zip.closeEntry();
                    }
                }
            }
            if (Files.isDirectory(assets)) try (var paths = Files.walk(assets)) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    if (Files.isSymbolicLink(file) || !file.toRealPath().startsWith(assets.toRealPath())) continue;
                    String name = "assets/" + assets.relativize(file).toString().replace('\\', '/');
                    if (name.equals("assets/socialmod/themes/default.json")) continue;
                    zip.putNextEntry(new ZipEntry(name)); Files.copy(file, zip); zip.closeEntry();
                }
            }
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    private static void add(ZipOutputStream zip, String name, String value) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(value.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
}
