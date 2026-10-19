package ar.edu.nuevohogar.persistencia.dao;

import ar.edu.nuevohogar.modelo.Propiedad;
import ar.edu.nuevohogar.modelo.Registro;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Consultas de lectura y búsqueda de propiedades. */
public final class PropiedadDAO {
    private static final String SELECT =
            "SELECT p.*,t.nombre AS tipo,e.nombre AS verificacion,c.nombre AS comercial,"
                + "CASE WHEN u.id_rol=1 THEN 'Admin' ELSE CONCAT(u.nombre,' ',u.apellido) END AS propietario FROM propiedades p "
                + "JOIN tipos_propiedad t ON t.id_tipo_propiedad=p.id_tipo_propiedad "
                + "JOIN estados_verificacion e ON e.id_estado_verificacion=p.id_estado_verificacion "
                + "JOIN estados_comerciales c ON c.id_estado_comercial=p.id_estado_comercial "
                + "LEFT JOIN usuarios u ON u.id_usuario=p.id_propietario ";

    private final ConsultasSQL jdbc;

    public PropiedadDAO(ConexionBaseDatos database) {
        jdbc = new ConsultasSQL(database);
    }

    public List<Propiedad> search(
            boolean catalog,
            String query,
            Integer type,
            Integer operation,
            BigDecimal max,
            String currency,
            int bedrooms,
            int capacity,
            Set<Integer> features,
            int role,
            int actor) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (catalog) {
            sql.append(" AND p.id_estado_verificacion=3 AND p.id_estado_comercial IN (1,2)");
        } else if (role == 3) {
            sql.append(" AND p.id_propietario=?");
            args.add(actor);
        } else if (role == 2) {
            sql.append(
                    " AND (p.id_agente_creador=? OR EXISTS(SELECT 1 FROM clientes ca"
                        + " WHERE ca.id_cliente=p.id_propietario AND ca.id_agente_asignado=?))");
            args.add(actor);
            args.add(actor);
        }
        if (query != null && !query.isBlank()) {
            sql.append(
                    " AND (LOWER(p.titulo) LIKE ? OR LOWER(p.localidad) LIKE ? OR"
                        + " LOWER(p.direccion) LIKE ?)");
            String like = "%" + query.trim().toLowerCase() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (type != null) {
            sql.append(" AND p.id_tipo_propiedad=?");
            args.add(type);
        }
        if (bedrooms > 0) {
            sql.append(" AND p.dormitorios>=?");
            args.add(bedrooms);
        }
        if (capacity > 0) {
            sql.append(" AND p.capacidad>=?");
            args.add(capacity);
        }
        sql.append(
                " AND EXISTS(SELECT 1 FROM propiedad_ofertas o WHERE"
                    + " o.id_propiedad=p.id_propiedad");
        if (operation != null) {
            sql.append(" AND o.id_tipo_transaccion=?");
            args.add(operation);
        }
        if (max != null) {
            sql.append(" AND o.precio<=? AND o.moneda=?");
            args.add(max);
            args.add(currency);
        }
        sql.append(")");
        for (int feature : features) {
            sql.append(
                    " AND EXISTS(SELECT 1 FROM propiedad_caracteristicas pc WHERE"
                        + " pc.id_propiedad=p.id_propiedad AND pc.id_caracteristica=?)");
            args.add(feature);
        }
        sql.append(" ORDER BY p.fecha_creacion DESC,p.id_propiedad DESC");
        return jdbc.list(sql.toString(), args.toArray()).stream().map(Propiedad::new).toList();
    }

    public Optional<Propiedad> findById(int id) {
        return jdbc.list(SELECT + " WHERE p.id_propiedad=?", id)
                .stream()
                .findFirst()
                .map(Propiedad::new);
    }

    public static int insert(Connection connection, Propiedad.Datos data) throws SQLException {
        return ConsultasSQL.insert(
                connection,
                "INSERT INTO propiedades(id_tipo_propiedad,id_tipo_transaccion,titulo,direccion,"
                    + "localidad,precio,moneda,descripcion,superficie_total_m2,"
                    + "superficie_cubierta_m2,antiguedad_anos,ambientes,dormitorios,banos,"
                    + "id_agente_creador,id_propietario,id_edificio,numero_unidad,capacidad,"
                    + "expensas,piso,departamento_numero,apto_profesional)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                data.idTipoPropiedad(), data.idTipoTransaccion(), data.titulo(),
                data.direccion(), data.localidad(), data.precio(), data.moneda(),
                data.descripcion(), data.superficieTotal(), data.superficieCubierta(),
                data.antiguedad(), data.ambientes(), data.dormitorios(), data.banos(),
                data.idAgenteCreador(), data.idPropietario(), data.idEdificio(),
                data.numeroUnidad(), data.capacidad(), data.expensas(), data.piso(),
                data.departamento(), data.aptoProfesional() ? 1 : 0);
    }

    public static void update(Connection connection, int id, Propiedad.Datos data)
            throws SQLException {
        ConsultasSQL.exec(
                connection,
                "UPDATE propiedades SET id_tipo_propiedad=?,id_tipo_transaccion=?,titulo=?,"
                    + "direccion=?,localidad=?,precio=?,moneda=?,descripcion=?,"
                    + "superficie_total_m2=?,superficie_cubierta_m2=?,antiguedad_anos=?,"
                    + "ambientes=?,dormitorios=?,banos=?,id_propietario=?,id_edificio=?,"
                    + "numero_unidad=?,capacidad=?,expensas=?,piso=?,departamento_numero=?,"
                    + "apto_profesional=?,id_estado_verificacion=1,id_admin_verificador=NULL,"
                    + "fecha_verificacion=NULL,motivo_rechazo=NULL WHERE id_propiedad=?",
                data.idTipoPropiedad(), data.idTipoTransaccion(), data.titulo(),
                data.direccion(), data.localidad(), data.precio(), data.moneda(),
                data.descripcion(), data.superficieTotal(), data.superficieCubierta(),
                data.antiguedad(), data.ambientes(), data.dormitorios(), data.banos(),
                data.idPropietario(), data.idEdificio(), data.numeroUnidad(),
                data.capacidad(), data.expensas(), data.piso(), data.departamento(),
                data.aptoProfesional() ? 1 : 0, id);
    }

    public static Propiedad fromRow(Registro row) {
        return new Propiedad(row);
    }
}
