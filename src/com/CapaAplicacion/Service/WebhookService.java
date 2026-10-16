package com.CapaAplicacion.Service;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Interfaces.TipoServicio;
import com.CapaDominio.Entidades.EstadoPago;
import com.CapaDominio.Entidades.EstadoVenta;
import com.CapaDominio.Entidades.Negocio;
import com.CapaDominio.Entidades.Pago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Entidades.VentasEventos;
import com.CapaInfraestructura.Configuracion.ConfigLoader;
import com.CapaInfraestructura.Configuracion.ConfiguracionMercadoPago;
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

    private ConfiguracionMercadoPago configuracionPara(long idNegocio) {
        return new ConfiguracionMercadoPago(negocioRepository, idNegocio);
    }

    public void procesarAsync(String paymentId) {
        procesarAsync("payment", paymentId);
    }

    public void procesarAsync(String type, String id) {
        executor.ejecutar(() -> procesar(type, id));
    }

    public boolean haySecretoFirmaConfigurado() {
        for (String secreto : secretosFirmaCandidatos()) {
            if (secreto != null && !secreto.isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** Valida x-signature según el manifiesto HMAC-SHA256 de Mercado Pago. */
    public boolean validarFirma(String paymentId, String signatureHeader, String requestId) {
        if (paymentId == null || signatureHeader == null || requestId == null || requestId.isBlank()) return false;

        String timestamp = null;
        String receivedHash = null;
        for (String component : signatureHeader.split(",")) {
            String[] pair = component.trim().split("=", 2);
            if (pair.length != 2) continue;
            if ("ts".equals(pair[0])) timestamp = pair[1];
            if ("v1".equals(pair[0])) receivedHash = pair[1];
        }
        if (timestamp == null || receivedHash == null) return false;

        String manifest = "id:" + paymentId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        byte[] actualHash;
        try {
            actualHash = HexFormat.of().parseHex(receivedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
        for (String secreto : secretosFirmaCandidatos()) {
            if (secreto == null || secreto.isBlank()) {
                continue;
            }
            try {
                Mac hmac = Mac.getInstance("HmacSHA256");
                hmac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] expectedHash = hmac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
                if (MessageDigest.isEqual(expectedHash, actualHash)) {
                    return true;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "No se pudo validar la firma del webhook de Mercado Pago.", e);
            }
        }
        return false;
    }

    private java.util.List<String> secretosFirmaCandidatos() {
        java.util.List<String> secretos = new java.util.ArrayList<>();
        String global = ConfigLoader.getProperty("mercadopago.webhook-secret");
        if (global != null && !global.isBlank()) {
            secretos.add(global);
        }
        if (negocioRepository != null) {
            for (Negocio negocio : negocioRepository.listarActivos()) {
                String secreto = configuracionPara(negocio.getId()).obtenerSecretoWebhook();
                if (secreto != null && !secreto.isBlank() && !secretos.contains(secreto)) {
                    secretos.add(secreto);
                }
            }
        }
        return secretos;
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
            if ("order".equalsIgnoreCase(type) || "orders".equalsIgnoreCase(type)) {
                procesarOrderPorNegocio(id, null);
                return;
            }
            if ("payment".equalsIgnoreCase(type)) {
                if (!id.matches("\\d+")) {
                    LOGGER.warning("Notificación de pago de Mercado Pago ignorada: ID inválido.");
                    return;
                }
                procesarPaymentMultiNegocio(id);
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

    private void procesarOrderPorNegocio(String orderId, String paymentId) throws MPException, MPApiException {
        Optional<Pago> pagoEncontrado = pagoRepository.buscarPorOrderId(orderId);
        if (pagoEncontrado.isEmpty()) {
            LOGGER.warning("No existe un pago persistido para Mercado Pago Order " + orderId);
            return;
        }
        Pago pago = pagoEncontrado.get();
        String accessToken = configuracionPara(pago.getIdNegocio()).obtenerAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            LOGGER.severe("No hay access token configurado para el negocio del pago de Mercado Pago Order " + orderId);
            return;
        }
        MercadoPagoConfig.setAccessToken(accessToken);
        procesarOrder(orderId, paymentId);
    }

    private void procesarPaymentMultiNegocio(String paymentId) {
        if (negocioRepository == null) {
            LOGGER.severe("No hay repositorio de negocios para resolver la notificación de Mercado Pago.");
            return;
        }
        for (Negocio negocio : negocioRepository.listarActivos()) {
            String accessToken = configuracionPara(negocio.getId()).obtenerAccessToken();
            if (accessToken == null || accessToken.isBlank()) {
                continue;
            }
            try {
                MercadoPagoConfig.setAccessToken(accessToken);
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
                if (pagoEncontrado.get().getIdNegocio() != negocio.getId()) {
                    continue;
                }
                validarPago(payment, pagoEncontrado.get());
                procesarOrder(orderId, paymentId);
                return;
            } catch (MPApiException | MPException e) {
                LOGGER.log(Level.FINE, "La notificación no corresponde al negocio " + negocio.getId(), e);
            }
        }
        LOGGER.warning("No se pudo asociar la notificación de pago de Mercado Pago a ningún negocio configurado.");
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
