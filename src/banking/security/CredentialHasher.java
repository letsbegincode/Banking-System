package banking.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility responsible for hashing passwords and verifying raw credentials against stored digests.
 */
public final class CredentialHasher {
    private static final SecureRandom RANDOM = new SecureRandom();

    private CredentialHasher() {
    }

    /**
     * Generates a salted SHA-256 hash for the supplied password.
     *
     * @param password raw password
     * @return salt and digest encoded as {@code salt:hash}
     */
    public static String hashPassword(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = digest(password, salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Validates the password against the stored salt and digest.
     *
     * @param password raw password
     * @param stored   stored {@code salt:hash} representation
     * @return {@code true} when the password matches the stored digest
     */
    public static boolean verifyPassword(String password, String stored) {
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        String[] parts = stored.split(":", 2);
        if (parts.length != 2) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
        byte[] actualHash = digest(password, salt);
        if (expectedHash.length != actualHash.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < expectedHash.length; i++) {
            diff |= expectedHash[i] ^ actualHash[i];
        }
        return diff == 0;
    }

    private static byte[] digest(String password, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }
}
