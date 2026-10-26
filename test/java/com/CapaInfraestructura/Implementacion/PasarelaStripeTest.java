package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.CapaAplicacion.puerto.EstadoPagoExterno;
import com.CapaAplicacion.puerto.ProveedorPago;
import com.CapaAplicacion.puerto.ResultadoInicio;
import com.CapaAplicacion.puerto.SolicitudCobro;
import com.CapaDominio.Excepciones.MetodoPagoNoSoportadoException;
import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;
import com.stripe.model.checkout.Session;
import com.stripe.net.ApiResource;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasarelaStripeTest {
    private static final String URL_EXITO =
            "https://shop.example.com/ok?session_id={CHECKOUT_SESSION_ID}";
    private static final String URL_CANCELACION = "https://shop.example.com/cancel";

    private final Map<String, String> anteriores = new HashMap<>();

    @BeforeEach
    void configurar() {
        fijar("app.perfil", "dev");
        fijar("stripe.secret-key", "sk_test_falsa");
        fijar("stripe.success-url", URL_EXITO);
        fijar("stripe.cancel-url", URL_CANCELACION);
        fijar("stripe.allowed-return-origins", "https://shop.example.com");
        fijar("stripe.connect-timeout-ms", "5000");
        fijar("stripe.read-timeout-ms", "5000");
    }

    @AfterEach
    void restaurar() {
        for (Map.Entry<String, String> entrada : anteriores.entrySet()) {
            if (entrada.getValue() == null) {
                System.clearProperty(entrada.getKey());
            } else {
                System.setProperty(entrada.getKey(), entrada.getValue());
            }
        }
    }

    @Test
    void creaCheckoutConIdempotenciaYTimeouts() {
        AtomicReference<SessionCreateParams> parametrosRecibidos = new AtomicReference<>();
        AtomicReference<RequestOptions> opcionesRecibidas = new AtomicReference<>();
        Session sesion = ApiResource.GSON.fromJson(
                "{\"id\":\"cs_test_123\",\"object\":\"checkout.session\","
                        + "\"url\":\"https://checkout.stripe.com/c/pay/cs_test_123\"}",
                Session.class);
        PasarelaStripe pasarela = new PasarelaStripe(null, (parametros, opciones) -> {
            parametrosRecibidos.set(parametros);
            opcionesRecibidas.set(opciones);
            return sesion;
        });

        ResultadoInicio resultado = pasarela.iniciarCobro(solicitud(URL_EXITO));

        assertEquals("cs_test_123", resultado.idExterno());
        assertEquals("https://checkout.stripe.com/c/pay/cs_test_123", resultado.urlPago());
        assertEquals(EstadoPagoExterno.PENDIENTE, resultado.estado());
        assertNotNull(resultado.rawResponse());

        SessionCreateParams parametros = parametrosRecibidos.get();
        assertNotNull(parametros);
        assertEquals(URL_EXITO, parametros.getSuccessUrl());
        assertEquals(URL_CANCELACION, parametros.getCancelUrl());
        assertEquals("42", parametros.getClientReferenceId());
        assertEquals("42", parametros.getMetadata().get("id_venta"));
        assertEquals("idem-123", parametros.getMetadata().get("gateway_idempotency_key"));
        assertEquals(1, parametros.getLineItems().size());
        assertEquals(2L, parametros.getLineItems().get(0).getQuantity());
        assertEquals(10000L, parametros.getLineItems().get(0).getPriceData().getUnitAmount());
        assertEquals("ars", parametros.getLineItems().get(0).getPriceData().getCurrency());

        RequestOptions opciones = opcionesRecibidas.get();
        assertNotNull(opciones);
        assertEquals("idem-123", opciones.getIdempotencyKey());
        assertEquals(5000, opciones.getConnectTimeout());
        assertEquals(5000, opciones.getReadTimeout());
    }

    @Test
    void exigePlaceholderDeSesionEnLaUrlDeExito() {
        PasarelaStripe pasarela = new PasarelaStripe(null, (parametros, opciones) -> {
            throw new AssertionError("No debe invocar a Stripe.");
        });

        PasarelaNoDisponibleException error = assertThrows(
                PasarelaNoDisponibleException.class,
                () -> pasarela.iniciarCobro(solicitud("https://shop.example.com/ok")));
        assertTrue(error.getMessage().contains("CHECKOUT_SESSION_ID"));
    }

    @Test
    void rechazaSolicitudDeOtroProveedor() {
        PasarelaStripe pasarela = new PasarelaStripe(null, (parametros, opciones) -> {
            throw new AssertionError("No debe invocar a Stripe.");
        });

        assertThrows(MetodoPagoNoSoportadoException.class,
                () -> pasarela.iniciarCobro(solicitud(ProveedorPago.MERCADO_PAGO, URL_EXITO)));
    }

    private SolicitudCobro solicitud(String urlExito) {
        return solicitud(ProveedorPago.STRIPE, urlExito);
    }

    private SolicitudCobro solicitud(ProveedorPago proveedor, String urlExito) {
        return new SolicitudCobro(
                7L,
                42,
                proveedor,
                new BigDecimal("200.00"),
                "ARS",
                "Venta 42",
                "idem-123",
                List.of(new SolicitudCobro.ItemCobro(5, 2, new BigDecimal("100.00"))),
                urlExito,
                URL_CANCELACION,
                urlExito,
                null);
    }

    private void fijar(String clave, String valor) {
        anteriores.putIfAbsent(clave, System.getProperty(clave));
        System.setProperty(clave, valor);
    }
}
