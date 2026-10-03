package com.takumistudios.socialmod.server.storage;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.takumistudios.socialmod.SocialMod;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Backends {@code h2}, {@code mysql} y {@code mariadb} (PLAN 4.2, fase 5). Una tabla clave/valor
 * {@code <prefijo>documents(bucket, doc_key, data, updated)} con el mismo JSON que el backend {@code file}.
 * <ul>
 *     <li>El driver JDBC no va dentro del jar (PLAN 14: nada de librerías grandes ni nativas): se busca en el
 *     classpath (si otro mod ya lo trae) o en los {@code .jar} de {@code config/socialmod/drivers/}.</li>
 *     <li>La auditoría y las exportaciones siguen en archivos ({@code audit.log}, {@code exports/}) para que el staff
 *     los pueda leer y entregar sin tocar la base de datos.</li>
 *     <li>La primera vez, si la tabla está vacía y hay datos del backend {@code file}, se importan.</li>
 *     <li>Una sola conexión usada solo desde el hilo de E/S de {@link SocialStorage}; se reabre si se cae.</li>
 * </ul>
 */
public final class JdbcStorageBackend implements StorageBackend {
    private static final Gson GSON = new Gson();
    private static final Pattern SAFE_PREFIX = Pattern.compile("[A-Za-z0-9_]{0,32}");

    private final String id;
    private final Driver driver;
    private final String url;
    private final Properties properties;
    private final String table;
    private final FileStorageBackend files;
    private @Nullable Connection connection;

    private JdbcStorageBackend(String id, Driver driver, String url, Properties properties, String prefix, FileStorageBackend files) {
        this.id = id;
        this.driver = driver;
        this.url = url;
        this.properties = properties;
        this.table = prefix + "documents";
        this.files = files;
    }

    /**
     * Abre el backend o lanza una excepción con un mensaje claro (el servidor avisa y usa {@code file}).
     * @param kind {@code h2}, {@code mysql} o {@code mariadb}
     */
    public static JdbcStorageBackend open(String kind, String jdbcUrl, String user, String password, String prefix,
                                          Path configDir, FileStorageBackend files) throws IOException {
        if (!SAFE_PREFIX.matcher(prefix).matches()) {
            throw new IOException("tablePrefix no válido (solo letras, números y _): " + prefix);
        }
        String driverClass = switch (kind) {
            case "h2" -> "org.h2.Driver";
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "mariadb" -> "org.mariadb.jdbc.Driver";
            default -> throw new IOException("Backend desconocido: " + kind);
        };
        String url = jdbcUrl == null || jdbcUrl.isBlank()
                ? (kind.equals("h2") ? "jdbc:h2:file:" + files.root().resolve("socialmod-h2").toAbsolutePath() : "")
                : jdbcUrl;
        if (url.isEmpty()) {
            throw new IOException("Falta storage.jdbcUrl para el backend " + kind);
        }
        Driver driver = loadDriver(driverClass, configDir.resolve("drivers"));
        Properties properties = new Properties();
        if (user != null && !user.isEmpty()) {
            properties.setProperty("user", user);
        }
        if (password != null && !password.isEmpty()) {
            properties.setProperty("password", password);
        }
        JdbcStorageBackend backend = new JdbcStorageBackend(kind, driver, url, properties, prefix, files);
        try {
            backend.createTable();
            backend.importFromFilesIfEmpty();
        } catch (SQLException e) {
            backend.close();
            throw new IOException("No se pudo preparar la base de datos (" + redact(url) + "): " + e.getMessage(), e);
        }
        return backend;
    }

    private static Driver loadDriver(String className, Path driversDir) throws IOException {
        try {
            return (Driver) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // no está en el classpath: se busca en config/socialmod/drivers
        }
        List<URL> jars = new ArrayList<>();
        if (Files.isDirectory(driversDir)) {
            try (Stream<Path> list = Files.list(driversDir)) {
                for (Path jar : list.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
                    jars.add(jar.toUri().toURL());
                }
            }
        }
        if (jars.isEmpty()) {
            throw new IOException("No se encontró el driver " + className + ": copia su .jar en " + driversDir);
        }
        try {
            URLClassLoader loader = new URLClassLoader(jars.toArray(URL[]::new), JdbcStorageBackend.class.getClassLoader());
            return (Driver) Class.forName(className, true, loader).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IOException("El driver " + className + " no está en " + driversDir + ": " + e, e);
        }
    }

