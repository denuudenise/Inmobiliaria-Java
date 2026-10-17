package ar.edu.nuevohogar.dao;

import ar.edu.nuevohogar.model.Row;

import java.sql.*;
import java.util.*;

/** Única capa que ejecuta SQL. Todos los valores se envían con parámetros. */
public final class JdbcDao {
    private final Database db;

    public JdbcDao(Database db) {
        this.db = db;
    }

    public List<Row> list(String sql, Object... args) {
        try (var c = db.open()) {
            return list(c, sql, args);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar la base de datos.", e);
        }
    }

    public static List<Row> list(Connection c, String sql, Object... args) throws SQLException {
        try (var p = prepare(c, sql, args);
                var rs = p.executeQuery()) {
            List<Row> rows = new ArrayList<>();
            var md = rs.getMetaData();
            while (rs.next()) {
                Row row = new Row();
                for (int i = 1; i <= md.getColumnCount(); i++)
                    row.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
                rows.add(row);
            }
            return rows;
        }
    }

    private static PreparedStatement prepare(Connection c, String sql, Object... args)
            throws SQLException {
        var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
        return p;
    }

    public static int exec(Connection c, String sql, Object... args) throws SQLException {
        try (var p = prepare(c, sql, args)) {
            return p.executeUpdate();
        }
    }

    public static int insert(Connection c, String sql, Object... args) throws SQLException {
        try (var p = prepare(c, sql, args)) {
            p.executeUpdate();
            try (var rs = p.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("No se generó el identificador");
                return rs.getInt(1);
            }
        }
    }

    public Row one(String sql, Object... args) {
        return list(sql, args).stream()
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("El registro ya no está disponible."));
    }
}
