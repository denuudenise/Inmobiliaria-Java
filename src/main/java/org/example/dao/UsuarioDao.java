package org.example.dao;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public class UsuarioDao {
    public record Usuario(int id, String nombre, String apellido, String email, String rol) {
        public String nombreCompleto() { return nombre + " " + apellido; }
    }

    public Usuario iniciarSesion(String email, String password) throws SQLException {
        String sql = "SELECT u.id_usuario, u.nombre, u.apellido, u.email, u.password_hash, u.activo, r.nombre AS rol " +
                "FROM usuarios u JOIN roles r ON r.id_rol = u.id_rol WHERE LOWER(u.email) = LOWER(?) LIMIT 1";
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email.trim());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next() || !rs.getBoolean("activo")) return null;
                String hash = rs.getString("password_hash");
                if (hash == null || !BCrypt.checkpw(password, hash)) return null;
                return new Usuario(rs.getInt("id_usuario"), rs.getString("nombre"),
                        rs.getString("apellido"), rs.getString("email"), rs.getString("rol"));
            }
        }
    }

    /** crea clientes. */
    public void registrarCliente(String nombre, String apellido, String dni, String telefono,
                                 String email, String password) throws SQLException {
        String sql = "INSERT INTO usuarios (nombre, apellido, dni, telefono, email, password_hash, id_rol, activo) " +
                "VALUES (?, ?, ?, ?, ?, ?, 3, 1)";
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nombre.trim());
            statement.setString(2, apellido.trim());
            statement.setString(3, dni.trim());
            statement.setString(4, telefono.trim().isEmpty() ? null : telefono.trim());
            statement.setString(5, email.trim().toLowerCase(Locale.ROOT));
            statement.setString(6, hash);
            statement.executeUpdate();
        }
    }
}
