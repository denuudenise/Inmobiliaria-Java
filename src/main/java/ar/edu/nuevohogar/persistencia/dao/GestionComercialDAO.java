package ar.edu.nuevohogar.persistencia.dao;

import ar.edu.nuevohogar.modelo.Registro;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static ar.edu.nuevohogar.persistencia.dao.ConsultasSQL.*;

/** Acceso a visitas y recordatorios del seguimiento comercial. */
public final class GestionComercialDAO {
    private final ConsultasSQL consultas;
    public GestionComercialDAO(ConexionBaseDatos db) { consultas = new ConsultasSQL(db); }

    public List<Registro> visitasDeAgente(int agente) {
        return consultas.list("SELECT v.*,p.titulo AS propiedad,CONCAT(u.nombre,' ',u.apellido) AS cliente "
                + "FROM visitas v JOIN propiedades p ON p.id_propiedad=v.id_propiedad "
                + "JOIN usuarios u ON u.id_usuario=v.id_cliente WHERE v.id_agente=? "
                + "ORDER BY v.fecha_hora DESC", agente);
    }
    public List<Registro> visitasDeCliente(int cliente) {
        return consultas.list("SELECT v.*,p.titulo AS propiedad,CONCAT(a.nombre,' ',a.apellido) AS agente "
                + "FROM visitas v JOIN propiedades p ON p.id_propiedad=v.id_propiedad "
                + "JOIN usuarios a ON a.id_usuario=v.id_agente WHERE v.id_cliente=? "
                + "ORDER BY v.fecha_hora DESC", cliente);
    }
    public List<Registro> todasLasVisitas() {
        return consultas.list("SELECT v.*,p.titulo AS propiedad,CONCAT(c.nombre,' ',c.apellido) AS cliente,"
                + "CONCAT(a.nombre,' ',a.apellido) AS agente FROM visitas v "
                + "JOIN propiedades p ON p.id_propiedad=v.id_propiedad "
                + "JOIN usuarios c ON c.id_usuario=v.id_cliente JOIN usuarios a ON a.id_usuario=v.id_agente "
                + "ORDER BY v.fecha_hora DESC");
    }
    public int crearVisita(Connection c, int property, int client, int agent, LocalDateTime date, String notes) throws Exception {
        return insert(c, "INSERT INTO visitas(id_propiedad,id_cliente,id_agente,fecha_hora,observaciones) VALUES(?,?,?,?,?)",
                property, client, agent, date, notes);
    }
    public int cambiarEstadoVisita(Connection c, int id, String status, String result) throws Exception {
        return exec(c, "UPDATE visitas SET estado=?,resultado=? WHERE id_visita=?", status, result, id);
    }
    public List<Registro> tareas(int agente) {
        return consultas.list("SELECT t.*,CONCAT(u.nombre,' ',u.apellido) AS cliente,p.titulo AS propiedad "
                + "FROM tareas_seguimiento t JOIN usuarios u ON u.id_usuario=t.id_cliente "
                + "LEFT JOIN propiedades p ON p.id_propiedad=t.id_propiedad "
                + "WHERE t.id_agente=? ORDER BY CASE WHEN t.estado='Pendiente' THEN 0 ELSE 1 END,t.fecha_limite", agente);
    }
    public List<Registro> todasLasTareas() {
        return consultas.list("SELECT t.*,CONCAT(u.nombre,' ',u.apellido) AS cliente,p.titulo AS propiedad,"
                + "CONCAT(a.nombre,' ',a.apellido) AS agente FROM tareas_seguimiento t "
                + "JOIN usuarios u ON u.id_usuario=t.id_cliente JOIN usuarios a ON a.id_usuario=t.id_agente "
                + "LEFT JOIN propiedades p ON p.id_propiedad=t.id_propiedad ORDER BY t.fecha_limite");
    }
    public int crearTarea(Connection c, int agent, int client, Integer property, String title, String detail, LocalDateTime due) throws Exception {
        return insert(c, "INSERT INTO tareas_seguimiento(id_agente,id_cliente,id_propiedad,titulo,detalle,fecha_limite) VALUES(?,?,?,?,?,?)",
                agent, client, property, title, detail, due);
    }
    public int completarTarea(Connection c, int id, int agent, boolean admin) throws Exception {
        return exec(c, "UPDATE tareas_seguimiento SET estado='Completada',fecha_completada=NOW() WHERE id_tarea=? AND (?=1 OR id_agente=?) AND estado='Pendiente'",
                id, admin ? 1 : 0, agent);
    }

