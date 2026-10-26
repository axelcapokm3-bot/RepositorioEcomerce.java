package com.CapaAplicacion.Seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Calcula y compara la huella SHA-256 del token de bootstrap. */
public final class HuellaTokenBootstrap {

    private HuellaTokenBootstrap() {
    }

    public static String calcular(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("El token de bootstrap es obligatorio.");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexadecimal = new StringBuilder(digest.length * 2);
            for (byte valor : digest) {
                hexadecimal.append(String.format("%02x", valor & 0xff));
            }
            return hexadecimal.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.", exception);
        }
    }

    public static boolean coincide(String huellaEsperada, String token) {
        if (huellaEsperada == null || huellaEsperada.isBlank()
                || token == null || token.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                huellaEsperada.getBytes(StandardCharsets.US_ASCII),
                calcular(token).getBytes(StandardCharsets.US_ASCII));
    }
}
