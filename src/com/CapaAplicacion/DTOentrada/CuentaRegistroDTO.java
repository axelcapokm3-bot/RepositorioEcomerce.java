package com.CapaAplicacion.DTOentrada;

public record CuentaRegistroDTO(
        String nombreCuenta,
        String correoElectronico,
        String contraseniaPlana,
        int idCliente,
        String rol
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
        if (idCliente <= 0) {
            throw new IllegalArgumentException("La cuenta debe vincularse a un cliente válido.");
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
