package com.CapaAplicacion.Service;

import com.CapaAplicacion.Interfaces.IProcesadorEventoWebhook;
import com.CapaDominio.Entidades.EventoWebhook;
import com.CapaDominio.Entidades.ProveedorWebhook;
import com.CapaDominio.Excepciones.WebhookProcessingException;
import com.stripe.model.Event;
import com.stripe.net.ApiResource;

/** Despacha un evento Outbox al servicio de la pasarela correspondiente. */
public class ProcesadorEventosWebhook implements IProcesadorEventoWebhook {
    private final WebhookService mercadoPagoWebhookService;
    private final StripeWebhookService stripeWebhookService;

    public ProcesadorEventosWebhook(WebhookService mercadoPagoWebhookService,
                                    StripeWebhookService stripeWebhookService) {
        if (mercadoPagoWebhookService == null || stripeWebhookService == null) {
            throw new IllegalArgumentException("Los servicios de webhook son obligatorios.");
        }
        this.mercadoPagoWebhookService = mercadoPagoWebhookService;
        this.stripeWebhookService = stripeWebhookService;
    }

    @Override
    public void procesar(EventoWebhook evento) throws WebhookProcessingException {
        if (evento == null || evento.getProveedor() == null) {
            throw new WebhookProcessingException(
                    "El evento de webhook no tiene proveedor.", false);
        }
        if (evento.getProveedor() == ProveedorWebhook.MERCADO_PAGO) {
            mercadoPagoWebhookService.procesarEvento(evento.getTipo(), evento.getEventoId());
            return;
        }
        if (evento.getProveedor() == ProveedorWebhook.STRIPE) {
            Event stripeEvent;
            try {
                stripeEvent = ApiResource.GSON.fromJson(evento.getPayload(), Event.class);
            } catch (RuntimeException exception) {
                throw new WebhookProcessingException(
                        "No se pudo reconstruir el evento de Stripe desde el payload.",
                        false, exception);
            }
            if (stripeEvent == null || stripeEvent.getId() == null
                    || !stripeEvent.getId().equals(evento.getEventoId())) {
                throw new WebhookProcessingException(
                        "El payload de Stripe no coincide con el evento registrado.", false);
            }
            stripeWebhookService.procesarEvento(stripeEvent);
            return;
        }
        throw new WebhookProcessingException(
                "Proveedor de webhook no soportado: " + evento.getProveedor(), false);
    }
}
