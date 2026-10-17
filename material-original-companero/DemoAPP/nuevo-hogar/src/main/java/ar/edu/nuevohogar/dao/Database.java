package ar.edu.nuevohogar.dao;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;

public final class Database {
    private final String url, user, password;
    public final boolean demo;
    public final Path storage;

    public Database() throws Exception {
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
    }

    public Database(String url, Path storage) {
        this.url = url;
        user = "sa";
        password = "";
        demo = true;
        this.storage = storage.toAbsolutePath().normalize();
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public void initialize() throws Exception {
        if (!demo) return; // La base real se prepara explícitamente con los scripts SQL.
        try (var c = open();
                var in = Database.class.getResourceAsStream("/db/demo.sql")) {
            try (var rs = c.getMetaData().getTables(null, null, "schema_version", null)) {
                if (rs.next()) return;
            }
            if (in == null) throw new IOException("Falta db/demo.sql");
            String sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (String statement : sql.split(";"))
                if (!statement.isBlank())
                    try (var s = c.createStatement()) {
                        s.execute(statement);
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
