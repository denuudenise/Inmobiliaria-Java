package ar.edu.nuevohogar.dao;

import ar.edu.nuevohogar.model.AdminDashboardData;
import ar.edu.nuevohogar.model.Row;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consultas agrupadas y listados recientes del tablero de administración. */
public final class AdminDashboardDao {
    private final JdbcDao jdbc;

    public AdminDashboardDao(Database database) {
        jdbc = new JdbcDao(database);
    }

    public AdminDashboardData load() {
        List<Row> propertyStates = jdbc.list(
                "SELECT e.nombre AS etiqueta,COUNT(p.id_propiedad) AS cantidad"
                    + " FROM estados_verificacion e LEFT JOIN propiedades p ON"
                    + " p.id_estado_verificacion=e.id_estado_verificacion"
                    + " GROUP BY e.id_estado_verificacion,e.nombre ORDER BY e.id_estado_verificacion");
        List<Row> propertyTypes = jdbc.list(
                "SELECT t.nombre AS etiqueta,COUNT(p.id_propiedad) AS cantidad"
                    + " FROM tipos_propiedad t LEFT JOIN propiedades p ON"
                    + " p.id_tipo_propiedad=t.id_tipo_propiedad"
                    + " GROUP BY t.id_tipo_propiedad,t.nombre ORDER BY cantidad DESC,t.nombre");
        List<Row> requestStates = jdbc.list(
                "SELECT estado AS etiqueta,COUNT(*) AS cantidad FROM publicaciones"
                    + " GROUP BY estado ORDER BY cantidad DESC,estado");
        List<Row> latestRequests = jdbc.list(
                "SELECT s.id_publicacion,s.id_propiedad,p.titulo,p.localidad,s.estado,"
                    + "s.fecha_solicitud AS fecha,"
                    + "CASE WHEN u.id_rol=1 THEN 'Admin' ELSE CONCAT(u.nombre,' ',u.apellido) END AS solicitante"
                    + " FROM publicaciones s JOIN propiedades p ON p.id_propiedad=s.id_propiedad"
                    + " JOIN usuarios u ON u.id_usuario=s.id_solicitante"
                    + " ORDER BY s.fecha_solicitud DESC,s.id_publicacion DESC LIMIT 6");
        List<Row> latestProperties = jdbc.list(
                "SELECT p.id_propiedad,p.titulo,p.localidad,e.nombre AS verificacion,"
                    + "c.nombre AS comercial,CASE WHEN u.id_rol=1 THEN 'Admin' ELSE"
                    + " CONCAT_WS(' ',u.nombre,u.apellido) END AS propietario"
                    + " FROM propiedades p JOIN estados_verificacion e ON"
                    + " e.id_estado_verificacion=p.id_estado_verificacion JOIN estados_comerciales c"
                    + " ON c.id_estado_comercial=p.id_estado_comercial LEFT JOIN usuarios u ON"
                    + " u.id_usuario=p.id_propietario ORDER BY p.fecha_creacion DESC,p.id_propiedad DESC LIMIT 5");
        List<Row> agents = jdbc.list(
                "SELECT u.id_usuario,CONCAT(u.nombre,' ',u.apellido) AS agente,"
                    + "COUNT(DISTINCT c.id_cliente) AS clientes,"
                    + "COUNT(DISTINCT p.id_propiedad) AS propiedades"
                    + " FROM usuarios u LEFT JOIN clientes c ON c.id_agente_asignado=u.id_usuario"
                    + " LEFT JOIN propiedades p ON p.id_agente_creador=u.id_usuario"
                    + " WHERE u.id_rol=2 AND u.activo=1 GROUP BY u.id_usuario,u.nombre,u.apellido"
                    + " ORDER BY clientes DESC,propiedades DESC,u.apellido LIMIT 5");
        List<Row> activity = jdbc.list(
                "SELECT a.accion,a.entidad,a.id_registro,a.detalle,"
                    + "a.fecha AS fecha,"
                    + "CASE WHEN u.id_rol=1 THEN 'Admin' ELSE CONCAT(u.nombre,' ',u.apellido) END AS usuario"
                    + " FROM auditoria a JOIN usuarios u ON u.id_usuario=a.id_usuario"
                    + " ORDER BY a.fecha DESC,a.id_evento DESC LIMIT 6");

        return new AdminDashboardData(
                count("SELECT COUNT(*) AS cantidad FROM clientes"),
                count("SELECT COUNT(*) AS cantidad FROM usuarios WHERE id_rol=2 AND activo=1"),
                count("SELECT COUNT(*) AS cantidad FROM usuarios WHERE activo=1"),
                count("SELECT COUNT(*) AS cantidad FROM propiedades"),
                count("SELECT COUNT(*) AS cantidad FROM propiedades WHERE id_estado_verificacion=3"),
                count("SELECT COUNT(*) AS cantidad FROM propiedades WHERE id_estado_verificacion=2"),
                count("SELECT COUNT(*) AS cantidad FROM propiedades WHERE id_estado_verificacion=4"),
                count("SELECT COUNT(*) AS cantidad FROM propiedades WHERE id_estado_comercial=3"),
                count("SELECT COUNT(*) AS cantidad FROM propiedades WHERE id_estado_comercial=4"),
                count("SELECT COUNT(*) AS cantidad FROM publicaciones WHERE estado='Pendiente'"),
                count("SELECT COUNT(*) AS cantidad FROM propiedades_cerradas"),
                asMap(propertyStates),
                asMap(propertyTypes),
                asMap(requestStates),
                latestRequests,
                latestProperties,
                agents,
                activity);
    }

    public List<Row> listAgents() {
        return jdbc.list(
                "SELECT u.id_usuario,CONCAT(u.nombre,' ',u.apellido) AS agente,u.email,u.telefono,"
                    + "u.activo,COUNT(DISTINCT c.id_cliente) AS clientes,"
                    + "COUNT(DISTINCT p.id_propiedad) AS propiedades"
                    + " FROM usuarios u LEFT JOIN clientes c ON c.id_agente_asignado=u.id_usuario"
                    + " LEFT JOIN propiedades p ON p.id_agente_creador=u.id_usuario"
                    + " WHERE u.id_rol=2 GROUP BY u.id_usuario,u.nombre,u.apellido,u.email,u.telefono,u.activo"
                    + " ORDER BY u.activo DESC,u.apellido,u.nombre");
    }

    private long count(String sql) {
        return jdbc.list(sql).getFirst().id("cantidad");
    }

    private static Map<String, Long> asMap(List<Row> rows) {
        Map<String, Long> values = new LinkedHashMap<>();
        for (Row row : rows) values.put(row.text("etiqueta"), (long) row.id("cantidad"));
        return values;
    }
}
