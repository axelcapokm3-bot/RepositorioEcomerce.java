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
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pasarela Stripe que crea una Checkout Session y devuelve su URL de pago. */
public class PasarelaStripe implements IPasarelaPago {
    private final long idNegocio;
    private final INegocioRepository negocioRepository;

    public PasarelaStripe(long idNegocio, INegocioRepository negocioRepository) {
        this.idNegocio = idNegocio;
        this.negocioRepository = negocioRepository;
    }

    @Override
    public void validarConfiguracion() {
        ConfiguracionCheckout configuracion = configuracionesRetorno();
        if (vacio(configuracion.claveSecreta)) {
            throw new PasarelaNoDisponibleException("Stripe no está configurado para este negocio.");
        }
        if (vacio(configuracion.urlExito) || vacio(configuracion.urlCancelacion)) {
            throw new PasarelaNoDisponibleException(
                    "Faltan las URLs de retorno de Checkout de Stripe.");
        }
        if (!configuracion.urlExito.contains("{CHECKOUT_SESSION_ID}")) {
            throw new PasarelaNoDisponibleException(
                    "La URL de éxito de Stripe debe incluir {CHECKOUT_SESSION_ID}.");
        }
    }

    @Override
    public void validar(Venta venta) throws PagoRechazadoException {
        if (venta == null || venta.calculoTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PagoRechazadoException("El monto a pagar debe ser mayor a cero.");
        }
        if (venta.getId() <= 0 || venta.getDetalles() == null || venta.getDetalles().isEmpty()) {
            throw new PagoRechazadoException("La venta debe estar persistida y contener productos.");
        }
        validarConfiguracion();
        for (DetalleVenta detalle : venta.getDetalles()) {
            try {
                detalle.getPrecioUnitario().setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2).longValueExact();
            } catch (ArithmeticException e) {
                throw new PagoRechazadoException("El monto excede la precisión admitida por Stripe.");
            }
        }
    }

    @Override
    public ResultadoPago procesar(Venta venta, String claveIdempotenciaPasarela)
            throws PagoRechazadoException, PasarelaNoDisponibleException {
        validar(venta);
        if (claveIdempotenciaPasarela == null || claveIdempotenciaPasarela.isBlank()) {
            throw new PagoRechazadoException("La clave de idempotencia es obligatoria.");
        }

        ConfiguracionCheckout configuracion = configuracionesRetorno();
        List<SessionCreateParams.LineItem> items = crearItems(venta);
        SessionCreateParams.Builder parametrosCheckout = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(configuracion.urlExito)
                .setCancelUrl(configuracion.urlCancelacion)
                .setClientReferenceId(String.valueOf(venta.getId()))
                .putMetadata("gateway_idempotency_key", claveIdempotenciaPasarela)
                .putMetadata("id_venta", String.valueOf(venta.getId()))
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD);
        for (SessionCreateParams.LineItem item : items) {
            parametrosCheckout.addLineItem(item);
        }
        SessionCreateParams parametros = parametrosCheckout.build();
        RequestOptions options = RequestOptions.builder()
                .setApiKey(configuracion.claveSecreta)
                .setIdempotencyKey(claveIdempotenciaPasarela)
                .build();

        try {
            Session session = Session.create(parametros, options);
            if (session.getId() == null || session.getUrl() == null || session.getUrl().isBlank()) {
                throw new PasarelaNoDisponibleException(
                        "Stripe no devolvió el identificador y la URL de Checkout Session.");
            }
            return ResultadoPago.pendiente(String.valueOf(venta.getId()), venta.calculoTotal(), "STRIPE", session.getUrl(),
                    session.getId(), session.toJson());
        } catch (StripeException e) {
            throw new PasarelaNoDisponibleException("Error al crear Checkout Session en Stripe: " + e.getMessage());
        }
    }

    private List<SessionCreateParams.LineItem> crearItems(Venta venta) throws PagoRechazadoException {
        List<SessionCreateParams.LineItem> items = new ArrayList<>();
        for (DetalleVenta detalle : venta.getDetalles()) {
            long importeCentavos;
            try {
                importeCentavos = detalle.getPrecioUnitario()
                        .setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2)
                        .longValueExact();
            } catch (ArithmeticException e) {
                throw new PagoRechazadoException("El monto excede la precisión admitida por Stripe.");
            }

            SessionCreateParams.LineItem.PriceData.ProductData producto =
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName("Producto " + detalle.getIdProducto())
                            .build();
            SessionCreateParams.LineItem.PriceData precio =
                    SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency("ars")
                            .setUnitAmount(importeCentavos)
                            .setProductData(producto)
                            .build();
            SessionCreateParams.LineItem item = SessionCreateParams.LineItem.builder()
                    .setQuantity((long) detalle.getCantidad())
                    .setPriceData(precio)
                    .build();
            items.add(item);
        }
        return items;
    }

    private ConfiguracionCheckout configuracionesRetorno() {
        ConfiguracionCheckout configuracion = new ConfiguracionCheckout();
        configuracion.claveSecreta = buscarValor("stripe.secret-key", "stripe_secret_key");
        configuracion.urlExito = buscarValor("stripe.success-url", "stripe_success_url");
        configuracion.urlCancelacion = buscarValor("stripe.cancel-url", "stripe_cancel_url");
        return configuracion;
    }

    private String buscarValor(String clavePropiedad, String claveCatalogo) {
        String valor = ConfigLoader.getProperty(clavePropiedad);
        if (!vacio(valor)) {
            return valor;
        }
        if (negocioRepository != null) {
            Map<ConfiguracionCatalogo, String> efectivas = negocioRepository.listarConfiguracionesEfectivas(idNegocio);
            for (Map.Entry<ConfiguracionCatalogo, String> entrada : efectivas.entrySet()) {
                if (claveCatalogo.equalsIgnoreCase(entrada.getKey().getClave())
                        && !vacio(entrada.getValue())) {
                    return entrada.getValue();
                }
            }
        }
        return null;
    }

    private boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    @Override
    public TipoDePago getTipoSoportado() {
        return TipoDePago.STRIPE;
    }

    private static class ConfiguracionCheckout {
        private String claveSecreta;
        private String urlExito;
        private String urlCancelacion;
    }
}
