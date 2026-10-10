package com.CapaAplicacion.Service;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Interfaces.TipoServicio;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Entidades.EstadoPago;
import com.CapaDominio.Entidades.EstadoVenta;
import com.CapaDominio.Entidades.Pago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Entidades.VentasEventos;
import com.CapaInfraestructura.Configuracion.ConfigLoader;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.merchantorder.MerchantOrderClient;
import com.mercadopago.client.order.OrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.order.Order;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Consulta y aplica en segundo plano el estado confirmado por Mercado Pago. */
public class WebhookService {
    private static final Logger LOGGER = Logger.getLogger(WebhookService.class.getName());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CLAVE_WEBHOOK_SECRET = "mercado_pago_webhook_secret";
    private static final int ID_NEGOCIO = 1;

    private final INegocioRepository negocioRepository;
    private final IPagoRepository pagoRepository;
    private final IVentaRepository ventaRepository;
    private final EventBus eventBus;
    private final IServiceExecutor executor;
    private final PaymentClient paymentClient;
    private final OrderClient orderClient;
    private final PagoService pagoService;

    public WebhookService(INegocioRepository negocioRepository, IPagoRepository pagoRepository,
                          IVentaRepository ventaRepository, EventBus eventBus) {
        this(negocioRepository, pagoRepository, ventaRepository, eventBus,
                RegistroExecutors.obtener(TipoServicio.NOTIFICACIONES),
                new PaymentClient(), new OrderClient());
    }

    public WebhookService(INegocioRepository negocioRepository, IPagoRepository pagoRepository,
                          IVentaRepository ventaRepository, EventBus eventBus,
                          IServiceExecutor executor, PaymentClient paymentClient,
                          OrderClient orderClient) {
        this.negocioRepository = negocioRepository;
        this.pagoRepository = pagoRepository;
        this.ventaRepository = ventaRepository;
        this.eventBus = eventBus;
        this.executor = executor;
        this.paymentClient = paymentClient;
        this.orderClient = orderClient;
        this.pagoService = new PagoService(pagoRepository, ventaRepository, eventBus);
    }

    public void procesarAsync(String paymentId) {
        procesarAsync("payment", paymentId);
    }

    public void procesarAsync(String type, String id) {
        executor.ejecutar(() -> procesar(type, id));
    }

    public boolean haySecretoFirmaConfigurado() {
        String secret = buscarConfiguracion(CLAVE_WEBHOOK_SECRET);
        return secret != null && !secret.isBlank();
    }

