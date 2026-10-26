package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.puerto.ProveedorPago;
import com.CapaInfraestructura.Configuracion.ConfigLoader;
import com.CapaInfraestructura.Configuracion.ConfiguracionMercadoPago;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;

import java.util.Map;

public final class ConfiguracionWebhook implements com.CapaAplicacion.puerto.ConfiguracionWebhook {
    private final INegocioRepository negocioRepository;

    public ConfiguracionWebhook(INegocioRepository negocioRepository) {
        this.negocioRepository = negocioRepository;
    }

    @Override
    public String secreto(long idNegocio, ProveedorPago proveedor) {
        if (proveedor == null) {
            throw new IllegalArgumentException("El proveedor de pago es obligatorio.");
        }
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El negocio del webhook debe ser válido.");
        }
        return switch (proveedor) {
            case MERCADO_PAGO -> secretoMercadoPago(idNegocio);
            case STRIPE -> secretoStripe(idNegocio);
        };
    }

    private String secretoMercadoPago(long idNegocio) {
        String secreto = new ConfiguracionMercadoPago(negocioRepository, idNegocio)
                .obtenerSecretoWebhook();
        if (!vacio(secreto)) {
            return secreto;
        }
        return valorNoVacio(ConfigLoader.getProperty("mercadopago.webhook-secret"));
    }

    private String secretoStripe(long idNegocio) {
        if (negocioRepository != null) {
            Map<ConfiguracionCatalogo, String> configuraciones =
                    negocioRepository.listarConfiguracionesEfectivas(idNegocio);
            if (configuraciones != null) {
                for (Map.Entry<ConfiguracionCatalogo, String> entrada : configuraciones.entrySet()) {
                    if (entrada.getKey() != null
                            && "stripe_webhook_secret".equalsIgnoreCase(entrada.getKey().getClave())
                            && !vacio(entrada.getValue())) {
                        return entrada.getValue();
                    }
                }
            }
        }
        return valorNoVacio(ConfigLoader.getSecretProperty("stripe.webhook-secret"));
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String valorNoVacio(String valor) {
        return vacio(valor) ? null : valor;
    }
}
