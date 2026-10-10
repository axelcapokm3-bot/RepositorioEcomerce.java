package com.CapaAplicacion.DTOs;

import java.math.BigDecimal;

/** Datos normalizados usados para solicitar una Checkout Preference. */
public record PagoMercadoPagoRequestDTO(
        String descripcion,
        int cantidad,
        BigDecimal monto,
        String moneda,
        String claveIntento
) {
    public PagoMercadoPagoRequestDTO {
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripción del pago es obligatoria.");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero.");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero.");
        }
        if (moneda == null || moneda.isBlank()) {
            throw new IllegalArgumentException("La moneda del pago es obligatoria.");
        }
        if (claveIntento == null || claveIntento.isBlank()) {
            throw new IllegalArgumentException("La clave del intento de pago es obligatoria.");
        }
    }
}
