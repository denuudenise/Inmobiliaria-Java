package ar.edu.nuevohogar.persistencia.dao;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public final class ConexionBaseDatos implements AutoCloseable {
    private static final Object DEMO_INITIALIZATION_LOCK = new Object();
    private final String url, user, password;
    private com.zaxxer.hikari.HikariDataSource pool;
    private boolean closed;
    public final boolean demo;
    public final Path storage;
    public final Path webMediaRoot;
    public final String webMediaPrefix;
    public final String mediaApiUrl;
    public final String mediaWebUrl;

    public ConexionBaseDatos() throws Exception {
        Properties p = loadProperties();
        url = setting(p, "db.url", "DB_URL", "jdbc:h2:./data/nuevo-hogar;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        user = setting(p, "db.user", "DB_USER", "sa");
        password = setting(p, "db.password", "DB_PASSWORD", "");
        demo = url.startsWith("jdbc:h2:");
        storage =
                Path.of(p.getProperty("config.base", "")).resolve(setting(p, "storage.path", "STORAGE_PATH", "data/archivos"))
                        .toAbsolutePath()
                        .normalize();
        Files.createDirectories(storage);
        String webRoot = setting(p, "media.web.root", "MEDIA_WEB_ROOT", "").trim();
        webMediaRoot = webRoot.isEmpty() ? null : Path.of(p.getProperty("config.base", "")).resolve(webRoot).toAbsolutePath().normalize();
        String prefix = setting(p, "media.web.prefix", "MEDIA_WEB_PREFIX", "/Inmobiliaria/");
        webMediaPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        if (!webMediaPrefix.startsWith("/") || webMediaPrefix.equals("/"))
            throw new IllegalArgumentException("El prefijo multimedia debe identificar la carpeta de la web.");
        mediaApiUrl = setting(p, "media.api.url", "MEDIA_API_URL", "").trim();
        mediaWebUrl = setting(p, "media.web.url", "MEDIA_WEB_URL", "").trim().replaceAll("/+$", "");
    }

    public ConexionBaseDatos(String url, Path storage) {
        this(url, storage, null, "/Inmobiliaria/");
    }

    public ConexionBaseDatos(String url, Path storage, Path webMediaRoot, String webMediaPrefix) {
        this.url = url;
        user = "sa";
        password = "";
        demo = true;
        this.storage = storage.toAbsolutePath().normalize();
        this.webMediaRoot = webMediaRoot == null ? null : webMediaRoot.toAbsolutePath().normalize();
        this.webMediaPrefix = webMediaPrefix.endsWith("/") ? webMediaPrefix : webMediaPrefix + "/";
        this.mediaApiUrl = "";
        this.mediaWebUrl = "";
    }

    public Connection open() throws SQLException {
        // Reutiliza conexiones físicas sin cambiar el driver, URL, SSL ni credenciales.
        // H2 conserva conexiones independientes para las pruebas de demostración.
        if (url.startsWith("jdbc:mariadb:") || url.startsWith("jdbc:mysql:")) {
            com.zaxxer.hikari.HikariDataSource source;
            synchronized (this) {
                if (closed) throw new SQLException("La conexión de la aplicación está cerrada.");
                if (pool == null) {
                    source = new com.zaxxer.hikari.HikariDataSource();
                    source.setJdbcUrl(url);
                    source.setUsername(user);
                    source.setPassword(password);
                    source.setMaximumPoolSize(4);
                    source.setMinimumIdle(0);
                    source.setConnectionTimeout(10000);
                    source.setValidationTimeout(3000);
                    source.setIdleTimeout(60000);
                    source.setMaxLifetime(300000);
                    source.setInitializationFailTimeout(-1);
                    source.addDataSourceProperty("connectTimeout", "10000");
                    source.addDataSourceProperty("socketTimeout", "20000");
                    pool = source;
                }
                source = pool;
            }
            return source.getConnection();
        }
        synchronized (this) {
            if (closed) throw new SQLException("La conexión de la aplicación está cerrada.");
        }
        Properties connection = new Properties();
        connection.setProperty("user", user);
        connection.setProperty("password", password);
        if (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:")) {
            connection.setProperty("connectTimeout", "10000");
            connection.setProperty("socketTimeout", "20000");
        }
        return DriverManager.getConnection(url, connection);
    }

    @Override public synchronized void close() {
        closed = true;
        if (pool != null) pool.close();
    }

    private static String setting(Properties properties, String property, String environment, String fallback) {
        String value = System.getenv(environment);
        if (value != null && !value.isBlank()) return value.trim();
        return properties.getProperty(property, fallback).trim();
    }

    private static Properties loadProperties() throws Exception {
        Properties properties = new Properties();
        LinkedHashSet<Path> directories = new LinkedHashSet<>();
        addAncestors(directories, Path.of("").toAbsolutePath().normalize());
        try {
            Path code = Path.of(ConexionBaseDatos.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            addAncestors(directories, Files.isDirectory(code) ? code : code.getParent());
        } catch (Exception ignored) {
            // En algunos lanzadores la ubicación del código no está expuesta; se conserva la búsqueda por cwd.
        }
        for (Path directory : directories) {
            Path config = directory.resolve("config.properties");
            if (Files.isRegularFile(config)) {
                try (var in = Files.newInputStream(config)) { properties.load(in); }
                properties.setProperty("config.base", directory.toString());
                break;
            }
        }
        return properties;
    }

    private static void addAncestors(Set<Path> directories, Path start) {
        for (Path path = start; path != null; path = path.getParent()) directories.add(path);
    }

    public void initialize() throws Exception {
        if (!demo) return; // La base real se prepara explícitamente con los scripts SQL.
        synchronized (DEMO_INITIALIZATION_LOCK) {
            try (var c = open();
                    var in = ConexionBaseDatos.class.getResourceAsStream("/db/demo.sql")) {
                // H2 confirma DDL por separado: un inicio interrumpido puede dejar tablas parciales.
                // El script agrega únicamente lo que falta, incluso si ya existe schema_version.
                if (in == null) throw new IOException("Falta db/demo.sql");
                String sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                for (String statement : sql.split(";"))
                    if (!statement.isBlank())
                        try (var s = c.createStatement()) {
                            s.execute(statement);
                        }
            }
        }
    }

    public interface Work<T> {
        T run(Connection c) throws Exception;
    }

    public <T> T transaction(Work<T> work) {
        try (var c = open()) {
            c.setAutoCommit(false);
            try {
                T value = work.run(c);
                c.commit();
                return value;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo guardar la operación. Verificá la conexión y los datos.", e);
        }
    }
}
