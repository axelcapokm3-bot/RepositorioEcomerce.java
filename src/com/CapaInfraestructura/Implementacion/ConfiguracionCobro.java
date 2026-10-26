package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.puerto.ProveedorPago;
import com.CapaInfraestructura.Configuracion.ConfigLoader;
import com.CapaInfraestructura.Configuracion.ValidadorUrlRetorno;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;

import java.util.Map;

public final class ConfiguracionCobro implements com.CapaAplicacion.puerto.ConfiguracionCobro {
    private static final String RUTA_WEBHOOK_MERCADO_PAGO = "/api/webhooks/mercado-pago";
    private static final String RUTA_WEBHOOK_STRIPE = "/api/webhooks/stripe";

    private final INegocioRepository negocioRepository;

    public ConfiguracionCobro(INegocioRepository negocioRepository) {
        this.negocioRepository = negocioRepository;
    }

    @Override
    public String urlRetorno(long idNegocio, ProveedorPago proveedor) {
        return retorno(idNegocio, proveedor).exito();
    }

    @Override
    public String urlCancelacion(long idNegocio, ProveedorPago proveedor) {
        return retorno(idNegocio, proveedor).cancelacion();
    }

    @Override
    public String urlPendiente(long idNegocio, ProveedorPago proveedor) {
        return retorno(idNegocio, proveedor).pendiente();
    }

    @Override
    public String urlWebhook(long idNegocio, ProveedorPago proveedor) {
        if (proveedor == null) {
            throw new IllegalArgumentException("El proveedor de pago es obligatorio.");
        }
        String explicita = ConfigLoader.getProperty(
                proveedor == ProveedorPago.MERCADO_PAGO
                        ? "mercadopago.webhook-url"
                        : "stripe.webhook-url");
        if (!vacio(explicita)) {
            return explicita.trim();
        }
        String base = ConfigLoader.getProperty("app.backend.url");
        if (vacio(base)) {
            return null;
        }
        String ruta = proveedor == ProveedorPago.MERCADO_PAGO
                ? RUTA_WEBHOOK_MERCADO_PAGO
                : RUTA_WEBHOOK_STRIPE;
        String baseNormalizada = base.trim();
        while (baseNormalizada.endsWith("/")) {
            baseNormalizada = baseNormalizada.substring(0, baseNormalizada.length() - 1);
        }
        return baseNormalizada + ruta;
    }

    private Retorno retorno(long idNegocio, ProveedorPago proveedor) {
        if (proveedor == null) {
            throw new IllegalArgumentException("El proveedor de pago es obligatorio.");
        }
        return switch (proveedor) {
            case MERCADO_PAGO -> retornoMercadoPago(idNegocio);
            case STRIPE -> retornoStripe(idNegocio);
        };
    }

    private Retorno retornoMercadoPago(long idNegocio) {
        String publicBaseUrl = buscarValor(
                idNegocio, "mercadopago.public-base-url", "mercadopago_public_base_url");
        if (vacio(publicBaseUrl)) {
            publicBaseUrl = ConfigLoader.getProperty("mercadopago.checkout-return-url");
        }
        String exito = buscarValor(
                idNegocio, "mercadopago.back_url.success", "mercadopago_back_url_success");
        String cancelacion = buscarValor(
                idNegocio, "mercadopago.back_url.failure", "mercadopago_back_url_failure");
        String pendiente = buscarValor(
                idNegocio, "mercadopago.back_url.pending", "mercadopago_back_url_pending");
        PasarelaMercadoPago.ConfiguracionRetorno configuracion =
                PasarelaMercadoPago.crearConfiguracionRetorno(
                        publicBaseUrl, exito, cancelacion, pendiente);
        return new Retorno(
                configuracion.successUrl(),
                configuracion.failureUrl(),
                configuracion.pendingUrl());
    }

    private Retorno retornoStripe(long idNegocio) {
        String origenesPermitidos = ConfigLoader.getProperty("stripe.allowed-return-origins");
        if (vacio(origenesPermitidos)) {
            origenesPermitidos = ConfigLoader.getProperty("app.frontend.url");
        }
        boolean produccion = "prod".equalsIgnoreCase(ConfigLoader.getProperty("app.perfil"));
        String exito = ValidadorUrlRetorno.validar(
                buscarValor(idNegocio, "stripe.success-url", "stripe_success_url"),
                "éxito",
                origenesPermitidos,
                produccion);
        String cancelacion = ValidadorUrlRetorno.validar(
                buscarValor(idNegocio, "stripe.cancel-url", "stripe_cancel_url"),
                "cancelación",
                origenesPermitidos,
                produccion);
        return new Retorno(exito, cancelacion, exito);
    }

    private String buscarValor(long idNegocio, String clavePropiedad, String claveCatalogo) {
        String valor = ConfigLoader.getProperty(clavePropiedad);
        if (!vacio(valor)) {
            return valor;
        }
        if (negocioRepository == null) {
            return null;
        }
        Map<ConfiguracionCatalogo, String> configuraciones =
                negocioRepository.listarConfiguracionesEfectivas(idNegocio);
        if (configuraciones == null) {
            return null;
        }
        for (Map.Entry<ConfiguracionCatalogo, String> entrada : configuraciones.entrySet()) {
            if (entrada.getKey() != null
                    && claveCatalogo.equalsIgnoreCase(entrada.getKey().getClave())
                    && !vacio(entrada.getValue())) {
                return entrada.getValue();
            }
        }
        return null;
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private record Retorno(String exito, String cancelacion, String pendiente) {
    }
}
