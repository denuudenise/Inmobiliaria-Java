package ar.edu.nuevohogar.dao;

import ar.edu.nuevohogar.model.SolicitudPublicacion;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Consultas de persistencia de las solicitudes de publicación. */
public final class SolicitudDao {
    private final JdbcDao jdbc;

    public SolicitudDao(Database database) {
        jdbc = new JdbcDao(database);
    }

    public List<SolicitudPublicacion> listForRole(int roleId, int userId) {
        String scope =
                switch (roleId) {
                    case 2 -> "WHERE p.id_agente_creador=? OR EXISTS(SELECT 1 FROM clientes ca"
                            + " WHERE ca.id_cliente=p.id_propietario AND"
                            + " ca.id_agente_asignado=?) ";
                    case 3 -> "WHERE p.id_propietario=? ";
                    default -> "";
                };
        Object[] args =
                switch (roleId) {
                    case 2 -> new Object[] {userId, userId};
                    case 3 -> new Object[] {userId};
                    default -> new Object[] {};
                };
        return jdbc.list(
                        "SELECT s.*,p.titulo,p.id_propietario,"
                            + "CASE WHEN u.id_rol=1 THEN 'Admin' ELSE CONCAT(u.nombre,' ',u.apellido) END AS solicitante FROM publicaciones s"
                            + " JOIN propiedades p ON p.id_propiedad=s.id_propiedad JOIN usuarios u"
                            + " ON u.id_usuario=s.id_solicitante "
                            + scope
                            + "ORDER BY s.fecha_solicitud DESC,s.id_publicacion DESC",
                        args)
                .stream()
                .map(SolicitudPublicacion::new)
                .toList();
    }

    public static int insert(Connection connection, int propertyId, int requesterId)
            throws SQLException {
        return JdbcDao.insert(
                connection,
                "INSERT INTO publicaciones(id_propiedad,id_solicitante) VALUES(?,?)",
                propertyId,
                requesterId);
    }
}
