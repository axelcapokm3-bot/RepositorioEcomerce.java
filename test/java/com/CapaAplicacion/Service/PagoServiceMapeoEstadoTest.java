package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.CapaAplicacion.puerto.EstadoPagoExterno;
import com.CapaDominio.Entidades.EstadoPago;
import org.junit.jupiter.api.Test;

class PagoServiceMapeoEstadoTest {

    @Test
    void mapeaEstadoExpiradoACancelado() {
        assertSame(EstadoPago.CANCELADO,
                PagoService.mapearEstadoExterno(EstadoPagoExterno.EXPIRADO));
    }

    @Test
    void mapeaTodosLosEstadosSinAgregarEstadosInternos() {
        assertSame(EstadoPago.PENDIENTE,
                PagoService.mapearEstadoExterno(EstadoPagoExterno.PENDIENTE));
        assertSame(EstadoPago.APROBADO,
                PagoService.mapearEstadoExterno(EstadoPagoExterno.APROBADO));
        assertSame(EstadoPago.RECHAZADO,
                PagoService.mapearEstadoExterno(EstadoPagoExterno.RECHAZADO));
        assertSame(EstadoPago.CANCELADO,
                PagoService.mapearEstadoExterno(EstadoPagoExterno.CANCELADO));
        assertThrows(IllegalArgumentException.class,
                () -> PagoService.mapearEstadoExterno(null));
        assertEquals(4, EstadoPago.values().length);
    }
}
