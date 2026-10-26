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
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Entidades.VentasEventos;
import com.CapaDominio.Excepciones.TransicionPagoInvalidaException;
import com.CapaDominio.Excepciones.WebhookProcessingException;
import com.CapaInfraestructura.Configuracion.ConfigLoader;
import com.CapaInfraestructura.Configuracion.ConfiguracionMercadoPago;
import com.CapaInfraestructura.Configuracion.SanitizadorLog;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.client.order.OrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.order.Order;
import com.mercadopago.resources.order.OrderPayment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
    private final Function<Long, ConfiguracionMercadoPago> configuracionFactory;

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
        this(negocioRepository, pagoRepository, ventaRepository, eventBus, executor,
                paymentClient, orderClient,
                idNegocio -> new ConfiguracionMercadoPago(negocioRepository, idNegocio));
    }

    public WebhookService(INegocioRepository negocioRepository, IPagoRepository pagoRepository,
                          IVentaRepository ventaRepository, EventBus eventBus,
                          PagoService pagoService) {
        this(negocioRepository, pagoRepository, ventaRepository, eventBus,
                RegistroExecutors.obtener(TipoServicio.NOTIFICACIONES),
                new PaymentClient(), new OrderClient(),
                idNegocio -> new ConfiguracionMercadoPago(negocioRepository, idNegocio),
                pagoService);
    }

    WebhookService(INegocioRepository negocioRepository, IPagoRepository pagoRepository,
                   IVentaRepository ventaRepository, EventBus eventBus,
                   IServiceExecutor executor, PaymentClient paymentClient,
                   OrderClient orderClient,
                   Function<Long, ConfiguracionMercadoPago> configuracionFactory) {
        this(negocioRepository, pagoRepository, ventaRepository, eventBus, executor,
                paymentClient, orderClient, configuracionFactory,
                new PagoService(pagoRepository, ventaRepository, eventBus, null, Map.of(), null));
    }

    WebhookService(INegocioRepository negocioRepository, IPagoRepository pagoRepository,
                   IVentaRepository ventaRepository, EventBus eventBus,
                   IServiceExecutor executor, PaymentClient paymentClient,
                   OrderClient orderClient,
                   Function<Long, ConfiguracionMercadoPago> configuracionFactory,
                   PagoService pagoService) {
        this.negocioRepository = negocioRepository;
        this.pagoRepository = pagoRepository;
        this.ventaRepository = ventaRepository;
        this.eventBus = eventBus;
        this.executor = executor;
        this.paymentClient = paymentClient;
        this.orderClient = orderClient;
        this.configuracionFactory = configuracionFactory;
        this.pagoService = pagoService;
    }

    private ConfiguracionMercadoPago configuracionPara(long idNegocio) {
        return configuracionFactory.apply(idNegocio);
    }

    private MPRequestOptions opcionesPara(String accessToken) {
        return MPRequestOptions.builder()
                .accessToken(accessToken)
                .build();
    }

    public void procesarAsync(String paymentId) {
        procesarAsync("payment", paymentId);
    }

    public void procesarAsync(String type, String id) {
        executor.ejecutar(() -> procesar(type, id));
    }

    public boolean haySecretoFirmaConfigurado() {
        return haySecretoFirmaConfigurado(null, null);
    }

    public boolean haySecretoFirmaConfigurado(String type, String id) {
        for (String secreto : secretosFirmaCandidatos(type, id)) {
            if (secreto != null && !secreto.isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** Valida x-signature según el manifiesto HMAC-SHA256 de Mercado Pago. */
    public boolean validarFirma(String paymentId, String signatureHeader, String requestId) {
        return validarFirma(null, paymentId, signatureHeader, requestId);
    }

    public boolean validarFirma(String type, String id, String signatureHeader, String requestId) {
        if (id == null || signatureHeader == null || requestId == null || requestId.isBlank()) return false;

        String timestamp = null;
        String receivedHash = null;
        for (String component : signatureHeader.split(",")) {
            String[] pair = component.trim().split("=", 2);
            if (pair.length != 2) continue;
            if ("ts".equals(pair[0])) timestamp = pair[1];
            if ("v1".equals(pair[0])) receivedHash = pair[1];
        }
        if (timestamp == null || receivedHash == null) return false;
        if (!timestampVigente(timestamp)) return false;

        String manifest = "id:" + id + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        byte[] actualHash;
        try {
            actualHash = HexFormat.of().parseHex(receivedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
        for (String secreto : secretosFirmaCandidatos(type, id)) {
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
                LOGGER.warning("No se pudo validar la firma del webhook de Mercado Pago: "
                        + SanitizadorLog.error(e));
            }
        }
        return false;
    }

    private boolean timestampVigente(String timestamp) {
        long recibido;
        try {
            recibido = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return false;
        }
        if (recibido <= 0) {
            return false;
        }
        long ahora = java.time.Instant.now().getEpochSecond();
        long tolerancia = toleranciaFirmaSegundos();
        return recibido <= ahora + tolerancia && recibido >= ahora - tolerancia;
    }

    private long toleranciaFirmaSegundos() {
        String configurada = ConfigLoader.getProperty("webhook.firma-tolerancia-segundos");
        if (configurada != null && !configurada.isBlank()) {
            try {
                long segundos = Long.parseLong(configurada.trim());
                if (segundos >= 1 && segundos <= 3600) {
                    return segundos;
                }
            } catch (NumberFormatException exception) {
                return 300L;
            }
        }
        return 300L;
    }

    private List<String> secretosFirmaCandidatos(String type, String id) {
        Long idNegocio = resolverNegocio(type, id);
        if (idNegocio == null) {
            return secretosFirmaCandidatos();
        }
        List<String> secretos = new ArrayList<>();
        String global = ConfigLoader.getProperty("mercadopago.webhook-secret");
        if (global != null && !global.isBlank()) {
            secretos.add(global);
        }
        String secretoNegocio = configuracionPara(idNegocio).obtenerSecretoWebhook();
        if (secretoNegocio != null && !secretoNegocio.isBlank() && !secretos.contains(secretoNegocio)) {
            secretos.add(secretoNegocio);
        }
        return secretos;
    }

    private Long resolverNegocio(String type, String id) {
        if (pagoRepository == null || id == null || id.isBlank()) {
            return null;
        }
        Optional<Pago> pago = Optional.empty();
        if (esTipoOrder(type)) {
            pago = pagoRepository.buscarPorOrderId(id);
        } else if (esTipoPayment(type)) {
            pago = pagoRepository.buscarPorMpPaymentId(id).filter(this::esPagoMercadoPago);
        }
        return pago.map(Pago::getIdNegocio).orElse(null);
    }

    private List<String> secretosFirmaCandidatos() {
        List<String> secretos = new ArrayList<>();
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
        try {
            procesarEvento(type, id);
        } catch (WebhookProcessingException exception) {
            LOGGER.severe("No se pudo procesar el webhook de Mercado Pago: "
                    + SanitizadorLog.error(exception));
        }
    }

    public void procesarEvento(String type, String id) throws WebhookProcessingException {
        if (id == null || id.isBlank()) {
            throw new WebhookProcessingException(
                    "No se puede procesar un webhook de Mercado Pago sin ID.", false);
        }
        try {
            if (esTipoOrder(type)) {
                procesarOrderPorNegocio(id, null);
                return;
            }
            if (esTipoPayment(type)) {
                if (!id.matches("\\d+")) {
                    throw new WebhookProcessingException(
                            "El ID de pago de Mercado Pago no es numérico.", false);
                }
                procesarPaymentMultiNegocio(id);
                return;
            }
            throw new WebhookProcessingException(
                    "Tipo de webhook de Mercado Pago no soportado: " + type, false);
        } catch (WebhookProcessingException exception) {
            throw exception;
        } catch (MPApiException exception) {
            String content = exception.getApiResponse() == null
                    ? exception.getMessage()
                    : exception.getApiResponse().getContent();
            throw new WebhookProcessingException(
                    "Mercado Pago rechazó la consulta del pago: " + content, true, exception);
        } catch (MPException exception) {
            throw new WebhookProcessingException(
                    "No se pudo consultar Mercado Pago para procesar el webhook.", true, exception);
        } catch (TransicionPagoInvalidaException exception) {
            throw new WebhookProcessingException(exception.getMessage(), false, exception);
        } catch (IllegalArgumentException exception) {
            throw new WebhookProcessingException(exception.getMessage(), false, exception);
        } catch (RuntimeException exception) {
            throw new WebhookProcessingException(
                    "No se pudo procesar la notificación de Mercado Pago.", true, exception);
        }
    }

    private void procesarOrderPorNegocio(String orderId, String paymentId) throws MPException, MPApiException {
        Optional<Pago> pagoEncontrado = pagoRepository.buscarPorOrderId(orderId)
                .filter(this::esPagoMercadoPago);
        if (pagoEncontrado.isPresent()) {
            Pago pago = pagoEncontrado.get();
            String accessToken = configuracionPara(pago.getIdNegocio()).obtenerAccessToken();
            if (accessToken == null || accessToken.isBlank()) {
                throw new WebhookProcessingException(
                        "No hay access token configurado para el negocio del pago de Mercado Pago Order "
                                + orderId + ".", true);
            }
            procesarOrder(orderId, paymentId, opcionesPara(accessToken), pago);
            return;
        }

        String accessToken = tokenUnicoParaPrimeraOrder(orderId);
        if (accessToken == null) {
            throw new WebhookProcessingException(
                    "No se pudo resolver un access token único para la primera notificación de Order "
                            + orderId + ".", true);
        }
        MPRequestOptions options = opcionesPara(accessToken);
        Order order = orderClient.get(orderId, options);
        Optional<Pago> pagoPorReferencia = pagoDeOrder(order);
        if (pagoPorReferencia.isEmpty()) {
            throw new WebhookProcessingException(
                    "No se pudo asociar la Order de Mercado Pago " + orderId
                            + " con un pago local.", true);
        }
        Pago pago = pagoPorReferencia.get();
        if (!tokenDelNegocioCoincide(pago, accessToken)) {
            throw new WebhookProcessingException(
                    "El token que leyó la Order de Mercado Pago no coincide con el negocio del pago.",
                    false);
        }
        procesarOrderVerificado(order, paymentId, pago);
    }

    private String tokenUnicoParaPrimeraOrder(String orderId) {
        if (negocioRepository == null) {
            LOGGER.warning("No hay repositorio de negocios para resolver la primera notificación de Order " + orderId);
            return null;
        }
        List<String> tokens = tokensAccesoDistintos();
        if (tokens.isEmpty()) {
            LOGGER.warning("No hay access token configurado para resolver la primera notificación de Order " + orderId);
            return null;
        }
        if (tokens.size() > 1) {
            LOGGER.warning("La primera notificación de Order " + orderId
                    + " requiere un token único; el modo multi-cuenta queda pendiente.");
            return null;
        }
        return tokens.get(0);
    }

    private void procesarPaymentMultiNegocio(String paymentId) throws MPException, MPApiException {
        if (procesarPagoPersistido(paymentId)) {
            return;
        }
        if (negocioRepository == null) {
            throw new WebhookProcessingException(
                    "No hay repositorio de negocios para resolver la notificación de Mercado Pago.",
                    true);
        }
        for (String accessToken : tokensAccesoDistintos()) {
            MPRequestOptions options = opcionesPara(accessToken);
            Payment payment;
            try {
                payment = paymentClient.get(Long.valueOf(paymentId), options);
            } catch (MPApiException | MPException e) {
                LOGGER.log(Level.FINE, "La notificación no corresponde a un token de negocio configurado: "
                        + SanitizadorLog.error(e));
                continue;
            }
            Optional<Pago> pagoEncontrado = pagoDePayment(payment);
            if (pagoEncontrado.isEmpty()) {
                continue;
            }
            Pago pago = pagoEncontrado.get();
            if (!tokenDelNegocioCoincide(pago, accessToken)) {
                LOGGER.severe("El token que leyó el pago de Mercado Pago no coincide con el configurado para el negocio del pago.");
                continue;
            }
            procesarPagoVerificado(paymentId, payment, pago, options);
            return;
        }
        throw new WebhookProcessingException(
                "No se pudo asociar la notificación de pago de Mercado Pago a ningún negocio configurado.",
                true);
    }

    private boolean procesarPagoPersistido(String paymentId) throws MPException, MPApiException {
        Optional<Pago> pagoEncontrado = pagoRepository.buscarPorMpPaymentId(paymentId)
                .filter(this::esPagoMercadoPago);
        if (pagoEncontrado.isEmpty()) {
            return false;
        }
        Pago pago = pagoEncontrado.get();
        String accessToken = configuracionPara(pago.getIdNegocio()).obtenerAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new WebhookProcessingException(
                    "No hay access token configurado para el negocio del pago de Mercado Pago.",
                    true);
        }
        MPRequestOptions options = opcionesPara(accessToken);
        Payment payment;
        try {
            payment = paymentClient.get(Long.valueOf(paymentId), options);
        } catch (MPApiException | MPException e) {
            throw new WebhookProcessingException(
                    "No se pudo confirmar el pago persistido con el token de su negocio.",
                    true, e);
        }
        procesarPagoVerificado(paymentId, payment, pago, options);
        return true;
    }

    private List<String> tokensAccesoDistintos() {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (Negocio negocio : negocioRepository.listarActivos()) {
            String accessToken = configuracionPara(negocio.getId()).obtenerAccessToken();
            if (accessToken != null && !accessToken.isBlank()) {
                tokens.add(accessToken);
            }
        }
        return new ArrayList<>(tokens);
    }

    private Optional<Pago> pagoDePayment(Payment payment) {
        if (payment.getOrder() == null || payment.getOrder().getId() == null) {
            LOGGER.warning("El pago de Mercado Pago no está asociado a una Order.");
            return Optional.empty();
        }
        String orderId = String.valueOf(payment.getOrder().getId());
        Optional<Pago> pagoEncontrado = pagoRepository.buscarPorOrderId(orderId)
                .filter(this::esPagoMercadoPago);
        if (pagoEncontrado.isPresent()) {
            return pagoEncontrado;
        }
        String paymentId = payment.getId() == null ? null : String.valueOf(payment.getId());
        return pagoPorReferenciaExterna(payment.getExternalReference(), orderId, paymentId);
    }

    private Optional<Pago> pagoDeOrder(Order order) {
        if (order == null || order.getId() == null || order.getId().isBlank()) {
            LOGGER.warning("La Order de Mercado Pago no tiene un identificador válido.");
            return Optional.empty();
        }
        return pagoPorReferenciaExterna(order.getExternalReference(), order.getId(), null);
    }

    private Optional<Pago> pagoPorReferenciaExterna(String externalReference, String orderId, String paymentId) {
        Integer idVenta = parsearIdVenta(externalReference);
        if (idVenta == null) {
            LOGGER.warning("La notificación de Mercado Pago no trae una referencia externa de venta válida.");
            return Optional.empty();
        }
        List<Pago> candidatos = pagoRepository.buscarCandidatosMercadoPagoPorVenta(idVenta);
        if (candidatos == null || candidatos.isEmpty()) {
            LOGGER.warning("No existe un pago local candidato para la venta " + idVenta
                    + " y la Order " + orderId + ".");
            return Optional.empty();
        }
        List<Pago> validos = candidatos.stream()
                .filter(this::esPagoMercadoPago)
                .filter(this::ventaDelPagoExiste)
                .toList();
        if (validos.isEmpty()) {
            LOGGER.warning("Ningún pago de la venta " + idVenta + " pertenece a su negocio.");
            return Optional.empty();
        }
        if (validos.size() > 1) {
            LOGGER.warning("La venta " + idVenta + " tiene " + validos.size()
                    + " pagos candidatos; se requiere una asociación inequívoca.");
            return Optional.empty();
        }
        Pago pago = validos.get(0);
        if (tieneOrderId(pago) && !pago.getOrderId().equals(orderId)) {
            LOGGER.warning("El Order ID de la notificación no coincide con el pago local de la venta " + idVenta + ".");
            return Optional.empty();
        }
        if (paymentId != null && !paymentId.isBlank()
                && pago.getMpPaymentId() != null && !pago.getMpPaymentId().isBlank()
                && !pago.getMpPaymentId().equals(paymentId)) {
            LOGGER.warning("El Payment ID de la notificación no coincide con el pago local de la venta " + idVenta + ".");
            return Optional.empty();
        }
        return Optional.of(pago);
    }

    private Integer parsearIdVenta(String externalReference) {
        if (externalReference == null) {
            return null;
        }
        String valor = externalReference.trim();
        if (!valor.matches("\\d+")) {
            return null;
        }
        try {
            int idVenta = Integer.parseInt(valor);
            return idVenta > 0 ? idVenta : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean ventaDelPagoExiste(Pago pago) {
        if (pago == null || ventaRepository == null) {
            return false;
        }
        Optional<Venta> venta = ventaRepository.buscarPorId(pago.getIdNegocio(), pago.getIdVenta());
        return venta.filter(v -> v.getId() == pago.getIdVenta()
                && v.getIdNegocio() == pago.getIdNegocio()).isPresent();
    }

    private boolean tieneOrderId(Pago pago) {
        return pago != null && pago.getOrderId() != null && !pago.getOrderId().isBlank();
    }

    private boolean tokenDelNegocioCoincide(Pago pago, String accessToken) {
        String tokenNegocio = configuracionPara(pago.getIdNegocio()).obtenerAccessToken();
        return tokenNegocio != null && !tokenNegocio.isBlank()
                && tokenNegocio.equals(accessToken);
    }

    private void procesarPagoVerificado(String paymentId, Payment payment, Pago pago,
                                        MPRequestOptions options) throws MPException, MPApiException {
        if (payment.getOrder() == null || payment.getOrder().getId() == null) {
            throw new WebhookProcessingException(
                    "El pago de Mercado Pago no está asociado a una Order.", false);
        }
        String orderId = String.valueOf(payment.getOrder().getId());
        if (tieneOrderId(pago) && !pago.getOrderId().equals(orderId)) {
            throw new WebhookProcessingException(
                    "La Order del pago de Mercado Pago no coincide con el pago persistido.",
                    false);
        }
        validarPago(payment, pago);
        procesarOrder(orderId, paymentId, options, pago);
    }

    private void procesarOrder(String orderId, String paymentId, MPRequestOptions options, Pago pago)
            throws MPException, MPApiException {
        Order order = orderClient.get(orderId, options);
        procesarOrderVerificado(order, paymentId, pago);
    }

    private void procesarOrderVerificado(Order order, String paymentId, Pago pago) {
        if (order == null || order.getId() == null || order.getId().isBlank()) {
            throw new WebhookProcessingException(
                    "La Order de Mercado Pago no tiene un identificador válido.", false);
        }
        validarOrder(order, pago);
        EstadoPago estado = mapearEstadoOrden(order, pago);
        String paymentIdPersistido = (paymentId == null || paymentId.isBlank())
                ? extraerPaymentId(order)
                : paymentId;
        if (pago.getMpPaymentId() != null && !pago.getMpPaymentId().isBlank()
                && paymentIdPersistido != null && !paymentIdPersistido.isBlank()
                && !pago.getMpPaymentId().equals(paymentIdPersistido)) {
            throw new WebhookProcessingException(
                    "El Payment ID de la Order no coincide con el pago persistido.", false);
        }
        pagoService.aplicarEstadoDePasarela(
                pago, estado, paymentIdPersistido, order.getId(), serializar(order));
    }

    private String extraerPaymentId(Order order) {
        if (order == null || order.getTransactions() == null
                || order.getTransactions().getPayments() == null) {
            return null;
        }
        String ultimo = null;
        for (OrderPayment pago : order.getTransactions().getPayments()) {
            if (pago == null || pago.getId() == null || pago.getId().isBlank()) {
                continue;
            }
            ultimo = pago.getId();
            if ("approved".equalsIgnoreCase(pago.getStatus())
                    || "processed".equalsIgnoreCase(pago.getStatus())) {
                return pago.getId();
            }
        }
        return ultimo;
    }

    private boolean esTipoOrder(String type) {
        return "order".equalsIgnoreCase(type) || "orders".equalsIgnoreCase(type);
    }

    private boolean esTipoPayment(String type) {
        return "payment".equalsIgnoreCase(type);
    }

    private boolean esPagoMercadoPago(Pago pago) {
        return pago != null
                && TipoDePago.MERCADO_PAGO.name().equalsIgnoreCase(pago.getPasarela());
    }

    private void validarOrder(Order order, Pago pago) {
        if (order == null || pago == null) {
            throw new IllegalArgumentException("No se puede validar una Order o un pago nulo.");
        }
        if (tieneOrderId(pago) && !pago.getOrderId().equals(order.getId())) {
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
