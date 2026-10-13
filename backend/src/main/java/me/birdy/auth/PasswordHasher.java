package me.birdy.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hashes passwords with PBKDF2-HMAC-SHA256.
 *
 * <p>The stored form is {@code pbkdf2-sha256$<iterations>$<salt>$<hash>} (salt and hash in unpadded Base64).
 * Because the parameters travel with the hash, the iteration count can be raised later: old hashes keep
 * verifying and {@link #needsRehash(String)} tells the caller to upgrade them at the next login.
 */
@ApplicationScoped
public class PasswordHasher {

    static final String SCHEME = "pbkdf2-sha256";
    static final int ITERATIONS = 600_000;
    static final int SALT_BYTES = 16;
    static final int HASH_BYTES = 32;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String SEPARATOR = "$";
    private static final Base64.Encoder ENCODER = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        return hash(password, ITERATIONS);
    }

    String hash(String password, int iterations) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, iterations, HASH_BYTES);
        return String.join(SEPARATOR, SCHEME, Integer.toString(iterations),
                ENCODER.encodeToString(salt), ENCODER.encodeToString(hash));
    }

    /** Returns whether {@code password} matches the stored hash; malformed stored values never match. */
    public boolean verify(String password, String stored) {
        Parsed parsed = parse(stored);
        if (parsed == null || password == null) {
            return false;
        }
        byte[] candidate = derive(password, parsed.salt(), parsed.iterations(), parsed.hash().length);
        return MessageDigest.isEqual(candidate, parsed.hash());
    }

    /** Returns whether the stored hash was made with weaker parameters than the current ones. */
    public boolean needsRehash(String stored) {
        Parsed parsed = parse(stored);
        return parsed == null
                || parsed.iterations() < ITERATIONS
                || parsed.salt().length < SALT_BYTES
                || parsed.hash().length < HASH_BYTES;
    }

    private record Parsed(int iterations, byte[] salt, byte[] hash) {
    }

    private static Parsed parse(String stored) {
        if (stored == null) {
            return null;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !SCHEME.equals(parts[0])) {
            return null;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = DECODER.decode(parts[2]);
            byte[] hash = DECODER.decode(parts[3]);
            if (iterations < 1 || salt.length == 0 || hash.length == 0) {
                return null;
            }
            return new Parsed(iterations, salt, hash);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int lengthBytes) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, lengthBytes * Byte.SIZE);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available", e);
        } finally {
            spec.clearPassword();
        }
    }
}
