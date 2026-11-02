package ar.edu.nuevohogar.servidor;

import ar.edu.nuevohogar.backend.autenticacion.ClavesSeguras;
import ar.edu.nuevohogar.modelo.Usuario;
import ar.edu.nuevohogar.persistencia.dao.ConexionBaseDatos;
import ar.edu.nuevohogar.persistencia.dao.ConsultasSQL;
import ar.edu.nuevohogar.persistencia.dao.UsuarioDAO;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/** API central de archivos privados. Desplegar detrás de HTTPS; el API Secret solo vive aquí. */
public final class ServidorDocumentosPrivados {
    private static final int MAX_BYTES = 50 * 1024 * 1024;
    private static final long SESSION_SECONDS = 8 * 60 * 60;
    private final ConexionBaseDatos database;
    private final UsuarioDAO users;
    private final Cloudinary cloudinary;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, LoginFailures> loginFailures = new ConcurrentHashMap<>();

    private record Session(int userId, int role, long expiresAt) {}
    private record LoginFailures(int attempts, long lockedUntil) {}

    private ServidorDocumentosPrivados(ConexionBaseDatos database, Cloudinary cloudinary) {
        this.database = database;
        this.users = new UsuarioDAO(database);
        this.cloudinary = cloudinary;
    }

    public static void main(String[] args) throws Exception {
        String cloudinaryUrl = env("CLOUDINARY_URL");
        if (cloudinaryUrl.isBlank()) throw new IllegalStateException("Falta configurar CLOUDINARY_URL en el servidor.");
        ConexionBaseDatos database = new ConexionBaseDatos();
        if (database.demo) throw new IllegalStateException("El servidor privado requiere DB_URL apuntando a la base MariaDB compartida.");
        int port = Integer.parseInt(envOr("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 64);
        ServidorDocumentosPrivados api = new ServidorDocumentosPrivados(database, new Cloudinary(cloudinaryUrl));
        server.createContext("/api/sesion", api::session);
        server.createContext("/api/documentos", api::documents);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        server.start();
        System.out.println("API de documentos privados activa en el puerto " + port);
    }

    private void session(HttpExchange exchange) throws IOException {
        if (exchange.getRequestURI().getPath().equals("/api/sesion/cerrar")) {
            if (!exchange.getRequestMethod().equals("POST")) { respond(exchange, 405, "{\"error\":\"Método no permitido\"}"); return; }
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) sessions.remove(authorization.substring(7).trim());
            respond(exchange, 200, "{\"cerrada\":true}");
            return;
        }
        if (!exchange.getRequestMethod().equals("POST")) { respond(exchange, 405, "{\"error\":\"Método no permitido\"}"); return; }
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        LoginFailures failures = loginFailures.get(ip);
        long now = Instant.now().getEpochSecond();
        if (failures != null && failures.lockedUntil() > now) { respond(exchange, 429, "{\"error\":\"Intentá más tarde\"}"); return; }
        try {
            Map<String, String> form = parseForm(new String(exchange.getRequestBody().readNBytes(4096), StandardCharsets.UTF_8));
            String email = form.getOrDefault("email", "");
            String password = form.getOrDefault("password", "");
            Usuario user = users.findActiveByEmail(email).orElse(null);
            if (user == null || !ClavesSeguras.verify(password, user.passwordHash())) {
                registerFailure(ip, failures, now);
                respond(exchange, 401, "{\"error\":\"Correo o contraseña incorrectos\"}"); return;
            }
            loginFailures.remove(ip);
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            sessions.put(token, new Session(user.idUsuario(), user.idRol(), now + SESSION_SECONDS));
            respond(exchange, 200, "{\"token\":\"" + token + "\",\"expira\":" + (now + SESSION_SECONDS) + "}");
        } catch (Exception e) {
            respond(exchange, 400, "{\"error\":\"No se pudo iniciar sesión\"}");
        }
    }

    private void documents(HttpExchange exchange) throws IOException {
        try {
            Session session = authorize(exchange);
            if (session == null) { respond(exchange, 401, "{\"error\":\"Sesión inválida o vencida\"}"); return; }
            if (exchange.getRequestMethod().equals("POST") && exchange.getRequestURI().getPath().equals("/api/documentos")) {
                upload(exchange, session); return;
            }
            if (exchange.getRequestMethod().equals("GET")) {
                signedUrl(exchange, session); return;
            }
            respond(exchange, 404, "{\"error\":\"Ruta inexistente\"}");
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, "{\"error\":\"" + json(e.getMessage()) + "\"}");
        } catch (Exception e) {
            respond(exchange, 500, "{\"error\":\"No se pudo completar la operación de archivos\"}");
        }
    }

