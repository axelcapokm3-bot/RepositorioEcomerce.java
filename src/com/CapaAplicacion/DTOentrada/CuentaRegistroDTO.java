package com.CapaAplicacion.DTOentrada;

import java.time.LocalDate;

public record CuentaRegistroDTO(
        String nombreCuenta,
        String correoElectronico,
        String contraseniaPlana,
        String dniCliente,
        String rol,
        String nombreCliente,
        String apellidoCliente,
        LocalDate fechaNacimientoCliente,
        String direccionCliente,
        String localidadCliente
) {
    public CuentaRegistroDTO {
        if (nombreCuenta == null || nombreCuenta.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de cuenta es obligatorio.");
        }
        if (nombreCuenta.trim().length() > 100) {
            throw new IllegalArgumentException("El nombre de cuenta no puede superar 100 caracteres.");
        }
        if (correoElectronico == null || correoElectronico.trim().isEmpty()) {
            throw new IllegalArgumentException("El correo electrónico es obligatorio.");
        }
        validarContrasenia(contraseniaPlana);
        validarDni(dniCliente);
        dniCliente = dniCliente.trim();
    }

    private static void validarDni(String dni) {
        if (dni == null || dni.trim().isEmpty()) {
            throw new IllegalArgumentException("El DNI del cliente es obligatorio.");
        }
        String dniLimpio = dni.trim();
        if (dniLimpio.length() < 7 || dniLimpio.length() > 8) {
            throw new IllegalArgumentException("El DNI debe contener entre 7 y 8 dígitos.");
        }
        for (int i = 0; i < dniLimpio.length(); i++) {
            if (!Character.isDigit(dniLimpio.charAt(i))) {
                throw new IllegalArgumentException("El DNI debe contener únicamente números.");
            }
        }
    }

    public static void validarContrasenia(String contrasenia) {
        if (contrasenia == null || contrasenia.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        }
        boolean tieneMayuscula = false;
        boolean tieneMinuscula = false;
        boolean tieneNumero = false;
        for (int i = 0; i < contrasenia.length(); i++) {
            char c = contrasenia.charAt(i);
            if (Character.isUpperCase(c)) {
                tieneMayuscula = true;
            } else if (Character.isLowerCase(c)) {
                tieneMinuscula = true;
            } else if (Character.isDigit(c)) {
                tieneNumero = true;
            }
        }
        if (!tieneMayuscula || !tieneMinuscula || !tieneNumero) {
            throw new IllegalArgumentException(
                    "La contraseña debe incluir al menos una mayúscula, una minúscula y un número.");
        }
    }
}
