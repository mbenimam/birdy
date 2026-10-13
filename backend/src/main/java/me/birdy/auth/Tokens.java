package me.birdy.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Creates the opaque random secrets handed to clients (session tokens, chat tickets) and the digests under
 * which the server remembers them, so a leaked database or heap dump does not reveal usable secrets.
 */
@ApplicationScoped
public class Tokens {

    static final int TOKEN_BYTES = 32;

    /** Length of a token in its textual form: 32 bytes in unpadded URL-safe Base64. */
    public static final int TOKEN_LENGTH = 43;

    private final SecureRandom random = new SecureRandom();

    /** Returns a new token: 32 random bytes in URL-safe Base64. */
    public String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Returns the SHA-256 digest of a token as 64 lowercase hexadecimal characters. */
    public String digest(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