    /** Valida x-signature según el manifiesto HMAC-SHA256 de Mercado Pago. */
    public boolean validarFirma(String paymentId, String signatureHeader, String requestId) {
        if (paymentId == null || signatureHeader == null || requestId == null || requestId.isBlank()) return false;
        String secret = buscarConfiguracion(CLAVE_WEBHOOK_SECRET);
        if (secret == null || secret.isBlank()) return false;

        String timestamp = null;
        String receivedHash = null;
        for (String component : signatureHeader.split(",")) {
            String[] pair = component.trim().split("=", 2);
            if (pair.length != 2) continue;
            if ("ts".equals(pair[0])) timestamp = pair[1];
            if ("v1".equals(pair[0])) receivedHash = pair[1];
        }
        if (timestamp == null || receivedHash == null) return false;

        try {
            String manifest = "id:" + paymentId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expectedHash = hmac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            byte[] actualHash = HexFormat.of().parseHex(receivedHash);
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "No se pudo validar la firma del webhook de Mercado Pago.", e);
            return false;
        }
    }

    public void procesar(String paymentId) {
        procesar("payment", paymentId);
    }

    public void procesar(String type, String id) {
        if (id == null || id.isBlank()) {
            LOGGER.warning("Notificación de Mercado Pago ignorada: falta el ID.");
            return;
        }
        try {
            String accessToken = buscarAccessToken();
            if (accessToken == null || accessToken.isBlank()) {
                LOGGER.severe("No hay access token configurado para validar la notificación de Mercado Pago.");
                return;
            }
            MercadoPagoConfig.setAccessToken(accessToken);
            if ("order".equalsIgnoreCase(type) || "orders".equalsIgnoreCase(type)) {
                procesarOrder(id, null);
                return;
            }
            if ("payment".equalsIgnoreCase(type)) {
                if (!id.matches("\\d+")) {
                    LOGGER.warning("Notificación de pago de Mercado Pago ignorada: ID inválido.");
                    return;
                }
                procesarPayment(id);
                return;
            }
            LOGGER.info("Notificación de Mercado Pago ignorada. Tipo no soportado: " + type);
        } catch (MPApiException e) {
            String content = e.getApiResponse() == null ? e.getMessage() : e.getApiResponse().getContent();
            LOGGER.log(Level.SEVERE, "Mercado Pago rechazó la consulta del pago: " + content, e);
        } catch (MPException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudo procesar la notificación de Mercado Pago.", e);
        }
    }

    private void procesarOrder(String orderId, String paymentId)
            throws MPException, MPApiException {
        Order order = orderClient.get(orderId);
        Optional<Pago> pagoEncontrado = pagoRepository.buscarPorOrderId(orderId);
        if (pagoEncontrado.isEmpty()) {
            LOGGER.warning("No existe un pago persistido para Mercado Pago Order " + orderId);
            return;
        }
        Pago pago = pagoEncontrado.get();
        validarOrder(order, pago);
        EstadoPago estado = mapearEstadoOrden(order, pago);
        pagoService.aplicarEstadoDePasarela(
                pago, estado, paymentId, orderId, serializar(order));
    }

    private void procesarPayment(String paymentId) throws MPException, MPApiException {
        Payment payment = paymentClient.get(Long.valueOf(paymentId));
        if (payment.getOrder() == null || payment.getOrder().getId() == null) {
            LOGGER.warning("El pago de Mercado Pago no está asociado a una Order.");
            return;
        }

        String orderId = String.valueOf(payment.getOrder().getId());
        Optional<Pago> pagoEncontrado = pagoRepository.buscarPorOrderId(orderId);
        if (pagoEncontrado.isEmpty()) {
            LOGGER.warning("No existe un pago local para Mercado Pago Order " + orderId);
            return;
        }

        Pago pago = pagoEncontrado.get();
        validarPago(payment, pago);
        procesarOrder(orderId, paymentId);
    }

    private void validarOrder(Order order, Pago pago) {
        if (order == null || pago == null) {
            throw new IllegalArgumentException("No se puede validar una Order o un pago nulo.");
        }
        if (!pago.getOrderId().equals(order.getId())) {
            throw new IllegalArgumentException("El Order ID no coincide con el pago persistido.");
        }
        if (!String.valueOf(pago.getIdVenta()).equals(order.getExternalReference())) {
            throw new IllegalArgumentException("La referencia externa de la Order no coincide con la venta.");
        }
        if (new BigDecimal(order.getTotalAmount()).compareTo(pago.getMonto()) != 0) {
            throw new IllegalArgumentException("El importe de la Order no coincide con el importe de la venta.");
        }
        if (!pago.getMoneda().equalsIgnoreCase(order.getCurrency())) {
            throw new IllegalArgumentException("La moneda de la Order no coincide con la moneda del pago.");
        }
    }

    private void validarPago(Payment payment, Pago pago) {
        if (payment.getTransactionAmount() == null
                || payment.getTransactionAmount().compareTo(pago.getMonto()) != 0) {
            throw new IllegalArgumentException("El importe del pago de Mercado Pago no coincide con la venta.");
        }
        if (!pago.getMoneda().equalsIgnoreCase(payment.getCurrencyId())) {
            throw new IllegalArgumentException("La moneda del pago de Mercado Pago no coincide con la venta.");
        }
        if (!String.valueOf(pago.getIdVenta()).equals(payment.getExternalReference())) {
            throw new IllegalArgumentException(
                    "La referencia externa del pago no coincide con la venta.");
        }
    }

    private String buscarAccessToken() {
        String token = ConfigLoader.getProperty("mercadopago.access-token");
        if (token != null && !token.isBlank()) {
            return token;
        }
        return buscarConfiguracion("mercado_pago_access_token");
    }

    private String buscarConfiguracion(String clave) {
        if ("mercado_pago_webhook_secret".equalsIgnoreCase(clave)) {
            String secret = ConfigLoader.getProperty("mercadopago.webhook-secret");
            if (secret != null && !secret.isBlank()) return secret;
        }
        if (negocioRepository == null) return null;
        Map<ConfiguracionCatalogo, String> configuraciones = negocioRepository.listarConfiguracionesEfectivas(ID_NEGOCIO);
        for (Map.Entry<ConfiguracionCatalogo, String> entry : configuraciones.entrySet()) {
            if (clave.equalsIgnoreCase(entry.getKey().getClave())
                    && entry.getValue() != null && !entry.getValue().isBlank()) {
                return entry.getValue();
            }
        }
        return null;
    }

    private EstadoPago mapearEstadoOrden(Order order, Pago pago) {
        String estado = order.getStatus();
        if ("processed".equalsIgnoreCase(estado)
                && new BigDecimal(order.getTotalPaidAmount()).compareTo(pago.getMonto()) == 0) {
            return EstadoPago.APROBADO;
        }
        if ("cancelled".equalsIgnoreCase(estado) || "expired".equalsIgnoreCase(estado)) {
            return EstadoPago.CANCELADO;
        }
        if ("failed".equalsIgnoreCase(estado)) {
            return EstadoPago.RECHAZADO;
        }
        return EstadoPago.PENDIENTE;
    }

    private String serializar(Object response) {
        try {
            return OBJECT_MAPPER.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "No se pudo serializar la respuesta verificada de Mercado Pago.", e);
        }
    }
}
