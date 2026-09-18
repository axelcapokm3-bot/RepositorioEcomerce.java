package com.CapaDominio.Entidades;

import java.math.BigDecimal;

public final class PoliticaDescuento {
    private static final BigDecimal PORCENTAJE_ESTANDAR = new BigDecimal("0.10");
    private static final BigDecimal MONTO_ESTANDAR = new BigDecimal("20.00");

    private PoliticaDescuento() {
    }

    public static IDescuentos resolver(String tipo) {
        if (tipo == null || tipo.isBlank() || "NINGUNO".equalsIgnoreCase(tipo)) {
            return null;
        }

        return switch (tipo.toUpperCase()) {
            case "PORCENTAJE" -> new DescuentoPorPorcentaje(PORCENTAJE_ESTANDAR);
            case "MONTO" -> new DescuentoPorMonto(MONTO_ESTANDAR, BigDecimal.ZERO);
            case "ESPECIAL" -> new DescuentosEspeciales();
            default -> throw new IllegalArgumentException("Tipo de descuento no permitido.");
        };
    }
}