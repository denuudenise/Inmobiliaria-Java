package ar.edu.nuevohogar.service;

import static ar.edu.nuevohogar.dao.JdbcDao.*;

import ar.edu.nuevohogar.dao.*;
import ar.edu.nuevohogar.model.Row;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;

/** Reglas del negocio compartidas por todas las pantallas; permisos comprobados aquí. */
public final class AgencyService {
    public final Database db;
    private final JdbcDao dao;
    private Integer sessionId;
    private static final String PROPERTY =
            "SELECT p.*,t.nombre AS tipo,e.nombre AS verificacion,c.nombre AS"
                + " comercial,CONCAT(u.nombre,' ',u.apellido) AS propietario FROM propiedades p"
                + " JOIN tipos_propiedad t ON t.id_tipo_propiedad=p.id_tipo_propiedad JOIN"
                + " estados_verificacion e ON e.id_estado_verificacion=p.id_estado_verificacion"
                + " JOIN estados_comerciales c ON c.id_estado_comercial=p.id_estado_comercial LEFT"
                + " JOIN usuarios u ON u.id_usuario=p.id_propietario ";

    public AgencyService(Database db) {
        this.db = db;
        dao = new JdbcDao(db);
    }

    public boolean signedIn() {
        return sessionId != null;
    }

    public Row current() {
        if (sessionId == null) throw new IllegalArgumentException("Iniciá sesión para continuar.");
        Row u = dao.one("SELECT * FROM usuarios WHERE id_usuario=? AND activo=1", sessionId);
        u.remove("password_hash");
        return u;
    }

    public int role() {
        return signedIn() ? current().id("id_rol") : 0;
    }

    private int require(int... roles) {
        Row u = current();
        for (int r : roles) if (u.id("id_rol") == r) return u.id("id_usuario");
        throw new IllegalArgumentException("Tu rol no tiene permiso para realizar esta acción.");
    }

    public void logout() {
        sessionId = null;
    }

    public void login(String email, String password) {
        var rows =
                dao.list(
                        "SELECT * FROM usuarios WHERE LOWER(email)=? AND activo=1",
                        email.trim().toLowerCase(Locale.ROOT));
        if (rows.isEmpty() || !Passwords.verify(password, rows.getFirst().text("password_hash")))
            throw new IllegalArgumentException("Correo o contraseña incorrectos.");
        sessionId = rows.getFirst().id("id_usuario");
    }

    private static String required(Map<String, ?> data, String key, int max) {
        String v = Objects.toString(data.get(key), "").trim();
        if (v.isEmpty() || v.length() > max)
            throw new IllegalArgumentException(
                    "Completá " + key.replace('_', ' ') + " (máximo " + max + " caracteres).");
        return v;
    }

    private static String optional(Map<String, ?> data, String key) {
        return Objects.toString(data.get(key), "").trim();
    }

