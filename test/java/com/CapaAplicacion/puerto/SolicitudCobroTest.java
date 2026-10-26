package com.CapaAplicacion.puerto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SolicitudCobroTest {

    @Test
    void exponeLosCamposDelCobro() {
        SolicitudCobro solicitud = solicitud("idem-1");

        assertEquals(7L, solicitud.idNegocio());
        assertEquals(42, solicitud.idVenta());
        assertEquals(ProveedorPago.STRIPE, solicitud.proveedor());
        assertEquals(new BigDecimal("200.00"), solicitud.monto());
        assertEquals("ARS", solicitud.moneda());
        assertEquals("idem-1", solicitud.idempotencyKey());
        assertEquals(1, solicitud.items().size());
        assertEquals(5, solicitud.items().get(0).idProducto());
        assertEquals(2, solicitud.items().get(0).cantidad());
        assertEquals(new BigDecimal("100.00"), solicitud.items().get(0).precioUnitario());
    }

    @Test
    void exigeClaveDeIdempotencia() {
        assertThrows(IllegalArgumentException.class, () -> solicitud(null));
        assertThrows(IllegalArgumentException.class, () -> solicitud(" "));
    }

    @Test
    void copiaLosItemsParaEvitarMutacionesExternas() {
        List<SolicitudCobro.ItemCobro> items = new ArrayList<>();
        items.add(new SolicitudCobro.ItemCobro(5, 2, new BigDecimal("100.00")));

        SolicitudCobro solicitud = new SolicitudCobro(
                7L,
                42,
                ProveedorPago.STRIPE,
                new BigDecimal("200.00"),
                "ARS",
                "Venta 42",
                "idem-1",
                items,
                "https://shop.example.com/ok",
                "https://shop.example.com/cancel",
                "https://shop.example.com/pending",
                "https://shop.example.com/webhook");

        items.clear();

        assertEquals(1, solicitud.items().size());
        assertThrows(UnsupportedOperationException.class,
                () -> solicitud.items().add(
                        new SolicitudCobro.ItemCobro(6, 1, BigDecimal.ONE)));
    }

    @Test
    void rechazaReferenciasYMontosInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> new SolicitudCobro(0L, 42, ProveedorPago.STRIPE,
                        BigDecimal.ONE, "ARS", null, "idem-1", List.of(),
                        null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SolicitudCobro(7L, 0, ProveedorPago.STRIPE,
                        BigDecimal.ONE, "ARS", null, "idem-1", List.of(),
                        null, null, null, null));
        assertThrows(NullPointerException.class,
                () -> new SolicitudCobro(7L, 42, null,
                        BigDecimal.ONE, "ARS", null, "idem-1", List.of(),
                        null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SolicitudCobro(7L, 42, ProveedorPago.STRIPE,
                        BigDecimal.ZERO, "ARS", null, "idem-1", List.of(),
                        null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SolicitudCobro(7L, 42, ProveedorPago.STRIPE,
                        BigDecimal.ONE, " ", null, "idem-1", List.of(),
                        null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SolicitudCobro(7L, 42, ProveedorPago.STRIPE,
                        BigDecimal.ONE, "ARS", null, "idem-1", null,
                        null, null, null, null));
    }

    @Test
    void validaLosItemsDelCobro() {
        assertThrows(IllegalArgumentException.class,
                () -> new SolicitudCobro.ItemCobro(0, 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> new SolicitudCobro.ItemCobro(1, 0, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> new SolicitudCobro.ItemCobro(1, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SolicitudCobro.ItemCobro(1, 1, new BigDecimal("-1")));
    }

    private SolicitudCobro solicitud(String idempotencyKey) {
        return new SolicitudCobro(
                7L,
                42,
                ProveedorPago.STRIPE,
                new BigDecimal("200.00"),
                "ARS",
                "Venta 42",
                idempotencyKey,
                List.of(new SolicitudCobro.ItemCobro(5, 2, new BigDecimal("100.00"))),
                "https://shop.example.com/ok",
                "https://shop.example.com/cancel",
                "https://shop.example.com/pending",
                "https://shop.example.com/webhook");
    }
}
