package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.puerto.EstadoPagoExterno;
import com.CapaAplicacion.puerto.EventoWebhook;
import com.CapaAplicacion.puerto.PasarelaPago;
import com.CapaAplicacion.puerto.ProveedorPago;
import com.CapaAplicacion.puerto.ResultadoInicio;
import com.CapaAplicacion.puerto.SolicitudCobro;
import com.CapaInfraestructura.Configuracion.ConfigLoader;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Excepciones.MetodoPagoNoSoportadoException;
import com.CapaDominio.Excepciones.PagoRechazadoException;
import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pasarela Stripe que crea una Checkout Session y devuelve su URL de pago. */
public class PasarelaStripe implements PasarelaPago {
    private final INegocioRepository negocioRepository;
    private final CreadorSesion creadorSesion;

    public PasarelaStripe(INegocioRepository negocioRepository) {
        this(negocioRepository, Session::create);
    }

    PasarelaStripe(INegocioRepository negocioRepository, CreadorSesion creadorSesion) {
        if (creadorSesion == null) {
            throw new IllegalArgumentException("El creador de Checkout Session es obligatorio.");
        }
        this.negocioRepository = negocioRepository;
        this.creadorSesion = creadorSesion;
    }

    @FunctionalInterface
    interface CreadorSesion {
        Session crear(SessionCreateParams parametros, RequestOptions opciones) throws StripeException;
    }

    @Override
    public ResultadoInicio iniciarCobro(SolicitudCobro solicitud) {
        validarSolicitud(solicitud);
        String claveSecreta = buscarValor(solicitud.idNegocio(), "stripe.secret-key", "stripe_secret_key");
        if (vacio(claveSecreta)) {
            throw new PasarelaNoDisponibleException("Stripe no está configurado para este negocio.");
        }

        List<SessionCreateParams.LineItem> items = crearItems(solicitud);
        SessionCreateParams.Builder parametrosCheckout = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(solicitud.urlRetorno())
                .setCancelUrl(solicitud.urlCancelacion())
                .setClientReferenceId(String.valueOf(solicitud.idVenta()))
                .putMetadata("gateway_idempotency_key", solicitud.idempotencyKey())
                .putMetadata("id_venta", String.valueOf(solicitud.idVenta()))
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD);
        for (SessionCreateParams.LineItem item : items) {
            parametrosCheckout.addLineItem(item);
        }
        RequestOptions options = RequestOptions.builder()
                .setApiKey(claveSecreta)
                .setIdempotencyKey(solicitud.idempotencyKey())
                .setConnectTimeout(enteroConfigurado("stripe.connect-timeout-ms", 5000, 500, 60000))
                .setReadTimeout(enteroConfigurado("stripe.read-timeout-ms", 5000, 1000, 120000))
                .build();

        try {
            Session session = creadorSesion.crear(parametrosCheckout.build(), options);
            if (session.getId() == null || session.getUrl() == null || session.getUrl().isBlank()) {
                throw new PasarelaNoDisponibleException(
                        "Stripe no devolvió el identificador y la URL de Checkout Session.");
            }
            return new ResultadoInicio(
                    session.getId(),
                    session.getUrl(),
                    EstadoPagoExterno.PENDIENTE,
                    session.toJson());
        } catch (StripeException e) {
            throw new PasarelaNoDisponibleException("Error al crear Checkout Session en Stripe: " + e.getMessage());
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
                "El parseo de webhooks de Stripe se habilita en el Paso 3.");
    }

    private void validarSolicitud(SolicitudCobro solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("La solicitud de cobro es obligatoria.");
        }
        if (solicitud.proveedor() != ProveedorPago.STRIPE) {
            throw new MetodoPagoNoSoportadoException(
                    "La solicitud de cobro no corresponde a Stripe.");
        }
        if (solicitud.monto() == null || solicitud.monto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PagoRechazadoException("El monto a pagar debe ser mayor a cero.");
        }
        if (solicitud.items() == null || solicitud.items().isEmpty()) {
            throw new PagoRechazadoException("La venta debe incluir al menos un producto.");
        }
        if (vacio(solicitud.urlRetorno()) || vacio(solicitud.urlCancelacion())) {
            throw new PasarelaNoDisponibleException(
                    "Faltan las URLs de retorno de Checkout de Stripe.");
        }
        if (!solicitud.urlRetorno().contains("{CHECKOUT_SESSION_ID}")) {
            throw new PasarelaNoDisponibleException(
                    "La URL de éxito de Stripe debe incluir {CHECKOUT_SESSION_ID}.");
        }
        for (SolicitudCobro.ItemCobro item : solicitud.items()) {
            try {
                item.precioUnitario().setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2).longValueExact();
            } catch (ArithmeticException e) {
                throw new PagoRechazadoException("El monto excede la precisión admitida por Stripe.");
            }
        }
    }

    private List<SessionCreateParams.LineItem> crearItems(SolicitudCobro solicitud) {
        List<SessionCreateParams.LineItem> items = new ArrayList<>();
        for (SolicitudCobro.ItemCobro itemCobro : solicitud.items()) {
            long importeCentavos;
            try {
                importeCentavos = itemCobro.precioUnitario()
                        .setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2)
                        .longValueExact();
            } catch (ArithmeticException e) {
                throw new PagoRechazadoException("El monto excede la precisión admitida por Stripe.");
            }

            SessionCreateParams.LineItem.PriceData.ProductData producto =
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName("Producto " + itemCobro.idProducto())
                            .build();
            SessionCreateParams.LineItem.PriceData precio =
                    SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(solicitud.moneda().toLowerCase(Locale.ROOT))
                            .setUnitAmount(importeCentavos)
                            .setProductData(producto)
                            .build();
            SessionCreateParams.LineItem item = SessionCreateParams.LineItem.builder()
                    .setQuantity((long) itemCobro.cantidad())
                    .setPriceData(precio)
                    .build();
            items.add(item);
        }
        return items;
    }

    private String buscarValor(long idNegocio, String clavePropiedad, String claveCatalogo) {
        String valor = "stripe.secret-key".equals(clavePropiedad)
                ? ConfigLoader.getSecretProperty(clavePropiedad)
                : ConfigLoader.getProperty(clavePropiedad);
        if (!vacio(valor)) {
            return valor;
        }
        if (negocioRepository != null) {
            Map<ConfiguracionCatalogo, String> efectivas =
                    negocioRepository.listarConfiguracionesEfectivas(idNegocio);
            if (efectivas != null) {
                for (Map.Entry<ConfiguracionCatalogo, String> entrada : efectivas.entrySet()) {
                    if (entrada.getKey() != null
                            && claveCatalogo.equalsIgnoreCase(entrada.getKey().getClave())
                            && !vacio(entrada.getValue())) {
                        return entrada.getValue();
                    }
                }
            }
        }
        return null;
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static int enteroConfigurado(String clave, int predeterminado, int minimo, int maximo) {
        String valor = ConfigLoader.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            return predeterminado;
        }
        try {
            int numero = Integer.parseInt(valor.trim());
            return numero >= minimo && numero <= maximo ? numero : predeterminado;
        } catch (NumberFormatException exception) {
            return predeterminado;
        }
    }
}
