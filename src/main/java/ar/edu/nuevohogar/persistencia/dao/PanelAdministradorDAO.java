package ar.edu.nuevohogar.persistencia.dao;

import ar.edu.nuevohogar.modelo.DatosPanelAdministrador;
import ar.edu.nuevohogar.modelo.Registro;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consultas agrupadas y listados recientes del tablero de administración. */
public final class PanelAdministradorDAO {
    private final ConexionBaseDatos database;

    public PanelAdministradorDAO(ConexionBaseDatos database) {
        this.database = database;
    }

    public DatosPanelAdministrador load() {
        try (var c = database.open()) {
        List<Registro> propertyStates = ConsultasSQL.list(c,
                "SELECT e.nombre AS etiqueta,COUNT(p.id_propiedad) AS cantidad"
                    + " FROM estados_verificacion e LEFT JOIN propiedades p ON"
                    + " p.id_estado_verificacion=e.id_estado_verificacion"
                    + " GROUP BY e.id_estado_verificacion,e.nombre ORDER BY e.id_estado_verificacion");
        List<Registro> propertyTypes = ConsultasSQL.list(c,
                "SELECT t.nombre AS etiqueta,COUNT(p.id_propiedad) AS cantidad"
                    + " FROM tipos_propiedad t LEFT JOIN propiedades p ON"
                    + " p.id_tipo_propiedad=t.id_tipo_propiedad"
                    + " GROUP BY t.id_tipo_propiedad,t.nombre ORDER BY cantidad DESC,t.nombre");
        List<Registro> requestStates = ConsultasSQL.list(c,
                "SELECT estado AS etiqueta,COUNT(*) AS cantidad FROM publicaciones"
                    + " GROUP BY estado ORDER BY cantidad DESC,estado");
        List<Registro> latestRequests = ConsultasSQL.list(c,
                "SELECT s.id_publicacion,s.id_propiedad,p.titulo,p.localidad,s.estado,"
                    + "s.fecha_solicitud AS fecha,"
                    + "CASE WHEN u.id_rol=1 THEN 'Admin' ELSE CONCAT(u.nombre,' ',u.apellido) END AS solicitante"
                    + " FROM publicaciones s JOIN propiedades p ON p.id_propiedad=s.id_propiedad"
                    + " JOIN usuarios u ON u.id_usuario=s.id_solicitante"
                    + " ORDER BY s.fecha_solicitud DESC,s.id_publicacion DESC LIMIT 6");
        List<Registro> latestProperties = ConsultasSQL.list(c,
                "SELECT p.id_propiedad,p.titulo,p.localidad,e.nombre AS verificacion,"
                    + "c.nombre AS comercial,CASE WHEN u.id_rol=1 THEN 'Admin' ELSE"
                    + " CONCAT_WS(' ',u.nombre,u.apellido) END AS propietario"
                    + " FROM propiedades p JOIN estados_verificacion e ON"
                    + " e.id_estado_verificacion=p.id_estado_verificacion JOIN estados_comerciales c"
                    + " ON c.id_estado_comercial=p.id_estado_comercial LEFT JOIN usuarios u ON"
                    + " u.id_usuario=p.id_propietario ORDER BY p.fecha_creacion DESC,p.id_propiedad DESC LIMIT 5");
        List<Registro> agents = ConsultasSQL.list(c,
                "SELECT u.id_usuario,CONCAT(u.nombre,' ',u.apellido) AS agente,"
                    + "COUNT(DISTINCT c.id_cliente) AS clientes,"
                    + "COUNT(DISTINCT p.id_propiedad) AS propiedades"
                    + " FROM usuarios u LEFT JOIN clientes c ON (c.id_agente_asignado=u.id_usuario"
                    + " OR EXISTS(SELECT 1 FROM contactos ct WHERE ct.id_cliente=c.id_cliente AND ct.id_agente=u.id_usuario)"
                    + " OR EXISTS(SELECT 1 FROM visitas v WHERE v.id_cliente=c.id_cliente AND v.id_agente=u.id_usuario)"
                    + " OR EXISTS(SELECT 1 FROM tareas_seguimiento st WHERE st.id_cliente=c.id_cliente AND st.id_agente=u.id_usuario)"
                    + " OR EXISTS(SELECT 1 FROM solicitudes_publicacion sp WHERE sp.id_cliente=c.id_cliente AND sp.id_agente=u.id_usuario)"
                    + " OR EXISTS(SELECT 1 FROM ofertas_cliente o WHERE o.id_cliente=c.id_cliente AND o.id_agente=u.id_usuario)"
                    + " OR EXISTS(SELECT 1 FROM intereses i JOIN propiedades ip ON ip.id_propiedad=i.id_propiedad WHERE i.id_cliente=c.id_cliente AND " + PropiedadDAO.responsibleAgentSql("ip") + "=u.id_usuario)"
                    + " OR EXISTS(SELECT 1 FROM propiedades own WHERE own.id_propietario=c.id_cliente AND " + PropiedadDAO.responsibleAgentSql("own") + "=u.id_usuario))"
                    + " LEFT JOIN propiedades p ON " + PropiedadDAO.responsibleAgentSql("p") + "=u.id_usuario"
                    + " WHERE u.id_rol=2 AND u.activo=1 GROUP BY u.id_usuario,u.nombre,u.apellido"
                    + " ORDER BY clientes DESC,propiedades DESC,u.apellido LIMIT 5");
        List<Registro> activity = ConsultasSQL.list(c,
                "SELECT a.accion,a.entidad,a.id_registro,a.detalle,"
                    + "a.fecha AS fecha,"
                    + "CASE WHEN u.id_rol=1 THEN 'Admin' ELSE CONCAT(u.nombre,' ',u.apellido) END AS usuario"
                    + " FROM auditoria a JOIN usuarios u ON u.id_usuario=a.id_usuario"
                    + " ORDER BY a.fecha DESC,a.id_evento DESC LIMIT 6");

        // Un viaje a la base para los trece contadores del panel.
        Registro counts = ConsultasSQL.list(c, "SELECT (SELECT COUNT(*) FROM clientes) AS n0,(SELECT COUNT(*) FROM usuarios WHERE id_rol=2 AND activo=1) AS n1,(SELECT COUNT(*) FROM usuarios WHERE activo=1) AS n2,(SELECT COUNT(*) FROM propiedades) AS n3,(SELECT COUNT(*) FROM propiedades WHERE id_estado_verificacion=3) AS n4,(SELECT COUNT(*) FROM propiedades WHERE id_estado_verificacion=2) AS n5,(SELECT COUNT(*) FROM propiedades WHERE id_estado_verificacion=4) AS n6,(SELECT COUNT(*) FROM propiedades WHERE id_estado_comercial=3) AS n7,(SELECT COUNT(*) FROM propiedades WHERE id_estado_comercial=4) AS n8,(SELECT COUNT(*) FROM publicaciones WHERE estado='Pendiente') AS n9,(SELECT COUNT(*) FROM propiedades_cerradas) AS n10,(SELECT COUNT(*) FROM solicitudes_publicacion WHERE id_estado_verificacion=2) AS n11,(SELECT COUNT(*) FROM contactos WHERE tipo='Consulta' AND estado='Pendiente') AS n12").getFirst();
        return new DatosPanelAdministrador(
                ((Number) counts.get("n0")).longValue(),
                ((Number) counts.get("n1")).longValue(),
                ((Number) counts.get("n2")).longValue(),
                ((Number) counts.get("n3")).longValue(),
                ((Number) counts.get("n4")).longValue(),
                ((Number) counts.get("n5")).longValue(),
                ((Number) counts.get("n6")).longValue(),
                ((Number) counts.get("n7")).longValue(),
                ((Number) counts.get("n8")).longValue(),
                ((Number) counts.get("n9")).longValue(),
                ((Number) counts.get("n10")).longValue(),
                ((Number) counts.get("n11")).longValue(),
                ((Number) counts.get("n12")).longValue(),
                asMap(propertyStates),
                asMap(propertyTypes),
                asMap(requestStates),
                latestRequests,
                latestProperties,
                agents,
                activity);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("No se pudo cargar el resumen de administración.", e);
        }
    }

