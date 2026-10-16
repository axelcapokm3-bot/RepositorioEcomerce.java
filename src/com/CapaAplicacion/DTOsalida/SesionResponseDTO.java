package com.CapaAplicacion.DTOsalida;

public record SesionResponseDTO(
        String token,
        String rol,
        int idCuenta,
        int idCliente,
        long idNegocio,
        long expiraEn
) {
}
