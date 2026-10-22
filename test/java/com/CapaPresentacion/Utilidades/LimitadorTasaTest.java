package com.CapaPresentacion.Utilidades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimitadorTasaTest {

    @BeforeEach
    void limpiarAntes() {
        LimitadorTasa.limpiar();
    }

    @AfterEach
    void limpiarDespues() {
        LimitadorTasa.detener();
        System.clearProperty("rate-limit.max-claves");
        System.clearProperty("rate-limit.catalogo.ip");
        System.clearProperty("rate-limit.catalogo.limite");
    }

    @Test
    @DisplayName("usa el límite específico y cae al límite base del ámbito")
    void resuelveLimiteEspecificoConFallback() {
        System.setProperty("rate-limit.catalogo.limite", "25");
        System.setProperty("rate-limit.catalogo.ip", "7");

        assertEquals(7, LimitadorTasa.limiteConfigurado("catalogo", "ip", 300));
        assertEquals(25, LimitadorTasa.limiteConfigurado("catalogo", "negocio", 300));
        assertEquals(300, LimitadorTasa.limiteConfigurado("otro", "ip", 300));
    }

    @Test
    @DisplayName("permite hasta el máximo y luego indica reintento")
    void permiteHastaElMaximo() {
        assertFalse(LimitadorTasa.evaluar("ip|1|test", 2, 60).excedido());
        assertFalse(LimitadorTasa.evaluar("ip|1|test", 2, 60).excedido());

        LimitadorTasa.Resultado excedido = LimitadorTasa.evaluar("ip|1|test", 2, 60);

        assertTrue(excedido.excedido());
        assertTrue(excedido.segundosParaReintentar() > 0);
        assertTrue(excedido.segundosParaReintentar() <= 60);
    }

    @Test
    @DisplayName("reinicia el contador cuando vence la ventana")
    void reiniciaAlVencerLaVentana() throws InterruptedException {
        assertFalse(LimitadorTasa.evaluar("ip|2|test", 1, 1).excedido());
        assertTrue(LimitadorTasa.evaluar("ip|2|test", 1, 1).excedido());

        Thread.sleep(1100L);

        assertFalse(LimitadorTasa.evaluar("ip|2|test", 1, 1).excedido());
    }

    @Test
    @DisplayName("ignora claves vacías y límites no positivos")
    void ignoraEntradasInvalidas() {
        assertFalse(LimitadorTasa.evaluar(null, 1, 60).excedido());
        assertFalse(LimitadorTasa.evaluar("", 1, 60).excedido());
        assertFalse(LimitadorTasa.evaluar("ip|3|test", 0, 60).excedido());
        assertFalse(LimitadorTasa.evaluar("ip|3|test", -1, 60).excedido());
    }

    @Test
    @DisplayName("limita la cantidad de claves almacenadas")
    void limitaCantidadDeClaves() {
        System.setProperty("rate-limit.max-claves", "2");

        LimitadorTasa.evaluar("ip|4|a", 10, 60);
        LimitadorTasa.evaluar("ip|4|b", 10, 60);
        LimitadorTasa.evaluar("ip|4|c", 10, 60);

        assertTrue(LimitadorTasa.cantidadClaves() <= 2);
    }
}
