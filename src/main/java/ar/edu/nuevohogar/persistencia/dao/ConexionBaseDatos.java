package ar.edu.nuevohogar.persistencia.dao;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;

public final class ConexionBaseDatos {
    private static final Object DEMO_INITIALIZATION_LOCK = new Object();
    private final String url, user, password;
    public final boolean demo;
    public final Path storage;
    public final Path webMediaRoot;
    public final String webMediaPrefix;

    public ConexionBaseDatos() throws Exception {
        Properties p = new Properties();
        Path config = Path.of("config.properties");
        if (Files.exists(config))
            try (var in = Files.newInputStream(config)) {
                p.load(in);
            }
        url =
                p.getProperty(
                        "db.url", "jdbc:h2:./data/nuevo-hogar;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        user = p.getProperty("db.user", "sa");
        password = p.getProperty("db.password", "");
        demo = url.startsWith("jdbc:h2:");
        storage =
                Path.of(p.getProperty("storage.path", "data/archivos"))
                        .toAbsolutePath()
                        .normalize();
        Files.createDirectories(storage);
        String webRoot = p.getProperty("media.web.root", "").trim();
        webMediaRoot = webRoot.isEmpty() ? null : Path.of(webRoot).toAbsolutePath().normalize();
        String prefix = p.getProperty("media.web.prefix", "/Inmobiliaria/");
        webMediaPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        if (!webMediaPrefix.startsWith("/") || webMediaPrefix.equals("/"))
            throw new IllegalArgumentException("El prefijo multimedia debe identificar la carpeta de la web.");
    }

    public ConexionBaseDatos(String url, Path storage) {
        this.url = url;
        user = "sa";
        password = "";
        demo = true;
        this.storage = storage.toAbsolutePath().normalize();
        webMediaRoot = null;
        webMediaPrefix = "/Inmobiliaria/";
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
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
