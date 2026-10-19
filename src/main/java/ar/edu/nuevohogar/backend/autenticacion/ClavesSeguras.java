package ar.edu.nuevohogar.backend.autenticacion;

import java.security.*;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.mindrot.jbcrypt.BCrypt;

public final class ClavesSeguras {
    private static final int ITERATIONS = 600_000;

    public static String hash(String password) {
        if (password == null || password.length() < 10 || password.length() > 200)
            throw new IllegalArgumentException(
                    "La contraseña debe tener entre 10 y 200 caracteres.");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return "pbkdf2$"
                + ITERATIONS
                + "$"
                + Base64.getEncoder().encodeToString(salt)
                + "$"
                + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }

    public static boolean verify(String password, String stored) {
        try {
            if (password == null || stored == null) return false;
            // Los registros nuevos usan PBKDF2. También se aceptan hashes BCrypt
            // previos (por ejemplo, los creados desde PHP o por el primer módulo Java).
            if (stored.startsWith("$2y$") || stored.startsWith("$2b$"))
                stored = "$2a$" + stored.substring(4);
            if (stored.startsWith("$2a$")) return BCrypt.checkpw(password, stored);

            String[] parts = stored.split("\\$");
            if (parts.length != 4 || !parts[0].equals("pbkdf2")) return false;
            int n = Integer.parseInt(parts[1]);
            if (n < 100_000 || n > 2_000_000) return false;
            return MessageDigest.isEqual(
                    Base64.getDecoder().decode(parts[3]),
                    derive(password, Base64.getDecoder().decode(parts[2]), n));
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int n) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, n, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            spec.clearPassword();
        }
    }
}
