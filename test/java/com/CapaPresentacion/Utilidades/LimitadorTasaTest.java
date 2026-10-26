package com.CapaPresentacion.Utilidades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    @DisplayName("bloquea la IP tras superar el máximo de intentos")
    void bloqueaTrasMaximoDeIntentos() {
        assertFalse(LimitadorTasa.evaluarIntento("ip|5|login", 2, 60, 30).excedido());
        assertFalse(LimitadorTasa.evaluarIntento("ip|5|login", 2, 60, 30).excedido());

        LimitadorTasa.Resultado bloqueado =
                LimitadorTasa.evaluarIntento("ip|5|login", 2, 60, 30);

        assertTrue(bloqueado.excedido());
        assertTrue(bloqueado.segundosParaReintentar() > 0);
        assertTrue(bloqueado.segundosParaReintentar() <= 30);
        assertTrue(LimitadorTasa.estaBloqueada("ip|5|login"));
        assertEquals(1, LimitadorTasa.cantidadBloqueos());
    }

    @Test
    @DisplayName("dos hilos concurrentes no superan el máximo permitido")
    void concurrenciaNoSuperaElMaximo() throws Exception {
        int maximo = 5;
        int intentosPorHilo = 20;
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        AtomicInteger permitidos = new AtomicInteger();
        List<Future<?>> tareas = new ArrayList<>();
        try {
            for (int hilo = 0; hilo < 2; hilo++) {
                tareas.add(ejecutor.submit(() -> {
                    inicio.await();
                    for (int intento = 0; intento < intentosPorHilo; intento++) {
                        if (!LimitadorTasa.evaluarIntento("ip|6|login", maximo, 60, 60).excedido()) {
                            permitidos.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            inicio.countDown();
            for (Future<?> tarea : tareas) {
                tarea.get();
            }
        } finally {
            ejecutor.shutdownNow();
        }

        assertEquals(maximo, permitidos.get());
        assertTrue(LimitadorTasa.estaBloqueada("ip|6|login"));
    }

    @Test
    @DisplayName("al expirar el bloqueo elimina el bloqueo y el contador de intentos")
    void liberaBloqueoYContadoresAlExpirar() throws InterruptedException {
        assertFalse(LimitadorTasa.evaluarIntento("ip|7|login", 1, 60, 1).excedido());
        assertTrue(LimitadorTasa.evaluarIntento("ip|7|login", 1, 60, 1).excedido());
        assertTrue(LimitadorTasa.estaBloqueada("ip|7|login"));

        Thread.sleep(1100L);
        assertFalse(LimitadorTasa.estaBloqueada("ip|7|login"));

        assertFalse(LimitadorTasa.evaluarIntento("ip|7|login", 1, 60, 1).excedido());
        assertEquals(0, LimitadorTasa.cantidadBloqueos());
        assertEquals(1, LimitadorTasa.cantidadIntentos());
    }

    @Test
    @DisplayName("ignora intentos con clave vacía o máximo no positivo")
    void ignoraIntentosInvalidos() {
        assertFalse(LimitadorTasa.evaluarIntento(null, 1, 60, 60).excedido());
        assertFalse(LimitadorTasa.evaluarIntento("", 1, 60, 60).excedido());
        assertFalse(LimitadorTasa.evaluarIntento("ip|8|login", 0, 60, 60).excedido());
        assertFalse(LimitadorTasa.evaluarIntento("ip|8|login", -1, 60, 60).excedido());
    }
}
