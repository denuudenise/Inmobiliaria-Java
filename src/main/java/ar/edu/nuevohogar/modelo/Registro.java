package ar.edu.nuevohogar.modelo;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

/** Resultado de consulta DAO. Conserva los nombres de columnas del modelo SQL. */
public class Registro extends LinkedHashMap<String, Object> {
    public String text(String key) {
        Object v = get(key);
        return v == null ? "" : v.toString();
    }

    public int id(String key) {
        Object v = get(key);
        return v == null ? 0 : ((Number) v).intValue();
    }

    public BigDecimal money(String key) {
        return get(key) == null ? BigDecimal.ZERO : new BigDecimal(text(key));
    }

    public boolean flag(String key) {
        return Boolean.TRUE.equals(get(key)) || "1".equals(text(key));
    }

    @Override
    public String toString() {
        return containsKey("etiqueta") ? text("etiqueta") : text("nombre");
    }
}