    private void upload(HttpExchange exchange, Session session) throws Exception {
        Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());
        int propertyId = positiveInt(query.get("propiedad"));
        String type = query.getOrDefault("tipo", "");
        String name = query.getOrDefault("nombre", "").replace('\\', '/');
        int existingDocument = query.containsKey("documento") ? positiveInt(query.get("documento")) : 0;
        String extension = extension(name);
        if (!Set.of("Plano", "Escritura", "Servicio", "Impuesto").contains(type)) throw new IllegalArgumentException("Tipo de documento inválido.");
        if (!Set.of("pdf", "jpg", "jpeg", "png").contains(extension)) throw new IllegalArgumentException("Formato no admitido; usá PDF, JPG o PNG.");
        if (name.isBlank() || name.length() > 255) throw new IllegalArgumentException("Nombre de archivo inválido.");
        if (!canAccess(session, propertyId)) { respond(exchange, 403, "{\"error\":\"No tenés acceso a los documentos de esta propiedad\"}"); return; }
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BYTES + 1);
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("El archivo debe pesar entre 1 byte y 50 MB.");
        String resourceType = extension.equals("pdf") ? "raw" : "image";
        String publicId = "Inmuebles/Privados/propiedad-" + propertyId + "/" + UUID.randomUUID();
        Map<?, ?> uploaded = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                "resource_type", resourceType,
                "type", "authenticated",
                "public_id", publicId,
                "overwrite", false,
                "unique_filename", false));
        String stored = "cloudinary-private|" + resourceType + "|" + uploaded.get("public_id") + "|" + uploaded.get("format");
        try {
            int documentId = database.transaction(connection -> {
                int id;
                if (existingDocument > 0) {
                    if (ConsultasSQL.list(connection,
                            "SELECT id_documento FROM documentos WHERE id_documento=? AND id_propiedad=?",
                            existingDocument, propertyId).isEmpty())
                        throw new IllegalArgumentException("El documento que querés migrar no existe en esta propiedad.");
                    id = existingDocument;
                    ConsultasSQL.exec(connection,
                            "UPDATE documentos SET archivo=? WHERE id_documento=? AND id_propiedad=?",
                            stored, existingDocument, propertyId);
                } else {
                    id = ConsultasSQL.insert(connection,
                            "INSERT INTO documentos(id_propiedad,tipo,nombre,archivo,id_usuario) VALUES(?,?,?,?,?)",
                            propertyId, type, name, stored, session.userId());
                }
                ConsultasSQL.exec(connection,
                        "INSERT INTO auditoria(id_usuario,accion,entidad,id_registro,detalle) VALUES(?,?,?,?,?)",
                        session.userId(), existingDocument > 0 ? "Documento migrado a Cloudinary" : "Documento privado subido a Cloudinary", "documentos", id,
                        "Propiedad #" + propertyId + " · " + type);
                return id;
            });
            respond(exchange, 201, "{\"id\":" + documentId + "}");
        } catch (Exception e) {
            try { cloudinary.uploader().destroy((String) uploaded.get("public_id"), ObjectUtils.asMap("resource_type", resourceType, "type", "authenticated")); }
            catch (Exception ignored) { }
            throw e;
        }
    }

    private void signedUrl(HttpExchange exchange, Session session) throws Exception {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        if (parts.length != 5 || !parts[4].equals("url")) { respond(exchange, 404, "{\"error\":\"Ruta inexistente\"}"); return; }
        int documentId = positiveInt(parts[3]);
        int propertyId = positiveInt(parseForm(exchange.getRequestURI().getRawQuery()).get("propiedad"));
        ar.edu.nuevohogar.modelo.Registro row = database.transaction(connection -> {
            var rows = ConsultasSQL.list(connection,
                    "SELECT id_propiedad,archivo FROM documentos WHERE id_documento=?", documentId);
            if (rows.isEmpty()) throw new IllegalArgumentException("Documento inexistente.");
            var found = rows.getFirst();
            if (found.id("id_propiedad") != propertyId) throw new IllegalArgumentException("El documento no pertenece a esta propiedad.");
            return found;
        });
        if (!canAccess(session, propertyId)) { respond(exchange, 403, "{\"error\":\"No tenés permiso para abrir este documento\"}"); return; }
        String[] reference = row.text("archivo").split("\\|", 4);
        if (reference.length != 4 || !reference[0].equals("cloudinary-private")) throw new IllegalArgumentException("El documento no está en el almacenamiento privado.");
        String url = cloudinary.privateDownload(reference[2], reference[3], ObjectUtils.asMap(
                "resource_type", reference[1], "type", "authenticated",
                "expires_at", Instant.now().getEpochSecond() + 300));
        if (url.startsWith("http://")) url = "https://" + url.substring("http://".length());
        respond(exchange, 200, "{\"url\":\"" + json(url) + "\"}");
    }

    private boolean canAccess(Session session, int property) {
        return !new ConsultasSQL(database).list(
                "SELECT p.id_propiedad FROM propiedades p WHERE p.id_propiedad=? AND"
                        + " (?=1 OR (?=2 AND (p.id_agente_creador=? OR EXISTS(SELECT 1 FROM clientes c"
                        + " WHERE c.id_cliente=p.id_propietario AND c.id_agente_asignado=?)))"
                        + " OR (?=3 AND p.id_propietario=?))",
                property, session.role(), session.role(), session.userId(), session.userId(),
                session.role(), session.userId()).isEmpty();
    }

    private Session authorize(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7).trim();
        Session session = sessions.get(token);
        if (session != null && session.expiresAt() <= Instant.now().getEpochSecond()) {
            sessions.remove(token); return null;
        }
        return session;
    }

    private void registerFailure(String ip, LoginFailures old, long now) {
        int attempts = old == null || old.lockedUntil() <= now ? 1 : old.attempts() + 1;
        loginFailures.put(ip, new LoginFailures(attempts, attempts >= 5 ? now + 600 : 0));
    }

    private static Map<String, String> parseForm(String value) {
        Map<String, String> result = new java.util.HashMap<>();
        if (value == null || value.isBlank()) return result;
        for (String pair : value.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }

    private static int positiveInt(String value) {
        try { int id = Integer.parseInt(value); if (id > 0) return id; }
        catch (Exception ignored) { }
        throw new IllegalArgumentException("Identificador inválido.");
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String env(String key) { return java.util.Objects.toString(System.getenv(key), "").trim(); }
    private static String envOr(String key, String fallback) { String value = env(key); return value.isBlank() ? fallback : value; }
}
