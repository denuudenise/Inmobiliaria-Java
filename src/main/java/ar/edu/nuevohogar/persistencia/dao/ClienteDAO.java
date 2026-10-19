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

    public List<Cliente> listAssignedToAgent(int agentId) {
        return list(agentId, true);
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
