package ar.edu.nuevohogar.dao;

import ar.edu.nuevohogar.model.Row;
import ar.edu.nuevohogar.model.Usuario;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Consultas de persistencia de la entidad Usuario. */
public final class UsuarioDao {
    private final JdbcDao jdbc;

    public UsuarioDao(Database database) {
        jdbc = new JdbcDao(database);
    }

    public Optional<Usuario> findActiveByEmail(String email) {
        return jdbc.list(
                        "SELECT * FROM usuarios WHERE LOWER(email)=? AND activo=1",
                        email.trim().toLowerCase(Locale.ROOT))
                .stream()
                .findFirst()
                .map(Usuario::from);
    }

    public Optional<Usuario> findActiveById(int id) {
        return jdbc.list("SELECT * FROM usuarios WHERE id_usuario=? AND activo=1", id)
                .stream()
                .findFirst()
                .map(Usuario::from);
    }

    public static int insert(
            Connection connection,
            String nombre,
            String apellido,
            String dni,
            String telefono,
            String email,
            String passwordHash,
            int roleId,
            String direccion,
            String localidad,
            String fechaNacimiento,
            String genero,
            String tipoDocumento)
            throws SQLException {
        return JdbcDao.insert(
                connection,
                "INSERT INTO usuarios(nombre,apellido,dni,telefono,email,password_hash,id_rol,"
                    + "direccion,localidad,fecha_nacimiento,genero,tipo_documento)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                nombre,
                apellido,
                dni,
                telefono,
                email,
                passwordHash,
                roleId,
                direccion,
                localidad,
                fechaNacimiento,
                genero,
                tipoDocumento);
    }

    public static void updateProfile(
            Connection connection,
            int id,
            String nombre,
            String apellido,
            String telefono,
            String direccion,
            String localidad)
            throws SQLException {
        JdbcDao.exec(
                connection,
                "UPDATE usuarios SET nombre=?,apellido=?,telefono=?,direccion=?,localidad=?"
                    + " WHERE id_usuario=?",
                nombre,
                apellido,
                telefono,
                direccion,
                localidad,
                id);
    }

    public List<Row> findAllWithRole() {
        return jdbc.list(
                "SELECT u.id_usuario AS id,CASE WHEN u.id_rol=1 THEN 'Admin' ELSE"
                    + " CONCAT(u.nombre,' ',u.apellido) END AS etiqueta,"
                    + "u.email,r.nombre AS rol,u.activo FROM usuarios u JOIN roles r ON"
                    + " r.id_rol=u.id_rol ORDER BY u.apellido");
    }
}
