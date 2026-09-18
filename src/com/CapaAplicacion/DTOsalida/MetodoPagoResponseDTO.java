package com.CapaAplicacion.DTOsalida;

import java.math.BigDecimal;

public record MetodoPagoResponseDTO(
    int idCliente,
    BigDecimal saldoDisponible
) {}
