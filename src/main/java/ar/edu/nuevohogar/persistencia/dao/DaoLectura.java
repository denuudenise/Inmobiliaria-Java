package ar.edu.nuevohogar.persistencia.dao;

import ar.edu.nuevohogar.modelo.Registro;
import java.util.List;
import java.util.Optional;

/** Plantilla de consultas tipadas: cada entidad define cómo reconstruirse desde una fila. */
public abstract class DaoLectura<T> {
    protected final ConsultasSQL jdbc;

    protected DaoLectura(ConexionBaseDatos database) { jdbc = new ConsultasSQL(database); }

    protected abstract T map(Registro row);

    protected final List<T> select(String sql, Object... args) {
        return jdbc.list(sql, args).stream().map(this::map).toList();
    }

    protected final Optional<T> first(String sql, Object... args) {
        return jdbc.list(sql, args).stream().findFirst().map(this::map);
    }
}
