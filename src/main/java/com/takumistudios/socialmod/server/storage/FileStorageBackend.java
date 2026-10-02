package com.takumistudios.socialmod.server.storage;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Backend por defecto: un archivo JSON comprimido por documento en {@code <mundo>/socialmod/<cubo>/<clave>.json.gz}.
 * Sin dependencias. La escritura es atómica (archivo temporal + rename): un corte de luz no deja archivos a medias.
 * Si un archivo está corrupto se renombra a {@code .corrupt} y se continúa sin él.
 */
public final class FileStorageBackend implements StorageBackend {
    private static final Gson GSON = new Gson();
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,128}");
    private static final String EXTENSION = ".json.gz";

    private final Path root;

    public FileStorageBackend(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    @Override
    public String id() {
        return "file";
    }

    private Path file(String bucket, String key) {
        if (!SAFE_NAME.matcher(bucket).matches() || !SAFE_NAME.matcher(key).matches()) {
            throw new IllegalArgumentException("Nombre de archivo no permitido: " + bucket + "/" + key);
        }
        return root.resolve(bucket).resolve(key + EXTENSION);
    }

    @Override
    public JsonElement read(String bucket, String key) throws IOException {
        Path file = file(bucket, key);
        if (!Files.exists(file)) {
            return null;
        }
        try (Reader reader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (IOException | JsonParseException e) {
            Path corrupt = file.resolveSibling(file.getFileName() + "." + System.currentTimeMillis() + ".corrupt");
            Files.move(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("Archivo corrupto " + file + " (movido a " + corrupt.getFileName() + "): " + e.getMessage(), e);
        }
    }

    @Override
    public void write(String bucket, String key, JsonElement data) throws IOException {
        Path file = file(bucket, key);
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(temp)), StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void delete(String bucket, String key) throws IOException {
        Files.deleteIfExists(file(bucket, key));
    }

    @Override
    public List<String> keys(String bucket) throws IOException {
        Path dir = root.resolve(bucket);
        List<String> keys = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return keys;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(EXTENSION))
                    .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                    .forEach(keys::add);
        }
        return keys;
    }

    @Override
    public void appendLine(String log, String line) throws IOException {
        if (!SAFE_NAME.matcher(log).matches()) {
            throw new IllegalArgumentException("Nombre de registro no permitido: " + log);
        }
        Files.createDirectories(root);
        Files.writeString(root.resolve(log + ".log"), line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Override
    public void close() {
        // Sin recursos abiertos entre llamadas
    }
}