    public List<Registro> listAgents() {
        try (var c = database.open()) { return ConsultasSQL.list(c,
                "SELECT u.id_usuario,CONCAT(u.nombre,' ',u.apellido) AS agente,u.email,u.telefono,"
                    + "u.activo,COUNT(DISTINCT c.id_cliente) AS clientes,"
                    + "COUNT(DISTINCT p.id_propiedad) AS propiedades"
                    + " FROM usuarios u LEFT JOIN clientes c ON (c.id_agente_asignado=u.id_usuario"
                    + " OR EXISTS(SELECT 1 FROM contactos ct WHERE ct.id_cliente=c.id_cliente AND ct.id_agente=u.id_usuario)"
                    + " OR EXISTS(SELECT 1 FROM visitas v WHERE v.id_cliente=c.id_cliente AND v.id_agente=u.id_usuario)"
                    + " OR EXISTS(SELECT 1 FROM tareas_seguimiento st WHERE st.id_cliente=c.id_cliente AND st.id_agente=u.id_usuario)"
                    + " OR EXISTS(SELECT 1 FROM solicitudes_publicacion sp WHERE sp.id_cliente=c.id_cliente AND sp.id_agente=u.id_usuario)"
                    + " OR EXISTS(SELECT 1 FROM ofertas_cliente o WHERE o.id_cliente=c.id_cliente AND o.id_agente=u.id_usuario)"
                    + " OR EXISTS(SELECT 1 FROM intereses i JOIN propiedades ip ON ip.id_propiedad=i.id_propiedad WHERE i.id_cliente=c.id_cliente AND " + PropiedadDAO.responsibleAgentSql("ip") + "=u.id_usuario)"
                    + " OR EXISTS(SELECT 1 FROM propiedades own WHERE own.id_propietario=c.id_cliente AND " + PropiedadDAO.responsibleAgentSql("own") + "=u.id_usuario))"
                    + " LEFT JOIN propiedades p ON " + PropiedadDAO.responsibleAgentSql("p") + "=u.id_usuario"
                    + " WHERE u.id_rol=2 GROUP BY u.id_usuario,u.nombre,u.apellido,u.email,u.telefono,u.activo"
                    + " ORDER BY u.activo DESC,u.apellido,u.nombre");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("No se pudo cargar el equipo de agentes.", e);
        }
    }


    private static Map<String, Long> asMap(List<Registro> rows) {
        Map<String, Long> values = new LinkedHashMap<>();
        for (Registro row : rows) values.put(row.text("etiqueta"), (long) row.id("cantidad"));
        return values;
    }
}