    private static int integer(Map<String, ?> data, String key, int min, int max) {
        try {
            int n = Integer.parseInt(optional(data, key));
            if (n < min || n > max) throw new NumberFormatException();
            return n;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Revisá "
                            + key.replace('_', ' ')
                            + ": debe estar entre "
                            + min
                            + " y "
                            + max
                            + ".");
        }
    }

    private static BigDecimal decimal(Map<String, ?> data, String key, boolean positive) {
        try {
            BigDecimal n = new BigDecimal(optional(data, key).replace(',', '.'));
            if (n.scale() > 2
                    || n.signum() < (positive ? 1 : 0)
                    || n.compareTo(new BigDecimal("9999999999.99")) > 0)
                throw new NumberFormatException();
            return n;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Revisá "
                            + key.replace('_', ' ')
                            + ": ingresá un importe válido, con hasta 2 decimales.");
        }
    }

    private static void email(String value) {
        if (value.length() > 150 || !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Ingresá un correo electrónico válido.");
    }

    private static void audit(
            Connection c, int actor, String action, String entity, int id, String detail)
            throws Exception {
        exec(
                c,
                "INSERT INTO auditoria(id_usuario,accion,entidad,id_registro,detalle)"
                    + " VALUES(?,?,?,?,?)",
                actor,
                action,
                entity,
                id,
                detail);
    }

    private static void notify(Connection c, int user, String text) throws Exception {
        exec(c, "INSERT INTO notificaciones(id_usuario,mensaje) VALUES(?,?)", user, text);
    }

    private static void notifyStaff(Connection c, String text) throws Exception {
        for (Row r : list(c, "SELECT id_usuario FROM usuarios WHERE id_rol=1 AND activo=1"))
            notify(c, r.id("id_usuario"), text);
    }

    public int register(Map<String, String> data, String password) {
        return createUser(data, password, 3, false);
    }

    public int registerClient(Map<String, String> data, String password) {
        require(1, 2);
        return createUser(data, password, 3, true);
    }

    public int createStaffUser(Map<String, String> data, String password, int role) {
        require(1);
        if (role < 1 || role > 3) throw new IllegalArgumentException("Rol inválido");
        return createUser(data, password, role, true);
    }

    private int createUser(Map<String, String> d, String password, int role, boolean admin) {
        String name = required(d, "nombre", 100),
                last = required(d, "apellido", 100),
                dni = required(d, "dni", 20),
                mail = required(d, "email", 150).toLowerCase(Locale.ROOT);
        email(mail);
        String hash = Passwords.hash(password);
        int registeringUser = signedIn() ? current().id("id_usuario") : 0;
        Integer assignedAgent = signedIn() && role() == 2 ? registeringUser : null;
        String birth = optional(d, "fecha_nacimiento");
        if (!birth.isBlank() && LocalDate.parse(birth).isAfter(LocalDate.now()))
            throw new IllegalArgumentException("La fecha de nacimiento no puede ser futura.");
        if (!dao.list("SELECT id_usuario FROM usuarios WHERE dni=? OR LOWER(email)=?", dni, mail)
                .isEmpty())
            throw new IllegalArgumentException("Ya existe una ficha con ese documento o correo.");
        return db.transaction(
                c -> {
                    int id =
                            insert(
                                    c,
                                    "INSERT INTO"
                                        + " usuarios(nombre,apellido,dni,telefono,email,password_hash,id_rol,direccion,localidad,fecha_nacimiento,genero,tipo_documento)"
                                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                                    name,
                                    last,
                                    dni,
                                    optional(d, "telefono"),
                                    mail,
                                    hash,
                                    role,
                                    optional(d, "direccion"),
                                    optional(d, "localidad"),
                                    birth.isBlank() ? null : birth,
                                    optional(d, "genero"),
                                    d.getOrDefault("tipo_documento", "DNI"));
                    if (role == 3)
                        exec(
                                c,
                                "INSERT INTO clientes(id_cliente,fecha_inicio_relacion,id_agente_asignado)"
                                    + " VALUES(?,?,?)",
                                id,
                                LocalDate.now(),
                                assignedAgent);
                    audit(
                            c,
                            admin ? current().id("id_usuario") : id,
                            "Alta de usuario",
                            "usuarios",
                            id,
                            "Rol " + role);
                    return id;
                });
    }

    public void updateProfile(Map<String, String> d) {
        int actor = require(1, 2, 3);
        String name = required(d, "nombre", 100), last = required(d, "apellido", 100);
        db.transaction(
                c -> {
                    exec(
                            c,
                            "UPDATE usuarios SET"
                                + " nombre=?,apellido=?,telefono=?,direccion=?,localidad=? WHERE"
                                + " id_usuario=?",
                            name,
                            last,
                            optional(d, "telefono"),
                            optional(d, "direccion"),
                            optional(d, "localidad"),
                            actor);
                    audit(c, actor, "Edición de perfil", "usuarios", actor, "");
                    return null;
                });
    }

    public void changePassword(String old, String replacement) {
        int actor = require(1, 2, 3);
        Row u = dao.one("SELECT password_hash FROM usuarios WHERE id_usuario=?", actor);
        if (!Passwords.verify(old, u.text("password_hash")))
            throw new IllegalArgumentException("La contraseña actual no coincide.");
        String hash = Passwords.hash(replacement);
        db.transaction(
                c -> {
                    exec(c, "UPDATE usuarios SET password_hash=? WHERE id_usuario=?", hash, actor);
                    return null;
                });
    }

    public List<Row> lookup(String table) {
        String pk =
                switch (table) {
                    case "tipos_propiedad" -> "id_tipo_propiedad";
                    case "tipos_transaccion" -> "id_tipo_transaccion";
                    case "caracteristicas" -> "id_caracteristica";
                    case "estados_comerciales" -> "id_estado_comercial";
                    case "edificios" -> "id_edificio";
                    default -> throw new IllegalArgumentException("Catálogo inválido");
                };
        return dao.list(
                "SELECT " + pk + " AS id,nombre AS etiqueta FROM " + table + " ORDER BY nombre");
    }

    public List<Row> clients() {
        int actor = require(1, 2);
        String scope = role() == 2 ? " WHERE c.id_agente_asignado=?" : "";
        return dao.list(
                "SELECT u.id_usuario AS id,CONCAT(u.nombre,' ',u.apellido) AS"
                    + " etiqueta,u.dni,u.email,u.telefono,c.fecha_inicio_relacion,c.id_agente_asignado"
                    + " FROM usuarios u JOIN clientes c ON c.id_cliente=u.id_usuario"
                    + scope
                    + " ORDER BY u.apellido",
                role() == 2 ? new Object[] {actor} : new Object[] {});
    }

    public List<Row> users() {
        require(1);
        return dao.list(
                "SELECT u.id_usuario AS id,CONCAT(u.nombre,' ',u.apellido) AS"
                    + " etiqueta,u.email,r.nombre AS rol,u.activo FROM usuarios u JOIN roles r ON"
                    + " r.id_rol=u.id_rol ORDER BY u.apellido");
    }

    public List<Row> agents() {
        require(1, 2);
        return dao.list(
                "SELECT id_usuario AS id,CONCAT(nombre,' ',apellido) AS etiqueta FROM usuarios"
                    + " WHERE id_rol=2 AND activo=1");
    }

    public void setActive(int id, boolean active) {
        int actor = require(1);
        if (id == actor)
            throw new IllegalArgumentException("No podés desactivar tu propia cuenta.");
        db.transaction(
                c -> {
                    exec(c, "UPDATE usuarios SET activo=? WHERE id_usuario=?", active ? 1 : 0, id);
                    audit(
                            c,
                            actor,
                            "Estado de usuario",
                            "usuarios",
                            id,
                            active ? "Activo" : "Inactivo");
                    return null;
                });
    }

    public List<Row> properties(
            boolean catalog,
            String query,
            Integer type,
            Integer operation,
            BigDecimal max,
            String currency,
            int bedrooms,
            int capacity,
            Set<Integer> features) {
        StringBuilder sql = new StringBuilder(PROPERTY + " WHERE 1=1");
        List<Object> a = new ArrayList<>();
        if (catalog)
            sql.append(" AND p.id_estado_verificacion=3 AND p.id_estado_comercial IN (1,2)");
        else {
            int actor = require(1, 2, 3);
            if (role() == 3) {
                sql.append(" AND p.id_propietario=?");
                a.add(actor);
            } else if (role() == 2) {
                sql.append(
                        " AND (p.id_agente_creador=? OR EXISTS(SELECT 1 FROM clientes ca"
                            + " WHERE ca.id_cliente=p.id_propietario AND"
                            + " ca.id_agente_asignado=?))");
                a.add(actor);
                a.add(actor);
            }
        }
        if (query != null && !query.isBlank()) {
            sql.append(
                    " AND (LOWER(p.titulo) LIKE ? OR LOWER(p.localidad) LIKE ? OR"
                        + " LOWER(p.direccion) LIKE ?)");
            String q = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            a.add(q);
            a.add(q);
            a.add(q);
        }
        if (type != null) {
            sql.append(" AND p.id_tipo_propiedad=?");
            a.add(type);
        }
        if (bedrooms > 0) {
            sql.append(" AND p.dormitorios>=?");
            a.add(bedrooms);
        }
        if (capacity > 0) {
            sql.append(" AND p.capacidad>=?");
            a.add(capacity);
        }
        sql.append(
                " AND EXISTS(SELECT 1 FROM propiedad_ofertas o WHERE"
                    + " o.id_propiedad=p.id_propiedad");
        if (operation != null) {
            sql.append(" AND o.id_tipo_transaccion=?");
            a.add(operation);
        }
        if (max != null) {
            sql.append(" AND o.precio<=? AND o.moneda=?");
            a.add(max);
            a.add(currency);
        }
        sql.append(")");
        for (int f : features) {
            sql.append(
                    " AND EXISTS(SELECT 1 FROM propiedad_caracteristicas pc WHERE"
                        + " pc.id_propiedad=p.id_propiedad AND pc.id_caracteristica=?)");
            a.add(f);
        }
        sql.append(" ORDER BY p.fecha_creacion DESC,p.id_propiedad DESC");
        return dao.list(sql.toString(), a.toArray());
    }

    public List<Row> allProperties() {
        return properties(false, "", null, null, null, "USD", 0, 0, Set.of());
    }

    public Row property(int id) {
        Row p = dao.one(PROPERTY + " WHERE p.id_propiedad=?", id);
        if (p.id("id_estado_verificacion") == 3
                && Set.of(1, 2).contains(p.id("id_estado_comercial"))) return p;
        int actor = require(1, 2, 3);
        if (role() == 3 && p.id("id_propietario") != actor)
            throw new IllegalArgumentException("No podés acceder a esta propiedad.");
        if (role() == 2 && !agentCanAccessProperty(actor, id))
            throw new IllegalArgumentException("La propiedad no pertenece a tu cartera.");
        return p;
    }

    private boolean agentCanAccessProperty(int agent, int property) {
        return !dao.list(
                        "SELECT id_propiedad FROM propiedades p WHERE p.id_propiedad=? AND"
                            + " (p.id_agente_creador=? OR EXISTS(SELECT 1 FROM clientes c WHERE"
                            + " c.id_cliente=p.id_propietario AND c.id_agente_asignado=?))",
                        property,
                        agent,
                        agent)
                .isEmpty();
    }

    public List<Row> offers(int id) {
        property(id);
        return dao.list(
                "SELECT o.*,t.nombre AS operacion FROM propiedad_ofertas o JOIN tipos_transaccion t"
                    + " ON t.id_tipo_transaccion=o.id_tipo_transaccion WHERE id_propiedad=? ORDER"
                    + " BY o.id_tipo_transaccion",
                id);
    }

    public List<Row> features(int id) {
        property(id);
        return dao.list(
                "SELECT c.id_caracteristica AS id,c.nombre AS etiqueta FROM"
                    + " propiedad_caracteristicas pc JOIN caracteristicas c ON"
                    + " c.id_caracteristica=pc.id_caracteristica WHERE id_propiedad=?",
                id);
    }

    private Row editable(Connection c, int id, int actor) throws Exception {
        Row p =
                list(c, "SELECT * FROM propiedades WHERE id_propiedad=? FOR UPDATE", id).stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Propiedad inexistente"));
        if (role() == 3 && p.id("id_propietario") != actor)
            throw new IllegalArgumentException("Solo podés editar tus propiedades.");
        if (role() == 2 && !agentCanAccessProperty(actor, id))
            throw new IllegalArgumentException("Solo podés editar propiedades de tu cartera.");
        return p;
    }

    public record Offer(int type, BigDecimal price, String currency) {}

    public int saveProperty(
            Integer id,
            Map<String, String> d,
            List<Offer> offers,
            Set<Integer> features,
            boolean submit) {
        int actor = require(1, 2, 3);
        String title = required(d, "titulo", 200),
                address = required(d, "direccion", 255),
                city = required(d, "localidad", 100),
                description = required(d, "descripcion", 10000);
        int type = integer(d, "id_tipo_propiedad", 1, 1000),
                owner = role() == 3 ? actor : integer(d, "id_propietario", 1, Integer.MAX_VALUE);
        BigDecimal surface = decimal(d, "superficie_total_m2", true),
                covered = decimal(d, "superficie_cubierta_m2", false);
        if (surface.compareTo(new BigDecimal("999999.99")) > 0 || covered.compareTo(surface) > 0)
            throw new IllegalArgumentException(
                    "Revisá las superficies: la cubierta no puede superar a la total.");
        int rooms = integer(d, "ambientes", 0, 1000),
                beds = integer(d, "dormitorios", 0, 1000),
                baths = integer(d, "banos", 0, 1000),
                age = integer(d, "antiguedad_anos", 0, 1000),
                capacity = integer(d, "capacidad", 1, 1000);
        BigDecimal expenses = decimal(d, "expensas", false);
        if (expenses.compareTo(new BigDecimal("99999999.99")) > 0)
            throw new IllegalArgumentException("Expensas fuera de rango");
        if (offers.isEmpty()) throw new IllegalArgumentException("Agregá al menos una operación.");
        Set<Integer> unique = new HashSet<>();
        for (Offer o : offers)
            if (!unique.add(o.type)
                    || o.price == null
                    || o.price.signum() <= 0
                    || o.price.scale() > 2
                    || o.price.compareTo(new BigDecimal("9999999999.99")) > 0
                    || !Set.of("USD", "ARS").contains(o.currency))
                throw new IllegalArgumentException(
                        "Revisá el precio y la moneda de cada operación.");
        Integer building =
                optional(d, "id_edificio").isEmpty()
                        ? null
                        : integer(d, "id_edificio", 1, Integer.MAX_VALUE);
        String unit = optional(d, "numero_unidad");
        if (building != null && unit.isBlank())
            throw new IllegalArgumentException("Indicá el número de unidad del edificio.");
        return db.transaction(
                c -> {
                    if (list(c, "SELECT id_cliente FROM clientes WHERE id_cliente=?", owner)
                            .isEmpty())
                        throw new IllegalArgumentException(
                                "El propietario debe tener una ficha de cliente.");
                    int pid;
                    if (id == null) {
                        pid =
                                insert(
                                        c,
                                        "INSERT INTO"
                                            + " propiedades(id_tipo_propiedad,id_tipo_transaccion,titulo,direccion,localidad,precio,moneda,descripcion,superficie_total_m2,superficie_cubierta_m2,antiguedad_anos,ambientes,dormitorios,banos,id_agente_creador,id_propietario,id_edificio,numero_unidad,capacidad,expensas,piso,departamento_numero,apto_profesional)"
                                            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                        type,
                                        offers.getFirst().type,
                                        title,
                                        address,
                                        city,
                                        offers.getFirst().price,
                                        offers.getFirst().currency,
                                        description,
                                        surface,
                                        covered,
                                        age,
                                        rooms,
                                        beds,
                                        baths,
                                        role() == 3 ? null : actor,
                                        owner,
                                        building,
                                        building == null ? null : unit,
                                        capacity,
                                        expenses,
                                        optional(d, "piso"),
                                        optional(d, "departamento_numero"),
                                        "1".equals(d.get("apto_profesional")) ? 1 : 0);
                    } else {
                        Row previous = editable(c, id, actor);
                        if (Set.of(3, 4).contains(previous.id("id_estado_comercial")))
                            throw new IllegalArgumentException(
                                    "Reactivá la propiedad antes de editar una operación cerrada.");
                        pid = id;
                        exec(
                                c,
                                "UPDATE propiedades SET"
                                    + " id_tipo_propiedad=?,id_tipo_transaccion=?,titulo=?,direccion=?,localidad=?,precio=?,moneda=?,descripcion=?,superficie_total_m2=?,superficie_cubierta_m2=?,antiguedad_anos=?,ambientes=?,dormitorios=?,banos=?,id_propietario=?,id_edificio=?,numero_unidad=?,capacidad=?,expensas=?,piso=?,departamento_numero=?,apto_profesional=?,id_estado_verificacion=1,id_admin_verificador=NULL,fecha_verificacion=NULL,motivo_rechazo=NULL"
                                    + " WHERE id_propiedad=?",
                                type,
                                offers.getFirst().type,
                                title,
                                address,
                                city,
                                offers.getFirst().price,
                                offers.getFirst().currency,
                                description,
                                surface,
                                covered,
                                age,
                                rooms,
                                beds,
                                baths,
                                owner,
                                building,
                                building == null ? null : unit,
                                capacity,
                                expenses,
                                optional(d, "piso"),
                                optional(d, "departamento_numero"),
                                "1".equals(d.get("apto_profesional")) ? 1 : 0,
                                pid);
                        exec(
                                c,
                                "UPDATE publicaciones SET"
                                    + " estado='Reemplazada',fecha_revision=CURRENT_TIMESTAMP WHERE"
                                    + " id_propiedad=? AND estado='Pendiente'",
                                pid);
                        exec(c, "DELETE FROM propiedad_ofertas WHERE id_propiedad=?", pid);
                        exec(c, "DELETE FROM propiedad_caracteristicas WHERE id_propiedad=?", pid);
                    }
                    for (Offer o : offers)
                        exec(
                                c,
                                "INSERT INTO propiedad_ofertas VALUES(?,?,?,?)",
                                pid,
                                o.type,
                                o.price,
                                o.currency);
                    for (int f : features)
                        exec(c, "INSERT INTO propiedad_caracteristicas VALUES(?,?)", pid, f);
                    if (list(
                                    c,
                                    "SELECT * FROM roles_cliente WHERE id_cliente=? AND"
                                        + " tipo_rol='Propietario'",
                                    owner)
                            .isEmpty())
                        exec(c, "INSERT INTO roles_cliente VALUES(?,'Propietario')", owner);
                    audit(
                            c,
                            actor,
                            id == null ? "Alta de propiedad" : "Edición de propiedad",
                            "propiedades",
                            pid,
                            title);
                    if (submit) submit(c, pid, actor);
                    return pid;
                });
    }

    private void submit(Connection c, int id, int actor) throws Exception {
        Row p = editable(c, id, actor);
        if (!Set.of(1, 4).contains(p.id("id_estado_verificacion")))
            throw new IllegalArgumentException(
                    "Solo se envían borradores o propiedades rechazadas.");
        exec(
                c,
                "UPDATE propiedades SET id_estado_verificacion=2,motivo_rechazo=NULL WHERE"
                    + " id_propiedad=?",
                id);
        insert(c, "INSERT INTO publicaciones(id_propiedad,id_solicitante) VALUES(?,?)", id, actor);
        notifyStaff(c, "Solicitud de publicación #" + id + ": " + p.text("titulo"));
        audit(c, actor, "Solicitud de publicación", "propiedades", id, "");
    }

    public void submit(int id) {
        int actor = require(1, 2, 3);
        db.transaction(
                c -> {
                    submit(c, id, actor);
                    return null;
                });
    }

    public List<Row> requests() {
        int actor = require(1, 2, 3);
        String scope =
                switch (role()) {
                    case 2 -> "WHERE p.id_agente_creador=? OR EXISTS(SELECT 1 FROM clientes ca"
                            + " WHERE ca.id_cliente=p.id_propietario AND"
                            + " ca.id_agente_asignado=?) ";
                    case 3 -> "WHERE p.id_propietario=? ";
                    default -> "";
                };
        return dao.list(
                "SELECT s.*,p.titulo,p.id_propietario,CONCAT(u.nombre,' ',u.apellido) AS"
                    + " solicitante FROM publicaciones s JOIN propiedades p ON"
                    + " p.id_propiedad=s.id_propiedad JOIN usuarios u ON"
                    + " u.id_usuario=s.id_solicitante "
                        + scope
                        + "ORDER BY s.fecha_solicitud DESC,s.id_publicacion DESC",
                switch (role()) {
                    case 2 -> new Object[] {actor, actor};
                    case 3 -> new Object[] {actor};
                    default -> new Object[] {};
                });
    }

    public void review(int request, boolean approve, String comment) {
        int actor = require(1);
        if (!approve && (comment == null || comment.isBlank()))
            throw new IllegalArgumentException("Indicá el motivo del rechazo.");
        db.transaction(
                c -> {
                    Row initial =
                            list(c, "SELECT * FROM publicaciones WHERE id_publicacion=?", request)
                                    .getFirst();
                    int pid = initial.id("id_propiedad");
                    editable(c, pid, actor);
                    Row s =
                            list(
                                            c,
                                            "SELECT * FROM publicaciones WHERE id_publicacion=? FOR"
                                                + " UPDATE",
                                            request)
                                    .getFirst();
                    if (!s.text("estado").equals("Pendiente"))
                        throw new IllegalArgumentException(
                                "La solicitud ya fue revisada o reemplazada.");
                    exec(
                            c,
                            "UPDATE publicaciones SET"
                                + " estado=?,observaciones=?,id_revisor=?,fecha_revision=CURRENT_TIMESTAMP"
                                + " WHERE id_publicacion=?",
                            approve ? "Publicada" : "Rechazada",
                            comment,
                            actor,
                            request);
                    exec(
                            c,
                            "UPDATE propiedades SET"
                                + " id_estado_verificacion=?,id_admin_verificador=?,fecha_verificacion=CURRENT_TIMESTAMP,motivo_rechazo=?"
                                + " WHERE id_propiedad=?",
                            approve ? 3 : 4,
                            actor,
                            approve ? null : comment,
                            pid);
                    notify(
                            c,
                            s.id("id_solicitante"),
                            (approve ? "Publicación aprobada #" : "Publicación rechazada #")
                                    + pid
                                    + ". "
                                    + Objects.toString(comment, ""));
                    audit(
                            c,
                            actor,
                            approve ? "Aprobación" : "Rechazo",
                            "propiedades",
                            pid,
                            comment);
                    return null;
                });
    }

    public void changeState(int id, int state, String reason) {
        int actor = require(1, 2);
        if (!Set.of(1, 2, 5, 6).contains(state))
            throw new IllegalArgumentException("Usá Registrar cierre para vender o alquilar.");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Indicá el motivo del cambio.");
        db.transaction(
                c -> {
                    Row p = editable(c, id, actor);
                    int old = p.id("id_estado_comercial");
                    if (old == state)
                        throw new IllegalArgumentException("La propiedad ya tiene ese estado.");
                    setState(c, p, actor, state, reason);
                    if (Set.of(3, 4, 6).contains(old)) {
                        exec(
                                c,
                                "UPDATE propiedades SET id_estado_verificacion=1 WHERE"
                                    + " id_propiedad=?",
                                id);
                        exec(
                                c,
                                "UPDATE publicaciones SET estado='Reemplazada' WHERE id_propiedad=?"
                                    + " AND estado='Pendiente'",
                                id);
                    }
                    return null;
                });
    }

    private static void setState(Connection c, Row p, int actor, int state, String reason)
            throws Exception {
        exec(
                c,
                "UPDATE propiedades SET id_estado_comercial=? WHERE id_propiedad=?",
                state,
                p.id("id_propiedad"));
        exec(
                c,
                "INSERT INTO"
                    + " historial_estados(id_propiedad,id_usuario,estado_anterior,estado_nuevo,motivo)"
                    + " VALUES(?,?,?,?,?)",
                p.id("id_propiedad"),
                actor,
                p.id("id_estado_comercial"),
                state,
                reason);
        audit(c, actor, "Cambio de estado", "propiedades", p.id("id_propiedad"), reason);
    }

    public void close(int id, int client, int operation, BigDecimal price, BigDecimal commission) {
        int actor = require(1, 2);
        if (price == null
                || price.signum() <= 0
                || price.scale() > 2
                || commission == null
                || commission.signum() < 0
                || commission.compareTo(price) > 0)
            throw new IllegalArgumentException("Revisá el precio final y la comisión.");
        db.transaction(
                c -> {
                    Row p = editable(c, id, actor);
                    if (p.id("id_estado_verificacion") != 3
                            || !Set.of(1, 2, 5).contains(p.id("id_estado_comercial")))
                        throw new IllegalArgumentException(
                                "Solo se pueden cerrar propiedades aprobadas y disponibles,"
                                    + " reservadas o en trámite.");
                    if (list(c, "SELECT * FROM clientes WHERE id_cliente=?", client).isEmpty())
                        throw new IllegalArgumentException("Seleccioná un cliente.");
                    if (role() == 2
                            && list(
                                            c,
                                            "SELECT id_cliente FROM clientes WHERE id_cliente=? AND"
                                                + " id_agente_asignado=?",
                                            client,
                                            actor)
                                    .isEmpty())
                        throw new IllegalArgumentException(
                                "Solo podés registrar operaciones de un cliente asignado a vos.");
                    Row o =
                            list(
                                            c,
                                            "SELECT * FROM propiedad_ofertas WHERE id_propiedad=?"
                                                + " AND id_tipo_transaccion=?",
                                            id,
                                            operation)
                                    .stream()
                                    .findFirst()
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Esa operación no está ofrecida."));
                    exec(
                            c,
                            "INSERT INTO"
                                + " propiedades_cerradas(id_propiedad,id_cliente_comprador,id_agente_cierre,precio_final,comision_total,id_tipo_transaccion,moneda)"
                                + " VALUES(?,?,?,?,?,?,?)",
                            id,
                            client,
                            actor,
                            price,
                            commission,
                            operation,
                            o.text("moneda"));
                    setState(c, p, actor, operation == 1 ? 3 : 4, "Cierre de operación");
                    notify(c, client, "Se registró el cierre de la propiedad #" + id);
                    return null;
                });
    }

    public List<Row> operations() {
        int actor = require(1, 2);
        String scope =
                role() == 2
                        ? "WHERE p.id_agente_creador=? OR EXISTS(SELECT 1 FROM clientes cp"
                                + " WHERE cp.id_cliente=p.id_propietario AND"
                                + " cp.id_agente_asignado=?) OR EXISTS(SELECT 1 FROM clientes cc"
                                + " WHERE cc.id_cliente=c.id_cliente_comprador AND"
                                + " cc.id_agente_asignado=?) "
                        : "";
        return dao.list(
                "SELECT c.*,p.titulo,t.nombre AS operacion,CONCAT(u.nombre,' ',u.apellido) AS"
                    + " cliente FROM propiedades_cerradas c JOIN propiedades p ON"
                    + " p.id_propiedad=c.id_propiedad JOIN usuarios u ON"
                    + " u.id_usuario=c.id_cliente_comprador LEFT JOIN tipos_transaccion t ON"
                    + " t.id_tipo_transaccion=c.id_tipo_transaccion "
                    + scope
                    + "ORDER BY fecha_cierre DESC",
                role() == 2 ? new Object[] {actor, actor, actor} : new Object[] {});
    }

    private void accessClient(int client) {
        int actor = require(1, 2, 3);
        if (role() == 3 && actor != client)
            throw new IllegalArgumentException("Solo podés acceder a tu ficha.");
        List<Row> ficha = dao.list("SELECT * FROM clientes WHERE id_cliente=?", client);
        if (ficha.isEmpty())
            throw new IllegalArgumentException("Ficha de cliente inexistente.");
        if (role() == 2 && ficha.getFirst().id("id_agente_asignado") != actor)
            throw new IllegalArgumentException("Este cliente no está asignado a tu cartera.");
    }

    public List<Row> contacts(int client) {
        accessClient(client);
        return dao.list(
                "SELECT c.*,CONCAT(u.nombre,' ',u.apellido) AS agente FROM contactos c JOIN"
                    + " usuarios u ON u.id_usuario=c.id_agente WHERE id_cliente=? ORDER BY fecha"
                    + " DESC",
                client);
    }

    public void contact(
            int client, Integer property, String type, String description, String result) {
        int actor = require(1, 2);
        accessClient(client);
        if (!Set.of("Llamada", "Mensaje", "Visita", "Reunión").contains(type)
                || description.isBlank())
            throw new IllegalArgumentException("Completá el tipo y la descripción del contacto.");
        if (property != null) property(property);
        db.transaction(
                c -> {
                    int id =
                            insert(
                                    c,
                                    "INSERT INTO"
                                        + " contactos(id_cliente,id_agente,id_propiedad,tipo,descripcion,resultado)"
                                        + " VALUES(?,?,?,?,?,?)",
                                    client,
                                    actor,
                                    property,
                                    type,
                                    description,
                                    result);
                    audit(c, actor, "Registro de contacto", "contactos", id, type);
                    return null;
                });
    }

    public void interest(int client, int property, String state, String actions) {
        int actor = require(1, 2, 3);
        accessClient(client);
        property(property);
        if (!Set.of("Interesado", "Contactado", "Visita coordinada", "Negociación", "Descartado")
                .contains(state)) throw new IllegalArgumentException("Estado de interés inválido.");
        db.transaction(
                c -> {
                    if (list(
                                    c,
                                    "SELECT * FROM intereses WHERE id_cliente=? AND id_propiedad=?",
                                    client,
                                    property)
                            .isEmpty())
                        exec(
                                c,
                                "INSERT INTO intereses(id_cliente,id_propiedad,estado,acciones)"
                                    + " VALUES(?,?,?,?)",
                                client,
                                property,
                                state,
                                actions);
                    else
                        exec(
                                c,
                                "UPDATE intereses SET estado=?,acciones=? WHERE id_cliente=? AND"
                                    + " id_propiedad=?",
                                state,
                                actions,
                                client,
                                property);
                    audit(
                            c,
                            actor,
                            "Seguimiento de interés",
                            "propiedades",
                            property,
                            "Cliente #" + client + ": " + state + " · " + actions);
                    if (role() == 3)
                        notifyStaff(
                                c,
                                "Consulta del cliente #"
                                        + client
                                        + " por la propiedad #"
                                        + property
                                        + ". "
                                        + actions.substring(0, Math.min(350, actions.length())));
                    return null;
                });
    }

    public List<Row> interests(int client) {
        accessClient(client);
        return dao.list(
                "SELECT i.*,p.titulo FROM intereses i JOIN propiedades p ON"
                    + " p.id_propiedad=i.id_propiedad WHERE id_cliente=? ORDER BY i.fecha DESC",
                client);
    }

    public void saveClient(int client, Set<String> roles, Integer agent) {
        int actor = require(1, 2);
        accessClient(client);
        if (!Set.of("Comprador", "Vendedor", "Propietario", "Inquilino", "Inversor")
                .containsAll(roles)) throw new IllegalArgumentException("Rol comercial inválido");
        if (role() == 1 && agent != null && agents().stream().noneMatch(r -> r.id("id") == agent))
            throw new IllegalArgumentException("Agente inválido");
        db.transaction(
                c -> {
                    if (role() == 1)
                        exec(
                                c,
                                "UPDATE clientes SET id_agente_asignado=? WHERE id_cliente=?",
                                agent,
                                client);
                    exec(c, "DELETE FROM roles_cliente WHERE id_cliente=?", client);
                    for (String role : roles)
                        exec(c, "INSERT INTO roles_cliente VALUES(?,?)", client, role);
                    audit(
                            c,
                            actor,
                            "Actualización de ficha",
                            "clientes",
                            client,
                            String.join(", ", roles));
                    return null;
                });
    }

    public List<Row> clientRoles(int client) {
        accessClient(client);
        return dao.list("SELECT tipo_rol FROM roles_cliente WHERE id_cliente=?", client);
    }

    public Row preference(int client) {
        accessClient(client);
        return dao
                .list(
                        "SELECT * FROM preferencias_cliente WHERE id_cliente=? ORDER BY"
                            + " id_preferencia DESC",
                        client)
                .stream()
                .findFirst()
                .orElse(new Row());
    }

    public List<Row> preferenceFeatures(int client) {
        Row p = preference(client);
        return dao.list(
                "SELECT id_caracteristica AS id FROM preferencias_caracteristicas WHERE"
                    + " id_preferencia=?",
                p.id("id_preferencia"));
    }

    public void savePreference(int client, Map<String, String> d, Set<Integer> features) {
        int actor = require(1, 2, 3);
        accessClient(client);
        int op = integer(d, "id_tipo_transaccion", 1, 3);
        BigDecimal budget = decimal(d, "presupuesto_maximo", true),
                surface = decimal(d, "superficie_minima_m2", false);
        int rooms = integer(d, "ambientes_minimos", 0, 1000),
                beds = integer(d, "dormitorios_minimos", 0, 1000);
        String currency = required(d, "moneda", 3);
        if (!Set.of("USD", "ARS").contains(currency))
            throw new IllegalArgumentException("Moneda inválida");
        String date = optional(d, "fecha_estimada_concretar");
        if (!date.isBlank()) LocalDate.parse(date);
        Integer type =
                optional(d, "id_tipo_propiedad").isBlank()
                        ? null
                        : integer(d, "id_tipo_propiedad", 1, 1000);
        db.transaction(
                c -> {
                    for (Row old :
                            list(
                                    c,
                                    "SELECT id_preferencia FROM preferencias_cliente WHERE"
                                        + " id_cliente=?",
                                    client))
                        exec(
                                c,
                                "DELETE FROM preferencias_caracteristicas WHERE id_preferencia=?",
                                old.id("id_preferencia"));
                    exec(c, "DELETE FROM preferencias_cliente WHERE id_cliente=?", client);
                    int id =
                            insert(
                                    c,
                                    "INSERT INTO"
                                        + " preferencias_cliente(id_cliente,id_tipo_transaccion,id_tipo_propiedad,presupuesto_maximo,zona_interes,ambientes_minimos,dormitorios_minimos,superficie_minima_m2,fecha_estimada_concretar,requiere_venta_previa,moneda)"
                                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                                    client,
                                    op,
                                    type,
                                    budget,
                                    optional(d, "zona_interes"),
                                    rooms,
                                    beds,
                                    surface,
                                    date.isBlank() ? null : date,
                                    "1".equals(d.get("requiere_venta_previa")) ? 1 : 0,
                                    currency);
                    for (int f : features)
                        exec(c, "INSERT INTO preferencias_caracteristicas VALUES(?,?)", id, f);
                    audit(c, actor, "Perfil de búsqueda", "clientes", client, "");
                    return null;
                });
    }

    public List<Row> matches(int client) {
        Row p = preference(client);
        if (p.isEmpty()) return List.of();
        Set<Integer> fs = new HashSet<>();
        preferenceFeatures(client).forEach(r -> fs.add(r.id("id")));
        return properties(
                        true,
                        p.text("zona_interes"),
                        p.id("id_tipo_propiedad") == 0 ? null : p.id("id_tipo_propiedad"),
                        p.id("id_tipo_transaccion"),
                        p.money("presupuesto_maximo"),
                        p.text("moneda"),
                        p.id("dormitorios_minimos"),
                        0,
                        fs)
                .stream()
                .filter(
                        r ->
                                r.money("superficie_total_m2")
                                                        .compareTo(p.money("superficie_minima_m2"))
                                                >= 0
                                        && r.id("ambientes") >= p.id("ambientes_minimos"))
                .toList();
    }

    public void building(String name, String address, String city) {
        int actor = require(1, 2);
        if (name.isBlank() || address.isBlank() || city.isBlank())
            throw new IllegalArgumentException("Completá todos los datos del edificio.");
        db.transaction(
                c -> {
                    int id =
                            insert(
                                    c,
                                    "INSERT INTO edificios(nombre,direccion,localidad)"
                                        + " VALUES(?,?,?)",
                                    name,
                                    address,
                                    city);
                    audit(c, actor, "Alta de edificio", "edificios", id, name);
                    return null;
                });
    }

    public List<Row> buildings() {
        require(1, 2);
        return dao.list(
                "SELECT e.*, (SELECT COUNT(*) FROM propiedades p WHERE p.id_edificio=e.id_edificio)"
                    + " AS unidades FROM edificios e ORDER BY nombre");
    }

    public void agreement(
            int owner, Integer property, String professional, String conditions, LocalDate date) {
        int actor = require(1, 2);
        accessClient(owner);
        if (conditions.isBlank() || date == null)
            throw new IllegalArgumentException("Completá las condiciones y la fecha del convenio.");
        if (property != null && property(property).id("id_propietario") != owner)
            throw new IllegalArgumentException(
                    "El convenio debe corresponder al propietario de la propiedad.");
        db.transaction(
                c -> {
                    int id =
                            insert(
                                    c,
                                    "INSERT INTO"
                                        + " convenios(id_propietario,id_propiedad,profesional,condiciones,fecha)"
                                        + " VALUES(?,?,?,?,?)",
                                    owner,
                                    property,
                                    professional,
                                    conditions,
                                    date);
                    audit(c, actor, "Alta de convenio", "convenios", id, professional);
                    return null;
                });
    }

    public List<Row> agreements() {
        int actor = require(1, 2);
        return dao.list(
                "SELECT c.*,CONCAT(u.nombre,' ',u.apellido) AS propietario FROM convenios c JOIN"
                    + " usuarios u ON u.id_usuario=c.id_propietario "
                    + (role() == 2
                            ? "JOIN clientes cl ON cl.id_cliente=c.id_propietario WHERE"
                                    + " cl.id_agente_asignado=? "
                            : "")
                    + "ORDER BY fecha DESC",
                role() == 2 ? new Object[] {actor} : new Object[] {});
    }

    public List<Row> notifications() {
        int actor = require(1, 2, 3);
        return dao.list(
                "SELECT * FROM notificaciones WHERE id_usuario=? ORDER BY fecha DESC", actor);
    }

    public void readNotification(int id) {
        int actor = require(1, 2, 3);
        db.transaction(
                c -> {
                    exec(
                            c,
                            "UPDATE notificaciones SET leida=1 WHERE id_notificacion=? AND"
                                + " id_usuario=?",
                            id,
                            actor);
                    return null;
                });
    }

    public List<Row> activity() {
        int actor = require(1, 2);
        return dao.list(
                "SELECT a.*,CONCAT(u.nombre,' ',u.apellido) AS usuario FROM auditoria a JOIN"
                    + " usuarios u ON u.id_usuario=a.id_usuario "
                    + (role() == 2 ? "WHERE a.id_usuario=? " : "")
                    + "ORDER BY fecha DESC,id_evento DESC LIMIT 200",
                role() == 2 ? new Object[] {actor} : new Object[] {});
    }

    public List<Row> stateHistory(int property) {
        int actor = require(1, 2);
        if (role() == 2 && !agentCanAccessProperty(actor, property))
            throw new IllegalArgumentException("La propiedad no pertenece a tu cartera.");
        return dao.list(
                "SELECT h.*,e.nombre AS estado FROM historial_estados h JOIN estados_comerciales e"
                    + " ON e.id_estado_comercial=h.estado_nuevo WHERE id_propiedad=? ORDER BY fecha"
                    + " DESC",
                property);
    }

    public List<Row> attachments(int id, boolean privateDocs) {
        Row p = property(id);
        if (privateDocs) {
            int actor = require(1, 2, 3);
            if (role() == 3 && p.id("id_propietario") != actor)
                throw new IllegalArgumentException("La documentación es privada.");
            return dao.list(
                    "SELECT id_documento AS id,tipo,nombre,archivo FROM documentos WHERE"
                        + " id_propiedad=?",
                    id);
        }
        return dao.list(
                "SELECT id_multimedia AS id,tipo,url_archivo AS archivo FROM propiedad_multimedia"
                    + " WHERE id_propiedad=? ORDER BY orden,id_multimedia",
                id);
    }

    public void attach(int id, Path source, String type) throws Exception {
        int actor = require(1, 2, 3);
        Row p = property(id);
        if (role() == 3 && p.id("id_propietario") != actor)
            throw new IllegalArgumentException("Solo podés adjuntar archivos a tus propiedades.");
        Set<String> image = Set.of("jpg", "jpeg", "png"), video = Set.of("mp4", "webm");
        String name = source.getFileName().toString();
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        boolean media = type.equals("Imagen") || type.equals("Video");
        if (!Set.of("Imagen", "Video", "Plano", "Escritura", "Servicio", "Impuesto").contains(type))
            throw new IllegalArgumentException("Tipo de archivo inválido.");
        if (!(type.equals("Imagen")
                ? image.contains(ext)
                : type.equals("Video")
                        ? video.contains(ext)
                        : Set.of("pdf", "jpg", "jpeg", "png").contains(ext)))
            throw new IllegalArgumentException("Formato no admitido para " + type + ".");
        if (Files.size(source) > 50L * 1024 * 1024)
            throw new IllegalArgumentException("El archivo supera los 50 MB.");
        Files.createDirectories(db.storage);
        String file = UUID.randomUUID() + "." + ext;
        Path destination = db.storage.resolve(file);
        Files.copy(source, destination);
        try {
            db.transaction(
                    c -> {
                        Row current = editable(c, id, actor);
                        if (!Set.of(1, 4).contains(current.id("id_estado_verificacion")))
                            throw new IllegalArgumentException(
                                    "Guardá la propiedad como borrador antes de cambiar sus"
                                        + " archivos.");
                        if (media)
                            exec(
                                    c,
                                    "INSERT INTO"
                                        + " propiedad_multimedia(id_propiedad,url_archivo,tipo,orden)"
                                        + " VALUES(?,?,?,?)",
                                    id,
                                    file,
                                    type,
                                    1);
                        else
                            exec(
                                    c,
                                    "INSERT INTO"
                                        + " documentos(id_propiedad,tipo,nombre,archivo,id_usuario)"
                                        + " VALUES(?,?,?,?,?)",
                                    id,
                                    type,
                                    name,
                                    file,
                                    actor);
                        audit(c, actor, "Archivo adjunto", "propiedades", id, type + ": " + name);
                        return null;
                    });
        } catch (Exception e) {
            Files.deleteIfExists(destination);
            throw e;
        }
    }

    public Path attachmentPath(int property, int attachment, boolean privateDoc) {
        Row r =
                attachments(property, privateDoc).stream()
                        .filter(x -> x.id("id") == attachment)
                        .findFirst()
                        .orElseThrow();
        Path p = db.storage.resolve(r.text("archivo")).normalize();
        if (!p.startsWith(db.storage))
            throw new IllegalArgumentException("Ruta de archivo inválida.");
        return p;
    }

    public void bootstrapAdmin(Map<String, String> d, String password) {
        if (!dao.list("SELECT id_usuario FROM usuarios WHERE id_rol=1").isEmpty())
            throw new IllegalArgumentException("Ya existe un administrador.");
        createUser(d, password, 1, false);
    }

    public boolean needsAdmin() {
        return dao.list("SELECT id_usuario FROM usuarios WHERE id_rol=1").isEmpty();
    }
}