    public List<Registro> ofertasCliente(int cliente) {
        return consultas.list("SELECT o.*,p.titulo AS propiedad,t.nombre AS operacion,"
                + "CONCAT(a.nombre,' ',a.apellido) AS agente FROM ofertas_cliente o "
                + "JOIN propiedades p ON p.id_propiedad=o.id_propiedad "
                + "JOIN tipos_transaccion t ON t.id_tipo_transaccion=o.id_tipo_transaccion "
                + "JOIN usuarios a ON a.id_usuario=o.id_agente WHERE o.id_cliente=? ORDER BY o.fecha_creacion DESC", cliente);
    }
    public List<Registro> ofertasAgente(int agente) {
        return consultas.list("SELECT o.*,p.titulo AS propiedad,t.nombre AS operacion,"
                + "CONCAT(c.nombre,' ',c.apellido) AS cliente FROM ofertas_cliente o "
                + "JOIN propiedades p ON p.id_propiedad=o.id_propiedad "
                + "JOIN tipos_transaccion t ON t.id_tipo_transaccion=o.id_tipo_transaccion "
                + "JOIN usuarios c ON c.id_usuario=o.id_cliente WHERE o.id_agente=? ORDER BY o.fecha_creacion DESC", agente);
    }
    public List<Registro> todasLasOfertas() {
        return consultas.list("SELECT o.*,p.titulo AS propiedad,t.nombre AS operacion,"
                + "CONCAT(c.nombre,' ',c.apellido) AS cliente,CONCAT(a.nombre,' ',a.apellido) AS agente "
                + "FROM ofertas_cliente o JOIN propiedades p ON p.id_propiedad=o.id_propiedad "
                + "JOIN tipos_transaccion t ON t.id_tipo_transaccion=o.id_tipo_transaccion "
                + "JOIN usuarios c ON c.id_usuario=o.id_cliente JOIN usuarios a ON a.id_usuario=o.id_agente "
                + "ORDER BY o.fecha_creacion DESC");
    }
    public int crearOferta(Connection c, int property, int client, int agent, int operation,
            java.math.BigDecimal amount, String currency, java.time.LocalDate expiration, String notes) throws Exception {
        return insert(c, "INSERT INTO ofertas_cliente(id_propiedad,id_cliente,id_agente,id_tipo_transaccion,monto,moneda,fecha_vencimiento,observaciones) VALUES(?,?,?,?,?,?,?,?)",
                property, client, agent, operation, amount, currency, expiration, notes);
    }
    public int revisarOferta(Connection c, int offer, int reviewer, String status, String notes) throws Exception {
        return exec(c, "UPDATE ofertas_cliente SET estado=?,observaciones=?,id_revisor=?,fecha_revision=NOW() WHERE id_oferta=? AND estado='Pendiente'",
                status, notes, reviewer, offer);
    }
    public List<Registro> reservas() {
        return consultas.list("SELECT r.*,p.titulo AS propiedad,CONCAT(c.nombre,' ',c.apellido) AS cliente,"
                + "CONCAT(a.nombre,' ',a.apellido) AS agente FROM reservas r "
                + "JOIN propiedades p ON p.id_propiedad=r.id_propiedad "
                + "JOIN usuarios c ON c.id_usuario=r.id_cliente JOIN usuarios a ON a.id_usuario=r.id_agente "
                + "ORDER BY r.fecha_reserva DESC");
    }
    public List<Registro> reservasAgente(int agente) {
        return consultas.list("SELECT r.*,p.titulo AS propiedad,CONCAT(c.nombre,' ',c.apellido) AS cliente "
                + "FROM reservas r JOIN propiedades p ON p.id_propiedad=r.id_propiedad "
                + "JOIN usuarios c ON c.id_usuario=r.id_cliente WHERE r.id_agente=? ORDER BY r.fecha_reserva DESC", agente);
    }
    public List<Registro> reservasCliente(int cliente) {
        return consultas.list("SELECT r.*,p.titulo AS propiedad,CONCAT(a.nombre,' ',a.apellido) AS agente "
                + "FROM reservas r JOIN propiedades p ON p.id_propiedad=r.id_propiedad "
                + "JOIN usuarios a ON a.id_usuario=r.id_agente WHERE r.id_cliente=? ORDER BY r.fecha_reserva DESC", cliente);
    }
    public int crearReserva(Connection c, Registro offer, int agent, java.time.LocalDateTime due, String notes) throws Exception {
        return insert(c, "INSERT INTO reservas(id_propiedad,id_cliente,id_agente,id_tipo_transaccion,id_oferta,monto,moneda,fecha_limite,observaciones) VALUES(?,?,?,?,?,?,?,?,?)",
                offer.id("id_propiedad"), offer.id("id_cliente"), agent, offer.id("id_tipo_transaccion"),
                offer.id("id_oferta"), offer.money("monto"), offer.text("moneda"), due, notes);
    }
    public int resolverReserva(Connection c, int id, int reviewer, String status) throws Exception {
        return exec(c, "UPDATE reservas SET estado=?,id_autorizador=? WHERE id_reserva=? AND estado='Pendiente'",
                status, reviewer, id);
    }
    public List<Registro> checklist(int property) {
        return consultas.list("SELECT r.id_requisito,r.nombre,r.obligatorio,COALESCE(ch.estado,'Pendiente') AS estado,"
                + "ch.observaciones,ch.id_documento,d.nombre AS documento FROM requisitos_publicacion r "
                + "LEFT JOIN checklist_publicacion ch ON ch.id_requisito=r.id_requisito AND ch.id_propiedad=? "
                + "LEFT JOIN documentos d ON d.id_documento=ch.id_documento "
                + "WHERE r.activo=1 AND (r.id_tipo_propiedad IS NULL OR r.id_tipo_propiedad=(SELECT p.id_tipo_propiedad FROM propiedades p WHERE p.id_propiedad=?)) "
                + "ORDER BY r.obligatorio DESC,r.nombre", property, property);
    }
    public int actualizarChecklist(Connection c, int property, int requirement, Integer document, String status, String notes, int reviewer) throws Exception {
        return exec(c, "UPDATE checklist_publicacion SET estado=?,observaciones=?,id_documento=?,id_revisor=?,fecha_revision=NOW() WHERE id_propiedad=? AND id_requisito=?",
                status, notes, document, reviewer, property, requirement);
    }
}
