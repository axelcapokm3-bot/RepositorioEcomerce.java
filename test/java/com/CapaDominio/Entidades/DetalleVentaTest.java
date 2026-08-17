package com.CapaDominio.Entidades;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DetalleVentaTest {

    @Test
    @DisplayName("Constructor: crea detalle válido y calcula subtotal")
    void detalleValido() {
        DetalleVenta d = new DetalleVenta(1, new BigDecimal("45.00"), 2);
        assertEquals(1, d.getIdProducto());
        assertEquals(2, d.getCantidad());
        assertEquals(new BigDecimal("90.00"), d.getSubtotal());
    }

    @Test
    @DisplayName("Constructor: rechaza datos inválidos")
    void constructorRechazaInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new DetalleVenta(0, BigDecimal.TEN, 1));
        assertThrows(IllegalArgumentException.class, () -> new DetalleVenta(1, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new DetalleVenta(1, new BigDecimal("-1"), 1));
        assertThrows(IllegalArgumentException.class, () -> new DetalleVenta(1, BigDecimal.TEN, 0));
    }
}