    private static String redact(String url) {
        return url.replaceAll("(?i)(password=)[^&;]*", "$1***");
    }

    private Connection connection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // ya estaba cerrada
                }
            }
            connection = driver.connect(url, properties);
            if (connection == null) {
                throw new SQLException("El driver no acepta la URL " + redact(url));
            }
            connection.setAutoCommit(true);
        }
        return connection;
    }

    private void createTable() throws SQLException {
        String text = id.equals("h2") ? "CLOB" : "LONGTEXT";
        try (Statement statement = connection().createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + table + " (bucket VARCHAR(64) NOT NULL, doc_key VARCHAR(128) NOT NULL, "
                    + "data " + text + " NOT NULL, updated BIGINT NOT NULL, PRIMARY KEY (bucket, doc_key))"
                    + (id.equals("h2") ? "" : " CHARACTER SET utf8mb4"));
        }
    }

    private void importFromFilesIfEmpty() throws SQLException, IOException {
        try (Statement statement = connection().createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (result.next() && result.getLong(1) > 0) {
                return;
            }
        }
        Path root = files.root();
        if (!Files.isDirectory(root)) {
            return;
        }
        int imported = 0;
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                String bucket = dir.getFileName().toString();
                if (fileOnly(bucket) || !bucket.matches("[A-Za-z0-9_.-]{1,64}")) {
                    continue;
                }
                for (String key : files.keys(bucket)) {
                    JsonElement data;
                    try {
                        data = files.read(bucket, key);
                    } catch (IOException e) {
                        continue; // corrupto: FileStorageBackend ya lo apartó
                    }
                    if (data != null) {
                        write(bucket, key, data);
                        imported++;
                    }
                }
            }
        }
        if (imported > 0) {
            SocialMod.LOGGER.info("[SocialMod] Importados {} documentos del backend file a {} (los archivos se conservan)", imported, id);
        }
    }

    /** Lo que siempre va a archivos: exportaciones de datos (las entrega el staff). */
    private static boolean fileOnly(String bucket) {
        return bucket.equals(SocialStorage.EXPORTS);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public JsonElement read(String bucket, String key) throws IOException {
        if (fileOnly(bucket)) {
            return files.read(bucket, key);
        }
        try (PreparedStatement statement = connection().prepareStatement("SELECT data FROM " + table + " WHERE bucket = ? AND doc_key = ?")) {
            statement.setString(1, bucket);
            statement.setString(2, key);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return JsonParser.parseString(result.getString(1));
            }
        } catch (SQLException e) {
            throw new IOException("Error leyendo " + bucket + "/" + key + ": " + e.getMessage(), e);
        } catch (JsonParseException e) {
            throw new IOException("Documento corrupto " + bucket + "/" + key + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void write(String bucket, String key, JsonElement data) throws IOException {
        if (fileOnly(bucket)) {
            files.write(bucket, key, data);
            return;
        }
        String sql = id.equals("h2")
                ? "MERGE INTO " + table + " (bucket, doc_key, data, updated) KEY (bucket, doc_key) VALUES (?, ?, ?, ?)"
                : "INSERT INTO " + table + " (bucket, doc_key, data, updated) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE data = VALUES(data), updated = VALUES(updated)";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setString(1, bucket);
            statement.setString(2, key);
            statement.setString(3, GSON.toJson(data));
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Error escribiendo " + bucket + "/" + key + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String bucket, String key) throws IOException {
        if (fileOnly(bucket)) {
            files.delete(bucket, key);
            return;
        }
        try (PreparedStatement statement = connection().prepareStatement("DELETE FROM " + table + " WHERE bucket = ? AND doc_key = ?")) {
            statement.setString(1, bucket);
            statement.setString(2, key);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Error borrando " + bucket + "/" + key + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> keys(String bucket) throws IOException {
        if (fileOnly(bucket)) {
            return files.keys(bucket);
        }
        List<String> keys = new ArrayList<>();
        try (PreparedStatement statement = connection().prepareStatement("SELECT doc_key FROM " + table + " WHERE bucket = ?")) {
            statement.setString(1, bucket);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    keys.add(result.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IOException("Error listando " + bucket + ": " + e.getMessage(), e);
        }
        return keys;
    }

    @Override
    public void appendLine(String log, String line) throws IOException {
        files.appendLine(log, line);
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                SocialMod.LOGGER.debug("[SocialMod] Error cerrando la base de datos: {}", e.getMessage());
            }
            connection = null;
        }
    }
}
