package ar.edu.nuevohogar.backend;

import ar.edu.nuevohogar.persistencia.dao.ConexionBaseDatos;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Subida directa mediante preset unsigned; no utiliza ni almacena el API Secret. */
public final class CloudinaryService {
    private static final long MAX_BYTES = 50L * 1024 * 1024;
    private static final Pattern SECURE_URL = Pattern.compile("\\\"secure_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final String cloudName;
    private final String uploadPreset;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public CloudinaryService(ConexionBaseDatos db) {
        cloudName = db.cloudinaryCloudName;
        uploadPreset = db.cloudinaryUploadPreset;
        if (!cloudName.isBlank() && !cloudName.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("El cloud name de Cloudinary tiene un formato inválido.");
        if (!uploadPreset.isBlank() && !uploadPreset.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("El nombre del preset de Cloudinary tiene un formato inválido.");
    }

    public boolean configured() {
        return !cloudName.isBlank() && !uploadPreset.isBlank();
    }

    public String subir(Path file, boolean video) throws IOException, InterruptedException {
        if (!configured()) throw new IllegalStateException(
                "Configurá cloudinary.cloud_name y cloudinary.upload_preset en config.properties.");
        if (!Files.isRegularFile(file)) throw new IllegalArgumentException("No se encontró el archivo seleccionado.");
        long size = Files.size(file);
        if (size == 0 || size > MAX_BYTES) throw new IllegalArgumentException("El archivo debe pesar entre 1 byte y 50 MB.");
        String boundary = "----NuevoHogar" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipart(file, boundary);
        String endpoint = "https://api.cloudinary.com/v1_1/" + cloudName + "/"
                + (video ? "video" : "image") + "/upload";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Cloudinary rechazó el archivo (HTTP " + response.statusCode() + "). Revisá el preset unsigned y sus límites.");
        Matcher matcher = SECURE_URL.matcher(response.body());
        if (!matcher.find()) throw new IOException("Cloudinary no devolvió la URL segura del archivo.");
        return matcher.group(1);
    }

    private byte[] multipart(Path file, String boundary) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        String filename = file.getFileName().toString().replace("\"", "");
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"upload_preset\"\r\n\r\n" + uploadPreset + "\r\n");
        String mime = Files.probeContentType(file);
        if (mime == null) mime = "application/octet-stream";
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\nContent-Type: " + mime + "\r\n\r\n");
        Files.copy(file, out);
        write(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static void write(java.io.OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
