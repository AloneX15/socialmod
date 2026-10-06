package com.takumistudios.socialmod.client.theme;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Exports modpack files, never downloads or sends layouts to a server. */
public final class SeriesPackFiles {
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    public static Path export(Path gameDirectory) throws IOException {
        Path root = gameDirectory.toRealPath(), output = root.resolve("config/socialmod/presets/socialmod-series.zip");
        Files.createDirectories(output.getParent()); Path temporary = output.resolveSibling("socialmod-series.zip.tmp");
        var files = new java.util.TreeSet<Path>();
        for (String directory : List.of("config/fancymenu", "config/spiffyhud", "config/socialmod/integration")) {
            Path source = root.resolve(directory); if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) continue;
            try (var paths = Files.walk(source)) {
                for (var iterator = paths.iterator(); iterator.hasNext();) {
                    var file = iterator.next();
                    if (Files.isSymbolicLink(file)) throw new IOException("Symbolic links cannot be exported: " + file);
                    String name = file.getFileName().toString();
                    if (Files.isRegularFile(file) && !name.endsWith(".tmp") && !name.endsWith(".disabled")) {
                        files.add(file);
                        if (files.size() > 2000) throw new IOException("Too many series pack files");
                    }
                }
            }
        }
        // FancyMenu serializes local sources explicitly. Include instance-relative dependencies as well.
        for(var config : new ArrayList<>(files)) if(config.toString().endsWith(".txt") && Files.size(config) < 1024*1024) {
            var matcher = java.util.regex.Pattern.compile("\\[source:local\\]([^\\r\\n]*)").matcher(Files.readString(config));
            while(matcher.find()) {
                String reference = matcher.group(1).strip(); if(reference.isEmpty()) continue;
                if(reference.contains("{") || reference.matches("^[A-Za-z]:.*")) throw new IOException("Move external/dynamic assets into config/fancymenu/assets before export: " + reference);
                Path resource = root.resolve(reference.replace('\\','/').replaceFirst("^/+", "")).normalize();
                if(!resource.startsWith(root) || !Files.isRegularFile(resource,LinkOption.NOFOLLOW_LINKS) || !resource.toRealPath().startsWith(root)) throw new IOException("Missing or external layout asset: " + reference);
                files.add(resource);
            }
        }
        long total = 0; int count = 0;
        try (var zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
            for(var file : files) {
                if (!file.toRealPath().startsWith(root)) throw new IOException("Unsafe pack path");
                long size = Files.size(file); total += size;
                if (++count > 2000 || total > MAX_BYTES || size > 8L * 1024 * 1024) throw new IOException("Series pack too large");
                zip.putNextEntry(new ZipEntry(root.relativize(file).toString().replace('\\','/'))); Files.copy(file,zip); zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("SOCIALMOD-SETUP.txt")); zip.write("Install these config directories in the modpack instance. Requires SocialMod, FancyMenu and SpiffyHUD for HUD layouts. Use matching Minecraft versions. Creado por TakumiStudios.\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
        } catch (IOException e) { Files.deleteIfExists(temporary); throw e; }
        Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); return output;
    }
    private SeriesPackFiles() { }
}
