package com.CapaAplicacion.puerto;

import java.util.Objects;

public record ResultadoInicio(
        String idExterno,
        String urlPago,
        EstadoPagoExterno estado,
        String rawResponse) {

    public ResultadoInicio {
        if (idExterno == null || idExterno.isBlank()) {
            throw new IllegalArgumentException("El identificador externo del cobro es obligatorio.");
        }
        if (urlPago == null || urlPago.isBlank()) {
            throw new IllegalArgumentException("La URL de pago del cobro es obligatoria.");
        }
        Objects.requireNonNull(estado, "El estado externo del cobro es obligatorio.");
    }
}
