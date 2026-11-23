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
import java.time.YearMonth;
import java.util.*;

/** Reglas del negocio compartidas por todas las pantallas; permisos comprobados aquí. */
public final class ServicioInmobiliaria {
    public final ConexionBaseDatos db;
    private final ConsultasSQL dao;
    private final UsuarioDAO usuarioDao;
    private final PropiedadDAO propiedadDao;
    private final ClienteDAO clienteDao;
    private final SolicitudDAO solicitudDao;
    private final SolicitudPropiedadDAO solicitudPropiedadDao;
    private final GestionComercialDAO gestionDao;
    private final MultimediaDAO multimediaDao;
    private final MediaApiClient mediaApi;
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
        solicitudPropiedadDao = new SolicitudPropiedadDAO(db);
        gestionDao = new GestionComercialDAO(db);
        multimediaDao = new MultimediaDAO(db);
        mediaApi = new MediaApiClient(db);
    }

    public boolean signedIn() {
        return sessionId != null;
    }

    public String mediaPublicUrl(String storedPath) {
        return mediaApi.publicUrl(storedPath);
    }

    public String mediaStorageWarning() {
        return mediaApi.storageWarning();
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
        try { mediaApi.logout(); } catch (RuntimeException ignored) { /* cerrar sesión local aunque el hosting no responda */ }
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
        mediaApi.login(email, password);
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
                        ? clienteDao.listRelatedToAgent(actor)
                        : clienteDao.listForAdministrator();
        return clientes.stream().map(Cliente::toRow).toList();
    }

    public List<Registro> clientsForAgent(int agent) {
        require(1);
        if (agents().stream().noneMatch(a -> a.id("id") == agent))
            throw new IllegalArgumentException("El agente no existe o no está activo.");
        return clienteDao.listRelatedToAgent(agent).stream().map(Cliente::toRow).toList();
    }

    public List<Registro> linkedAgents(int client) {
        require(1, 2);
        accessClient(client);
        return dao.list("SELECT DISTINCT a.id_usuario AS id,CONCAT(a.nombre,' ',a.apellido) AS etiqueta "
                + "FROM usuarios a WHERE a.id_rol=2 AND ("
                + "EXISTS(SELECT 1 FROM clientes c WHERE c.id_cliente=? AND c.id_agente_asignado=a.id_usuario) "
                + "OR EXISTS(SELECT 1 FROM contactos ct WHERE ct.id_cliente=? AND ct.id_agente=a.id_usuario) "
                + "OR EXISTS(SELECT 1 FROM visitas v WHERE v.id_cliente=? AND v.id_agente=a.id_usuario) "
                + "OR EXISTS(SELECT 1 FROM tareas_seguimiento t WHERE t.id_cliente=? AND t.id_agente=a.id_usuario) "
                + "OR EXISTS(SELECT 1 FROM solicitudes_publicacion s WHERE s.id_cliente=? AND s.id_agente=a.id_usuario) "
                + "OR EXISTS(SELECT 1 FROM ofertas_cliente o WHERE o.id_cliente=? AND o.id_agente=a.id_usuario) "
                + "OR EXISTS(SELECT 1 FROM propiedades p WHERE p.id_propietario=? AND " + PropiedadDAO.responsibleAgentSql("p") + "=a.id_usuario) "
                + "OR EXISTS(SELECT 1 FROM intereses i JOIN propiedades p ON p.id_propiedad=i.id_propiedad WHERE i.id_cliente=? AND " + PropiedadDAO.responsibleAgentSql("p") + "=a.id_usuario) "
                + ") ORDER BY etiqueta", client, client, client, client, client, client, client, client);
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

    public List<Registro> propertiesForAgent(int agent) {
        require(1);
        if (agents().stream().noneMatch(a -> a.id("id") == agent))
            throw new IllegalArgumentException("El agente no existe o no está activo.");
        return propiedadDao.findForAgent(agent).stream().map(Propiedad::toRow).toList();
    }

    /** Reasigna una propiedad sin cambiar quién la creó ni alterar el esquema de la BD. */
    public void assignProperty(int propertyId, int newAgent) {
        int actor = require(1);
        db.transaction(c -> {
            if (!tableExists(c, "historial_asignaciones"))
                throw new IllegalStateException("La base no tiene la tabla historial_asignaciones.");
            Registro property = list(c,
                    "SELECT p.id_propiedad," + PropiedadDAO.responsibleAgentSql("p") + " AS agente "
                            + "FROM propiedades p WHERE p.id_propiedad=? FOR UPDATE", propertyId)
                    .stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("La propiedad no existe."));
            if (list(c, "SELECT id_usuario FROM usuarios WHERE id_usuario=? AND id_rol=2 AND activo=1",
                    newAgent).isEmpty())
                throw new IllegalArgumentException("Elegí un agente activo.");
            Integer previous = property.get("agente") == null ? null : property.id("agente");
            if (Objects.equals(previous, newAgent)) return null;
            exec(c, "INSERT INTO historial_asignaciones(tipo_entidad,id_entidad,agente_anterior,agente_nuevo,id_usuario_cambio,motivo) "
                    + "VALUES('PROPIEDAD',?,?,?,?,?)",
                    propertyId, previous, newAgent, actor, "Asignación individual desde Propiedades");
            audit(c, actor, "Asignación de propiedad", "propiedades", propertyId,
                    "Agente anterior: " + Objects.toString(previous, "Sin asignar") + "; agente nuevo: " + newAgent);
            return null;
        });
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
                        "SELECT id_propiedad FROM propiedades p WHERE p.id_propiedad=? AND "
                            + PropiedadDAO.responsibleAgentSql("p") + "=?",
                        property,
                        agent)
                .isEmpty();
    }

    private boolean agentCanAccessProperty(Connection connection, int agent, int property)
            throws SQLException {
        return !list(
                        connection,
                        "SELECT id_propiedad FROM propiedades p WHERE p.id_propiedad=? AND "
                            + PropiedadDAO.responsibleAgentSql("p") + "=?",
                        property,
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

    public record Offer(int type, BigDecimal price, String currency, BigDecimal minimumPrice) {
        public Offer(int type, BigDecimal price, String currency) {
            this(type, price, currency, null);
        }
    }

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
                covered = optional(d, "superficie_cubierta_m2").isBlank()
                        ? BigDecimal.ZERO : decimal(d, "superficie_cubierta_m2", false);
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
        for (Offer o : offers) {
            if (o.minimumPrice() != null && (o.type() != 1 || o.minimumPrice().signum() <= 0
                    || o.minimumPrice().scale() > 2 || o.minimumPrice().compareTo(o.price()) > 0))
                throw new IllegalArgumentException("El mínimo de venta debe ser positivo y no superar el precio publicado.");
        }
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
                            for (Registro oldOffer : list(c, "SELECT id_tipo_transaccion,precio,moneda,precio_minimo FROM propiedad_ofertas WHERE id_propiedad=?", currentProperty)) {
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
                                    if (newOffer.type() == oldOffer.id("id_tipo_transaccion")
                                            && !Objects.equals(oldOffer.get("precio_minimo"), newOffer.minimumPrice())
                                            && tableExists(c, "auditoria_cambios"))
                                        exec(c, "INSERT INTO auditoria_cambios(entidad,id_registro,campo,valor_anterior,valor_nuevo,motivo) VALUES('propiedades',?,'precio_minimo_venta',?,?,?)",
                                                currentProperty, Objects.toString(oldOffer.get("precio_minimo"), "Sin mínimo"), Objects.toString(newOffer.minimumPrice(), "Sin mínimo"), "Actualización desde la ficha");
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
                                "INSERT INTO propiedad_ofertas(id_propiedad,id_tipo_transaccion,precio,moneda,precio_minimo) VALUES(?,?,?,?,?)",
                                pid,
                                o.type,
                                o.price,
                                o.currency,
                                o.minimumPrice());
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

    public void publishDirectly(int id) {
        int actor = require(1);
        db.transaction(
                c -> {
                    Registro property = editable(c, id, actor);
                    if (!Set.of(1, 2, 4).contains(property.id("id_estado_verificacion")))
                        throw new IllegalArgumentException(
                                "La propiedad no está disponible para publicación directa.");
                    exec(
                            c,
                            "UPDATE propiedades SET id_estado_verificacion=3,id_admin_verificador=?,"
                                    + "fecha_verificacion=CURRENT_TIMESTAMP,motivo_rechazo=NULL "
                                    + "WHERE id_propiedad=?",
                            actor,
                            id);
                    exec(
                            c,
                            "UPDATE publicaciones SET estado='Publicada',observaciones=?,id_revisor=?,"
                                    + "fecha_revision=CURRENT_TIMESTAMP WHERE id_propiedad=? AND estado='Pendiente'",
                            "Publicada directamente por administración.",
                            actor,
                            id);
                    audit(
                            c,
                            actor,
                            "Publicación directa por administración",
                            "propiedades",
                            id,
                            property.text("titulo"));
                    int owner = property.id("id_propietario");
                    if (owner > 0 && owner != actor)
                        notify(c, owner, "Tu propiedad fue publicada en el catálogo: " + property.text("titulo"));
                    return null;
                });
    }

    public List<Registro> requests() {
        int actor = require(1, 2, 3);
        return solicitudDao.listForRole(role(), actor).stream()
                .map(SolicitudPublicacion::toRow)
                .toList();
    }

    public List<Registro> ownerRequests() {
        return solicitudPropiedadDao.list(role(), require(1, 2, 3));
    }

    public Registro ownerRequest(int id) {
        Registro request = solicitudPropiedadDao.find(id);
        requireOwnerRequestAccess(request);
        return request;
    }

    public List<Registro> ownerRequestOffers(int id) {
        requireOwnerRequestAccess(solicitudPropiedadDao.find(id));
        return solicitudPropiedadDao.offers(id);
    }

    public List<Registro> ownerRequestFeatures(int id) {
        requireOwnerRequestAccess(solicitudPropiedadDao.find(id));
        return solicitudPropiedadDao.features(id);
    }

    public List<Registro> ownerRequestMedia(int id) {
        requireOwnerRequestAccess(solicitudPropiedadDao.find(id));
        return solicitudPropiedadDao.media(id);
    }

    public List<Registro> ownerRequestDocuments(int id) {
        requireOwnerRequestAccess(solicitudPropiedadDao.find(id));
        return solicitudPropiedadDao.documents(id);
    }

    public Path ownerRequestFilePath(int request, int fileId, boolean privateDocument) {
        requireOwnerRequestAccess(solicitudPropiedadDao.find(request));
        Registro file = privateDocument
                ? dao.list("SELECT archivo FROM documentos WHERE id_documento=? AND id_solicitud=?", fileId, request)
                        .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("El documento no pertenece a la solicitud."))
                : dao.list("SELECT url_archivo AS archivo FROM solicitud_multimedia WHERE id_multimedia=? AND id_solicitud=?", fileId, request)
                        .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("El archivo no pertenece a la solicitud."));
        if (privateDocument && mediaApi.enabled())
            return mediaApi.download(fileId, file.text("archivo"));
        return RutasAdjuntos.resolve(db.storage, db.webMediaRoot, db.webMediaPrefix,
                file.text("archivo"), privateDocument);
    }

    public List<Registro> ownerRequestTaxes(int id) {
        requireOwnerRequestAccess(solicitudPropiedadDao.find(id));
        return dao.list("SELECT i.*,i.concepto AS nombre,i.base AS base_imponible,"
                + "CONCAT(u.nombre,' ',u.apellido) AS autor FROM propiedad_impuestos i "
                + "JOIN usuarios u ON u.id_usuario=i.id_usuario WHERE i.id_solicitud=? ORDER BY i.id_impuesto", id);
    }

    public int createOwnerRequest(Map<String, String> d, List<Offer> offers,
            Set<Integer> features, boolean missingPlans, boolean missingDeed, String documentNotes) {
        int owner = require(3);
        String title = required(d, "titulo", 200);
        String address = required(d, "direccion", 255);
        String city = required(d, "localidad", 100);
        String description = required(d, "descripcion", 10000);
        int propertyType = integer(d, "id_tipo_propiedad", 1, 1000);
        BigDecimal surface = decimal(d, "superficie_total_m2", true);
        if (surface.compareTo(new BigDecimal("999999.99")) > 0)
            throw new IllegalArgumentException("La superficie total está fuera de rango.");
        int rooms = integer(d, "ambientes", 0, 1000);
        int bedrooms = integer(d, "dormitorios", 0, 1000);
        int bathrooms = integer(d, "banos", 0, 1000);
        int age = integer(d, "antiguedad_anos", 0, 1000);
        validateOffers(offers);
        if (documentNotes != null && documentNotes.length() > 2000)
            throw new IllegalArgumentException("La observación documental no puede superar los 2000 caracteres.");
        return db.transaction(c -> {
            int id = insert(c, "INSERT INTO solicitudes_publicacion(id_cliente,id_tipo_propiedad,"
                    + "id_tipo_transaccion,id_estado_verificacion,titulo,direccion,localidad,descripcion,"
                    + "precio_estimado,superficie_total_m2,ambientes,dormitorios,banos,antiguedad_anos,"
                    + "moneda,faltan_planos,falta_escritura,observaciones_documentacion) "
                    + "VALUES(?,?,?,2,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", owner, propertyType,
                    offers.getFirst().type(), title, address, city, description, offers.getFirst().price(),
                    surface, rooms, bedrooms, bathrooms, age, offers.getFirst().currency(),
                    missingPlans ? 1 : 0, missingDeed ? 1 : 0,
                    documentNotes == null || documentNotes.isBlank() ? null : documentNotes.trim());
            for (Offer offer : offers)
                exec(c, "INSERT INTO solicitud_ofertas(id_solicitud,id_tipo_transaccion,precio,moneda,precio_minimo) VALUES(?,?,?,?,?)",
                        id, offer.type(), offer.price(), offer.currency(), offer.minimumPrice());
            for (int feature : features)
                exec(c, "INSERT INTO solicitud_caracteristicas(id_solicitud,id_caracteristica) VALUES(?,?)", id, feature);
            audit(c, owner, "Solicitud de publicación creada", "solicitudes_publicacion", id, title);
            return id;
        });
    }

    private void validateOffers(List<Offer> offers) {
        if (offers == null || offers.isEmpty()) throw new IllegalArgumentException("Agregá al menos una operación con precio.");
        Set<Integer> seen = new HashSet<>();
        for (Offer offer : offers) {
            if (!seen.add(offer.type()) || offer.price() == null || offer.price().signum() <= 0
                    || offer.price().scale() > 2 || !Set.of("USD", "ARS").contains(offer.currency()))
                throw new IllegalArgumentException("Revisá las operaciones, los precios y las monedas.");
            if (offer.minimumPrice() != null && (offer.type() != 1 || offer.minimumPrice().signum() <= 0
                    || offer.minimumPrice().scale() > 2 || offer.minimumPrice().compareTo(offer.price()) > 0))
                throw new IllegalArgumentException("El mínimo interno debe ser positivo y no superar el precio de venta.");
        }
    }

    private void requireOwnerRequestAccess(Registro request) {
        int actor = require(1, 2, 3);
        if (role() == 3 && request.id("id_cliente") != actor)
            throw new IllegalArgumentException("La solicitud no pertenece a tu cuenta.");
        if (role() == 2 && request.get("id_agente") != null && request.id("id_agente") != actor)
            throw new IllegalArgumentException("La solicitud está asignada a otro agente.");
    }

    public void claimOwnerRequest(int id) {
        int agent = require(2);
        db.transaction(c -> {
            Registro request = list(c, "SELECT * FROM solicitudes_publicacion WHERE id_solicitud=? FOR UPDATE", id)
                    .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("La solicitud no existe."));
            if (request.id("id_estado_verificacion") != 2 || request.get("id_agente") != null)
                throw new IllegalArgumentException("Solo podés tomar solicitudes pendientes y sin agente.");
            exec(c, "UPDATE solicitudes_publicacion SET id_agente=?,fecha_actualizacion=NOW() WHERE id_solicitud=? AND id_agente IS NULL", agent, id);
            if (tableExists(c, "historial_asignaciones"))
                exec(c, "INSERT INTO historial_asignaciones(tipo_entidad,id_entidad,agente_anterior,agente_nuevo,id_usuario_cambio,motivo) VALUES('SOLICITUD',?,NULL,?,?,?)",
                        id, agent, agent, "Solicitud tomada por el agente");
            audit(c, agent, "Solicitud tomada", "solicitudes_publicacion", id, "");
            return null;
        });
    }

    public void assignOwnerRequest(int id, int agent) {
        int admin = require(1);
        if (agents().stream().noneMatch(a -> a.id("id") == agent))
            throw new IllegalArgumentException("Elegí un agente activo.");
        db.transaction(c -> {
            Registro old = list(c, "SELECT id_agente,id_estado_verificacion FROM solicitudes_publicacion WHERE id_solicitud=? FOR UPDATE", id)
                    .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("La solicitud no existe."));
            if (old.id("id_estado_verificacion") != 2)
                throw new IllegalArgumentException("Solo se pueden asignar solicitudes pendientes.");
            Integer previous = old.get("id_agente") == null ? null : old.id("id_agente");
            if (!Objects.equals(previous, agent)) {
                exec(c, "UPDATE solicitudes_publicacion SET id_agente=?,fecha_actualizacion=NOW() WHERE id_solicitud=?", agent, id);
                if (tableExists(c, "historial_asignaciones"))
                    exec(c, "INSERT INTO historial_asignaciones(tipo_entidad,id_entidad,agente_anterior,agente_nuevo,id_usuario_cambio,motivo) VALUES('SOLICITUD',?,?,?,?,?)",
                            id, previous, agent, admin, "Asignación administrativa de solicitud");
                audit(c, admin, "Agente asignado a solicitud", "solicitudes_publicacion", id, "Agente #" + agent);
            }
            return null;
        });
    }

    public void rejectOwnerRequest(int id, String reason) {
        int admin = require(1);
        if (reason == null || reason.isBlank() || reason.length() > 2000)
            throw new IllegalArgumentException("Indicá un motivo de rechazo (hasta 2000 caracteres).");
        db.transaction(c -> {
            int updated = exec(c, "UPDATE solicitudes_publicacion SET id_estado_verificacion=4,motivo_rechazo=?,fecha_actualizacion=NOW() WHERE id_solicitud=? AND id_estado_verificacion=2",
                    reason.trim(), id);
            if (updated == 0) throw new IllegalArgumentException("La solicitud ya fue resuelta.");
            Registro row = list(c, "SELECT id_cliente,titulo FROM solicitudes_publicacion WHERE id_solicitud=?", id).getFirst();
            notify(c, row.id("id_cliente"), "Tu solicitud de publicación fue rechazada. Motivo: " + reason.trim());
            audit(c, admin, "Solicitud de publicación rechazada", "solicitudes_publicacion", id, reason.trim());
            return null;
        });
    }

    public int approveOwnerRequest(int id) {
        int admin = require(1);
        return db.transaction(c -> {
            Registro request = list(c, "SELECT * FROM solicitudes_publicacion WHERE id_solicitud=? FOR UPDATE", id)
                    .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("La solicitud no existe."));
            if (request.id("id_estado_verificacion") != 2)
                throw new IllegalArgumentException("La solicitud ya fue resuelta.");
            if (request.get("id_agente") == null)
                throw new IllegalArgumentException("Asigná un agente antes de aprobar la solicitud.");
            List<Registro> offers = list(c, "SELECT * FROM solicitud_ofertas WHERE id_solicitud=? ORDER BY id_tipo_transaccion", id);
            if (offers.isEmpty()) throw new IllegalArgumentException("La solicitud no tiene operaciones con precio.");
            Registro main = offers.getFirst();
            Propiedad.Datos data = new Propiedad.Datos(request.id("id_tipo_propiedad"),
                    main.id("id_tipo_transaccion"), request.text("titulo"), request.text("direccion"),
                    request.text("localidad"), main.money("precio"), main.text("moneda"),
                    request.text("descripcion"), request.money("superficie_total_m2"), BigDecimal.ZERO,
                    request.id("antiguedad_anos"), request.id("ambientes"), request.id("dormitorios"),
                    request.id("banos"), request.id("id_agente"), request.id("id_cliente"), null, null,
                    1, BigDecimal.ZERO, "", "", false);
            int property = PropiedadDAO.insert(c, data);
            exec(c, "UPDATE propiedades SET id_solicitud=?,id_estado_verificacion=3,id_estado_comercial=1,"
                    + "id_admin_verificador=?,fecha_verificacion=NOW(),faltan_planos=?,falta_escritura=?,"
                    + "observaciones_documentacion=? WHERE id_propiedad=?", id, admin,
                    request.id("faltan_planos"), request.id("falta_escritura"),
                    request.get("observaciones_documentacion"), property);
            for (Registro offer : offers)
                exec(c, "INSERT INTO propiedad_ofertas(id_propiedad,id_tipo_transaccion,precio,moneda,precio_minimo) VALUES(?,?,?,?,?)",
                        property, offer.id("id_tipo_transaccion"), offer.money("precio"), offer.text("moneda"), offer.get("precio_minimo"));
            for (Registro feature : list(c, "SELECT id_caracteristica FROM solicitud_caracteristicas WHERE id_solicitud=?", id))
                exec(c, "INSERT INTO propiedad_caracteristicas(id_propiedad,id_caracteristica) VALUES(?,?)", property, feature.id("id_caracteristica"));
            for (Registro media : list(c, "SELECT url_archivo,tipo FROM solicitud_multimedia WHERE id_solicitud=?", id))
                exec(c, "INSERT INTO propiedad_multimedia(id_propiedad,url_archivo,tipo,orden) VALUES(?,?,?,1)", property, media.text("url_archivo"), media.text("tipo"));
            exec(c, "UPDATE documentos SET id_propiedad=?,id_solicitud=NULL WHERE id_solicitud=?", property, id);
            exec(c, "UPDATE propiedad_impuestos SET id_propiedad=?,id_solicitud=NULL WHERE id_solicitud=?", property, id);
            exec(c, "UPDATE solicitudes_publicacion SET id_estado_verificacion=3,motivo_rechazo=NULL,fecha_actualizacion=NOW() WHERE id_solicitud=?", id);
            notify(c, request.id("id_cliente"), "Tu propiedad fue aprobada y publicada en el catálogo: " + request.text("titulo"));
            audit(c, admin, "Solicitud de publicación aprobada", "solicitudes_publicacion", id, "Propiedad #" + property);
            return property;
        });
    }

    public void attachOwnerRequest(int id, Path source, String type) throws Exception {
        int actor = require(1, 2, 3);
        Registro request = solicitudPropiedadDao.find(id);
        requireOwnerRequestAccess(request);
        if (role() == 3 && request.id("id_cliente") != actor)
            throw new IllegalArgumentException("La solicitud no pertenece a tu cuenta.");
        if (request.id("id_estado_verificacion") != 2)
            throw new IllegalArgumentException("Solo se adjuntan archivos a solicitudes pendientes.");
        String name = source.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        boolean media = type.equals("Imagen") || type.equals("Video");
        Set<String> allowed = type.equals("Imagen") ? Set.of("jpg", "jpeg", "png", "webp")
                : type.equals("Video") ? Set.of("mp4") : Set.of("pdf", "jpg", "jpeg", "png", "webp");
        if (!Set.of("Imagen", "Video", "Plano", "Escritura", "Impuesto", "Servicio", "Reglamento", "Otra").contains(type))
            throw new IllegalArgumentException("Tipo de archivo inválido.");
        if (!allowed.contains(ext)) throw new IllegalArgumentException("Formato no admitido para " + type + ".");
        long maxBytes = type.equals("Video") ? 20L * 1024 * 1024 : 2L * 1024 * 1024;
        if (!Files.isRegularFile(source) || Files.size(source) == 0 || Files.size(source) > maxBytes)
            throw new IllegalArgumentException(type.equals("Video")
                    ? "El video debe existir y pesar hasta 20 MB."
                    : "La imagen o el documento debe existir y pesar hasta 2 MB.");
        if (media && ownerRequestMedia(id).size() >= 8)
            throw new IllegalArgumentException("La solicitud admite hasta 8 fotos o videos.");
        if (!media && ownerRequestDocuments(id).size() >= 8)
            throw new IllegalArgumentException("La solicitud admite hasta 8 documentos.");
        if (mediaApi.enabled()) {
            mediaApi.upload("solicitud", id, type, source);
            return;
        }
        Path folder = media && db.webMediaRoot != null
                ? db.webMediaRoot.resolve("archivos").resolve("solicitudes")
                : db.storage.resolve("solicitudes").resolve(Integer.toString(id));
        Files.createDirectories(folder);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path destination = folder.resolve(filename);
        Files.copy(source, destination);
        String stored = media && db.webMediaRoot != null
                ? db.webMediaPrefix + "archivos/solicitudes/" + filename
                : db.storage.relativize(destination).toString().replace('\\', '/');
        try {
            db.transaction(c -> {
                if (media) exec(c, "INSERT INTO solicitud_multimedia(id_solicitud,url_archivo,tipo) VALUES(?,?,?)", id, stored, type);
                else exec(c, "INSERT INTO documentos(id_propiedad,id_solicitud,tipo,nombre,archivo,id_usuario) VALUES(NULL,?,?,?,?,?)",
                        id, type, name, stored, actor);
                audit(c, actor, "Archivo adjunto a solicitud", "solicitudes_publicacion", id, type + ": " + name);
                return null;
            });
        } catch (Exception e) {
            Files.deleteIfExists(destination);
            throw e;
        }
    }

    public void saveOwnerRequestTax(int id, PropertyTax tax) {
        int actor = require(3);
        Registro request = solicitudPropiedadDao.find(id);
        requireOwnerRequestAccess(request);
        if (request.id("id_estado_verificacion") != 2) throw new IllegalArgumentException("La solicitud ya fue resuelta.");
        Objects.requireNonNull(tax);
        db.transaction(c -> {
            int taxId = insert(c, "INSERT INTO propiedad_impuestos(id_propiedad,id_solicitud,concepto,base,porcentaje,importe,moneda,periodo,id_usuario) VALUES(NULL,?,?,?,?,?,?,?,?)",
                    id, tax.name(), tax.base(), tax.rate(), tax.amount(), tax.currency(), tax.period(), actor);
            audit(c, actor, "Impuesto adjuntado a solicitud", "solicitudes_publicacion", id, tax.name() + " · " + tax.currency() + " " + tax.amount());
            return taxId;
        });
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
                        ? "WHERE " + PropiedadDAO.responsibleAgentSql("p") + "=? OR c.id_agente_cierre=? OR EXISTS(SELECT 1 FROM clientes cc"
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

    private static final String RENT_LEDGER_MARKER = "NH_RENT_V1";

    /** Contratos que representan alquileres vigentes, derivados del cierre y estado de la propiedad. */
    public List<Registro> alquileresActivos() {
        int actor = require(1, 2);
        String scope = role() == 2
                ? " AND (c.id_agente_cierre=? OR " + PropiedadDAO.responsibleAgentSql("p") + "=? OR EXISTS(SELECT 1 FROM clientes cl "
                        + "WHERE cl.id_cliente=c.id_cliente_comprador AND cl.id_agente_asignado=?))"
                : "";
        Object[] args = role() == 2 ? new Object[] {actor, actor, actor} : new Object[] {};
        return dao.list("SELECT c.id_operacion,c.id_propiedad,c.id_cliente_comprador AS id_cliente,"
                + "COALESCE(cl.id_agente_asignado,c.id_agente_cierre) AS id_agente,"
                + "c.precio_final AS importe_base,c.moneda,c.fecha_cierre,p.titulo AS propiedad,"
                + "CONCAT(u.nombre,' ',u.apellido) AS cliente,CONCAT(a.nombre,' ',a.apellido) AS agente "
                + "FROM propiedades_cerradas c JOIN propiedades p ON p.id_propiedad=c.id_propiedad "
                + "JOIN usuarios u ON u.id_usuario=c.id_cliente_comprador "
                + "JOIN usuarios a ON a.id_usuario=COALESCE((SELECT cl2.id_agente_asignado FROM clientes cl2 "
                + "WHERE cl2.id_cliente=c.id_cliente_comprador),c.id_agente_cierre) "
                + "LEFT JOIN clientes cl ON cl.id_cliente=c.id_cliente_comprador "
                + "WHERE c.id_tipo_transaccion IN (2,3) AND p.id_estado_comercial=4 "
                + "AND c.id_operacion=(SELECT MAX(cx.id_operacion) FROM propiedades_cerradas cx "
                + "WHERE cx.id_propiedad=c.id_propiedad)" + scope + " ORDER BY c.fecha_cierre DESC", args);
    }

    /** Cuotas mensuales almacenadas como recordatorios especializados, sin alterar el esquema existente. */
    public List<Registro> cuotasAlquiler(int operationId) {
        Registro rental = rentalForAccess(operationId);
        List<Registro> rows = dao.list("SELECT id_tarea,titulo,detalle,fecha_limite,estado,fecha_completada "
                + "FROM tareas_seguimiento WHERE id_propiedad=? AND id_cliente=? ORDER BY fecha_limite DESC",
                rental.id("id_propiedad"), rental.id("id_cliente"));
        return rows.stream().filter(row -> row.text("detalle").startsWith(RENT_LEDGER_MARKER + "|" + operationId + "|"))
                .map(this::rentalInstallmentRow).toList();
    }

    public int crearCuotaAlquiler(int operationId, YearMonth period, BigDecimal amount,
            String currency, LocalDate dueDate) {
        int actor = require(1, 2);
        Registro rental = rentalForAccess(operationId);
        if (period == null || dueDate == null || amount == null || amount.signum() <= 0
                || amount.scale() > 2 || amount.compareTo(new BigDecimal("9999999999.99")) > 0
                || !Set.of("ARS", "USD").contains(currency))
            throw new IllegalArgumentException("Revisá el período, el vencimiento y el importe de la cuota.");
        String prefix = RENT_LEDGER_MARKER + "|" + operationId + "|" + period;
        return db.transaction(c -> {
            List<Registro> existing = list(c, "SELECT id_tarea,detalle FROM tareas_seguimiento "
                    + "WHERE id_propiedad=? AND id_cliente=?", rental.id("id_propiedad"), rental.id("id_cliente"));
            if (existing.stream().anyMatch(row -> row.text("detalle").startsWith(prefix + "|")))
                throw new IllegalArgumentException("Ya existe una cuota para " + period + ".");
            int agent = role() == 2 ? actor : rental.id("id_agente");
            String title = "Cobro alquiler " + period + " · " + rental.text("propiedad");
            String detail = prefix + "|" + amount.toPlainString() + "|" + currency + "|";
            int id = insert(c, "INSERT INTO tareas_seguimiento(id_agente,id_cliente,id_propiedad,titulo,detalle,fecha_limite) "
                    + "VALUES(?,?,?,?,?,?)", agent, rental.id("id_cliente"), rental.id("id_propiedad"),
                    title, detail, dueDate.atTime(23, 59));
            audit(c, actor, "Cuota de alquiler creada", "tareas_seguimiento", id,
                    rental.text("propiedad") + " · " + period + " · " + currency + " " + amount);
            return id;
        });
    }

    public void registrarPagoAlquiler(int taskId, BigDecimal amount, LocalDate paidOn, String method) {
        int actor = require(1, 2);
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || paidOn == null
                || paidOn.isAfter(LocalDate.now())
                || !Set.of("Efectivo", "Transferencia", "Depósito", "Otro").contains(method))
            throw new IllegalArgumentException("Revisá el importe, la fecha y el medio de pago.");
        db.transaction(c -> {
            Registro task = list(c, "SELECT * FROM tareas_seguimiento WHERE id_tarea=? FOR UPDATE", taskId)
                    .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("La cuota no existe."));
            String[] fields = rentalLedgerFields(task.text("detalle"));
            int operationId = Integer.parseInt(fields[1]);
            if (role() == 2 && !clientAccessible(actor, task.id("id_cliente")))
                throw new IllegalArgumentException("La cuota no pertenece a tu cartera.");
            if (amount.compareTo(new BigDecimal(fields[3]).subtract(rentalPaidTotal(fields[5]))) > 0)
                throw new IllegalArgumentException("El pago supera el saldo pendiente de la cuota.");
            String payments = fields[5].isBlank() ? amount.toPlainString() + "~" + paidOn + "~" + method
                    : fields[5] + ";" + amount.toPlainString() + "~" + paidOn + "~" + method;
            String detail = RENT_LEDGER_MARKER + "|" + operationId + "|" + fields[2] + "|" + fields[3]
                    + "|" + fields[4] + "|" + payments;
            BigDecimal paidTotal = rentalPaidTotal(payments);
            String status = paidTotal.compareTo(new BigDecimal(fields[3])) >= 0 ? "Completada" : "Parcial";
            exec(c, "UPDATE tareas_seguimiento SET detalle=?,estado=?,fecha_completada=? WHERE id_tarea=?",
                    detail, status, status.equals("Completada") ? paidOn.atTime(23, 59) : null, taskId);
            audit(c, actor, "Pago de alquiler registrado", "tareas_seguimiento", taskId,
                    fields[2] + " · " + method + " · " + amount + " " + fields[4] + " · operación #" + operationId);
            return null;
        });
    }

    private Registro rentalForAccess(int operationId) {
        int actor = require(1, 2);
        Registro rental = alquileresActivos().stream()
                .filter(row -> row.id("id_operacion") == operationId)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("El alquiler no está activo o no pertenece a tu cartera."));
        if (role() == 2 && !clientAccessible(actor, rental.id("id_cliente")))
            throw new IllegalArgumentException("El alquiler no pertenece a tu cartera.");
        return rental;
    }

    private Registro rentalInstallmentRow(Registro source) {
        String[] fields = rentalLedgerFields(source.text("detalle"));
        BigDecimal expected = new BigDecimal(fields[3]);
        BigDecimal paid = rentalPaidTotal(fields[5]);
        String state = paid.compareTo(expected) >= 0 ? "Completada"
                : paid.signum() > 0 ? "Parcial"
                : source.text("fecha_limite").substring(0, 10).compareTo(LocalDate.now().toString()) < 0
                        ? "Vencida" : "Pendiente";
        Registro row = new Registro();
        row.putAll(source);
        row.put("periodo", fields[2]);
        row.put("importe_esperado", expected);
        row.put("moneda", fields[4]);
        row.put("pagado", paid);
        row.put("saldo", expected.subtract(paid).max(BigDecimal.ZERO));
        row.put("estado_cuota", state);
        if (!fields[5].isBlank()) {
            String[] last = fields[5].split(";");
            String[] data = last[last.length - 1].split("~", 3);
            row.put("ultimo_pago", data[1]);
            row.put("medio_pago", data[2]);
        }
        return row;
    }

    private static String[] rentalLedgerFields(String detail) {
        String[] fields = detail.split("\\|", 6);
        if (fields.length != 6 || !fields[0].equals(RENT_LEDGER_MARKER))
            throw new IllegalArgumentException("El registro no es una cuota de alquiler válida.");
        return fields;
    }

    private static BigDecimal rentalPaidTotal(String payments) {
        if (payments.isBlank()) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (String payment : payments.split(";")) {
            String[] fields = payment.split("~", 3);
            if (fields.length != 3) throw new IllegalArgumentException("El historial de pagos está dañado.");
            total = total.add(new BigDecimal(fields[0]));
        }
        return total;
    }

    private void accessClient(int client) {
        int actor = require(1, 2, 3);
        if (role() == 3 && actor != client)
            throw new IllegalArgumentException("Solo podés acceder a tu ficha.");
        if (dao.list("SELECT id_cliente FROM clientes WHERE id_cliente=?", client).isEmpty())
            throw new IllegalArgumentException("Ficha de cliente inexistente.");
        if (role() == 2 && !clientAccessible(actor, client))
            throw new IllegalArgumentException("Este cliente no tiene actividad vinculada a tu cartera.");
    }

    public List<Registro> contacts(int client) {
        accessClient(client);
        return dao.list(
                "SELECT c.*,CONCAT(a.nombre,' ',a.apellido) AS agente FROM contactos c JOIN"
                    + " usuarios a ON a.id_usuario=c.id_agente WHERE c.id_cliente=? ORDER BY c.fecha"
                    + " DESC",
                client);
    }

    public void createInquiry(int propertyId, Integer clientId, String name, String email,
            String phone, String message) {
        if (message == null || message.trim().length() < 5 || message.trim().length() > 5000)
            throw new IllegalArgumentException("Escribí una consulta de entre 5 y 5000 caracteres.");
        Registro p = property(propertyId);
        if (p.id("id_estado_verificacion") != 3 || !Set.of(1, 2).contains(p.id("id_estado_comercial")))
            throw new IllegalArgumentException("Solo se reciben consultas sobre propiedades publicadas.");
        int agent = responsibleAgent(p);
        if (agent <= 0 && p.get("id_propietario") != null) {
            Registro assignment = dao.list("SELECT id_agente_asignado AS id FROM clientes WHERE id_cliente=?",
                    p.id("id_propietario")).stream().findFirst().orElse(new Registro());
            if (assignment.get("id") != null) agent = assignment.id("id");
        }
        if (agent <= 0) throw new IllegalArgumentException("La propiedad todavía no tiene un agente responsable.");
        final int responsibleAgent = agent;
        String contactName = Objects.toString(name, "").trim();
        String contactEmail = Objects.toString(email, "").trim();
        String contactPhone = Objects.toString(phone, "").trim();
        if (clientId == null && (contactName.isBlank() || contactName.length() > 120
                || (contactEmail.isBlank() && contactPhone.isBlank())))
            throw new IllegalArgumentException("Ingresá tu nombre y un correo o teléfono para que podamos responderte.");
        if (contactEmail.length() > 190 || contactPhone.length() > 40)
            throw new IllegalArgumentException("El correo o teléfono supera el largo permitido.");
        if (clientId != null) {
            int user = require(3);
            if (user != clientId) throw new IllegalArgumentException("La consulta no pertenece a la cuenta activa.");
        }
        db.transaction(c -> {
            int id = insert(c, "INSERT INTO contactos(id_cliente,id_agente,id_propiedad,tipo,descripcion,"
                    + "nombre_contacto,email_contacto,telefono_contacto,estado) VALUES(?,?,?,'Consulta',?,?,?,?, 'Pendiente')",
                    clientId, responsibleAgent, propertyId, message.trim(),
                    clientId == null ? contactName : null, clientId == null ? contactEmail : null,
                    clientId == null ? contactPhone : null);
            notify(c, responsibleAgent, "Nueva consulta sobre la propiedad " + p.text("titulo") + ".");
            if (clientId != null) audit(c, clientId, "Consulta enviada", "contactos", id, p.text("titulo"));
            return null;
        });
    }

    private int responsibleAgent(Registro property) {
        int agent = property.id("id_agente_responsable");
        if (agent <= 0) agent = property.id("id_agente_creador");
        if (agent <= 0 && property.get("id_propietario") != null) {
            Registro assignment = dao.list(
                    "SELECT id_agente_asignado AS id FROM clientes WHERE id_cliente=?",
                    property.id("id_propietario")).stream().findFirst().orElse(new Registro());
            if (assignment.get("id") != null) agent = assignment.id("id");
        }
        if (agent <= 0)
            throw new IllegalArgumentException("La propiedad todavía no tiene un agente responsable.");
        return agent;
    }

    public List<Registro> consultations() {
        int actor = require(1, 2);
        return dao.list("SELECT c.*,p.titulo AS propiedad,COALESCE(NULLIF(CONCAT(u.nombre,' ',u.apellido),' '),"
                + "c.nombre_contacto,'Consulta sin nombre') AS persona,COALESCE(u.email,c.email_contacto) AS correo,"
                + "COALESCE(u.telefono,c.telefono_contacto) AS telefono,CONCAT(a.nombre,' ',a.apellido) AS agente "
                + "FROM contactos c LEFT JOIN usuarios u ON u.id_usuario=c.id_cliente "
                + "JOIN usuarios a ON a.id_usuario=c.id_agente LEFT JOIN propiedades p ON p.id_propiedad=c.id_propiedad "
                + "WHERE c.tipo='Consulta' " + (role() == 2 ? "AND (c.id_agente=? OR " + PropiedadDAO.responsibleAgentSql("p") + "=?) " : "")
                + "ORDER BY CASE WHEN c.estado='Pendiente' THEN 0 ELSE 1 END,c.fecha DESC",
                role() == 2 ? new Object[] {actor, actor} : new Object[] {});
    }

    public void respondInquiry(int id, String response, String state) {
        int actor = require(1, 2);
        String answer = Objects.toString(response, "").trim();
        if (answer.length() < 5 || answer.length() > 5000)
            throw new IllegalArgumentException("La respuesta debe tener entre 5 y 5000 caracteres.");
        if (!Set.of("Respondida", "Cerrada").contains(state))
            throw new IllegalArgumentException("Elegí Respondida o Cerrada.");
        db.transaction(c -> {
            Registro inquiry = list(c, "SELECT * FROM contactos WHERE id_contacto=? AND tipo='Consulta' FOR UPDATE", id)
                    .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("La consulta no existe."));
            if (role() == 2 && inquiry.id("id_agente") != actor) {
                Integer property = inquiry.get("id_propiedad") == null ? null : inquiry.id("id_propiedad");
                if (property == null || !agentCanAccessProperty(c, actor, property))
                    throw new IllegalArgumentException("La consulta no está asignada a tu usuario.");
            }
            exec(c, "UPDATE contactos SET resultado=?,estado=?,fecha_respuesta=NOW() WHERE id_contacto=?", answer, state, id);
            audit(c, actor, state.equals("Cerrada") ? "Cerrar consulta" : "Responder consulta", "contactos", id, answer);
            return null;
        });
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
        if (role() == 1 && agent != null) {
            Integer currentAgent = dao.list(
                            "SELECT id_agente_asignado AS id FROM clientes WHERE id_cliente=?", client)
                    .stream().findFirst()
                    .map(row -> row.get("id") == null ? null : row.id("id"))
                    .orElse(null);
            if (!Objects.equals(currentAgent, agent)
                    && agents().stream().noneMatch(r -> r.id("id") == agent))
                throw new IllegalArgumentException("Elegí un agente activo para la nueva asignación.");
        }
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

    public List<Registro> activityChanges() {
        require(1);
        return dao.list("SELECT ch.*,a.fecha,CONCAT(u.nombre,' ',u.apellido) AS usuario "
                + "FROM auditoria_cambios ch LEFT JOIN auditoria a ON a.id_evento=ch.id_evento "
                + "LEFT JOIN usuarios u ON u.id_usuario=a.id_usuario "
                + "ORDER BY COALESCE(a.fecha,'1970-01-01') DESC,ch.id_cambio DESC LIMIT 200");
    }

    public List<Registro> allStateHistory() {
        require(1);
        return dao.list("SELECT h.*,p.titulo,CONCAT(u.nombre,' ',u.apellido) AS usuario,"
                + "ea.nombre AS anterior,en.nombre AS nuevo FROM historial_estados h "
                + "JOIN propiedades p ON p.id_propiedad=h.id_propiedad JOIN usuarios u ON u.id_usuario=h.id_usuario "
                + "JOIN estados_comerciales ea ON ea.id_estado_comercial=h.estado_anterior "
                + "JOIN estados_comerciales en ON en.id_estado_comercial=h.estado_nuevo ORDER BY h.fecha DESC LIMIT 200");
    }

    public List<Registro> allPriceHistory() {
        require(1);
        return dao.list("SELECT h.*,p.titulo,t.nombre AS operacion,CONCAT(u.nombre,' ',u.apellido) AS usuario "
                + "FROM historial_precios h JOIN propiedades p ON p.id_propiedad=h.id_propiedad "
                + "JOIN tipos_transaccion t ON t.id_tipo_transaccion=h.id_tipo_transaccion "
                + "JOIN usuarios u ON u.id_usuario=h.id_usuario ORDER BY h.fecha DESC LIMIT 200");
    }

    public List<Registro> allAssignmentHistory() {
        require(1);
        return dao.list("SELECT h.*,CONCAT(actor.nombre,' ',actor.apellido) AS usuario_cambio,"
                + "CONCAT(previous_agent.nombre,' ',previous_agent.apellido) AS agente_anterior_nombre,"
                + "CONCAT(next_agent.nombre,' ',next_agent.apellido) AS agente_nuevo_nombre "
                + "FROM historial_asignaciones h JOIN usuarios actor ON actor.id_usuario=h.id_usuario_cambio "
                + "LEFT JOIN usuarios previous_agent ON previous_agent.id_usuario=h.agente_anterior "
                + "LEFT JOIN usuarios next_agent ON next_agent.id_usuario=h.agente_nuevo "
                + "ORDER BY h.fecha DESC LIMIT 200");
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

    /** La solicitud de visita se dirige al agente responsable de la propiedad. */
    public void solicitarVisita(int propertyId, LocalDateTime when, String notes) {
        int actor = require(3);
        if (when == null || !when.isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("Elegí una fecha y hora futuras.");
        Registro p = property(propertyId);
        int agent = responsibleAgent(p);
        if (agent <= 0) throw new IllegalArgumentException("La propiedad todavía no tiene un agente responsable.");
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
        agendarVisita(clientId, propertyId, role() == 1 ? 0 : current().id("id_usuario"), when, notes);
    }

    public void agendarVisita(int clientId, int propertyId, int requestedAgent, LocalDateTime when, String notes) {
        int agentId = require(1, 2);
        if (role() == 2 && !clientAccessible(agentId, clientId))
            throw new IllegalArgumentException("Ese cliente no pertenece a tu cartera.");
        if (when == null || !when.isAfter(LocalDateTime.now())) throw new IllegalArgumentException("Elegí una fecha y hora futuras.");
        property(propertyId);
        int assigned = role() == 1 ? requestedAgent : agentId;
        if (assigned <= 0 || (role() == 1 && agents().stream().noneMatch(a -> a.id("id") == assigned)))
            throw new IllegalArgumentException("Elegí un agente activo para la visita.");
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
        return crearTareaSeguimiento(client, property, role() == 1 ? 0 : current().id("id_usuario"), title, detail, due);
    }

    public int crearTareaSeguimiento(int client, Integer property, int requestedAgent, String title, String detail, LocalDateTime due) {
        int actor = require(1, 2);
        if (role() == 2 && !clientAccessible(actor, client)) throw new IllegalArgumentException("Ese cliente no pertenece a tu cartera.");
        if (due == null || title == null || title.isBlank()) throw new IllegalArgumentException("Completá el título y el vencimiento del recordatorio.");
        int taskAgent = role() == 1 ? requestedAgent : actor;
        if (taskAgent <= 0 || (role() == 1 && agents().stream().noneMatch(a -> a.id("id") == taskAgent)))
            throw new IllegalArgumentException("Elegí un agente activo para el recordatorio.");
        return db.transaction(c -> {
            int id = gestionDao.crearTarea(c, taskAgent, client, property, title.trim(), detail == null ? "" : detail.trim(), due);
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

    private boolean clientAccessible(int agent, int client) {
        return !dao.list("SELECT c.id_cliente FROM clientes c WHERE c.id_cliente=? AND (c.id_agente_asignado=? "
                + "OR EXISTS(SELECT 1 FROM contactos ct WHERE ct.id_cliente=c.id_cliente AND ct.id_agente=?) "
                + "OR EXISTS(SELECT 1 FROM visitas v WHERE v.id_cliente=c.id_cliente AND v.id_agente=?) "
                + "OR EXISTS(SELECT 1 FROM tareas_seguimiento t WHERE t.id_cliente=c.id_cliente AND t.id_agente=?) "
                + "OR EXISTS(SELECT 1 FROM solicitudes_publicacion s WHERE s.id_cliente=c.id_cliente AND s.id_agente=?) "
                + "OR EXISTS(SELECT 1 FROM ofertas_cliente o WHERE o.id_cliente=c.id_cliente AND o.id_agente=?) "
                + "OR EXISTS(SELECT 1 FROM propiedades p WHERE p.id_propietario=c.id_cliente AND " + PropiedadDAO.responsibleAgentSql("p") + "=?) "
                + "OR EXISTS(SELECT 1 FROM intereses i JOIN propiedades p ON p.id_propiedad=i.id_propiedad WHERE i.id_cliente=c.id_cliente AND " + PropiedadDAO.responsibleAgentSql("p") + "=?))",
                client, agent, agent, agent, agent, agent, agent, agent, agent).isEmpty();
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
            String propertyScope = role() == 2 ? " AND " + PropiedadDAO.responsibleAgentSql("p") + "=?" : "";
            LocalDate propertyCutoff = LocalDate.now().minusDays(days.get("dias_propiedad_sin_actividad"));
            Object[] propertyArgs = role() == 2 ? new Object[] {propertyCutoff, actor} : new Object[] {propertyCutoff};
            for (Registro row : list(c, "SELECT p.id_propiedad," + PropiedadDAO.responsibleAgentSql("p") + " AS agente "
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
            String rentalScope = role() == 2 ? " AND id_agente=?" : "";
            Object[] rentalArgs = role() == 2
                    ? new Object[] {LocalDateTime.now().plusDays(days.get("dias_aviso_reserva")), actor}
                    : new Object[] {LocalDateTime.now().plusDays(days.get("dias_aviso_reserva"))};
            for (Registro row : list(c, "SELECT id_tarea,id_agente,titulo,fecha_limite FROM tareas_seguimiento "
                    + "WHERE COALESCE(SUBSTRING(detalle,1,10),'')='NH_RENT_V1' AND estado IN ('Pendiente','Parcial') "
                    + "AND fecha_limite<=?" + rentalScope, rentalArgs)) {
                String status = row.text("fecha_limite").compareTo(LocalDateTime.now().toString()) < 0
                        ? "vencida" : "próxima a vencer";
                String message = "Alerta de alquiler: " + row.text("titulo") + " " + status + ".";
                generated += notificarSiNoExiste(c, row.id("id_agente"), message);
                if (role() == 1) generated += notificarAdministradoresSiNoExiste(c, message);
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
        int agent = responsibleAgent(p);
        if (agent <= 0) throw new IllegalArgumentException("La propiedad todavía no tiene un agente responsable.");
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
        actualizarReserva(reservationId, approve ? "autorizar" : "cancelar", null);
    }

    public void actualizarReserva(int reservationId, String action, String payment) {
        int actor = require(1);
        db.transaction(c -> {
            Registro reservation = list(c, "SELECT * FROM reservas WHERE id_reserva=? FOR UPDATE", reservationId)
                    .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("La reserva no existe."));
            if (payment != null) {
                if (!Set.of("Pendiente", "Pagado", "Rechazado").contains(payment))
                    throw new IllegalArgumentException("Estado de pago inválido.");
                exec(c, "UPDATE reservas SET estado_pago=? WHERE id_reserva=?", payment, reservationId);
                audit(c, actor, "Actualizar pago de reserva", "reservas", reservationId, payment);
            }
            if (action != null && !action.isBlank()) {
                if (!Set.of("autorizar", "cancelar").contains(action))
                    throw new IllegalArgumentException("Acción de reserva inválida.");
                int propertyId = reservation.id("id_propiedad");
                String currentStatus = reservation.text("estado");
                if (action.equals("autorizar") && !currentStatus.equals("Pendiente"))
                    throw new IllegalArgumentException("Solo se pueden autorizar reservas pendientes.");
                if (action.equals("cancelar") && !Set.of("Pendiente", "Activa", "Aprobada").contains(currentStatus))
                    throw new IllegalArgumentException("La reserva no se puede cancelar desde su estado actual.");
                Registro before = list(c, "SELECT id_estado_comercial FROM propiedades WHERE id_propiedad=? FOR UPDATE", propertyId)
                        .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("La propiedad no existe."));
                if (action.equals("autorizar")) {
                    if (!Set.of(1, 2, 5).contains(before.id("id_estado_comercial")))
                        throw new IllegalArgumentException("La propiedad no admite reservas en su estado actual.");
                    if (!list(c, "SELECT id_reserva FROM reservas WHERE id_propiedad=? AND estado IN ('Activa','Aprobada') AND id_reserva<>? LIMIT 1",
                            propertyId, reservationId).isEmpty())
                        throw new IllegalArgumentException("La propiedad ya tiene otra reserva activa.");
                    exec(c, "UPDATE reservas SET estado='Aprobada',id_autorizador=? WHERE id_reserva=?", actor, reservationId);
                    if (before.id("id_estado_comercial") != 2) {
                        exec(c, "UPDATE propiedades SET id_estado_comercial=2 WHERE id_propiedad=?", propertyId);
                        exec(c, "INSERT INTO historial_estados(id_propiedad,id_usuario,estado_anterior,estado_nuevo,motivo) VALUES(?,?,?,?,?)",
                                propertyId, actor, before.id("id_estado_comercial"), 2, "Reserva autorizada #" + reservationId);
                    }
                    notify(c, reservation.id("id_agente"), "La reserva #" + reservationId + " fue autorizada por administración.");
                } else {
                    exec(c, "UPDATE reservas SET estado='Cancelada',id_autorizador=? WHERE id_reserva=?", actor, reservationId);
                    if (currentStatus.equals("Activa") || currentStatus.equals("Aprobada")) {
                        boolean otherActive = !list(c, "SELECT id_reserva FROM reservas WHERE id_propiedad=? AND estado IN ('Activa','Aprobada') AND id_reserva<>? LIMIT 1",
                                propertyId, reservationId).isEmpty();
                        if (!otherActive && before.id("id_estado_comercial") == 2) {
                            exec(c, "UPDATE propiedades SET id_estado_comercial=1 WHERE id_propiedad=?", propertyId);
                            exec(c, "INSERT INTO historial_estados(id_propiedad,id_usuario,estado_anterior,estado_nuevo,motivo) VALUES(?,?,?,?,?)",
                                    propertyId, actor, 2, 1, "Reserva cancelada #" + reservationId);
                        }
                    }
                }
                audit(c, actor, action.equals("autorizar") ? "Autorizar reserva" : "Cancelar reserva", "reservas", reservationId,
                        currentStatus + " → " + (action.equals("autorizar") ? "Activa" : "Cancelada"));
                notify(c, reservation.id("id_cliente"), action.equals("autorizar")
                        ? "Tu reserva #" + reservationId + " fue autorizada."
                        : "Tu reserva #" + reservationId + " fue cancelada.");
            }
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
            if (rate == null || rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0 || rate.scale() > 2)
                throw new IllegalArgumentException("El porcentaje debe estar entre 0 y 100, con hasta dos decimales.");
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
        return dao.list("SELECT i.*,i.concepto AS nombre,i.base AS base_imponible,"
                + "CONCAT(u.nombre,' ',u.apellido) AS autor FROM propiedad_impuestos i "
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
            int taxId = insert(c, "INSERT INTO propiedad_impuestos(id_propiedad,id_solicitud,concepto,base,porcentaje,importe,moneda,periodo,id_usuario) VALUES(?,NULL,?,?,?,?,?,?,?)",
                    id, tax.name(), tax.base(), tax.rate(), tax.amount(), tax.currency(), tax.period(), actor);
            audit(c, actor, "Cálculo de impuesto", "propiedad_impuestos", taxId,
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
        return multimediaDao.listar(id);
    }

    public void attach(int id, Path source, String type) throws Exception {
        int actor = require(1, 2, 3);
        Registro p = property(id);
        if (role() == 3 && p.id("id_propietario") != actor)
            throw new IllegalArgumentException("Solo podés adjuntar archivos a tus propiedades.");
        Set<String> image = Set.of("jpg", "jpeg", "png", "webp"), video = Set.of("mp4");
        String name = source.getFileName().toString();
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        boolean media = type.equals("Imagen") || type.equals("Video");
        if (!Set.of("Imagen", "Video", "Plano", "Escritura", "Servicio", "Impuesto", "Reglamento", "Otra").contains(type))
            throw new IllegalArgumentException("Tipo de archivo inválido.");
        if (!(type.equals("Imagen")
                ? image.contains(ext)
                : type.equals("Video")
                        ? video.contains(ext)
                        : Set.of("pdf", "jpg", "jpeg", "png", "webp").contains(ext)))
            throw new IllegalArgumentException("Formato no admitido para " + type + ".");
        long maxBytes = type.equals("Video") ? 20L * 1024 * 1024 : 2L * 1024 * 1024;
        if (Files.size(source) > maxBytes)
            throw new IllegalArgumentException(type.equals("Video")
                    ? "El video supera los 20 MB permitidos por la página."
                    : "La imagen o el documento supera los 2 MB permitidos por la página.");
        db.transaction(c -> {
            Registro current = editable(c, id, actor);
            validateAttachmentState(current, media);
            return null;
        });
        if (Files.size(source) == 0)
            throw new IllegalArgumentException("El archivo está vacío.");
        if (mediaApi.enabled()) {
            mediaApi.upload("propiedad", id, type, source);
            return;
        }
        String storedFile = copyAttachment(id, source, ext, media);
        Path destination = RutasAdjuntos.resolve(db.storage, db.webMediaRoot, db.webMediaPrefix,
                storedFile, !media);
        try {
            db.transaction(
                    c -> {
                        Registro current = editable(c, id, actor);
                        validateAttachmentState(current, media);
                        if (media)
                            multimediaDao.insertar(c, id, storedFile, type, 1);
                        else
                            exec(
                                    c,
                                    "INSERT INTO"
                                        + " documentos(id_propiedad,tipo,nombre,archivo,id_usuario)"
                                        + " VALUES(?,?,?,?,?)",
                                    id,
                                    type,
                                    name,
                                    storedFile,
                                    actor);
                        audit(c, actor, "Archivo adjunto", "propiedades", id, type + ": " + name);
                        return null;
                    });
        } catch (Exception e) {
            Files.deleteIfExists(destination);
            throw e;
        }
    }

    private void validateAttachmentState(Registro property, boolean media) {
        // La administración y los agentes responsables pueden completar el expediente privado
        // incluso después del cierre comercial; esto no cambia la operación ni la publicación.
        if (!media && role() != 3) return;
        if (media && role() != 3 && property.id("id_estado_verificacion") == 3
                && Set.of(1, 2, 5).contains(property.id("id_estado_comercial"))) return;
        if (!Set.of(1, 4).contains(property.id("id_estado_verificacion")))
            throw new IllegalArgumentException(
                    "Guardá la propiedad como borrador antes de cambiar sus archivos.");
    }

    private String copyAttachment(int property, Path source, String ext, boolean publicMedia) throws Exception {
        Path folder = publicMedia && db.webMediaRoot != null
                ? db.webMediaRoot.resolve("archivos").resolve("propiedades")
                : db.storage.resolve("propiedades").resolve(Integer.toString(property));
        Files.createDirectories(folder);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path destination = folder.resolve(filename);
        Files.copy(source, destination);
        return publicMedia && db.webMediaRoot != null
                ? db.webMediaPrefix + "archivos/propiedades/" + filename
                : db.storage.relativize(destination).toString().replace('\\', '/');
    }

    public Path attachmentPath(int property, int attachment, boolean privateDoc) {
        Registro r =
                attachments(property, privateDoc).stream()
                        .filter(x -> x.id("id") == attachment)
                        .findFirst()
                        .orElseThrow();
        if (r.text("archivo").startsWith("https://") || r.text("archivo").startsWith("http://"))
            throw new IllegalArgumentException("Este archivo sigue alojado en un servicio externo; descargalo y volvé a adjuntarlo para guardarlo localmente.");
        if (privateDoc && mediaApi.enabled())
            return mediaApi.download(attachment, r.text("archivo"));
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

