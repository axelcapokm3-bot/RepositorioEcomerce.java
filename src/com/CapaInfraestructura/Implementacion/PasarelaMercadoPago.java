package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IPasarelaPago;
import com.CapaInfraestructura.Configuracion.ConfigLoader;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Entidades.DetalleVenta;
import com.CapaDominio.Entidades.ResultadoPago;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Excepciones.PagoRechazadoException;
import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.client.order.OrderClient;
import com.mercadopago.client.order.OrderConfigRequest;
import com.mercadopago.client.order.OrderCreateRequest;
import com.mercadopago.client.order.OrderItemRequest;
import com.mercadopago.client.order.OrderOnlineConfig;
import com.mercadopago.resources.order.Order;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pasarela de Mercado Pago basada exclusivamente en Orders API. */
public class PasarelaMercadoPago implements IPasarelaPago {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String RETORNO_AUTOMATICO_APROBADO = "approved";

    private final int idNegocio;
    private final INegocioRepository negocioRepository;
    private final OrderClient orderClient;

    public PasarelaMercadoPago(int idNegocio, INegocioRepository negocioRepository) {
        this(idNegocio, negocioRepository, new OrderClient());
    }

    PasarelaMercadoPago(int idNegocio, INegocioRepository negocioRepository, OrderClient orderClient) {
        this.idNegocio = idNegocio;
        this.negocioRepository = negocioRepository;
        this.orderClient = orderClient;
    }

    @Override
    public void validarConfiguracion() {
        String accessToken = buscarAccessToken();
        if (vacio(accessToken)) {
            throw new PasarelaNoDisponibleException(
                    "Mercado Pago no está configurado para este negocio.");
        }
        obtenerConfiguracionRetorno();
    }

    @Override
    public void validar(Venta venta) throws PagoRechazadoException {
        if (venta == null || venta.calculoTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PagoRechazadoException("El monto a pagar debe ser mayor a cero.");
        }
        if (venta.getDetalles() == null || venta.getDetalles().isEmpty()) {
            throw new PagoRechazadoException("La venta debe incluir al menos un producto.");
        }
        validarConfiguracion();
    }

    @Override
    public ResultadoPago procesar(Venta venta, String claveIdempotenciaPasarela)
            throws PagoRechazadoException, PasarelaNoDisponibleException {
        validar(venta);
        if (claveIdempotenciaPasarela == null || claveIdempotenciaPasarela.isBlank()) {
            throw new PagoRechazadoException("La clave de idempotencia es obligatoria.");
        }

        String accessToken = buscarAccessToken();
        ConfiguracionRetorno configuracion = obtenerConfiguracionRetorno();
        try {
            MercadoPagoConfig.setAccessToken(accessToken);
            OrderCreateRequest request = crearSolicitud(venta, configuracion);
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Idempotency-Key", claveIdempotenciaPasarela);
            MPRequestOptions options = MPRequestOptions.builder().customHeaders(headers).build();

            Order order = orderClient.create(request, options);
            validarRespuesta(order);
            return ResultadoPago.pendiente(
                    String.valueOf(venta.getId()),
                    venta.calculoTotal(),
                    "MERCADO_PAGO",
                    order.getCheckoutUrl(),
                    order.getId(),
                    serializarRespuesta(order));
        } catch (MPApiException e) {
            String detalle = e.getApiResponse() == null ? e.getMessage() : e.getApiResponse().getContent();
            throw new PasarelaNoDisponibleException("Error de API de Mercado Pago: " + detalle);
        } catch (MPException e) {
            throw new PasarelaNoDisponibleException("Fallo de comunicación con Mercado Pago: " + e.getMessage());
        }
    }

    private OrderCreateRequest crearSolicitud(Venta venta, ConfiguracionRetorno retorno) {
        List<OrderItemRequest> items = new ArrayList<>();
        for (DetalleVenta detalle : venta.getDetalles()) {
            OrderItemRequest item = OrderItemRequest.builder()
                    .title("Producto " + detalle.getIdProducto())
                    .description("Producto del eCommerce")
                    .externalCode(String.valueOf(detalle.getIdProducto()))
                    .quantity(detalle.getCantidad())
                    .unitPrice(detalle.getPrecioUnitario().toPlainString())
                    .build();
            items.add(item);
        }

        OrderOnlineConfig onlineConfig = OrderOnlineConfig.builder()
                .successUrl(retorno.successUrl())
                .failureUrl(retorno.failureUrl())
                .pendingUrl(retorno.pendingUrl())
                .autoReturn(retorno.autoReturn())
                .build();
        OrderConfigRequest config = OrderConfigRequest.builder()
                .online(onlineConfig)
                .build();

        return OrderCreateRequest.builder()
                .type("online")
                .processingMode("manual")
                .externalReference(String.valueOf(venta.getId()))
                .totalAmount(venta.calculoTotal().toPlainString())
                .currency("ARS")
                .description("Venta " + venta.getId())
                .items(items)
                .config(config)
                .build();
    }

    private void validarRespuesta(Order order) throws PasarelaNoDisponibleException {
        if (order == null || order.getId() == null || order.getId().isBlank()
                || order.getCheckoutUrl() == null || order.getCheckoutUrl().isBlank()) {
            throw new PasarelaNoDisponibleException(
                    "Mercado Pago no devolvió el identificador y la URL de checkout de la Order.");
        }
    }

