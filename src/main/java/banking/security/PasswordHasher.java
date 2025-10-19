package banking.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Hashes operator credentials using SHA-256 with a per-password random salt.
 */
public class PasswordHasher {
    private static final int SALT_BYTES = 16;
    private final SecureRandom random;

    public PasswordHasher() {
        this(new SecureRandom());
    }

    PasswordHasher(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public String hash(String password) {
        Objects.requireNonNull(password, "password");
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] digest = digest(password, salt);
        return Base64.getEncoder().encodeToString(salt) + ':'
            + Base64.getEncoder().encodeToString(digest);
    }

    public boolean verify(String password, String storedHash) {
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(storedHash, "storedHash");
        String[] parts = storedHash.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Stored hash has unexpected format");
        }
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expected = Base64.getDecoder().decode(parts[1]);
        byte[] actual = digest(password, salt);
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] digest(String password, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
