package com.CapaAplicacion.DTOentrada;

import java.math.BigDecimal;

public record MetodoPagoRequestDTO(
    int idCliente,
    int claveTarjeta,
    BigDecimal saldoInicial
) {}
