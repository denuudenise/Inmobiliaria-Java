package ar.edu.nuevohogar.backend;

import static ar.edu.nuevohogar.persistencia.dao.ConsultasSQL.*;

import ar.edu.nuevohogar.persistencia.dao.*;
import ar.edu.nuevohogar.backend.autenticacion.ClavesSeguras;
import ar.edu.nuevohogar.modelo.Cliente;
import ar.edu.nuevohogar.modelo.Propiedad;
import ar.edu.nuevohogar.modelo.Registro;
import ar.edu.nuevohogar.modelo.SolicitudPublicacion;
import ar.edu.nuevohogar.modelo.Usuario;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Reglas del negocio compartidas por todas las pantallas; permisos comprobados aquí. */
public final class ServicioInmobiliaria {
    public final ConexionBaseDatos db;
    private final ConsultasSQL dao;
    private final UsuarioDAO usuarioDao;
    private final PropiedadDAO propiedadDao;
    private final ClienteDAO clienteDao;
    private final SolicitudDAO solicitudDao;
    private final GestionComercialDAO gestionDao;
    private Integer sessionId;
    private static final String PROPERTY =
            "SELECT p.*,t.nombre AS tipo,e.nombre AS verificacion,c.nombre AS"
                + " comercial,CASE WHEN u.id_rol=1 THEN 'Admin' ELSE CONCAT(u.nombre,' ',u.apellido) END AS propietario FROM propiedades p"
                + " JOIN tipos_propiedad t ON t.id_tipo_propiedad=p.id_tipo_propiedad JOIN"
                + " estados_verificacion e ON e.id_estado_verificacion=p.id_estado_verificacion"
                + " JOIN estados_comerciales c ON c.id_estado_comercial=p.id_estado_comercial LEFT"
                + " JOIN usuarios u ON u.id_usuario=p.id_propietario ";

    public ServicioInmobiliaria(ConexionBaseDatos db) {
        this.db = db;
        dao = new ConsultasSQL(db);
        usuarioDao = new UsuarioDAO(db);
        propiedadDao = new PropiedadDAO(db);
        clienteDao = new ClienteDAO(db);
        solicitudDao = new SolicitudDAO(db);
        gestionDao = new GestionComercialDAO(db);
    }

    public boolean signedIn() {
        return sessionId != null;
    }

    public Registro current() {
        if (sessionId == null) throw new IllegalArgumentException("Iniciá sesión para continuar.");
        return usuarioDao.findActiveById(sessionId)
                .map(Usuario::toRow)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta está inactiva."));
    }

    public int role() {
        return signedIn() ? current().id("id_rol") : 0;
    }

    private int require(int... roles) {
        Registro u = current();
        for (int r : roles) if (u.id("id_rol") == r) return u.id("id_usuario");
        throw new IllegalArgumentException("Tu rol no tiene permiso para realizar esta acción.");
    }

    public void logout() {
        sessionId = null;
    }

    public void login(String email, String password) {
        Usuario usuario =
                usuarioDao.findActiveByEmail(email)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Correo o contraseña incorrectos."));
        if (!ClavesSeguras.verify(password, usuario.passwordHash()))
            throw new IllegalArgumentException("Correo o contraseña incorrectos.");
        sessionId = usuario.idUsuario();
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
        for (Registro r : list(c, "SELECT id_usuario FROM usuarios WHERE id_rol=1 AND activo=1"))
            notify(c, r.id("id_usuario"), text);
    }

    private static boolean tableExists(Connection c, String table) throws SQLException {
        try (var rs = c.getMetaData().getTables(c.getCatalog(), null, table, new String[] {"TABLE"})) {
            if (rs.next()) return true;
        }
        try (var rs = c.getMetaData().getTables(c.getCatalog(), null, table.toUpperCase(Locale.ROOT), new String[] {"TABLE"})) {
            return rs.next();
        }
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
        if (role == 1)
            throw new IllegalArgumentException("El sistema utiliza una sola cuenta de administración.");
        return createUser(data, password, role, true);
    }

