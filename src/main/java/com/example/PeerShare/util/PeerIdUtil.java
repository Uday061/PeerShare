// src/main/java/com/peershare/util/PeerIdUtil.java
package com.example.PeerShare.util;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility to generate and represent 160-bit (20-byte) peer IDs.
 */
public class PeerIdUtil {
    private static final int ID_LENGTH_BYTES = 20; // 160 bits

    /**
     * Generates a random 20-byte ID.
     */
    public static byte[] generateRandomPeerId() {
        byte[] id = new byte[ID_LENGTH_BYTES];
        SecureRandom rnd = new SecureRandom();
        rnd.nextBytes(id);
        return id;
    }

    /**
     * Encodes a 20-byte ID as a Base64 string (for logging / human‐readable).
     */
    public static String encodeToBase64(byte[] peerId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(peerId);
    }

    /**
     * Decodes a Base64 string back to a 20-byte ID.
     */
    public static byte[] decodeFromBase64(String base64) {
        return Base64.getUrlDecoder().decode(base64);
    }
}