    private String buscarAccessToken() {
        String accessToken = ConfigLoader.getProperty("mercadopago.access-token");
        if (!vacio(accessToken)) {
            return accessToken;
        }
        return buscarConfiguracion("mercado_pago_access_token");
    }

    private ConfiguracionRetorno obtenerConfiguracionRetorno() {
        String publicBaseUrl = buscarValor(
                "mercadopago.public-base-url",
                "mercadopago_public_base_url");
        String successUrl = buscarValor(
                "mercadopago.back_url.success",
                "mercadopago_back_url_success");
        String failureUrl = buscarValor(
                "mercadopago.back_url.failure",
                "mercadopago_back_url_failure");
        String pendingUrl = buscarValor(
                "mercadopago.back_url.pending",
                "mercadopago_back_url_pending");

        return crearConfiguracionRetorno(
                publicBaseUrl,
                successUrl,
                failureUrl,
                pendingUrl);
    }

    static ConfiguracionRetorno crearConfiguracionRetorno(
            String publicBaseUrl,
            String successUrl,
            String failureUrl,
            String pendingUrl) {
        if (!vacio(publicBaseUrl)) {
            validarUrlPublica(publicBaseUrl);
            return new ConfiguracionRetorno(
                    agregarEstadoPago(publicBaseUrl, "success"),
                    agregarEstadoPago(publicBaseUrl, "failure"),
                    agregarEstadoPago(publicBaseUrl, "pending"),
                    RETORNO_AUTOMATICO_APROBADO);
        }

        return new ConfiguracionRetorno(
                validarUrlPublica(successUrl),
                validarUrlPublica(failureUrl),
                validarUrlPublica(pendingUrl),
                RETORNO_AUTOMATICO_APROBADO);
    }

    private static String agregarEstadoPago(String publicBaseUrl, String estado) {
        String url = publicBaseUrl.trim();
        int fragmento = url.indexOf('#');
        if (fragmento >= 0) {
            url = url.substring(0, fragmento);
        }

        String separador;
        int consulta = url.indexOf('?');
        if (consulta < 0) {
            separador = "?";
        } else if (url.endsWith("?") || url.endsWith("&")) {
            separador = "";
        } else {
            separador = "&";
        }

        return url + separador + "payment_status=" + estado;
    }

    private static String validarUrlPublica(String url) {
        if (vacio(url)) {
            throw new PasarelaNoDisponibleException(
                    "Configure MERCADOPAGO_PUBLIC_BASE_URL con la URL HTTPS pública del frontend.");
        }

        try {
            URI uri = new URI(url.trim());
            String host = uri.getHost();
            if (!uri.isAbsolute()
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || esHostLocal(host)) {
                throw new PasarelaNoDisponibleException(
                        "La URL de retorno de Mercado Pago debe ser HTTPS y accesible públicamente.");
            }
        } catch (URISyntaxException exception) {
            throw new PasarelaNoDisponibleException(
                    "La URL de retorno de Mercado Pago no tiene un formato válido.",
                    exception);
        }

        return url.trim();
    }

    private static boolean esHostLocal(String host) {
        String hostNormalizado = host.toLowerCase(java.util.Locale.ROOT);
        if ("localhost".equals(hostNormalizado)
                || hostNormalizado.endsWith(".localhost")
                || "0.0.0.0".equals(hostNormalizado)) {
            return true;
        }

        if (!hostNormalizado.matches("[0-9a-f:.]+")) {
            return false;
        }

        try {
            InetAddress direccion = InetAddress.getByName(hostNormalizado);
            return direccion.isLoopbackAddress() || direccion.isAnyLocalAddress();
        } catch (java.net.UnknownHostException exception) {
            return false;
        }
    }

    private String buscarValor(String clavePropiedad, String claveCatalogo) {
        String valor = ConfigLoader.getProperty(clavePropiedad);
        if (!vacio(valor)) {
            return valor;
        }
        return buscarConfiguracion(claveCatalogo);
    }

    private String buscarConfiguracion(String clave) {
        if (negocioRepository == null) {
            return null;
        }
        Map<ConfiguracionCatalogo, String> configuraciones =
                negocioRepository.listarConfiguracionesEfectivas(idNegocio);
        for (Map.Entry<ConfiguracionCatalogo, String> entrada : configuraciones.entrySet()) {
            if (clave.equalsIgnoreCase(entrada.getKey().getClave()) && !vacio(entrada.getValue())) {
                return entrada.getValue();
            }
        }
        return null;
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private String serializarRespuesta(Order order) throws PasarelaNoDisponibleException {
        try {
            return OBJECT_MAPPER.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            throw new PasarelaNoDisponibleException("No se pudo serializar la respuesta de la Order de Mercado Pago.");
        }
    }

    @Override
    public TipoDePago getTipoSoportado() {
        return TipoDePago.MERCADO_PAGO;
    }

    record ConfiguracionRetorno(
            String successUrl,
            String failureUrl,
            String pendingUrl,
            String autoReturn) {
    }
}