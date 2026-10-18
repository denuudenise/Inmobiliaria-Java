package ar.edu.nuevohogar.model;

/** Ficha comercial de un usuario cliente y su agente asignado. */
public final class Cliente {
    private final Row attributes;
    private final int idCliente;
    private final String nombreCompleto;
    private final String email;
    private final String dni;
    private final String telefono;
    private final int idAgenteAsignado;

    public Cliente(Row attributes) {
        this.attributes = new Row();
        this.attributes.putAll(attributes);
        idCliente = attributes.id("id");
        nombreCompleto = attributes.text("etiqueta");
        email = attributes.text("email");
        dni = attributes.text("dni");
        telefono = attributes.text("telefono");
        idAgenteAsignado = attributes.id("id_agente_asignado");
    }

    public int idCliente() { return idCliente; }
    public String nombreCompleto() { return nombreCompleto; }
    public String email() { return email; }
    public String dni() { return dni; }
    public String telefono() { return telefono; }
    public int idAgenteAsignado() { return idAgenteAsignado; }

    public Row toRow() {
        Row copy = new Row();
        copy.putAll(attributes);
        return copy;
    }

    @Override
    public String toString() {
        return "Cliente{id=" + idCliente + ", nombre='" + nombreCompleto + "'}";
    }
}
