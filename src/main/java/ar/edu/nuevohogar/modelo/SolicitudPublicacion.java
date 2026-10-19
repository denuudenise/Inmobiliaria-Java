package ar.edu.nuevohogar.modelo;

/** Solicitud de revisión y publicación de una propiedad. */
public final class SolicitudPublicacion {
    private final Registro attributes;
    private final int idSolicitud;
    private final int idPropiedad;
    private final String estado;
    private final String tituloPropiedad;
    private final String solicitante;

    public SolicitudPublicacion(Registro attributes) {
        this.attributes = new Registro();
        this.attributes.putAll(attributes);
        idSolicitud = attributes.id("id_publicacion");
        idPropiedad = attributes.id("id_propiedad");
        estado = attributes.text("estado");
        tituloPropiedad = attributes.text("titulo");
        solicitante = attributes.text("solicitante");
    }

    public int idSolicitud() { return idSolicitud; }
    public int idPropiedad() { return idPropiedad; }
    public String estado() { return estado; }
    public String tituloPropiedad() { return tituloPropiedad; }
    public String solicitante() { return solicitante; }

    public Registro toRow() {
        Registro copy = new Registro();
        copy.putAll(attributes);
        return copy;
    }

    @Override
    public String toString() {
        return "SolicitudPublicacion{id=" + idSolicitud + ", propiedad=" + idPropiedad
                + ", estado='" + estado + "'}";
    }
}
