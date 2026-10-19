package ar.edu.nuevohogar.modelo;

import java.math.BigDecimal;

/** Entidad de dominio de una propiedad; los campos completos del SELECT se conservan para la UI. */
public final class Propiedad {
    public record Datos(
            int idTipoPropiedad,
            int idTipoTransaccion,
            String titulo,
            String direccion,
            String localidad,
            BigDecimal precio,
            String moneda,
            String descripcion,
            BigDecimal superficieTotal,
            BigDecimal superficieCubierta,
            int antiguedad,
            int ambientes,
            int dormitorios,
            int banos,
            Integer idAgenteCreador,
            int idPropietario,
            Integer idEdificio,
            String numeroUnidad,
            int capacidad,
            BigDecimal expensas,
            String piso,
            String departamento,
            boolean aptoProfesional) {}

    private final Registro attributes;
    private final int idPropiedad;
    private final String titulo;
    private final String direccion;
    private final String localidad;
    private final int idTipoPropiedad;
    private final int idEstadoVerificacion;
    private final int idEstadoComercial;
    private final Integer idPropietario;
    private final BigDecimal superficieTotal;
    private final BigDecimal superficieCubierta;

    public Propiedad(Registro attributes) {
        this.attributes = new Registro();
        this.attributes.putAll(attributes);
        idPropiedad = attributes.id("id_propiedad");
        titulo = attributes.text("titulo");
        direccion = attributes.text("direccion");
        localidad = attributes.text("localidad");
        idTipoPropiedad = attributes.id("id_tipo_propiedad");
        idEstadoVerificacion = attributes.id("id_estado_verificacion");
        idEstadoComercial = attributes.id("id_estado_comercial");
        idPropietario = attributes.get("id_propietario") == null
                ? null : attributes.id("id_propietario");
        superficieTotal = attributes.money("superficie_total_m2");
        superficieCubierta = attributes.money("superficie_cubierta_m2");
    }

    public int idPropiedad() { return idPropiedad; }
    public String titulo() { return titulo; }
    public String direccion() { return direccion; }
    public String localidad() { return localidad; }
    public int idTipoPropiedad() { return idTipoPropiedad; }
    public int idEstadoVerificacion() { return idEstadoVerificacion; }
    public int idEstadoComercial() { return idEstadoComercial; }
    public Integer idPropietario() { return idPropietario; }
    public BigDecimal superficieTotal() { return superficieTotal; }
    public BigDecimal superficieCubierta() { return superficieCubierta; }

    public Registro toRow() {
        Registro copy = new Registro();
        copy.putAll(attributes);
        return copy;
    }

    @Override
    public String toString() {
        return "Propiedad{id=" + idPropiedad + ", titulo='" + titulo + "'}";
    }
}
