package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaDominio.Entidades.EstadoPago;
import com.CapaDominio.Entidades.Pago;
import com.CapaDominio.Excepciones.WebhookProcessingException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.ApiResource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StripeWebhookServiceTest {

    private static final String EVENTO = "{\"id\":\"evt_test\",\"object\":\"event\","
            + "\"api_version\":\"2024-06-20\",\"created\":1720000000,"
            + "\"data\":{\"object\":{\"id\":\"cs_test\",\"object\":\"checkout.session\","
            + "\"amount_total\":20000,\"currency\":\"ars\",\"payment_status\":\"paid\","
            + "\"client_reference_id\":\"7\",\"metadata\":{\"id_venta\":\"7\","
            + "\"gateway_idempotency_key\":\"venta-7-stripe\"}}},"
            + "\"livemode\":false,\"pending_webhooks\":1,"
            + "\"request\":{\"id\":null,\"idempotency_key\":null},"
            + "\"type\":\"checkout.session.completed\"}";

    @Test
    void reconstruyeElEventoYLaSesionDesdeElPayloadOutbox() throws Exception {
        Event event = ApiResource.GSON.fromJson(EVENTO, Event.class);

        assertEquals("evt_test", event.getId());
        assertEquals("checkout.session.completed", event.getType());
        assertInstanceOf(Session.class,
                event.getDataObjectDeserializer().deserializeUnsafe());
    }

    @Test
    void procesaUnaSesionPagadaYActualizaElPago() throws Exception {
        IPagoRepository pagoRepository = mock(IPagoRepository.class);
        PagoService pagoService = mock(PagoService.class);
        Pago pago = pago();
        when(pagoRepository.buscarPorMpPaymentId("cs_test")).thenReturn(Optional.of(pago));
        StripeWebhookService service = new StripeWebhookService(
                null, pagoRepository, mock(IVentaRepository.class),
                mock(EventBus.class), pagoService);
        Event event = ApiResource.GSON.fromJson(EVENTO, Event.class);

        service.procesarEvento(event);

        verify(pagoService).aplicarEstadoDePasarela(
                eq(pago), eq(EstadoPago.APROBADO), eq("cs_test"), isNull(), anyString());
    }

    @Test
    void fallaDeFormaReintentableSiElPagoNoExiste() {
        IPagoRepository pagoRepository = mock(IPagoRepository.class);
        when(pagoRepository.buscarPorMpPaymentId("cs_test")).thenReturn(Optional.empty());
        StripeWebhookService service = new StripeWebhookService(
                null, pagoRepository, mock(IVentaRepository.class),
                mock(EventBus.class), mock(PagoService.class));
        Event event = ApiResource.GSON.fromJson(EVENTO, Event.class);

        WebhookProcessingException exception = assertThrows(
                WebhookProcessingException.class, () -> service.procesarEvento(event));

        assertTrue(exception.isReintentable());
    }

    @Test
    void fallaDeFormaPermanenteSiElImporteNoCoincide() {
        IPagoRepository pagoRepository = mock(IPagoRepository.class);
        Pago pago = pago();
        pago.setMonto(new BigDecimal("199.00"));
        when(pagoRepository.buscarPorMpPaymentId("cs_test")).thenReturn(Optional.of(pago));
        StripeWebhookService service = new StripeWebhookService(
                null, pagoRepository, mock(IVentaRepository.class),
                mock(EventBus.class), mock(PagoService.class));
        Event event = ApiResource.GSON.fromJson(EVENTO, Event.class);

        WebhookProcessingException exception = assertThrows(
                WebhookProcessingException.class, () -> service.procesarEvento(event));

        assertFalse(exception.isReintentable());
    }

    private Pago pago() {
        Pago pago = new Pago(
                15, 7, "STRIPE", "cs_test", null, null, EstadoPago.PENDIENTE,
                new BigDecimal("200.00"), "ARS", null, "{}",
                "venta-7-stripe", LocalDateTime.now(), null);
        pago.setIdNegocio(1L);
        return pago;
    }
}
