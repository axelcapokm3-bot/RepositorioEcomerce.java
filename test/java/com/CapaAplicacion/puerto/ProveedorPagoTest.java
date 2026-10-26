package com.CapaAplicacion.puerto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProveedorPagoTest {

    @Test
    void exponeLosProveedoresSoportados() {
        assertArrayEquals(
                new ProveedorPago[]{ProveedorPago.MERCADO_PAGO, ProveedorPago.STRIPE},
                ProveedorPago.values());
        assertEquals(ProveedorPago.STRIPE, ProveedorPago.valueOf("STRIPE"));
    }
}
