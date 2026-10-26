package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.puerto.ProveedorPago;
import com.CapaAplicacion.puerto.SolicitudCobro;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.order.OrderClient;
import com.mercadopago.client.order.OrderCreateRequest;
import com.mercadopago.core.MPRequestOptions;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PasarelaMercadoPagoCredencialesTest {

    private static final String CLAVE_TOKEN = "mercadopago.access-token";
    private static final String CLAVE_BASE = "mercadopago.public-base-url";

    @Test
    void iniciarCobroEnviaLaCredencialEnLasOpcionesDeLaOperacion() throws Exception {
        String tokenAnterior = System.getProperty(CLAVE_TOKEN);
        String baseAnterior = System.getProperty(CLAVE_BASE);
        String globalAnterior = MercadoPagoConfig.getAccessToken();
        try {
            System.setProperty(CLAVE_TOKEN, "token-negocio-uno");
            System.setProperty(CLAVE_BASE, "https://tienda.example.com/src/index.html");

            OrderClient orderClient = mock(OrderClient.class);
            when(orderClient.create(any(OrderCreateRequest.class), any(MPRequestOptions.class)))
                    .thenThrow(new RuntimeException("corte de prueba"));
            PasarelaMercadoPago pasarela = new PasarelaMercadoPago(null, orderClient);

            assertThrows(RuntimeException.class,
                    () -> pasarela.iniciarCobro(solicitud(1L, 5, "clave-1")));

            ArgumentCaptor<MPRequestOptions> opciones = ArgumentCaptor.forClass(MPRequestOptions.class);
            verify(orderClient).create(any(OrderCreateRequest.class), opciones.capture());
            assertEquals("token-negocio-uno", opciones.getValue().getAccessToken());
            assertEquals("clave-1", opciones.getValue().getCustomHeaders().get("X-Idempotency-Key"));
            assertEquals(globalAnterior, MercadoPagoConfig.getAccessToken(),
                    "No debe mutarse el estado global del SDK de Mercado Pago");
        } finally {
            restaurar(CLAVE_TOKEN, tokenAnterior);
            restaurar(CLAVE_BASE, baseAnterior);
        }
    }

    @Test
    void orderEnviadaIncluyeLaVentaComoReferenciaExterna() throws Exception {
        String tokenAnterior = System.getProperty(CLAVE_TOKEN);
        String baseAnterior = System.getProperty(CLAVE_BASE);
        try {
            System.setProperty(CLAVE_TOKEN, "token-negocio-uno");
            System.setProperty(CLAVE_BASE, "https://tienda.example.com/src/index.html");

            OrderClient orderClient = mock(OrderClient.class);
            when(orderClient.create(any(OrderCreateRequest.class), any(MPRequestOptions.class)))
                    .thenThrow(new RuntimeException("corte de prueba"));
            PasarelaMercadoPago pasarela = new PasarelaMercadoPago(null, orderClient);

            assertThrows(RuntimeException.class,
                    () -> pasarela.iniciarCobro(solicitud(1L, 5, "clave-1")));

            ArgumentCaptor<OrderCreateRequest> solicitud = ArgumentCaptor.forClass(OrderCreateRequest.class);
            verify(orderClient).create(solicitud.capture(), any(MPRequestOptions.class));
            assertEquals("5", solicitud.getValue().getExternalReference());
        } finally {
            restaurar(CLAVE_TOKEN, tokenAnterior);
            restaurar(CLAVE_BASE, baseAnterior);
        }
    }

    @Test
    void cadaOperacionUsaSuPropioTokenSinArrastreEntreNegocios() throws Exception {
        String tokenAnterior = System.getProperty(CLAVE_TOKEN);
        String baseAnterior = System.getProperty(CLAVE_BASE);
        try {
            System.setProperty(CLAVE_BASE, "https://tienda.example.com/src/index.html");

            OrderClient orderClient = mock(OrderClient.class);
            when(orderClient.create(any(OrderCreateRequest.class), any(MPRequestOptions.class)))
                    .thenThrow(new RuntimeException("corte de prueba"));

            PasarelaMercadoPago pasarela = new PasarelaMercadoPago(null, orderClient);

            System.setProperty(CLAVE_TOKEN, "token-negocio-uno");
            assertThrows(RuntimeException.class,
                    () -> pasarela.iniciarCobro(solicitud(1L, 5, "clave-1")));

            System.setProperty(CLAVE_TOKEN, "token-negocio-dos");
            assertThrows(RuntimeException.class,
                    () -> pasarela.iniciarCobro(solicitud(2L, 6, "clave-2")));

            ArgumentCaptor<MPRequestOptions> opciones = ArgumentCaptor.forClass(MPRequestOptions.class);
            verify(orderClient, times(2)).create(any(OrderCreateRequest.class), opciones.capture());
            assertEquals(
                    List.of("token-negocio-uno", "token-negocio-dos"),
                    opciones.getAllValues().stream()
                            .map(MPRequestOptions::getAccessToken)
                            .toList());
        } finally {
            restaurar(CLAVE_TOKEN, tokenAnterior);
            restaurar(CLAVE_BASE, baseAnterior);
        }
    }

    private static SolicitudCobro solicitud(long idNegocio, int idVenta, String clave) {
        return new SolicitudCobro(
                idNegocio,
                idVenta,
                ProveedorPago.MERCADO_PAGO,
                new BigDecimal("100.00"),
                "ARS",
                "Venta " + idVenta,
                clave,
                List.of(new SolicitudCobro.ItemCobro(1, 1, new BigDecimal("100.00"))),
                "https://tienda.example.com/src/index.html?payment_status=success",
                "https://tienda.example.com/src/index.html?payment_status=failure",
                "https://tienda.example.com/src/index.html?payment_status=pending",
                null);
    }

    private static void restaurar(String clave, String valorAnterior) {
        if (valorAnterior == null) {
            System.clearProperty(clave);
        } else {
            System.setProperty(clave, valorAnterior);
        }
    }
}
