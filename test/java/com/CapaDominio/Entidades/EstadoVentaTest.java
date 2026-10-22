package com.CapaDominio.Entidades;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EstadoVentaTest {

    @Test
    @DisplayName("PENDIENTE transiciona a PAGADO, CANCELADO o EXPIRADO")
    void pendiente() {
        assertTrue(EstadoVenta.PENDIENTE.puedeTransicionarA(EstadoVenta.PAGADO));
        assertTrue(EstadoVenta.PENDIENTE.puedeTransicionarA(EstadoVenta.CANCELADO));
        assertTrue(EstadoVenta.PENDIENTE.puedeTransicionarA(EstadoVenta.EXPIRADO));
        assertFalse(EstadoVenta.PENDIENTE.puedeTransicionarA(EstadoVenta.ENVIADO));
        assertFalse(EstadoVenta.PENDIENTE.puedeTransicionarA(EstadoVenta.PENDIENTE));
    }

    @Test
    @DisplayName("PAGADO solo transiciona a ENVIADO")
    void pagado() {
        assertTrue(EstadoVenta.PAGADO.puedeTransicionarA(EstadoVenta.ENVIADO));
        assertFalse(EstadoVenta.PAGADO.puedeTransicionarA(EstadoVenta.CANCELADO));
        assertFalse(EstadoVenta.PAGADO.puedeTransicionarA(EstadoVenta.PENDIENTE));
    }

    @Test
    @DisplayName("ENVIADO, CANCELADO y EXPIRADO son terminales")
    void terminales() {
        assertFalse(EstadoVenta.ENVIADO.puedeTransicionarA(EstadoVenta.CANCELADO));
        assertFalse(EstadoVenta.CANCELADO.puedeTransicionarA(EstadoVenta.ENVIADO));
        assertFalse(EstadoVenta.EXPIRADO.puedeTransicionarA(EstadoVenta.PAGADO));
        assertFalse(EstadoVenta.EXPIRADO.puedeTransicionarA(EstadoVenta.ENVIADO));
    }
}