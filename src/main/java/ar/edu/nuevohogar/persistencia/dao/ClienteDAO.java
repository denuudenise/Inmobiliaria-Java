package ar.edu.nuevohogar.persistencia.dao;

import ar.edu.nuevohogar.modelo.Cliente;
import ar.edu.nuevohogar.modelo.Registro;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Consultas de persistencia de las fichas de cliente. */
public final class ClienteDAO {
    private final ConsultasSQL jdbc;

    public ClienteDAO(ConexionBaseDatos database) {
        jdbc = new ConsultasSQL(database);
    }

    public List<Cliente> listForAdministrator() {
        return list(0, false);
    }

    public List<Cliente> listRelatedToAgent(int agentId) {
        String sql = "SELECT u.id_usuario AS id,CONCAT(u.nombre,' ',u.apellido) AS etiqueta,"
                + "u.dni,u.email,u.telefono,c.fecha_inicio_relacion,c.id_agente_asignado "
                + "FROM usuarios u JOIN clientes c ON c.id_cliente=u.id_usuario WHERE "
                + "c.id_agente_asignado=? OR EXISTS(SELECT 1 FROM contactos ct WHERE ct.id_cliente=u.id_usuario AND ct.id_agente=?) "
                + "OR EXISTS(SELECT 1 FROM visitas v WHERE v.id_cliente=u.id_usuario AND v.id_agente=?) "
                + "OR EXISTS(SELECT 1 FROM tareas_seguimiento t WHERE t.id_cliente=u.id_usuario AND t.id_agente=?) "
                + "OR EXISTS(SELECT 1 FROM solicitudes_publicacion s WHERE s.id_cliente=u.id_usuario AND s.id_agente=?) "
                + "OR EXISTS(SELECT 1 FROM ofertas_cliente o WHERE o.id_cliente=u.id_usuario AND o.id_agente=?) "
                + "OR EXISTS(SELECT 1 FROM propiedades p WHERE p.id_propietario=u.id_usuario AND " + PropiedadDAO.responsibleAgentSql("p") + "=?) "
                + "OR EXISTS(SELECT 1 FROM intereses i JOIN propiedades p ON p.id_propiedad=i.id_propiedad WHERE i.id_cliente=u.id_usuario AND " + PropiedadDAO.responsibleAgentSql("p") + "=?) "
                + "ORDER BY u.apellido";
        return jdbc.list(sql, agentId, agentId, agentId, agentId, agentId, agentId, agentId, agentId)
                .stream().map(Cliente::new).toList();
    }

    public static void insert(Connection connection, int clientId, Integer agentId)
            throws SQLException {
        ConsultasSQL.exec(
                connection,
                "INSERT INTO clientes(id_cliente,fecha_inicio_relacion,id_agente_asignado)"
                    + " VALUES(?,CURRENT_DATE,?)",
                clientId,
                agentId);
    }

    private List<Cliente> list(int agentId, boolean filterAgent) {
        String sql =
                "SELECT u.id_usuario AS id,CONCAT(u.nombre,' ',u.apellido) AS etiqueta,"
                    + "u.dni,u.email,u.telefono,c.fecha_inicio_relacion,c.id_agente_asignado "
                    + "FROM usuarios u JOIN clientes c ON c.id_cliente=u.id_usuario "
                    + (filterAgent ? "WHERE c.id_agente_asignado=? " : "")
                    + "ORDER BY u.apellido";
        List<Registro> rows = filterAgent ? jdbc.list(sql, agentId) : jdbc.list(sql);
        return rows.stream().map(Cliente::new).toList();
    }
}
