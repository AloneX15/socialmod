package com.takumistudios.socialmod.server.storage;

import com.google.gson.JsonElement;

import java.io.IOException;
import java.util.List;

/**
 * Backend de almacenamiento intercambiable (PLAN 4.2). Trabaja con documentos JSON agrupados en "cubos"
 * ({@code players}, {@code groups}, {@code conversations}, {@code reports}...). Todas las llamadas se hacen
 * desde el hilo de E/S de {@link SocialStorage}, nunca desde el hilo principal del servidor.
 */
public interface StorageBackend extends AutoCloseable {
    String id();

    /** Lee un documento, o {@code null} si no existe. */
    JsonElement read(String bucket, String key) throws IOException;

    /** Escribe un documento de forma atómica (o se escribe entero o no se escribe). */
    void write(String bucket, String key, JsonElement data) throws IOException;

    void delete(String bucket, String key) throws IOException;

    List<String> keys(String bucket) throws IOException;

    /** Añade una línea a un registro de texto (auditoría). */
    void appendLine(String log, String line) throws IOException;

    @Override
    void close();
}
