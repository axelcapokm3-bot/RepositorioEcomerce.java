package com.CapaAplicacion.DTOsalida;

public record SesionResponseDTO(
        String token,
        String rol,
        long idCuenta,
        int idCliente,
        long idNegocio,
        long expiraEn
) {
}
