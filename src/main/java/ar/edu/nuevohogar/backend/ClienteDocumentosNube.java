package ar.edu.nuevohogar.backend;

import ar.edu.nuevohogar.persistencia.dao.ConexionBaseDatos;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cliente HTTPS para el backend desplegado que controla los documentos privados. */
public final class ClienteDocumentosNube {
    private static final long MAX_BYTES = 50L * 1024 * 1024;
    private static final Pattern TOKEN = Pattern.compile("\\\"token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern URL = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private volatile String token;

    public ClienteDocumentosNube(ConexionBaseDatos db) {
        baseUrl = db.privateMediaApiUrl;
        if (!baseUrl.isBlank() && !baseUrl.startsWith("https://"))
            throw new IllegalArgumentException("La URL del servicio privado debe usar HTTPS.");
    }

    public boolean configured() { return !baseUrl.isBlank(); }

    public void iniciarSesion(String email, String password) throws IOException, InterruptedException {
        if (!configured()) return;
        String form = "email=" + encode(email) + "&password=" + encode(password);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/sesion"))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200)
            throw new IOException("No se pudo iniciar la sesión segura del servicio de documentos (HTTP " + response.statusCode() + ").");
        Matcher found = TOKEN.matcher(response.body());
        if (!found.find()) throw new IOException("El servicio de documentos no devolvió una sesión válida.");
        token = found.group(1);
    }

    public void cerrarSesion() {
        String current = token;
        token = null;
        if (!configured() || current == null) return;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/sesion/cerrar"))
                    .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + current)
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) { }
    }

    public void subirDocumento(int propiedad, String tipo, Path archivo) throws IOException, InterruptedException {
        subirDocumento(propiedad, tipo, archivo, 0);
    }

    public void subirDocumento(int propiedad, String tipo, Path archivo, int documentoExistente)
            throws IOException, InterruptedException {
        if (!configured()) throw new IllegalStateException("Falta private_media.api_url en config.properties.");
        if (token == null) throw new IllegalStateException("Iniciá sesión nuevamente para cargar documentos en la nube.");
        long size = Files.size(archivo);
        if (size == 0 || size > MAX_BYTES) throw new IllegalArgumentException("El archivo debe pesar entre 1 byte y 50 MB.");
        String query = "propiedad=" + propiedad + "&tipo=" + encode(tipo)
                + "&nombre=" + encode(archivo.getFileName().toString());
        if (documentoExistente > 0) query += "&documento=" + documentoExistente;
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/documentos?" + query))
                .timeout(Duration.ofMinutes(3)).header("Authorization", "Bearer " + token)
                .header("Content-Type", contentType(archivo))
                .POST(HttpRequest.BodyPublishers.ofByteArray(Files.readAllBytes(archivo))).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 201)
            throw new IOException("No se pudo guardar el documento privado en la nube (HTTP " + response.statusCode() + ").");
    }

    public String urlDocumento(int propiedad, int documento) throws IOException, InterruptedException {
        if (!configured() || token == null) throw new IllegalStateException("Iniciá sesión en el servicio compartido para abrir documentos privados.");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/documentos/" + documento
                        + "/url?propiedad=" + propiedad))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200)
            throw new IOException("El servicio rechazó el acceso al documento (HTTP " + response.statusCode() + ").");
        Matcher found = URL.matcher(response.body());
        if (!found.find()) throw new IOException("El servicio no devolvió un enlace temporal válido.");
        return found.group(1).replace("\\/", "/").replace("\\u0026", "&");
    }

    private static String contentType(Path file) throws IOException {
        String type = Files.probeContentType(file);
        return type == null ? "application/octet-stream" : type;
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
