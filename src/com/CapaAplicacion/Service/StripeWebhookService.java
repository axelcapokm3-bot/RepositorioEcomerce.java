package com.CapaAplicacion.Service;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Interfaces.TipoServicio;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Entidades.EstadoPago;
import com.CapaDominio.Entidades.Pago;
import com.CapaInfraestructura.Configuracion.ConfigLoader;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;

import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Valida eventos Stripe y actualiza el pago/venta en segundo plano. */
public class StripeWebhookService {
    private static final Logger LOGGER = Logger.getLogger(StripeWebhookService.class.getName());

    private final INegocioRepository negocioRepository;
    private final IPagoRepository pagoRepository;
    private final IVentaRepository ventaRepository;
    private final com.CapaAplicacion.Interfaces.EventBus eventBus;
    private final IServiceExecutor executor;
    private final PagoService pagoService;

    public StripeWebhookService(INegocioRepository negocioRepository, IPagoRepository pagoRepository,
                                IVentaRepository ventaRepository,
                                com.CapaAplicacion.Interfaces.EventBus eventBus) {
        this(negocioRepository, pagoRepository, ventaRepository, eventBus,
                RegistroExecutors.obtener(TipoServicio.NOTIFICACIONES));
    }

    public StripeWebhookService(INegocioRepository negocioRepository, IPagoRepository pagoRepository,
                                IVentaRepository ventaRepository,
                                com.CapaAplicacion.Interfaces.EventBus eventBus,
                                IServiceExecutor executor) {
        this.negocioRepository = negocioRepository;
        this.pagoRepository = pagoRepository;
        this.ventaRepository = ventaRepository;
        this.eventBus = eventBus;
        this.executor = executor;
        this.pagoService = new PagoService(pagoRepository, ventaRepository, eventBus);
    }

    public boolean haySecretoFirmaConfigurado() {
        String secret = buscarSecreto();
        return secret != null && !secret.isBlank();
    }

    public Event validarEvento(String payload, String signatureHeader) throws SignatureVerificationException {
        String secret = buscarSecreto();
        if (secret == null || secret.isBlank()) throw new IllegalStateException("El secreto de webhook de Stripe no está configurado.");
        if (signatureHeader == null || signatureHeader.isBlank()) throw new SignatureVerificationException("Falta Stripe-Signature.", "");
        return Webhook.constructEvent(payload, signatureHeader, secret);
    }

    public void procesarAsync(Event event) {
        executor.ejecutar(() -> procesar(event));
    }

    public void procesar(Event event) {
        if (event == null) return;
        String tipo = event.getType();
        if (!esEventoCheckout(tipo)) {
            return;
        }

        try {
            StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
            if (!(stripeObject instanceof Session session)) {
                LOGGER.warning("Evento Stripe ignorado: no se pudo deserializar Checkout Session.");
                return;
            }
            Optional<Pago> pagoOpt = pagoRepository.buscarPorMpPaymentId(session.getId());
            if (pagoOpt.isEmpty()) {
                LOGGER.warning("No se encontró un intento persistido para Stripe Session " + session.getId());
                return;
            }
            Pago pago = pagoOpt.get();
            validarSesion(session, pago);
            EstadoPago estado = mapearEstado(tipo, session);
            pagoService.aplicarEstadoDePasarela(
                    pago, estado, session.getId(), null, session.toJson());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudo procesar el webhook de Stripe.", e);
        }
    }

    private boolean esEventoCheckout(String tipo) {
        return "checkout.session.completed".equals(tipo)
                || "checkout.session.async_payment_succeeded".equals(tipo)
                || "checkout.session.async_payment_failed".equals(tipo)
                || "checkout.session.expired".equals(tipo);
    }

    private void validarSesion(Session session, Pago pago) {
        if (!"STRIPE".equals(pago.getPasarela())) {
            throw new IllegalArgumentException("La sesión de Stripe no corresponde a la pasarela del pago.");
        }

        Map<String, String> metadata = session.getMetadata();
        String idVenta = metadata == null ? null : metadata.get("id_venta");
        String claveIdempotenciaPasarela = metadata == null
                ? null
                : metadata.get("gateway_idempotency_key");
        String referenciaCliente = session.getClientReferenceId();
        if (!String.valueOf(pago.getIdVenta()).equals(idVenta)
                || !String.valueOf(pago.getIdVenta()).equals(referenciaCliente)
                || pago.getClaveIdempotenciaPasarela() == null
                || !pago.getClaveIdempotenciaPasarela().equals(claveIdempotenciaPasarela)) {
            throw new IllegalArgumentException(
                    "La venta o la clave de idempotencia de Stripe no coincide con el pago.");
        }

        Long importeCentavos = session.getAmountTotal();
        if (importeCentavos == null) {
            throw new IllegalArgumentException("La sesión de Stripe no contiene un importe verificable.");
        }
        BigDecimal importe = BigDecimal.valueOf(importeCentavos, 2);
        if (importe.compareTo(pago.getMonto()) != 0) {
            throw new IllegalArgumentException("El importe de Stripe no coincide con el pago persistido.");
        }
        if (session.getCurrency() == null
                || !pago.getMoneda().equalsIgnoreCase(session.getCurrency())) {
            throw new IllegalArgumentException("La moneda de Stripe no coincide con el pago persistido.");
        }
    }

    private EstadoPago mapearEstado(String tipo, Session session) {
        if ("checkout.session.async_payment_failed".equals(tipo)) {
            return EstadoPago.RECHAZADO;
        }
        if ("checkout.session.expired".equals(tipo)) {
            return EstadoPago.CANCELADO;
        }
        if ("paid".equalsIgnoreCase(session.getPaymentStatus())) {
            return EstadoPago.APROBADO;
        }
        return EstadoPago.PENDIENTE;
    }

    private String buscarSecreto() {
        String secreto = ConfigLoader.getProperty("stripe.webhook-secret");
        if (secreto != null && !secreto.isBlank()) {
            return secreto;
        }
        if (negocioRepository == null) {
            return null;
        }

        Map<ConfiguracionCatalogo, String> configuraciones =
                negocioRepository.listarConfiguracionesEfectivas(1);
        for (Map.Entry<ConfiguracionCatalogo, String> entry : configuraciones.entrySet()) {
            if ("stripe_webhook_secret".equalsIgnoreCase(entry.getKey().getClave())
                    && entry.getValue() != null && !entry.getValue().isBlank()) {
                return entry.getValue();
            }
        }
        return null;
    }
}