    private int createUser(Map<String, String> d, String password, int role, boolean admin) {
        String name = required(d, "nombre", 100),
                last = required(d, "apellido", 100),
                dni = required(d, "dni", 20),
                mail = required(d, "email", 150).toLowerCase(Locale.ROOT);
        email(mail);
        String hash = ClavesSeguras.hash(password);
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
                            UsuarioDAO.insert(
                                    c,
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
                        ClienteDAO.insert(c, id, assignedAgent);
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
                    UsuarioDAO.updateProfile(
                            c,
                            actor,
                            name,
                            last,
                            optional(d, "telefono"),
                            optional(d, "direccion"),
                            optional(d, "localidad"));
                    audit(c, actor, "Edición de perfil", "usuarios", actor, "");
                    return null;
                });
    }

    public void changePassword(String old, String replacement) {
        int actor = require(1, 2, 3);
        Registro u = dao.one("SELECT password_hash FROM usuarios WHERE id_usuario=?", actor);
        if (!ClavesSeguras.verify(old, u.text("password_hash")))
            throw new IllegalArgumentException("La contraseña actual no coincide.");
        String hash = ClavesSeguras.hash(replacement);
        db.transaction(
                c -> {
                    exec(c, "UPDATE usuarios SET password_hash=? WHERE id_usuario=?", hash, actor);
                    return null;
                });
    }

    public void resetUserPassword(int userId, String replacement) {
        int actor = require(1);
        if (userId == actor)
            throw new IllegalArgumentException(
                    "Para cambiar tu propia contraseña, usá Mi perfil.");
        String hash = ClavesSeguras.hash(replacement);
        db.transaction(
                c -> {
                    if (list(c, "SELECT id_usuario FROM usuarios WHERE id_usuario=?", userId).isEmpty())
                        throw new IllegalArgumentException("No existe ese usuario.");
                    exec(c, "UPDATE usuarios SET password_hash=? WHERE id_usuario=?", hash, userId);
                    audit(c, actor, "Restablecimiento de contraseña", "usuarios", userId,
                            "Contraseña temporal asignada por administración");
                    return null;
                });
    }

    public List<Registro> lookup(String table) {
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

    public List<Registro> clients() {
        int actor = require(1, 2);
        List<Cliente> clientes =
                role() == 2
                        ? clienteDao.listAssignedToAgent(actor)
                        : clienteDao.listForAdministrator();
        return clientes.stream().map(Cliente::toRow).toList();
    }

    public List<Registro> users() {
        require(1);
        return usuarioDao.findAllWithRole();
    }

    public List<Registro> agents() {
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

    public List<Registro> properties(
            boolean catalog,
            String query,
            Integer type,
            Integer operation,
            BigDecimal max,
            String currency,
            int bedrooms,
            int capacity,
            Set<Integer> features) {
        int actor = 0;
        int activeRole = signedIn() ? role() : 0;
        if (!catalog) actor = require(1, 2, 3);
        return propiedadDao.search(
                        catalog,
                        query,
                        type,
                        operation,
                        max,
                        currency,
                        bedrooms,
                        capacity,
                        features,
                        activeRole,
                        actor)
                .stream()
                .map(Propiedad::toRow)
                .toList();
    }

    public List<Registro> allProperties() {
        return properties(false, "", null, null, null, "USD", 0, 0, Set.of());
    }

    public Registro property(int id) {
        Registro p =
                propiedadDao.findById(id)
                        .map(Propiedad::toRow)
                        .orElseThrow(
                                () -> new IllegalArgumentException("La propiedad no existe."));
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

    private boolean agentCanAccessProperty(Connection connection, int agent, int property)
            throws SQLException {
        return !list(
                        connection,
                        "SELECT id_propiedad FROM propiedades p WHERE p.id_propiedad=? AND"
                            + " (p.id_agente_creador=? OR EXISTS(SELECT 1 FROM clientes c WHERE"
                            + " c.id_cliente=p.id_propietario AND c.id_agente_asignado=?))",
                        property,
                        agent,
                        agent)
                .isEmpty();
    }

    public List<Registro> offers(int id) {
        property(id);
        return dao.list(
                "SELECT o.*,t.nombre AS operacion FROM propiedad_ofertas o JOIN tipos_transaccion t"
                    + " ON t.id_tipo_transaccion=o.id_tipo_transaccion WHERE id_propiedad=? ORDER"
                    + " BY o.id_tipo_transaccion",
                id);
    }

    public List<Registro> features(int id) {
        property(id);
        return dao.list(
                "SELECT c.id_caracteristica AS id,c.nombre AS etiqueta FROM"
                    + " propiedad_caracteristicas pc JOIN caracteristicas c ON"
                    + " c.id_caracteristica=pc.id_caracteristica WHERE id_propiedad=?",
                id);
    }

    private Registro editable(Connection c, int id, int actor) throws Exception {
        Registro p =
                list(c, "SELECT * FROM propiedades WHERE id_propiedad=? FOR UPDATE", id).stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Propiedad inexistente"));
        if (role() == 3 && p.id("id_propietario") != actor)
            throw new IllegalArgumentException("Solo podés editar tus propiedades.");
        if (role() == 2 && !agentCanAccessProperty(c, actor, id))
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
        Propiedad.Datos propertyData =
                new Propiedad.Datos(
                        type,
                        offers.getFirst().type(),
                        title,
                        address,
                        city,
                        offers.getFirst().price(),
                        offers.getFirst().currency(),
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
                        "1".equals(d.get("apto_profesional")));
        return db.transaction(
                c -> {
                    if (list(c, "SELECT id_cliente FROM clientes WHERE id_cliente=?", owner)
                            .isEmpty())
                        throw new IllegalArgumentException(
                                "El propietario debe tener una ficha de cliente.");
                    int pid;
                    if (id == null) {
                        pid = PropiedadDAO.insert(c, propertyData);
                    } else {
                        Registro previous = editable(c, id, actor);
                        if (Set.of(3, 4).contains(previous.id("id_estado_comercial")))
                            throw new IllegalArgumentException(
                                    "Reactivá la propiedad antes de editar una operación cerrada.");
                        pid = id;
                        if (tableExists(c, "historial_precios")) {
                            int currentProperty = pid;
                            for (Registro oldOffer : list(c, "SELECT id_tipo_transaccion,precio,moneda FROM propiedad_ofertas WHERE id_propiedad=?", currentProperty)) {
                                for (Offer newOffer : offers) {
                                    if (newOffer.type() == oldOffer.id("id_tipo_transaccion")
                                            && (newOffer.price().compareTo(oldOffer.money("precio")) != 0 || !newOffer.currency().equals(oldOffer.text("moneda"))))
                                        exec(c, "INSERT INTO historial_precios(id_propiedad,id_tipo_transaccion,id_usuario,precio_anterior,precio_nuevo,moneda,motivo) VALUES(?,?,?,?,?,?,?)",
                                                currentProperty, newOffer.type(), actor, oldOffer.money("precio"), newOffer.price(), newOffer.currency(), "Actualización de la ficha de propiedad");
                                    if (newOffer.type() == oldOffer.id("id_tipo_transaccion")
                                            && (newOffer.price().compareTo(oldOffer.money("precio")) != 0 || !newOffer.currency().equals(oldOffer.text("moneda")))
                                            && tableExists(c, "auditoria_cambios"))
                                        exec(c, "INSERT INTO auditoria_cambios(entidad,id_registro,campo,valor_anterior,valor_nuevo,motivo) VALUES('propiedades',?,?,?, ?,?)",
                                                currentProperty, "precio_operacion_" + newOffer.type(), oldOffer.money("precio") + " " + oldOffer.text("moneda"), newOffer.price() + " " + newOffer.currency(), "Actualización desde la ficha");
                                }
                            }
                        }
                        PropiedadDAO.update(c, pid, propertyData);
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
        Registro p = editable(c, id, actor);
        if (!Set.of(1, 4).contains(p.id("id_estado_verificacion")))
            throw new IllegalArgumentException(
                    "Solo se envían borradores o propiedades rechazadas.");
        exec(
                c,
                "UPDATE propiedades SET id_estado_verificacion=2,motivo_rechazo=NULL WHERE"
                    + " id_propiedad=?",
                id);
        exec(c, "INSERT INTO checklist_publicacion(id_propiedad,id_requisito) "
                + "SELECT ?,r.id_requisito FROM requisitos_publicacion r "
                + "WHERE r.activo=1 AND (r.id_tipo_propiedad IS NULL OR r.id_tipo_propiedad=?) "
                + "AND NOT EXISTS (SELECT 1 FROM checklist_publicacion cp WHERE cp.id_propiedad=? AND cp.id_requisito=r.id_requisito)",
                id, p.id("id_tipo_propiedad"), id);
        SolicitudDAO.insert(c, id, actor);
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

    public List<Registro> requests() {
        int actor = require(1, 2, 3);
        return solicitudDao.listForRole(role(), actor).stream()
                .map(SolicitudPublicacion::toRow)
                .toList();
    }

    public List<Registro> checklistPublicacion(int propertyId) {
        int actor = require(1, 2);
        if (role() == 2 && !agentCanAccessProperty(actor, propertyId))
            throw new IllegalArgumentException("La propiedad no pertenece a tu cartera.");
        return gestionDao.checklist(propertyId);
    }

    public void actualizarChecklistPublicacion(int propertyId, int requirement, Integer document,
            String status, String notes) {
        int actor = require(1);
        if (!Set.of("Pendiente", "Verificado", "Falta documentación").contains(status))
            throw new IllegalArgumentException("Estado documental inválido.");
        db.transaction(c -> {
            if (document != null && list(c, "SELECT id_documento FROM documentos WHERE id_documento=? AND id_propiedad=?", document, propertyId).isEmpty())
                throw new IllegalArgumentException("El documento seleccionado no pertenece a esta propiedad.");
            if (gestionDao.actualizarChecklist(c, propertyId, requirement, document, status, notes, actor) == 0)
                throw new IllegalArgumentException("No existe ese requisito para la propiedad.");
            audit(c, actor, "Checklist documental actualizado", "propiedades", propertyId, status + ": requisito " + requirement);
            return null;
        });
    }

    public void review(int request, boolean approve, String comment) {
        int actor = require(1);
        if (!approve && (comment == null || comment.isBlank()))
            throw new IllegalArgumentException("Indicá el motivo del rechazo.");
        db.transaction(
                c -> {
                    Registro initial =
                            list(c, "SELECT * FROM publicaciones WHERE id_publicacion=?", request)
                                    .getFirst();
                    int pid = initial.id("id_propiedad");
                    editable(c, pid, actor);
                    Registro s =
                            list(
                                            c,
                                            "SELECT * FROM publicaciones WHERE id_publicacion=? FOR"
                                                + " UPDATE",
                                            request)
                                    .getFirst();
                    if (!s.text("estado").equals("Pendiente"))
                        throw new IllegalArgumentException(
                                "La solicitud ya fue revisada o reemplazada.");
                    if (approve) {
                        List<Registro> incomplete = list(c,
                                "SELECT r.nombre FROM checklist_publicacion cp JOIN requisitos_publicacion r ON r.id_requisito=cp.id_requisito "
                                        + "WHERE cp.id_propiedad=? AND r.obligatorio=1 AND cp.estado<>'Verificado'", pid);
                        if (!incomplete.isEmpty())
                            throw new IllegalArgumentException("No se puede publicar todavía. Verificá en el checklist: "
                                    + String.join(", ", incomplete.stream().map(r -> r.text("nombre")).toList()) + ".");
                    }
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
                    Registro p = editable(c, id, actor);
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

    private static void setState(Connection c, Registro p, int actor, int state, String reason)
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
                    Registro p = editable(c, id, actor);
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
                    Registro o =
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

    public List<Registro> operations() {
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
                "SELECT c.*,p.titulo,t.nombre AS operacion,CASE WHEN u.id_rol=1 THEN 'Admin' ELSE CONCAT(u.nombre,' ',u.apellido) END AS"
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
        List<Registro> ficha = dao.list("SELECT * FROM clientes WHERE id_cliente=?", client);
        if (ficha.isEmpty())
            throw new IllegalArgumentException("Ficha de cliente inexistente.");
        if (role() == 2 && ficha.getFirst().id("id_agente_asignado") != actor)
            throw new IllegalArgumentException("Este cliente no está asignado a tu cartera.");
    }

    public List<Registro> contacts(int client) {
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

    public List<Registro> interests(int client) {
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
                    if (role() == 1) {
                        Integer previousAgent = list(c, "SELECT id_agente_asignado FROM clientes WHERE id_cliente=? FOR UPDATE", client)
                                .stream().findFirst().map(r -> r.get("id_agente_asignado") == null ? null : r.id("id_agente_asignado")).orElse(null);
                        if (!Objects.equals(previousAgent, agent) && tableExists(c, "historial_asignaciones"))
                            exec(c, "INSERT INTO historial_asignaciones(tipo_entidad,id_entidad,agente_anterior,agente_nuevo,id_usuario_cambio,motivo) VALUES('CLIENTE',?,?,?,?,?)",
                                    client, previousAgent, agent, actor, "Reasignación desde la ficha de cliente");
                        if (!Objects.equals(previousAgent, agent) && tableExists(c, "auditoria_cambios"))
                            exec(c, "INSERT INTO auditoria_cambios(entidad,id_registro,campo,valor_anterior,valor_nuevo,motivo) VALUES('clientes',?,'agente_asignado',?,?,?)",
                                    client, Objects.toString(previousAgent, "Sin asignar"), Objects.toString(agent, "Sin asignar"), "Reasignación desde la ficha de cliente");
                        exec(
                                c,
                                "UPDATE clientes SET id_agente_asignado=? WHERE id_cliente=?",
                                agent,
                                client);
                    }
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

    public List<Registro> clientRoles(int client) {
        accessClient(client);
        return dao.list("SELECT tipo_rol FROM roles_cliente WHERE id_cliente=?", client);
    }

    public Registro preference(int client) {
        accessClient(client);
        return dao
                .list(
                        "SELECT * FROM preferencias_cliente WHERE id_cliente=? ORDER BY"
                            + " id_preferencia DESC",
                        client)
                .stream()
                .findFirst()
                .orElse(new Registro());
    }

    public List<Registro> preferenceFeatures(int client) {
        Registro p = preference(client);
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
                    for (Registro old :
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

    public List<Registro> matches(int client) {
        Registro p = preference(client);
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

    public List<Registro> buildings() {
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

    public List<Registro> agreements() {
        int actor = require(1, 2);
        return dao.list(
                "SELECT c.*,CASE WHEN u.id_rol=1 THEN 'Admin' ELSE CONCAT(u.nombre,' ',u.apellido) END AS propietario FROM convenios c JOIN"
                    + " usuarios u ON u.id_usuario=c.id_propietario "
                    + (role() == 2
                            ? "JOIN clientes cl ON cl.id_cliente=c.id_propietario WHERE"
                                    + " cl.id_agente_asignado=? "
                            : "")
                    + "ORDER BY fecha DESC",
                role() == 2 ? new Object[] {actor} : new Object[] {});
    }

    public List<Registro> notifications() {
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

    public List<Registro> activity() {
        int actor = require(1, 2);
        return dao.list(
                "SELECT a.*,CASE WHEN u.id_rol=1 THEN 'Admin' ELSE CONCAT(u.nombre,' ',u.apellido) END AS usuario FROM auditoria a JOIN"
                    + " usuarios u ON u.id_usuario=a.id_usuario "
                    + (role() == 2 ? "WHERE a.id_usuario=? " : "")
                    + "ORDER BY fecha DESC,id_evento DESC LIMIT 200",
                role() == 2 ? new Object[] {actor} : new Object[] {});
    }

    public List<Registro> stateHistory(int property) {
        int actor = require(1, 2);
        if (role() == 2 && !agentCanAccessProperty(actor, property))
            throw new IllegalArgumentException("La propiedad no pertenece a tu cartera.");
        return dao.list(
                "SELECT h.*,e.nombre AS estado FROM historial_estados h JOIN estados_comerciales e"
                    + " ON e.id_estado_comercial=h.estado_nuevo WHERE id_propiedad=? ORDER BY fecha"
                    + " DESC",
                property);
    }

    /** Lista visitas visibles según el rol activo. */
    public List<Registro> visitas() {
        int actor = require(1, 2, 3);
        return switch (role()) {
            case 1 -> gestionDao.todasLasVisitas();
            case 2 -> gestionDao.visitasDeAgente(actor);
            default -> gestionDao.visitasDeCliente(actor);
        };
    }

    /** Cliente solicita una visita; el agente sale de la asignación registrada en clientes. */
    public void solicitarVisita(int propertyId, LocalDateTime when, String notes) {
        int actor = require(3);
        if (when == null || !when.isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("Elegí una fecha y hora futuras.");
        Registro p = property(propertyId);
        Registro assignment = dao.one("SELECT id_agente_asignado FROM clientes WHERE id_cliente=?", actor);
        if (assignment.get("id_agente_asignado") == null)
            throw new IllegalArgumentException("Todavía no tenés un agente asignado para coordinar la visita.");
        int agent = assignment.id("id_agente_asignado");
        if (p.id("id_estado_verificacion") != 3 || !Set.of(1, 2).contains(p.id("id_estado_comercial")))
            throw new IllegalArgumentException("Solo se pueden visitar propiedades publicadas y disponibles.");
        final int client = actor;
        db.transaction(c -> {
            gestionDao.crearVisita(c, propertyId, client, agent, when, optional(Map.of("notas", notes), "notas"));
            notify(c, agent, "Nueva solicitud de visita para la propiedad #" + propertyId + ".");
            audit(c, actor, "Solicitud de visita", "visitas", propertyId, when.toString());
            return null;
        });
    }

    /** El agente asignado coordina una visita con su cliente. */
    public void agendarVisita(int clientId, int propertyId, LocalDateTime when, String notes) {
        int agentId = require(1, 2);
        if (role() == 2 && !clientAccessible(agentId, clientId))
            throw new IllegalArgumentException("Ese cliente no pertenece a tu cartera.");
        if (when == null || !when.isAfter(LocalDateTime.now())) throw new IllegalArgumentException("Elegí una fecha y hora futuras.");
        property(propertyId);
        int assigned = role() == 1 ? assignedAgent(clientId) : agentId;
        db.transaction(c -> {
            gestionDao.crearVisita(c, propertyId, clientId, assigned, when, notes == null ? "" : notes.trim());
            notify(c, clientId, "Tu agente coordinó una visita para la propiedad #" + propertyId + ".");
            audit(c, agentId, "Visita programada", "visitas", propertyId, when.toString());
            return null;
        });
    }

    public void actualizarVisita(int id, String estado, String resultado) {
        int actor = require(1, 2);
        if (!Set.of("Programada", "Realizada", "Cancelada", "Reprogramar").contains(estado)) throw new IllegalArgumentException("Estado de visita inválido.");
        Registro visit = dao.one("SELECT * FROM visitas WHERE id_visita=?", id);
        if (role() == 2 && visit.id("id_agente") != actor) throw new IllegalArgumentException("La visita no pertenece a tu cartera.");
        db.transaction(c -> {
            gestionDao.cambiarEstadoVisita(c, id, estado, resultado == null ? "" : resultado.trim());
            audit(c, actor, "Actualización de visita", "visitas", id, estado);
            return null;
        });
    }

    public List<Registro> tareasSeguimiento() {
        int actor = require(1, 2);
        return role() == 1 ? gestionDao.todasLasTareas() : gestionDao.tareas(actor);
    }

    public int crearTareaSeguimiento(int client, Integer property, String title, String detail, LocalDateTime due) {
        int actor = require(1, 2);
        if (role() == 2 && !clientAccessible(actor, client)) throw new IllegalArgumentException("Ese cliente no pertenece a tu cartera.");
        if (due == null || title == null || title.isBlank()) throw new IllegalArgumentException("Completá el título y el vencimiento del recordatorio.");
        return db.transaction(c -> {
            int id = gestionDao.crearTarea(c, role() == 1 ? assignedAgent(client) : actor, client, property, title.trim(), detail == null ? "" : detail.trim(), due);
            audit(c, actor, "Recordatorio creado", "tareas_seguimiento", id, title.trim());
            return id;
        });
    }

    public void completarTarea(int id) {
        int actor = require(1, 2);
        db.transaction(c -> {
            if (gestionDao.completarTarea(c, id, actor, role() == 1) == 0) throw new IllegalArgumentException("El recordatorio ya se completó o no está asignado a tu usuario.");
            audit(c, actor, "Recordatorio completado", "tareas_seguimiento", id, "");
            return null;
        });
    }

    private int assignedAgent(int client) {
        Registro row = dao.one("SELECT id_agente_asignado FROM clientes WHERE id_cliente=?", client);
        if (row.get("id_agente_asignado") == null) throw new IllegalArgumentException("El cliente no tiene un agente asignado.");
        return row.id("id_agente_asignado");
    }

    private boolean clientAccessible(int agent, int client) {
        return !dao.list("SELECT id_cliente FROM clientes WHERE id_cliente=? AND id_agente_asignado=?", client, agent).isEmpty();
    }

    public List<Registro> historialPrecios(int propertyId) {
        int actor = require(1, 2);
        if (role() == 2 && !agentCanAccessProperty(actor, propertyId))
            throw new IllegalArgumentException("La propiedad no pertenece a tu cartera.");
        return dao.list("SELECT h.*,t.nombre AS operacion,CONCAT(u.nombre,' ',u.apellido) AS usuario "
                + "FROM historial_precios h JOIN tipos_transaccion t ON t.id_tipo_transaccion=h.id_tipo_transaccion "
                + "JOIN usuarios u ON u.id_usuario=h.id_usuario WHERE h.id_propiedad=? ORDER BY h.fecha DESC", propertyId);
    }

    public List<Registro> historialAsignaciones(int client) {
        require(1);
        return dao.list("SELECT h.*,CONCAT(u1.nombre,' ',u1.apellido) AS agente_anterior_nombre,"
                + "CONCAT(u2.nombre,' ',u2.apellido) AS agente_nuevo_nombre,"
                + "CONCAT(actor.nombre,' ',actor.apellido) AS usuario_cambio FROM historial_asignaciones h "
                + "LEFT JOIN usuarios u1 ON u1.id_usuario=h.agente_anterior "
                + "LEFT JOIN usuarios u2 ON u2.id_usuario=h.agente_nuevo "
                + "JOIN usuarios actor ON actor.id_usuario=h.id_usuario_cambio "
                + "WHERE h.tipo_entidad='CLIENTE' AND h.id_entidad=? ORDER BY h.fecha DESC", client);
    }

    public List<Registro> contactosAdicionales(int client) {
        accessClient(client);
        return dao.list("SELECT * FROM contactos_adicionales_cliente WHERE id_cliente=? ORDER BY es_principal DESC,tipo,valor", client);
    }

    public void guardarContactoAdicional(int client, String type, String value) {
        int actor = require(1, 2, 3);
        accessClient(client);
        if (!Set.of("Teléfono", "Correo", "WhatsApp", "Alternativo").contains(type))
            throw new IllegalArgumentException("Elegí un tipo de contacto válido.");
        if (value == null || value.isBlank() || value.trim().length() > 255)
            throw new IllegalArgumentException("Completá el dato de contacto (máximo 255 caracteres).");
        db.transaction(c -> {
            int id = insert(c, "INSERT INTO contactos_adicionales_cliente(id_cliente,tipo,valor) VALUES(?,?,?)",
                    client, type, value.trim());
            audit(c, actor, "Contacto adicional creado", "contactos_adicionales_cliente", id, type);
            return null;
        });
    }

    public List<Registro> configuracionAlertas() {
        require(1);
        return dao.list("SELECT * FROM configuracion_alertas ORDER BY clave");
    }

    public void guardarConfiguracionAlertas(Map<String, String> values) {
        int actor = require(1);
        Map<String, String> allowed = Map.of(
                "dias_cliente_sin_contacto", "Días sin contacto del cliente",
                "dias_propiedad_sin_actividad", "Días sin actividad de la propiedad",
                "dias_aviso_reserva", "Días de aviso previo a vencer una reserva");
        db.transaction(c -> {
            for (var setting : allowed.entrySet()) {
                int days = integer(values, setting.getKey(), 1, 365);
                if (exec(c, "UPDATE configuracion_alertas SET valor=?,actualizado_por=? WHERE clave=?",
                        days, actor, setting.getKey()) == 0)
                    throw new IllegalArgumentException("No está configurada la alerta: " + setting.getValue());
            }
            audit(c, actor, "Configuración de alertas actualizada", "configuracion_alertas", actor, "");
            return null;
        });
    }

    /** Genera alertas pendientes al abrir el dashboard, sin duplicar avisos no leídos. */
    public int generarAlertasDeSeguimiento() {
        int actor = require(1, 2);
        Map<String, Integer> days = new HashMap<>();
        configuracionAlertasInterna().forEach(r -> days.put(r.text("clave"), Integer.parseInt(r.text("valor"))));
        return db.transaction(c -> {
            int generated = 0;
            String scope = role() == 2 ? " AND cl.id_agente_asignado=?" : "";
            LocalDate clientCutoff = LocalDate.now().minusDays(days.get("dias_cliente_sin_contacto"));
            Object[] args = role() == 2 ? new Object[] {clientCutoff, actor} : new Object[] {clientCutoff};
            for (Registro row : list(c, "SELECT cl.id_cliente,cl.id_agente_asignado FROM clientes cl "
                    + "JOIN usuarios u ON u.id_usuario=cl.id_cliente WHERE cl.id_agente_asignado IS NOT NULL "
                    + "AND CAST(COALESCE((SELECT MAX(ct.fecha) FROM contactos ct WHERE ct.id_cliente=cl.id_cliente),u.fecha_registro) AS DATE)<=?" + scope, args)) {
                String message = "Alerta de seguimiento: cliente #" + row.id("id_cliente") + " lleva " + days.get("dias_cliente_sin_contacto") + " días sin contacto.";
                generated += notificarSiNoExiste(c, row.id("id_agente_asignado"), message);
            }
            String propertyScope = role() == 2 ? " AND (p.id_agente_creador=? OR cl.id_agente_asignado=?)" : "";
            LocalDate propertyCutoff = LocalDate.now().minusDays(days.get("dias_propiedad_sin_actividad"));
            Object[] propertyArgs = role() == 2 ? new Object[] {propertyCutoff, actor, actor} : new Object[] {propertyCutoff};
            for (Registro row : list(c, "SELECT p.id_propiedad,COALESCE(p.id_agente_creador,cl.id_agente_asignado) AS agente "
                    + "FROM propiedades p LEFT JOIN clientes cl ON cl.id_cliente=p.id_propietario "
                    + "WHERE p.id_estado_verificacion=3 AND p.id_estado_comercial=1 "
                    + "AND CAST(COALESCE((SELECT MAX(h.fecha) FROM historial_estados h WHERE h.id_propiedad=p.id_propiedad),p.fecha_creacion) AS DATE)<=?" + propertyScope, propertyArgs)) {
                String message = "Alerta de actividad: propiedad #" + row.id("id_propiedad") + " lleva " + days.get("dias_propiedad_sin_actividad") + " días sin novedades.";
                if (row.get("agente") != null) generated += notificarSiNoExiste(c, row.id("agente"), message);
            }
            for (Registro row : list(c, "SELECT id_reserva FROM reservas WHERE estado='Pendiente' AND fecha_limite<=?",
                    LocalDateTime.now().plusDays(days.get("dias_aviso_reserva")))) {
                generated += notificarAdministradoresSiNoExiste(c, "Alerta: reserva #" + row.id("id_reserva") + " próxima a vencer.");
            }
            return generated;
        });
    }

    private List<Registro> configuracionAlertasInterna() {
        return dao.list("SELECT clave,valor FROM configuracion_alertas");
    }

    private static int notificarSiNoExiste(Connection c, int user, String message) throws Exception {
        if (!list(c, "SELECT id_notificacion FROM notificaciones WHERE id_usuario=? AND mensaje=? AND leida=0", user, message).isEmpty()) return 0;
        notify(c, user, message);
        return 1;
    }

    private static int notificarAdministradoresSiNoExiste(Connection c, String message) throws Exception {
        int added = 0;
        for (Registro admin : list(c, "SELECT id_usuario FROM usuarios WHERE id_rol=1 AND activo=1"))
            added += notificarSiNoExiste(c, admin.id("id_usuario"), message);
        return added;
    }

    public List<Registro> ofertasComerciales() {
        int actor = require(1, 2, 3);
        return switch (role()) {
            case 1 -> gestionDao.todasLasOfertas();
            case 2 -> gestionDao.ofertasAgente(actor);
            default -> gestionDao.ofertasCliente(actor);
        };
    }

    public int crearOferta(int propertyId, int operation, BigDecimal amount, String currency, LocalDate expiration, String notes) {
        int client = require(3);
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || !Set.of("USD", "ARS").contains(currency))
            throw new IllegalArgumentException("Ingresá un monto y una moneda válidos.");
        Registro p = property(propertyId);
        if (p.id("id_estado_verificacion") != 3 || p.id("id_estado_comercial") != 1)
            throw new IllegalArgumentException("Solo se puede ofertar por propiedades publicadas y disponibles.");
        Registro listing = dao.one("SELECT precio,moneda FROM propiedad_ofertas WHERE id_propiedad=? AND id_tipo_transaccion=?", propertyId, operation);
        if (!listing.text("moneda").equals(currency)) throw new IllegalArgumentException("La oferta debe usar la moneda publicada para esta operación.");
        int agent = assignedAgent(client);
        if (expiration != null && expiration.isBefore(LocalDate.now())) throw new IllegalArgumentException("La fecha de vencimiento no puede estar en el pasado.");
        return db.transaction(c -> {
            int id = gestionDao.crearOferta(c, propertyId, client, agent, operation, amount, currency, expiration, notes == null ? "" : notes.trim());
            notify(c, agent, "El cliente envió una oferta para la propiedad #" + propertyId + ".");
            audit(c, client, "Oferta enviada", "ofertas_cliente", id, amount + " " + currency);
            return id;
        });
    }

    public void revisarOferta(int offerId, boolean accept, String comment) {
        int actor = require(1, 2);
        Registro offer = dao.one("SELECT * FROM ofertas_cliente WHERE id_oferta=?", offerId);
        if (role() == 2 && offer.id("id_agente") != actor) throw new IllegalArgumentException("La oferta no pertenece a tu cartera.");
        db.transaction(c -> {
            String status = accept ? "Aceptada" : "Rechazada";
            if (gestionDao.revisarOferta(c, offerId, actor, status, comment == null ? "" : comment.trim()) == 0)
                throw new IllegalArgumentException("La oferta ya fue revisada.");
            notify(c, offer.id("id_cliente"), "Tu oferta #" + offerId + " fue " + status.toLowerCase(Locale.ROOT) + ".");
            audit(c, actor, "Oferta revisada", "ofertas_cliente", offerId, status);
            return null;
        });
    }

    public List<Registro> reservasComerciales() {
        int actor = require(1, 2, 3);
        return switch (role()) {
            case 1 -> gestionDao.reservas();
            case 2 -> gestionDao.reservasAgente(actor);
            default -> gestionDao.reservasCliente(actor);
        };
    }

    public int solicitarReserva(int offerId, LocalDateTime due, String notes) {
        int agent = require(1, 2);
        if (due == null || !due.isAfter(LocalDateTime.now())) throw new IllegalArgumentException("Elegí un vencimiento futuro para la reserva.");
        Registro offer = dao.one("SELECT * FROM ofertas_cliente WHERE id_oferta=? AND estado='Aceptada'", offerId);
        if (role() == 2 && (offer.id("id_agente") != agent || !clientAccessible(agent, offer.id("id_cliente"))))
            throw new IllegalArgumentException("La oferta no pertenece a tu cartera.");
        return db.transaction(c -> {
            int id = gestionDao.crearReserva(c, offer, offer.id("id_agente"), due, notes == null ? "" : notes.trim());
            notifyStaff(c, "Solicitud de reserva #" + id + " pendiente de autorización.");
            audit(c, agent, "Reserva solicitada", "reservas", id, "Oferta #" + offerId);
            return id;
        });
    }

    public void resolverReserva(int reservationId, boolean approve) {
        int actor = require(1);
        db.transaction(c -> {
            Registro reservation = list(c, "SELECT * FROM reservas WHERE id_reserva=? FOR UPDATE", reservationId)
                    .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("La reserva no existe."));
            String status = approve ? "Aprobada" : "Rechazada";
            if (gestionDao.resolverReserva(c, reservationId, actor, status) == 0)
                throw new IllegalArgumentException("La reserva ya fue revisada.");
            if (approve) {
                int propertyId = reservation.id("id_propiedad");
                Registro before = list(c, "SELECT id_estado_comercial FROM propiedades WHERE id_propiedad=? FOR UPDATE", propertyId)
                        .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("La propiedad no existe."));
                int changed = exec(c, "UPDATE propiedades SET id_estado_comercial=2 WHERE id_propiedad=? AND id_estado_comercial=1",
                        propertyId);
                if (changed == 0) throw new IllegalArgumentException("La propiedad ya no está disponible; no se puede aprobar esta reserva.");
                exec(c, "INSERT INTO historial_estados(id_propiedad,id_usuario,estado_anterior,estado_nuevo,motivo) VALUES(?,?,?,?,?)",
                        propertyId, actor, before.id("id_estado_comercial"), 2, "Reserva aprobada #" + reservationId);
                notify(c, reservation.id("id_agente"), "La reserva #" + reservationId + " fue aprobada por administración.");
            }
            notify(c, reservation.id("id_cliente"), "Tu solicitud de reserva #" + reservationId + " fue " + status.toLowerCase(Locale.ROOT) + ".");
            audit(c, actor, "Reserva revisada", "reservas", reservationId, status);
            return null;
        });
    }

    public record PropertyTax(String name, BigDecimal base, BigDecimal rate, String currency, String period) {
        public PropertyTax {
            if (name == null || name.isBlank() || name.length() > 120
                    || period == null || period.isBlank() || period.length() > 80)
                throw new IllegalArgumentException("Completá el impuesto y el período (máximo 120 y 80 caracteres).");
            if (base == null || base.signum() < 0 || base.scale() > 2 || base.compareTo(new BigDecimal("9999999999.99")) > 0)
                throw new IllegalArgumentException("La base debe ser positiva o cero, con hasta dos decimales.");
            if (rate == null || rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0 || rate.scale() > 4)
                throw new IllegalArgumentException("El porcentaje debe estar entre 0 y 100, con hasta cuatro decimales.");
            if (!Set.of("ARS", "USD").contains(currency)) throw new IllegalArgumentException("Moneda inválida.");
            name = name.trim();
            period = period.trim();
        }
        public BigDecimal amount() {
            return base.multiply(rate).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        }
    }

    public boolean canViewPrivateProperty(int id) {
        if (!signedIn()) return false;
        int actor = require(1, 2, 3);
        Registro p = property(id);
        return role() == 1 || (role() == 2 && agentCanAccessProperty(actor, id))
                || (role() == 3 && p.id("id_propietario") == actor);
    }

    public List<Registro> propertyTaxes(int id) {
        if (!canViewPrivateProperty(id)) throw new IllegalArgumentException("El cálculo de impuestos es privado.");
        return dao.list("SELECT i.*, CONCAT(u.nombre,' ',u.apellido) AS autor FROM impuestos_propiedad i "
                + "JOIN usuarios u ON u.id_usuario=i.id_usuario WHERE i.id_propiedad=? ORDER BY i.id_impuesto", id);
    }

    public void savePropertyTax(int id, PropertyTax tax) {
        Objects.requireNonNull(tax);
        int actor = require(1, 2, 3);
        db.transaction(c -> {
            Registro p = editable(c, id, actor);
            if (!Set.of(1, 4).contains(p.id("id_estado_verificacion"))
                    && !(role() != 3 && p.id("id_estado_verificacion") == 2))
                throw new IllegalArgumentException("Guardá la propiedad como borrador antes de modificar sus impuestos.");
            int taxId = insert(c, "INSERT INTO impuestos_propiedad(id_propiedad,nombre,base_imponible,porcentaje,importe,moneda,periodo,id_usuario) VALUES(?,?,?,?,?,?,?,?)",
                    id, tax.name(), tax.base(), tax.rate(), tax.amount(), tax.currency(), tax.period(), actor);
            audit(c, actor, "Cálculo de impuesto", "impuestos_propiedad", taxId,
                    "Propiedad #" + id + " · " + tax.name() + " · " + tax.currency() + " " + tax.amount());
            return null;
        });
    }

    public List<Registro> attachments(int id, boolean privateDocs) {
        Registro p = property(id);
        if (privateDocs) {
            int actor = require(1, 2, 3);
            if (!canViewPrivateProperty(id))
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
        Registro p = property(id);
        if (role() == 3 && p.id("id_propietario") != actor)
            throw new IllegalArgumentException("Solo podés adjuntar archivos a tus propiedades.");
        Set<String> image = Set.of("jpg", "jpeg", "png"), video = Set.of("mp4");
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
                        Registro current = editable(c, id, actor);
                        if (!Set.of(1, 4).contains(current.id("id_estado_verificacion"))
                                && !(!media && role() != 3 && current.id("id_estado_verificacion") == 2))
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
        Registro r =
                attachments(property, privateDoc).stream()
                        .filter(x -> x.id("id") == attachment)
                        .findFirst()
                        .orElseThrow();
        return ar.edu.nuevohogar.persistencia.dao.RutasAdjuntos.resolve(
                db.storage, db.webMediaRoot, db.webMediaPrefix, r.text("archivo"), privateDoc);
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

