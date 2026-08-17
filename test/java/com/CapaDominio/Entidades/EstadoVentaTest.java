package com.CapaDominio.Entidades;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EstadoVentaTest {

    @Test
    @DisplayName("PENDIENTE solo transiciona a PAGADO")
    void pendiente() {
        assertTrue(EstadoVenta.PENDIENTE.puedeTransicionarA(EstadoVenta.PAGADO));
        assertFalse(EstadoVenta.PENDIENTE.puedeTransicionarA(EstadoVenta.CANCELADO));
        assertFalse(EstadoVenta.PENDIENTE.puedeTransicionarA(EstadoVenta.ENVIADO));
        assertFalse(EstadoVenta.PENDIENTE.puedeTransicionarA(EstadoVenta.PENDIENTE));
    }

    @Test
    @DisplayName("PAGADO transiciona a ENVIADO o CANCELADO")
    void pagado() {
        assertTrue(EstadoVenta.PAGADO.puedeTransicionarA(EstadoVenta.ENVIADO));
        assertTrue(EstadoVenta.PAGADO.puedeTransicionarA(EstadoVenta.CANCELADO));
        assertFalse(EstadoVenta.PAGADO.puedeTransicionarA(EstadoVenta.PENDIENTE));
    }

    @Test
    @DisplayName("ENVIADO y CANCELADO son terminales")
    void terminales() {
        assertFalse(EstadoVenta.ENVIADO.puedeTransicionarA(EstadoVenta.CANCELADO));
        assertFalse(EstadoVenta.CANCELADO.puedeTransicionarA(EstadoVenta.ENVIADO));
    }
}