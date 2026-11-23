package ar.edu.nuevohogar.backend;

import ar.edu.nuevohogar.persistencia.dao.ConexionBaseDatos;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;

/** Cliente para guardar archivos en el hosting compartido por todas las computadoras. */
final class MediaApiClient {
    private final String endpoint;
    private final String baseUrl;
    private HttpClient http;
    private String csrf;
    private String lastFailure;

    MediaApiClient(ConexionBaseDatos db) {
        endpoint = db.mediaApiUrl;
        baseUrl = db.mediaWebUrl;
        if (!endpoint.isBlank()) {
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !isLocal(uri.getHost()))
                throw new IllegalArgumentException("La API de archivos debe usar HTTPS.");
        }
    }

    boolean enabled() { return !endpoint.isBlank(); }

    void login(String email, String password) {
        if (!enabled()) return;
        csrf = null;
        lastFailure = null;
        try {
            String body = "{\"email\":" + quote(email) + ",\"password\":" + quote(password) + "}";
            HttpRequest request = HttpRequest.newBuilder(uri("?action=login"))
                    .timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            String response = send(request);
            csrf = jsonString(response, "csrf_token");
            if (csrf == null || csrf.isBlank()) throw new IllegalStateException("El servidor no inició la sesión de archivos.");
        } catch (RuntimeException e) {
            csrf = null;
            lastFailure = e.getMessage();
        }
    }

    String storageWarning() {
        if (!enabled() || csrf != null) return null;
        String details = lastFailure == null || lastFailure.isBlank() ? "" : " Detalle: " + lastFailure;
        return "Iniciaste sesión, pero el almacenamiento multimedia del servidor no está disponible."
                + details + " Podés seguir usando la app; las cargas y descargas remotas volverán cuando Torga restablezca el servicio.";
    }

    void logout() {
        if (!enabled() || csrf == null) return;
        String boundary = "----NuevoHogar" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest request = HttpRequest.newBuilder(uri("?action=logout"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(field(boundary, "csrf_token", csrf))).build();
        try { send(request); } finally { csrf = null; }
    }

    void upload(String target, int id, String type, Path source) {
        if (!enabled()) return;
        requireSession("subir archivos");
        String boundary = "----NuevoHogar" + UUID.randomUUID().toString().replace("-", "");
        try {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            for (String[] pair : new String[][]{{"csrf_token", csrf}, {"target", target},
                    {"id", Integer.toString(id)}, {"type", type}}) data.write(field(boundary, pair[0], pair[1]));
            String name = source.getFileName().toString().replace("\"", "_");
            data.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + name
                    + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            Files.copy(source, data);
            data.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(uri("?action=upload"))
                    .timeout(Duration.ofSeconds(180)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(data.toByteArray())).build();
            send(request);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo seleccionado.", e);
        }
    }

    Path download(int documentId, String storedName) {
        requireSession("consultar documentos privados");
        HttpRequest request = HttpRequest.newBuilder(uri("?action=download&id=" + documentId))
                .timeout(Duration.ofSeconds(30)).GET().build();
        try {
            HttpResponse<byte[]> response = http().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException(error(response.body(), response.statusCode()));
            String ext = storedName != null && storedName.matches("(?i).+\\.(pdf|jpg|jpeg|png|webp)")
                    ? storedName.substring(storedName.lastIndexOf('.')) : ".bin";
            Path temp = Files.createTempFile("nuevo-hogar-documento-", ext);
            Files.write(temp, response.body(), StandardOpenOption.TRUNCATE_EXISTING);
            temp.toFile().deleteOnExit();
            return temp;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la descarga del documento.", e);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo descargar el documento privado.", e);
        }
    }

    String publicUrl(String stored) {
        if (stored == null || stored.isBlank() || stored.startsWith("https://") || stored.startsWith("http://")) return stored;
        if (!baseUrl.isBlank() && stored.startsWith("/")) return baseUrl + stored;
        return null;
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException(error(response.body().getBytes(StandardCharsets.UTF_8), response.statusCode()));
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la conexión con el almacenamiento de archivos.", e);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo conectar con el almacenamiento de archivos: " + e.getMessage(), e);
        }
    }

    private URI uri(String suffix) { return URI.create(endpoint + suffix); }
    private synchronized HttpClient http() {
        if (http == null) {
            CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        }
        return http;
    }
    private void requireSession(String operation) {
        if (csrf != null) return;
        String warning = storageWarning();
        throw new IllegalStateException(warning != null ? warning : "Iniciá sesión para " + operation + ".");
    }
    private static byte[] field(String boundary, String name, String value) {
        return ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
    private static String error(byte[] body, int status) {
        String text = new String(body, StandardCharsets.UTF_8);
        String message = jsonString(text, "error");
        return message == null ? "El servidor de archivos respondió con error (" + status + ")." : message;
    }
    private static String jsonString(String json, String key) {
        var matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(key)
                + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(json);
        if (!matcher.find()) return null;
        return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }
    private static boolean isLocal(String host) {
        return host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1"));
    }
}
