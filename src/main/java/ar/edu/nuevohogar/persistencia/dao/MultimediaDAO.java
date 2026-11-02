package ar.edu.nuevohogar.persistencia.dao;

import ar.edu.nuevohogar.modelo.Registro;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Persistencia de las referencias a fotos y videos de propiedades. */
public final class MultimediaDAO {
    private final ConsultasSQL jdbc;

    public MultimediaDAO(ConexionBaseDatos database) {
        jdbc = new ConsultasSQL(database);
    }

    public List<Registro> listar(int propiedad) {
        return jdbc.list("SELECT id_multimedia AS id,tipo,url_archivo AS archivo FROM"
                + " propiedad_multimedia WHERE id_propiedad=? ORDER BY orden,id_multimedia", propiedad);
    }

    public List<Registro> listar(Connection connection, int propiedad) throws SQLException {
        return ConsultasSQL.list(connection,
                "SELECT id_multimedia AS id,tipo,url_archivo AS archivo FROM propiedad_multimedia"
                        + " WHERE id_propiedad=? ORDER BY orden,id_multimedia",
                propiedad);
    }

    public int insertar(Connection connection, int propiedad, String url, String tipo, int orden)
            throws SQLException {
        return ConsultasSQL.insert(connection,
                "INSERT INTO propiedad_multimedia(id_propiedad,url_archivo,tipo,orden) VALUES(?,?,?,?)",
                propiedad, url, tipo, orden);
    }
}
