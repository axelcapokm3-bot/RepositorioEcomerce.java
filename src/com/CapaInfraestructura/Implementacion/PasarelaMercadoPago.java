package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.puerto.EstadoPagoExterno;
import com.CapaAplicacion.puerto.EventoWebhook;
import com.CapaAplicacion.puerto.PasarelaPago;
import com.CapaAplicacion.puerto.ProveedorPago;
import com.CapaAplicacion.puerto.ResultadoInicio;
import com.CapaAplicacion.puerto.SolicitudCobro;
import com.CapaInfraestructura.Configuracion.ConfigLoader;
import com.CapaInfraestructura.Configuracion.ConfiguracionMercadoPago;
import com.CapaDominio.Excepciones.MetodoPagoNoSoportadoException;
import com.CapaDominio.Excepciones.PagoRechazadoException;
import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pasarela de Mercado Pago basada exclusivamente en Orders API. */
public class PasarelaMercadoPago implements PasarelaPago {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String RETORNO_AUTOMATICO_APROBADO = "approved";

    private final INegocioRepository negocioRepository;
    private final OrderClient orderClient;

    public PasarelaMercadoPago(INegocioRepository negocioRepository) {
        this(negocioRepository, new OrderClient());
    }

    PasarelaMercadoPago(INegocioRepository negocioRepository, OrderClient orderClient) {
        if (orderClient == null) {
            throw new IllegalArgumentException("El cliente de Orders es obligatorio.");
        }
        this.negocioRepository = negocioRepository;
        this.orderClient = orderClient;
    }

    @Override
    public ResultadoInicio iniciarCobro(SolicitudCobro solicitud) {
        validarSolicitud(solicitud);
        String accessToken = new ConfiguracionMercadoPago(negocioRepository, solicitud.idNegocio())
                .obtenerAccessToken();
        if (vacio(accessToken)) {
            throw new PasarelaNoDisponibleException(
                    "Mercado Pago no está configurado para este negocio.");
        }

        try {
            OrderCreateRequest request = crearSolicitud(solicitud);
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Idempotency-Key", solicitud.idempotencyKey());
            MPRequestOptions options = MPRequestOptions.builder()
                    .accessToken(accessToken)
                    .customHeaders(headers)
                    .build();

            Order order = orderClient.create(request, options);
            validarRespuesta(order);
            return new ResultadoInicio(
                    order.getId(),
                    order.getCheckoutUrl(),
                    EstadoPagoExterno.PENDIENTE,
                    serializarRespuesta(order));
        } catch (MPApiException e) {
            String detalle = e.getApiResponse() == null ? e.getMessage() : e.getApiResponse().getContent();
            throw new PasarelaNoDisponibleException("Error de API de Mercado Pago: " + detalle);
        } catch (MPException e) {
            throw new PasarelaNoDisponibleException("Fallo de comunicación con Mercado Pago: " + e.getMessage());
        }
    }

    @Override
    public EstadoPagoExterno consultarEstado(String idExterno) {
        throw new UnsupportedOperationException(
                "La consulta de estado requiere el contexto del negocio.");
    }

    @Override
    public EventoWebhook parsearWebhook(String payload, Map<String, String> headers, String secreto) {
        throw new UnsupportedOperationException(
                "El parseo de webhooks de Mercado Pago se habilita en el Paso 3.");
    }

    private void validarSolicitud(SolicitudCobro solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("La solicitud de cobro es obligatoria.");
        }
        if (solicitud.proveedor() != ProveedorPago.MERCADO_PAGO) {
            throw new MetodoPagoNoSoportadoException(
                    "La solicitud de cobro no corresponde a Mercado Pago.");
        }
        if (solicitud.monto() == null || solicitud.monto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PagoRechazadoException("El monto a pagar debe ser mayor a cero.");
        }
        if (solicitud.items() == null || solicitud.items().isEmpty()) {
            throw new PagoRechazadoException("La venta debe incluir al menos un producto.");
        }
        if (vacio(solicitud.urlRetorno()) || vacio(solicitud.urlCancelacion())) {
            throw new PasarelaNoDisponibleException(
                    "Faltan las URLs de retorno de Mercado Pago.");
        }
    }

    private OrderCreateRequest crearSolicitud(SolicitudCobro solicitud) {
        List<OrderItemRequest> items = new ArrayList<>();
        for (SolicitudCobro.ItemCobro itemCobro : solicitud.items()) {
            OrderItemRequest item = OrderItemRequest.builder()
                    .title("Producto " + itemCobro.idProducto())
                    .description("Producto del eCommerce")
                    .externalCode(String.valueOf(itemCobro.idProducto()))
                    .quantity(itemCobro.cantidad())
                    .unitPrice(itemCobro.precioUnitario().toPlainString())
                    .build();
            items.add(item);
        }

        String urlPendiente = vacio(solicitud.urlPendiente())
                ? solicitud.urlRetorno()
                : solicitud.urlPendiente();
        OrderOnlineConfig.OrderOnlineConfigBuilder onlineConfig = OrderOnlineConfig.builder()
                .successUrl(solicitud.urlRetorno())
                .failureUrl(solicitud.urlCancelacion())
                .pendingUrl(urlPendiente)
                .autoReturn(RETORNO_AUTOMATICO_APROBADO);
        if (!vacio(solicitud.urlWebhook())) {
            onlineConfig.callbackUrl(solicitud.urlWebhook());
        }
        OrderConfigRequest config = OrderConfigRequest.builder()
                .online(onlineConfig.build())
                .build();

        String descripcion = vacio(solicitud.descripcion())
                ? "Venta " + solicitud.idVenta()
                : solicitud.descripcion();
        return OrderCreateRequest.builder()
                .type("online")
                .processingMode("manual")
                .externalReference(String.valueOf(solicitud.idVenta()))
                .totalAmount(solicitud.monto().toPlainString())
                .currency(solicitud.moneda())
                .description(descripcion)
                .items(items)
                .config(config)
                .build();
    }

    private void validarRespuesta(Order order) {
        if (order == null || order.getId() == null || order.getId().isBlank()
                || order.getCheckoutUrl() == null || order.getCheckoutUrl().isBlank()) {
            throw new PasarelaNoDisponibleException(
                    "Mercado Pago no devolvió el identificador y la URL de checkout de la Order.");
        }
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

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private String serializarRespuesta(Order order) {
        try {
            return OBJECT_MAPPER.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            throw new PasarelaNoDisponibleException("No se pudo serializar la respuesta de la Order de Mercado Pago.");
        }
    }

    record ConfiguracionRetorno(
            String successUrl,
            String failureUrl,
            String pendingUrl,
            String autoReturn) {
    }
}
