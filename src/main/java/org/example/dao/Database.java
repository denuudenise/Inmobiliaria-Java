package org.example.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Conexión JDBC para la base inmobiliaria local. Se puede sobrescribir con variables de entorno. */
public final class Database {
    private static final String URL = setting("INMOBILIARIA_DB_URL", "jdbc:mariadb://localhost:3306/inmobiliaria");
    private static final String USER = setting("INMOBILIARIA_DB_USER", "root");
    private static final String PASSWORD = setting("INMOBILIARIA_DB_PASSWORD", "");

    private Database() { }

    public static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private static String setting(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
