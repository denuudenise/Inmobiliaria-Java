package ar.edu.nuevohogar.persistencia.dao;

import ar.edu.nuevohogar.modelo.Registro;
import java.util.List;

/** Acceso a las solicitudes de publicación de propietarios de la versión web. */
public final class SolicitudPropiedadDAO {
    private final ConsultasSQL jdbc;

    public SolicitudPropiedadDAO(ConexionBaseDatos database) {
        jdbc = new ConsultasSQL(database);
    }

    public List<Registro> list(int role, int user) {
        String scope = switch (role) {
            case 2 -> "WHERE s.id_agente=? OR (s.id_agente IS NULL AND s.id_estado_verificacion=2) ";
            case 3 -> "WHERE s.id_cliente=? ";
            default -> "";
        };
        Object[] args = role == 2 || role == 3 ? new Object[] {user} : new Object[] {};
        return jdbc.list("SELECT s.*,u.nombre AS cliente_nombre,u.apellido AS cliente_apellido,"
                + "u.email AS cliente_email,tp.nombre AS tipo,tt.nombre AS operacion,ev.nombre AS estado_nombre,"
                + "CONCAT(a.nombre,' ',a.apellido) AS agente FROM solicitudes_publicacion s "
                + "JOIN usuarios u ON u.id_usuario=s.id_cliente "
                + "JOIN tipos_propiedad tp ON tp.id_tipo_propiedad=s.id_tipo_propiedad "
                + "JOIN tipos_transaccion tt ON tt.id_tipo_transaccion=s.id_tipo_transaccion "
                + "JOIN estados_verificacion ev ON ev.id_estado_verificacion=s.id_estado_verificacion "
                + "LEFT JOIN usuarios a ON a.id_usuario=s.id_agente " + scope
                + "ORDER BY CASE WHEN s.id_estado_verificacion=2 THEN 0 ELSE 1 END,s.fecha_solicitud DESC,s.id_solicitud DESC", args);
    }

    public Registro find(int id) {
        return jdbc.list("SELECT s.*,u.nombre AS cliente_nombre,u.apellido AS cliente_apellido,"
                + "u.email AS cliente_email,tp.nombre AS tipo,tt.nombre AS operacion,ev.nombre AS estado_nombre,"
                + "CONCAT(a.nombre,' ',a.apellido) AS agente FROM solicitudes_publicacion s "
                + "JOIN usuarios u ON u.id_usuario=s.id_cliente "
                + "JOIN tipos_propiedad tp ON tp.id_tipo_propiedad=s.id_tipo_propiedad "
                + "JOIN tipos_transaccion tt ON tt.id_tipo_transaccion=s.id_tipo_transaccion "
                + "JOIN estados_verificacion ev ON ev.id_estado_verificacion=s.id_estado_verificacion "
                + "LEFT JOIN usuarios a ON a.id_usuario=s.id_agente WHERE s.id_solicitud=?", id)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("La solicitud no existe."));
    }

    public List<Registro> offers(int id) {
        return jdbc.list("SELECT o.*,t.nombre AS operacion FROM solicitud_ofertas o "
                + "JOIN tipos_transaccion t ON t.id_tipo_transaccion=o.id_tipo_transaccion "
                + "WHERE o.id_solicitud=? ORDER BY o.id_tipo_transaccion", id);
    }

    public List<Registro> features(int id) {
        return jdbc.list("SELECT c.id_caracteristica AS id,c.nombre AS etiqueta "
                + "FROM solicitud_caracteristicas sc JOIN caracteristicas c "
                + "ON c.id_caracteristica=sc.id_caracteristica WHERE sc.id_solicitud=? ORDER BY c.nombre", id);
    }

    public List<Registro> media(int id) {
        return jdbc.list("SELECT id_multimedia AS id,tipo,url_archivo AS archivo "
                + "FROM solicitud_multimedia WHERE id_solicitud=? ORDER BY fecha_carga,id_multimedia", id);
    }

    public List<Registro> documents(int id) {
        return jdbc.list("SELECT id_documento AS id,tipo,nombre,archivo,fecha FROM documentos "
                + "WHERE id_solicitud=? ORDER BY fecha,id_documento", id);
    }
}
