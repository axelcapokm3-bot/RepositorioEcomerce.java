package com.CapaInfraestructura.Implementacion;

import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasarelaMercadoPagoTest {

    @Test
    void creaUrlsDeRetornoDesdeLaBasePublica() {
        PasarelaMercadoPago.ConfiguracionRetorno configuracion =
                PasarelaMercadoPago.crearConfiguracionRetorno(
                        "https://checkout.example.com/src/index.html",
                        null,
                        null,
                        null);

        assertEquals(
                "https://checkout.example.com/src/index.html?payment_status=success",
                configuracion.successUrl());
        assertEquals(
                "https://checkout.example.com/src/index.html?payment_status=failure",
                configuracion.failureUrl());
        assertEquals(
                "https://checkout.example.com/src/index.html?payment_status=pending",
                configuracion.pendingUrl());
        assertEquals("approved", configuracion.autoReturn());
    }

    @Test
    void conservaLosParametrosExistentesDeLaUrlBase() {
        PasarelaMercadoPago.ConfiguracionRetorno configuracion =
                PasarelaMercadoPago.crearConfiguracionRetorno(
                        "https://checkout.example.com/retorno?origen=local",
                        null,
                        null,
                        null);

        assertEquals(
                "https://checkout.example.com/retorno?origen=local&payment_status=success",
                configuracion.successUrl());
    }

    @Test
    void rechazaUrlsLocalesYNoSeguras() {
        assertThrows(
                PasarelaNoDisponibleException.class,
                () -> PasarelaMercadoPago.crearConfiguracionRetorno(
                        "http://127.0.0.1:5500/src/index.html",
                        null,
                        null,
                        null));
        assertThrows(
                PasarelaNoDisponibleException.class,
                () -> PasarelaMercadoPago.crearConfiguracionRetorno(
                        "https://localhost:5500/src/index.html",
                        null,
                        null,
                        null));
    }

    @Test
    void exigeUrlsDeRetornoCuandoNoHayBasePublica() {
        assertThrows(
                PasarelaNoDisponibleException.class,
                () -> PasarelaMercadoPago.crearConfiguracionRetorno(
                        null,
                        null,
                        null,
                        null));
    }
}