package com.CapaAplicacion.DTOsalida;

public record VinculoResponseDTO(
        long idNegocio,
        String nombreNegocio,
        String subdominio,
        int idCliente,
        String rol,
        String estado
) {
}
