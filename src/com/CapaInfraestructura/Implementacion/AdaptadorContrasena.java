package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IServicioContrasena;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AdaptadorContrasena implements IServicioContrasena {
    private static final String PREFIJO = "pbkdf2";
    private static final int ITERACIONES = 210000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;

    private final SecureRandom aleatorio;

    public AdaptadorContrasena() {
        this.aleatorio = new SecureRandom();
    }

    AdaptadorContrasena(SecureRandom aleatorio) {
        if (aleatorio == null) {
            throw new IllegalArgumentException("El generador aleatorio es obligatorio.");
        }
        this.aleatorio = aleatorio;
    }

    @Override
    public String generarHash(String contraseniaPlana) {
        if (contraseniaPlana == null || contraseniaPlana.isEmpty()) {
            throw new IllegalArgumentException("La contraseña es obligatoria.");
        }
        byte[] sal = new byte[BYTES_SAL];
        aleatorio.nextBytes(sal);
        byte[] hash = derivar(contraseniaPlana.toCharArray(), sal, ITERACIONES);
        return PREFIJO + "$" + ITERACIONES + "$"
                + Base64.getEncoder().withoutPadding().encodeToString(sal) + "$"
                + Base64.getEncoder().withoutPadding().encodeToString(hash);
    }

    @Override
    public boolean verificar(String contraseniaPlana, String hashAlmacenado) {
        if (contraseniaPlana == null || contraseniaPlana.isEmpty()
                || hashAlmacenado == null || hashAlmacenado.isEmpty()) {
            return false;
        }
        String[] partes = hashAlmacenado.split("\\$");
        if (partes.length != 4 || !PREFIJO.equals(partes[0])) {
            return false;
        }
        int iteraciones;
        try {
            iteraciones = Integer.parseInt(partes[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (iteraciones <= 0) {
            return false;
        }
        byte[] sal;
        byte[] esperado;
        try {
            sal = Base64.getDecoder().decode(partes[2]);
            esperado = Base64.getDecoder().decode(partes[3]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] calculado = derivar(contraseniaPlana.toCharArray(), sal, iteraciones);
        return MessageDigest.isEqual(esperado, calculado);
    }

    private static byte[] derivar(char[] contrasenia, byte[] sal, int iteraciones) {
        try {
            SecretKeyFactory fabrica = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec especificacion = new PBEKeySpec(contrasenia, sal, iteraciones, BITS_HASH);
            try {
                return fabrica.generateSecret(especificacion).getEncoded();
            } finally {
                especificacion.clearPassword();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("No se pudo derivar la clave de la contraseña.", e);
        }
    }
}
