package com.CapaAplicacion.puerto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EventoWebhookTest {

    @Test
    void exponeLosCamposDelEvento() {
        EventoWebhook evento = new EventoWebhook(
                ProveedorPago.MERCADO_PAGO,
                "request-123",
                "payment",
                "5001",
                EstadoPagoExterno.PENDIENTE,
                "{\"data\":{\"id\":\"5001\"}}");

        assertSame(ProveedorPago.MERCADO_PAGO, evento.proveedor());
        assertEquals("request-123", evento.idEvento());
        assertEquals("payment", evento.tipo());
        assertEquals("5001", evento.idExterno());
        assertSame(EstadoPagoExterno.PENDIENTE, evento.estado());
        assertEquals("{\"data\":{\"id\":\"5001\"}}", evento.payload());
    }

    @Test
    void exigeLosCamposObligatorios() {
        assertThrows(NullPointerException.class,
                () -> new EventoWebhook(null, "id", "payment", "5001",
                        EstadoPagoExterno.PENDIENTE, "{}"));
        assertThrows(IllegalArgumentException.class,
                () -> new EventoWebhook(ProveedorPago.STRIPE, " ", "payment", "5001",
                        EstadoPagoExterno.PENDIENTE, "{}"));
        assertThrows(IllegalArgumentException.class,
                () -> new EventoWebhook(ProveedorPago.STRIPE, "id", " ", "5001",
                        EstadoPagoExterno.PENDIENTE, "{}"));
        assertThrows(IllegalArgumentException.class,
                () -> new EventoWebhook(ProveedorPago.STRIPE, "id", "payment", " ",
                        EstadoPagoExterno.PENDIENTE, "{}"));
        assertThrows(NullPointerException.class,
                () -> new EventoWebhook(ProveedorPago.STRIPE, "id", "payment", "5001",
                        null, "{}"));
        assertThrows(IllegalArgumentException.class,
                () -> new EventoWebhook(ProveedorPago.STRIPE, "id", "payment", "5001",
                        EstadoPagoExterno.PENDIENTE, " "));
    }
}
