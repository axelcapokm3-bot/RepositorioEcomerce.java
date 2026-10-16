package com.CapaAplicacion.DTOentrada;

public record CuentaIngresoDTO(
        String correoElectronico,
        String contraseniaPlana
) {
    public CuentaIngresoDTO {
        if (correoElectronico == null || correoElectronico.trim().isEmpty()) {
            throw new IllegalArgumentException("El correo electrónico es obligatorio.");
        }
        if (contraseniaPlana == null || contraseniaPlana.isEmpty()) {
            throw new IllegalArgumentException("La contraseña es obligatoria.");
        }
    }
}
