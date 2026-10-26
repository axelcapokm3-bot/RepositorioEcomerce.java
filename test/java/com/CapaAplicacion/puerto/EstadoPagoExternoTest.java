package com.CapaAplicacion.puerto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EstadoPagoExternoTest {

    @Test
    void exponeLosEstadosExternosSoportados() {
        assertArrayEquals(
                new EstadoPagoExterno[]{
                        EstadoPagoExterno.PENDIENTE,
                        EstadoPagoExterno.APROBADO,
                        EstadoPagoExterno.RECHAZADO,
                        EstadoPagoExterno.CANCELADO,
                        EstadoPagoExterno.EXPIRADO
                },
                EstadoPagoExterno.values());
        assertEquals(EstadoPagoExterno.EXPIRADO, EstadoPagoExterno.valueOf("EXPIRADO"));
    }
}
