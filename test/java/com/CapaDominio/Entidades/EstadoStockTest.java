package com.CapaDominio.Entidades;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EstadoStockTest {

    @Test
    @DisplayName("Evaluar estado: agotado, crítico y disponible")
    void evaluarEstado() {
        assertEquals(EstadoStock.AGOTADO, EstadoStock.evaluarEstado(0));
        assertEquals(EstadoStock.CRITICO, EstadoStock.evaluarEstado(1));
        assertEquals(EstadoStock.CRITICO, EstadoStock.evaluarEstado(10));
        assertEquals(EstadoStock.DISPONIBLE, EstadoStock.evaluarEstado(11));
    }

    @Test
    @DisplayName("Cantidad a reponer según estado")
    void cantidadAReponer() {
        assertEquals(100, EstadoStock.AGOTADO.getCantidadAReponer());
        assertEquals(50, EstadoStock.CRITICO.getCantidadAReponer());
        assertEquals(0, EstadoStock.DISPONIBLE.getCantidadAReponer());
    }
}