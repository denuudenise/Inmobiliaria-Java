package ar.edu.nuevohogar.modelo;

/** Usuario autenticable del sistema. La vista pública del objeto nunca incluye el hash. */
public final class Usuario {
    private final int idUsuario;
    private final String nombre;
    private final String apellido;
    private final String dni;
    private final String telefono;
    private final String email;
    private final String passwordHash;
    private final int idRol;
    private final boolean activo;
    private final String direccion;
    private final String localidad;

    public Usuario(
            int idUsuario,
            String nombre,
            String apellido,
            String dni,
            String telefono,
            String email,
            String passwordHash,
            int idRol,
            boolean activo,
            String direccion,
            String localidad) {
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.apellido = apellido;
        this.dni = dni;
        this.telefono = telefono;
        this.email = email;
        this.passwordHash = passwordHash;
        this.idRol = idRol;
        this.activo = activo;
        this.direccion = direccion;
        this.localidad = localidad;
    }

    public static Usuario from(Registro row) {
        return new Usuario(
                row.id("id_usuario"), row.text("nombre"), row.text("apellido"),
                row.text("dni"), row.text("telefono"), row.text("email"),
                row.text("password_hash"), row.id("id_rol"), row.flag("activo"),
                row.text("direccion"), row.text("localidad"));
    }

    public int idUsuario() { return idUsuario; }
    public String nombre() { return nombre; }
    public String apellido() { return apellido; }
    public String dni() { return dni; }
    public String telefono() { return telefono; }
    public String email() { return email; }
    public String passwordHash() { return passwordHash; }
    public int idRol() { return idRol; }
    public boolean activo() { return activo; }
    public String direccion() { return direccion; }
    public String localidad() { return localidad; }

    public Registro toRow() {
        Registro row = new Registro();
        row.put("id_usuario", idUsuario);
        row.put("nombre", nombre);
        row.put("apellido", apellido);
        row.put("dni", dni);
        row.put("telefono", telefono);
        row.put("email", email);
        row.put("id_rol", idRol);
        row.put("activo", activo);
        row.put("direccion", direccion);
        row.put("localidad", localidad);
        return row;
    }

    @Override
    public String toString() {
        return "Usuario{id=" + idUsuario + ", email='" + email + "', rol=" + idRol + "}";
    }
}
