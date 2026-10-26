package com.CapaAplicacion.puerto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record SolicitudCobro(
        long idNegocio,
        int idVenta,
        ProveedorPago proveedor,
        BigDecimal monto,
        String moneda,
        String descripcion,
        String idempotencyKey,
        List<ItemCobro> items,
        String urlRetorno,
        String urlCancelacion,
        String urlPendiente,
        String urlWebhook) {

    public SolicitudCobro {
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El negocio de la solicitud de cobro debe ser válido.");
        }
        if (idVenta <= 0) {
            throw new IllegalArgumentException("La venta de la solicitud de cobro debe ser válida.");
        }
        Objects.requireNonNull(proveedor, "El proveedor de pago es obligatorio.");
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto de la solicitud de cobro debe ser mayor a cero.");
        }
        if (moneda == null || moneda.isBlank()) {
            throw new IllegalArgumentException("La moneda de la solicitud de cobro es obligatoria.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La clave de idempotencia del cobro es obligatoria.");
        }
        if (items == null) {
            throw new IllegalArgumentException("Los ítems de la solicitud de cobro son obligatorios.");
        }
        items = List.copyOf(items);
    }

    public record ItemCobro(int idProducto, int cantidad, BigDecimal precioUnitario) {

        public ItemCobro {
            if (idProducto <= 0) {
                throw new IllegalArgumentException("El producto del ítem debe ser válido.");
            }
            if (cantidad <= 0) {
                throw new IllegalArgumentException("La cantidad del ítem debe ser mayor a cero.");
            }
            if (precioUnitario == null || precioUnitario.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El precio unitario del ítem no es válido.");
            }
        }
    }
}
