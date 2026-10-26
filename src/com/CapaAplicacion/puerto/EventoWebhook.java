package com.CapaAplicacion.puerto;

import java.util.Objects;

public record EventoWebhook(
        ProveedorPago proveedor,
        String idEvento,
        String tipo,
        String idExterno,
        EstadoPagoExterno estado,
        String payload) {

    public EventoWebhook {
        Objects.requireNonNull(proveedor, "El proveedor del evento es obligatorio.");
        if (idEvento == null || idEvento.isBlank()) {
            throw new IllegalArgumentException("El identificador del evento es obligatorio.");
        }
        if (tipo == null || tipo.isBlank()) {
            throw new IllegalArgumentException("El tipo del evento es obligatorio.");
        }
        if (idExterno == null || idExterno.isBlank()) {
            throw new IllegalArgumentException("El identificador externo del pago es obligatorio.");
        }
        Objects.requireNonNull(estado, "El estado del evento es obligatorio.");
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("El payload del evento es obligatorio.");
        }
    }
}
