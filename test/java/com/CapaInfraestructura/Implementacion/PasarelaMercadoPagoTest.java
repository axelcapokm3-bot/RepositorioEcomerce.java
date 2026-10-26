package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.puerto.ProveedorPago;
import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    @Test
    void aceptaLaPropiedadCheckoutReturnUrlComoAlternativa() {
        String urlAnterior = System.getProperty("mercadopago.checkout-return-url");
        try {
            System.setProperty(
                    "mercadopago.checkout-return-url",
                    "https://checkout.example.com/src/index.html");

            ConfiguracionCobro configuracion = new ConfiguracionCobro(null);
            assertEquals(
                    "https://checkout.example.com/src/index.html?payment_status=success",
                    configuracion.urlRetorno(1L, ProveedorPago.MERCADO_PAGO));
        } finally {
            restaurarPropiedad("mercadopago.checkout-return-url", urlAnterior);
        }
    }

    @Test
    void aceptaLaUrlPublicaDesdeLaVariableDeEntorno() {
        String publicBaseUrl = System.getenv("MERCADOPAGO_PUBLIC_BASE_URL");
        assumeTrue(publicBaseUrl != null && !publicBaseUrl.isBlank());

        String urlAnterior = System.getProperty("mercadopago.public-base-url");
        try {
            System.clearProperty("mercadopago.public-base-url");

            ConfiguracionCobro configuracion = new ConfiguracionCobro(null);
            assertDoesNotThrow(() -> configuracion.urlRetorno(1L, ProveedorPago.MERCADO_PAGO));
        } finally {
            restaurarPropiedad("mercadopago.public-base-url", urlAnterior);
        }
    }

    private static void restaurarPropiedad(String clave, String valorAnterior) {
        if (valorAnterior == null) {
            System.clearProperty(clave);
        } else {
            System.setProperty(clave, valorAnterior);
        }
    }
}