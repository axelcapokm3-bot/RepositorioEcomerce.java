package com.CapaInfraestructura.Implementacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class HuellaToken {

    private HuellaToken() {
    }

    public static String calcular(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("El token es obligatorio.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No se pudo calcular la huella del token.", exception);
        }
    }
}
