package com.CapaDominio.Entidades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.CapaDominio.Excepciones.TransicionPagoInvalidaException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PagoTest {

    @Test
    void permiteTransicionesDesdePendiente() {
        Pago pago = pago(EstadoPago.PENDIENTE);
        pago.transicionarA(EstadoPago.APROBADO);
        assertEquals(EstadoPago.APROBADO, pago.getEstado());
    }

    @Test
    void ignoraLaMismaTransicion() {
        Pago pago = pago(EstadoPago.PENDIENTE);
        pago.transicionarA(EstadoPago.PENDIENTE);
        assertEquals(EstadoPago.PENDIENTE, pago.getEstado());
    }

    @Test
    void rechazaRetrocesosYEstadosTerminales() {
        Pago aprobado = pago(EstadoPago.APROBADO);
        assertThrows(TransicionPagoInvalidaException.class,
                () -> aprobado.transicionarA(EstadoPago.RECHAZADO));
        assertEquals(EstadoPago.APROBADO, aprobado.getEstado());
    }

    private Pago pago(EstadoPago estado) {
        Pago pago = new Pago(
                1, 7, TipoDePago.MERCADO_PAGO.name(), null, null, "order-7",
                estado, new BigDecimal("200.00"), "ARS", null, "{}",
                "venta-7-mercado_pago", null, null);
        pago.setIdNegocio(1L);
        return pago;
    }
}
